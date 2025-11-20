package com.example.frogdetection.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import ai.onnxruntime.*
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class FrogDetectionResult(
    val label: String,
    val score: Float,
    val box: RectF
)

object FrogDetectionHelper {
    private const val TAG = "🐸FrogDetectionHelper"

    // MODEL & PREPROCESS
    private const val MODEL_ASSET = "frog_yolo11_detect_simplified.onnx"
    private const val LABELS_ASSET = "labels.txt"
    private const val INPUT_SIZE = 640         // confirmed by you
    private const val CONF_THRESHOLD = 0.25f   // tune as needed
    private const val NMS_IOU = 0.45f
    private const val MAX_RESULTS = 50
    private const val DEBUG = true

    // ONNX runtime handles (initialized once)
    @Volatile private var env: OrtEnvironment? = null
    @Volatile private var session: OrtSession? = null

    // labels and caches
    private var labels: List<String> = emptyList()

    // grid centers (in letterbox-space pixels) and stride map
    private var centerX: FloatArray? = null
    private var centerY: FloatArray? = null
    private var strideMap: FloatArray? = null
    private var anchorCount = 0

    // -------------------------
    // Initialization
    // -------------------------
    @Synchronized
    fun init(context: Context) {
        if (session != null && env != null) return

        try {
            env = OrtEnvironment.getEnvironment()

            // copy model from assets to filesDir
            val outFile = File(context.filesDir, MODEL_ASSET)
            if (!outFile.exists()) {
                context.assets.open(MODEL_ASSET).use { input ->
                    FileOutputStream(outFile).use { fos ->
                        val buf = ByteArray(8 * 1024)
                        var r = input.read(buf)
                        while (r > 0) {
                            fos.write(buf, 0, r)
                            r = input.read(buf)
                        }
                    }
                }
            }
            Log.d(TAG, "✅ Model ready at: ${outFile.absolutePath}")

            // session opts (mobile-safe)
            val opts = OrtSession.SessionOptions()
            try {
                opts.addCPU(true)
                Log.d(TAG, "✅ addCPU(true) applied")
            } catch (t: Throwable) {
                Log.w(TAG, "⚠ addCPU(true) failed: ${t.message}")
            }

            session = env!!.createSession(outFile.absolutePath, opts)
            labels = loadLabels(context)
            buildGridStrideCache() // prepare center arrays
            Log.d(TAG, "✅ ONNX session ready. labels=${labels.size} anchorCount=$anchorCount")
        } catch (e: Exception) {
            Log.e(TAG, "❌ init failed: ${e.message}", e)
            try { env?.close() } catch (_: Exception) {}
            env = null
            session = null
        }
    }

    private fun loadLabels(context: Context): List<String> {
        return try {
            context.assets.open(LABELS_ASSET).bufferedReader().useLines { seq ->
                seq.map { it.trim() }.filter { it.isNotEmpty() }.toList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "labels.txt missing; using fallback labels")
            listOf(
                "Asian Painted Frog",
                "Cane Toad",
                "Common Southeast Asian Tree Frog",
                "East Asian Bullfrog",
                "Paddy Field Frog",
                "Wood Frog"
            )
        }
    }

    // -------------------------
    // Grid / stride caches
    // -------------------------
    private fun buildGridStrideCache(imgSize: Int = INPUT_SIZE) {
        // Strides and corresponding feature map sizes for 640:
        val strides = intArrayOf(8, 16, 32) // P3,P4,P5
        val cxList = ArrayList<Float>()
        val cyList = ArrayList<Float>()
        val sList = ArrayList<Float>()

        for (s in strides) {
            val fm = imgSize / s
            for (y in 0 until fm) {
                for (x in 0 until fm) {
                    // center of cell in letterbox pixel coordinates
                    val centerX = (x + 0.5f) * s
                    val centerY = (y + 0.5f) * s
                    cxList.add(centerX)
                    cyList.add(centerY)
                    sList.add(s.toFloat())
                }
            }
        }
        anchorCount = cxList.size
        centerX = FloatArray(anchorCount) { cxList[it] }
        centerY = FloatArray(anchorCount) { cyList[it] }
        strideMap = FloatArray(anchorCount) { sList[it] }
        Log.d(TAG, "✅ grid built: anchors=$anchorCount (expected 8400 for 640)")
    }

    // -------------------------
    // Preprocess: letterbox -> CHW float tensor
    // -------------------------
    private data class Prep(val tensor: OnnxTensor, val scale: Float, val padX: Float, val padY: Float)

