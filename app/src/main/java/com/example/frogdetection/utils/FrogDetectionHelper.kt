// File: com/example/frogdetection/utils/FrogDetectionHelper.kt
package com.example.frogdetection.utils

import android.content.Context
import android.graphics.*
import android.util.Log
import ai.onnxruntime.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.FloatBuffer
import kotlin.math.*

data class FrogDetectionResult(
    val label: String,
    val score: Float,
    val boundingBox: RectF
)

object FrogDetectionHelper {

    private const val TAG = "🐸FrogDetection"
    private const val MODEL_ASSET = "best.onnx"
    private const val LABELS_ASSET = "labels.txt"
    private const val INPUT_SIZE = 640
    private const val CONF_THRESHOLD = 0.25f
    private const val NMS_IOU = 0.45f
    private const val MAX_DETECTIONS = 5

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var labels: List<String> = emptyList()

    // ---------------- Initialization ----------------
    @Synchronized
    private fun ensureInitialized(context: Context) {
        if (session != null) return

        env = OrtEnvironment.getEnvironment()

        val outFile = File(context.filesDir, MODEL_ASSET)
        if (!outFile.exists()) {
            context.assets.open(MODEL_ASSET).use { input ->
                FileOutputStream(outFile).use { fos ->
                    val buf = ByteArray(4096)
                    var n = input.read(buf)
                    while (n > 0) {
                        fos.write(buf, 0, n)
                        n = input.read(buf)
                    }
                }
            }
        }
        Log.d(TAG, "✅ Model file ready: ${outFile.absolutePath} (size=${outFile.length()})")

        val opts = OrtSession.SessionOptions()
        try {
            opts.addNnapi()
            Log.d(TAG, "✅ NNAPI delegate enabled")
        } catch (_: Exception) {
            try {
                opts.addXnnpack(emptyMap())
                Log.d(TAG, "✅ XNNPACK delegate enabled")
            } catch (_: Exception) {
                Log.d(TAG, "⚠ Falling back to CPU")
            }
        }

        session = env!!.createSession(outFile.absolutePath, opts)
        labels = loadLabels(context)
        Log.d(TAG, "✅ ONNX session ready. Labels (${labels.size})")
    }