    private fun makeLetterboxTensor(bitmap: Bitmap): Prep {
        val w = bitmap.width
        val h = bitmap.height
        val scale = min(INPUT_SIZE / w.toFloat(), INPUT_SIZE / h.toFloat())
        val newW = (w * scale).roundToInt()
        val newH = (h * scale).roundToInt()
        val resized = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        val padded = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(padded)
        canvas.drawColor(android.graphics.Color.rgb(114, 114, 114))
        val padX = ((INPUT_SIZE - newW) / 2f)
        val padY = ((INPUT_SIZE - newH) / 2f)
        canvas.drawBitmap(resized, padX, padY, null)

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        padded.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        val plane = INPUT_SIZE * INPUT_SIZE
        val floats = FloatArray(3 * plane)

        for (i in 0 until plane) {
            val p = pixels[i]
            floats[i] = ((p shr 16) and 0xFF) / 255.0f
            floats[i + plane] = ((p shr 8) and 0xFF) / 255.0f
            floats[i + 2 * plane] = (p and 0xFF) / 255.0f
        }

        val fb = FloatBuffer.wrap(floats)
        val tensor = OnnxTensor.createTensor(env, fb, longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()))
        return Prep(tensor, scale, padX, padY)
    }

    // -------------------------
    // Helpers
    // -------------------------
    private fun sigmoid(x: Float) = 1f / (1f + exp(-x))

    private fun iou(a: RectF, b: RectF): Float {
        val l = max(a.left, b.left)
        val t = max(a.top, b.top)
        val r = min(a.right, b.right)
        val bb = min(a.bottom, b.bottom)
        val w = max(0f, r - l)
        val h = max(0f, bb - t)
        val inter = w * h
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun nms(boxes: MutableList<FrogDetectionResult>): List<FrogDetectionResult> {
        val keep = ArrayList<FrogDetectionResult>()
        boxes.sortByDescending { it.score }
        while (boxes.isNotEmpty() && keep.size < MAX_RESULTS) {
            val best = boxes.removeAt(0)
            keep.add(best)
            val it = boxes.iterator()
            while (it.hasNext()) {
                val other = it.next()
                if (iou(best.box, other.box) > NMS_IOU) it.remove()
            }
        }
        return keep
    }

    // -------------------------
    // Public detection API
    // -------------------------
    /**
     * Convenience: init+detect (keeps compatibility with older call-sites)
     * Calls init(context) if not already initialized.
     * Call from background thread.
     */
    fun detectFrogs(context: Context, bitmap: Bitmap): List<FrogDetectionResult> {
        init(context)
        return detectFrogs(bitmap)
    }

    /**
     * Run detection on letterboxed bitmap.
     * Must be called from background thread (Dispatchers.IO).
     */
    fun detectFrogs(bitmap: Bitmap): List<FrogDetectionResult> {
        val sess = session ?: run {
            Log.e(TAG, "detectFrogs: session not initialized. Call init(context) first.")
            return emptyList()
        }

        val prep = try {
            makeLetterboxTensor(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Preprocess failed: ${e.message}", e)
            return emptyList()
        }

        try {
            val inputName = sess.inputNames.firstOrNull() ?: run {
                prep.tensor.close()
                Log.e(TAG, "Session has no inputs")
                return emptyList()
            }

            val outputs = sess.run(mapOf(inputName to prep.tensor))
            if (outputs == null || outputs.size() == 0) {
                prep.tensor.close()
                outputs?.close()
                Log.w(TAG, "Empty ONNX outputs")
                return emptyList()
            }

            val firstValue = outputs.get(0).value
            val detections = mutableListOf<FrogDetectionResult>()

            // Expecting shape [1, C, N] or [C, N] or [N, C] — transpose to preds[N][C]
            if (firstValue is Array<*>) {
                val d0 = firstValue.size
                if (d0 > 0) {
                    val el0 = firstValue[0]
                    if (el0 is Array<*>) {
                        // [batch, C, N] -> take batch 0
                        val batch = d0
                        val C = el0.size
                        val N = if (C > 0) (el0[0] as? FloatArray)?.size ?: -1 else -1
                        Log.d(TAG, "📊 ONNX raw dims: batch=$batch, C=$C, N=$N")
                        val layer0 = el0 as Array<*>
                        if (N > 0) {
                            val preds = Array(N) { FloatArray(C) }
                            for (c in 0 until C) {
                                val arrC = layer0[c] as? FloatArray ?: continue
                                for (n in 0 until N) preds[n][c] = arrC[n]
                            }
                            parseAndDecode(preds, prep, bitmap.width, bitmap.height, detections)
                        } else {
                            Log.w(TAG, "Unexpected N in 3D output (N=$N)")
                        }
                    } else if (el0 is FloatArray) {
                        // firstValue is Array<FloatArray> (2D) -> could be [C,N] or [N,C]
                        val arr = firstValue as Array<FloatArray>
                        val rows = arr.size
                        val cols = if (rows > 0) arr[0].size else 0
                        Log.d(TAG, "📊 ONNX 2D raw dims: rows=$rows cols=$cols")
                        if (rows <= 64 && cols > 0) {
                            // likely [C, N] -> transpose
                            val C = rows
                            val N = cols
                            val preds = Array(N) { FloatArray(C) }
                            for (r in 0 until rows) {
                                val rowArr = arr[r]
                                for (c in 0 until cols) preds[c][r] = rowArr[c]
                            }
                            parseAndDecode(preds, prep, bitmap.width, bitmap.height, detections)
                        } else {
                            // treat as [N, C]
                            parseAndDecode(arr, prep, bitmap.width, bitmap.height, detections)
                        }
                    } else {
                        Log.w(TAG, "Unhandled nested type for output element: ${el0?.javaClass}")
                    }
                } else {
                    Log.w(TAG, "Output array zero-length")
                }
            } else {
                Log.w(TAG, "Unrecognized ONNX output type: ${firstValue?.javaClass}")
            }

            outputs.close()
            prep.tensor.close()

            if (detections.isEmpty()) return emptyList()
            return nms(detections.toMutableList())
        } catch (e: Exception) {
            Log.e(TAG, "Detection failed: ${e.message}", e)
            try { prep.tensor.close() } catch (_: Exception) {}
            return emptyList()
        }
    }

    // -------------------------
    // Parsing & decoding for your model:
    // preds: Array[N][C] where C == 10 (4 box distances + 6 class logits)
    // -------------------------
    private fun parseAndDecode(
        preds: Array<FloatArray>,
        prep: Prep,
        origW: Int,
        origH: Int,
        outList: MutableList<FrogDetectionResult>
    ) {
        val N = preds.size
        if (N == 0) return
        val C = preds[0].size
        if (C < 5) return

        // Expectation: C = 4 + numClasses (here numClasses = 6)
        val numClassCandidates = C - 4
        if (numClassCandidates <= 0) return

        val cxArr = centerX ?: run {
            Log.w(TAG, "Grid centers not built")
            buildGridStrideCache()
            centerX ?: return
        }
        val cyArr = centerY ?: return
        val sMap = strideMap ?: return

        if (cxArr.size < N || cyArr.size < N) {
            // possible mismatch -> rebuild
            buildGridStrideCache()
        }

        // Debug dump of first few pred rows
        if (DEBUG) {
            Log.d(TAG, "================= DEBUG PRED DUMP =================")
            val limit = min(10, N)
            for (i in 0 until limit) {
                val row = preds[i]
                val lx = row[0]
                val ty = row[1]
                val rx = row[2]
                val by = row[3]
                // best class
                var bestClass = -1
                var bestScore = -Float.MAX_VALUE
                for (c in 0 until numClassCandidates) {
                    val raw = row[4 + c]
                    val prob = sigmoid(raw) // class logits -> prob
                    if (prob > bestScore) {
                        bestScore = prob
                        bestClass = c
                    }
                }
                val name = labels.getOrNull(bestClass) ?: "class_$bestClass"
                // compute mapped box (approx)
                val centerXv = cxArr[i]
                val centerYv = cyArr[i]
                val left = centerXv - lx
                val top = centerYv - ty
                val right = centerXv + rx
                val bottom = centerYv + by
                Log.d(TAG, "[$i] raw=[${lx.format2()},${ty.format2()},${rx.format2()},${by.format2()}] " +
                        "mapped=[${left.format2()},${top.format2()},${right.format2()},${bottom.format2()}] -> best=$name score=${bestScore.format2()}")
            }
            Log.d(TAG, "====================================================")
        }

        for (i in 0 until N) {
            val row = preds[i]
            if (row.size < 4 + numClassCandidates) continue

            val lx = row[0]
            val ty = row[1]
            val rx = row[2]
            val by = row[3]

            // classes: apply sigmoid to logits
            var bestClass = -1
            var bestProb = 0f
            for (c in 0 until numClassCandidates) {
                val prob = sigmoid(row[4 + c])
                if (prob > bestProb) {
                    bestProb = prob
                    bestClass = c
                }
            }

            if (bestProb < CONF_THRESHOLD) continue

            val cx = cxArr.getOrNull(i) ?: continue
            val cy = cyArr.getOrNull(i) ?: continue

            // Convert predicted distances to absolute coordinates:
            // x1 = cx - left_distance
            // y1 = cy - top_distance
            // x2 = cx + right_distance
            // y2 = cy + bottom_distance
            val x1 = cx - lx
            val y1 = cy - ty
            val x2 = cx + rx
            val y2 = cy + by

            // Map from letterbox coords back to original image coords
            val left = (x1 - prep.padX) / prep.scale
            val top = (y1 - prep.padY) / prep.scale
            val right = (x2 - prep.padX) / prep.scale
            val bottom = (y2 - prep.padY) / prep.scale

            val cl = max(0f, left)
            val ct = max(0f, top)
            val cr = min(origW.toFloat(), right)
            val cb = min(origH.toFloat(), bottom)

            if (cr - cl < 2f || cb - ct < 2f) continue

            val lbl = labels.getOrNull(bestClass) ?: "label_$bestClass"
            outList.add(FrogDetectionResult(lbl, bestProb, RectF(cl, ct, cr, cb)))
        }
    }

    private fun Float.format2(): String = String.format("%.2f", this)

    // -------------------------
    // Cleanup
    // -------------------------
    @Synchronized
    fun close() {
        try { session?.close() } catch (_: Exception) {}
        session = null
        try { env?.close() } catch (_: Exception) {}
        env = null
        Log.d(TAG, "🧹 ONNX resources released")
    }
}