    private fun loadLabels(context: Context): List<String> {
        return try {
            context.assets.open(LABELS_ASSET)
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() }
        } catch (_: Exception) {
            Log.w(TAG, "⚠ labels.txt not found; using fallback labels")
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

    // ---------------- Preprocessing ----------------
    private data class LetterboxResult(
        val tensor: OnnxTensor,
        val scale: Float,
        val padX: Float,
        val padY: Float
    )

    private fun makeLetterboxTensor(bmp: Bitmap): LetterboxResult {
        val w = bmp.width
        val h = bmp.height
        val scale = min(INPUT_SIZE.toFloat() / w, INPUT_SIZE.toFloat() / h)

        val nw = (w * scale).roundToInt()
        val nh = (h * scale).roundToInt()

        val resized = Bitmap.createScaledBitmap(bmp, nw, nh, true)
        val padded = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)

        val padX = ((INPUT_SIZE - nw) / 2f)
        val padY = ((INPUT_SIZE - nh) / 2f)

        val canvas = Canvas(padded)
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.drawBitmap(resized, padX, padY, null)

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        padded.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val floatArr = FloatArray(3 * pixels.size)
        val plane = pixels.size

        for (i in pixels.indices) {
            val p = pixels[i]
            floatArr[i] = ((p shr 16) and 0xFF) / 255f
            floatArr[i + plane] = ((p shr 8) and 0xFF) / 255f
            floatArr[i + 2 * plane] = (p and 0xFF) / 255f
        }

        val buffer = FloatBuffer.wrap(floatArr)
        val tensor = OnnxTensor.createTensor(env, buffer, longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()))
        return LetterboxResult(tensor, scale, padX, padY)
    }

    // ---------------- Utilities ----------------
    private fun anyArrayLength(o: Any?): Int {
        if (o == null) return -1
        return try { java.lang.reflect.Array.getLength(o) } catch (_: Exception) { -1 }
    }

    private fun iou(a: RectF, b: RectF): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)

        val w = max(0f, right - left)
        val h = max(0f, bottom - top)
        val inter = w * h
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union <= 0) 0f else inter / union
    }

    private fun nms(boxes: List<RectF>, scores: List<Float>, classes: List<Int>): List<Int> {
        val order = scores.indices.sortedByDescending { scores[it] }.toMutableList()
        val keep = mutableListOf<Int>()

        while (order.isNotEmpty()) {
            val i = order.removeAt(0)
            keep.add(i)
            order.removeAll { j ->
                classes[i] == classes[j] && iou(boxes[i], boxes[j]) > NMS_IOU
            }
        }
        return keep.take(MAX_DETECTIONS)
    }

    // ---------------- Raw YOLO Parser ----------------
    private fun parseYOLORaw(
        preds: Array<FloatArray>,
        scale: Float,
        padX: Float,
        padY: Float,
        origW: Int,
        origH: Int
    ): List<FrogDetectionResult> {

        fun sigmoid(x: Float) = 1f / (1f + exp(-x))

        val boxes = mutableListOf<RectF>()
        val scores = mutableListOf<Float>()
        val classes = mutableListOf<Int>()

        val numCols = preds[0].size
        val numClasses = numCols - 5

        for ((index, det) in preds.withIndex()) {

            // DEBUG raw values periodically
            if (index % 500 == 0) {
                Log.d(TAG, "Pred[$index]: raw=" + det.joinToString(", ") { "%.3f".format(it) })
            }

            val cx = det[0]  // absolute pixel coords already
            val cy = det[1]
            val w = det[2]
            val h = det[3]

            val obj = sigmoid(det[4])

            val clsScores = FloatArray(numClasses) { i -> sigmoid(det[5 + i]) }
            val (clsIdx, clsConf) = clsScores.withIndex().maxByOrNull { it.value } ?: continue

            val score = obj * clsConf

            // DEBUG score pipeline
            Log.d(TAG, "Score=%.4f obj=%.4f cls=%.4f clsIdx=%d".format(score, obj, clsConf, clsIdx))

            if (score < CONF_THRESHOLD) continue

            val left = ((cx - w / 2f) - padX) / scale
            val top = ((cy - h / 2f) - padY) / scale
            val right = ((cx + w / 2f) - padX) / scale
            val bottom = ((cy + h / 2f) - padY) / scale

            // DEBUG bbox
            Log.d(TAG, "BBox: L=%.1f T=%.1f R=%.1f B=%.1f".format(left, top, right, bottom))

            boxes.add(RectF(left, top, right, bottom))
            scores.add(score)
            classes.add(clsIdx)
        }

        if (boxes.isEmpty()) return emptyList()

        val keep = nms(boxes, scores, classes)
        return keep.map { i ->
            FrogDetectionResult(
                labels.getOrNull(classes[i]) ?: "Unknown",
                scores[i],
                boxes[i]
            )
        }
    }

    // ---------------- Detection Entry ----------------
    fun detectFrogs(context: Context, bitmap: Bitmap): List<FrogDetectionResult> {
        ensureInitialized(context)
        val sess = session ?: return emptyList()

        val prep = makeLetterboxTensor(bitmap)

        return try {
            val inputName = sess.inputNames.first()
            sess.run(mapOf(inputName to prep.tensor)).use { results ->
                if (results.size() == 0) return emptyList()

                val out = results[0].value
                Log.d(TAG, "ℹ️ Output runtime type: ${out?.javaClass}")

                val d0 = anyArrayLength(out)
                val d1 = anyArrayLength(java.lang.reflect.Array.get(out, 0))
                val d2 = anyArrayLength(java.lang.reflect.Array.get(java.lang.reflect.Array.get(out, 0), 0))

                Log.d(TAG, "ℹ️ Output shape (3D): $d0 x $d1 x $d2")

                // Expect raw YOLO: [1,10,8400]
                if (d0 == 1 && d1 == 10 && d2 > 0) {
                    val transposed = Array(d2) { FloatArray(d1) }
                    for (i in 0 until d1) {
                        val row = java.lang.reflect.Array.get(out, 0)
                        val inner = java.lang.reflect.Array.get(row, i)
                        for (j in 0 until d2) {
                            transposed[j][i] = java.lang.reflect.Array.getFloat(inner, j)
                        }
                    }

                    Log.d(TAG, "ℹ️ Transposed preds shape: ${transposed.size} x ${transposed[0].size}")

                    // DEBUG first row
                    val sample = transposed[0]

                    Log.e(TAG, "FIRST RAW ROW (0): " + sample.joinToString(", ") )


                    return parseYOLORaw(
                        transposed,
                        prep.scale,
                        prep.padX,
                        prep.padY,
                        bitmap.width,
                        bitmap.height
                    )
                }

                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Detection failed: ${e.message}", e)
            emptyList()
        } finally {
            prep.tensor.close()
        }
    }
}
