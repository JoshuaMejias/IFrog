// File: app/src/main/java/com/example/frogdetection/ml/FrogDetector.kt
package com.example.frogdetection.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.frogdetection.utils.FrogDetectionResult
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * FrogDetector: loads an ONNX YOLO11 detection model (detection export),
 * runs CPU-only inference and decodes the 1x10x8400 output into bounding boxes + labels.
 *
 * IMPORTANT:
 * - Model asset filename expected in app assets (change MODEL_ASSET if different).
 * - Uses CPU only (no addXnnpack/addNnapi calls) to avoid provider API issues.
 */
class FrogDetector(private val context: Context) {

    companion object {
        private const val TAG = "🐸FrogDetector"
        private const val MODEL_ASSET = "frog_yolo11_detect_simplified.onnx" // change to your asset name
        private const val INPUT_SIZE = 640
        private const val CONF_THRESHOLD = 0.35f
        private const val NMS_IOU = 0.45f
        private const val MAX_RESULTS = 10
        private const val NUM_CLASSES = 6 // your dataset classes
        private val STRIDES = intArrayOf(8, 16, 32)
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    // precomputed grid and strides flattened (one entry per cell)
    private val gridXY: Array<FloatArray>
    private val strideList: FloatArray

    init {
        val modelFile = copyAssetToFile(MODEL_ASSET)
        session = env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
        val (g, s) = buildGridAndStrides()
        gridXY = g
        strideList = s
        Log.d(TAG, "✅ Model ready: ${modelFile.absolutePath}, grid cells=${gridXY.size}")
    }

    private fun copyAssetToFile(assetName: String): File {
        val out = File(context.filesDir, assetName)
        if (!out.exists()) {
            context.assets.open(assetName).use { input ->
                FileOutputStream(out).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return out
    }

    // Build grid positions and stride per cell (order must match model's flattening)
    private fun buildGridAndStrides(): Pair<Array<FloatArray>, FloatArray> {
        val grid = ArrayList<FloatArray>()
        val sList = ArrayList<Float>()
        for (s in STRIDES) {
            val fm = INPUT_SIZE / s
            for (y in 0 until fm) {
                for (x in 0 until fm) {
                    grid.add(floatArrayOf(x.toFloat(), y.toFloat()))
                    sList.add(s.toFloat())
                }
            }
        }
        return Pair(grid.toTypedArray(), sList.toFloatArray())
    }

    // Letterbox (maintain aspect ratio) -> CHW float buffer [1,3,H,W] normalized to [0,1]
    private data class Prep(val tensor: OnnxTensor, val scale: Float, val padX: Float, val padY: Float)

    private fun preprocess(bitmap: Bitmap): Prep {
        val w = bitmap.width; val h = bitmap.height
        val scale = min(INPUT_SIZE / w.toFloat(), INPUT_SIZE / h.toFloat())
        val newW = (w * scale).roundToInt()
        val newH = (h * scale).roundToInt()

        val resized = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        val padded = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(padded)
        canvas.drawColor(Color.rgb(114, 114, 114))
        val padX = ((INPUT_SIZE - newW) / 2f)
        val padY = ((INPUT_SIZE - newH) / 2f)
        canvas.drawBitmap(resized, padX, padY, null)

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        padded.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val area = INPUT_SIZE * INPUT_SIZE
        val buf = FloatBuffer.allocate(1 * 3 * area)
        // R plane
        for (i in 0 until area) {
            val p = pixels[i]
            buf.put(((p shr 16) and 0xFF) / 255.0f)
        }
        // G plane
        for (i in 0 until area) {
            val p = pixels[i]
            buf.put(((p shr 8) and 0xFF) / 255.0f)
        }
        // B plane
        for (i in 0 until area) {
            val p = pixels[i]
            buf.put((p and 0xFF) / 255.0f)
        }
        buf.rewind()
        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        val tensor = OnnxTensor.createTensor(env, buf, shape)
        return Prep(tensor, scale, padX, padY)
    }

    // sigmoid
    private fun sigmoid(x: Float) = (1f / (1f + exp(-x)))

    // IoU
    private fun iou(a: RectF, b: RectF): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        val w = max(0f, right - left)
        val h = max(0f, bottom - top)
        val inter = w * h
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun nms(list: List<FrogDetectionResult>): List<FrogDetectionResult> {
        val out = ArrayList<FrogDetectionResult>()
        val sorted = list.sortedByDescending { it.score }.toMutableList()
        while (sorted.isNotEmpty() && out.size < MAX_RESULTS) {
            val best = sorted.removeAt(0)
            out.add(best)
            val it = sorted.iterator()
            while (it.hasNext()) {
                val other = it.next()
                if (iou(best.box, other.box) > NMS_IOU) it.remove()
            }
        }
        return out
    }

    /**
     * Main API: run detection on a Bitmap and return list of FrogDetectionResult
     */
    fun detectFrogs(bitmap: Bitmap): List<FrogDetectionResult> {
        val prep = preprocess(bitmap)
        val inputName = session.inputNames.iterator().next()
        val outputs = session.run(mapOf(inputName to prep.tensor))

        // close input tensor asap
        prep.tensor.close()

        val outputResult = session.run(mapOf(inputName to prep.tensor))

        if (outputResult.size() == 0) {
            prep.tensor.close()
            outputResult.close()
            return emptyList()
        }

// Use the first output
        val outputTensor = outputResult[0].value

// After decoding:
        prep.tensor.close()
        outputResult.close()


        // attempt to cast to [1, C, N] (C=10, N=8400)
        val rawAny = outputs[0].value
        // close results container but keep the actual arrays
        outputs.close()

        // Defensive cast: many bindings return Array<Array<FloatArray>> -> out[0] = Array<FloatArray>[] shape 1xC x N
        val nested: Array<Array<FloatArray>> = try {
            @Suppress("UNCHECKED_CAST")
            rawAny as Array<Array<FloatArray>>
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cast output to expected Array<Array<FloatArray>>: ${e.message}")
            return emptyList()
        }

        if (nested.isEmpty() || nested[0].isEmpty()) return emptyList()
        val out0 = nested[0] // shape C x N

        val C = out0.size
        val N = out0[0].size
        if (C < 6) {
            Log.e(TAG, "Unexpected channel count: $C")
            return emptyList()
        }

        val results = ArrayList<FrogDetectionResult>(32)

        // decode each anchor point (N cells)
        for (i in 0 until N) {
            // channels order: 0:cx,1:cy,2:w,3:h,4:obj, 5.. -> class logits (C-5 maybe = NUM_CLASSES or NUM_CLASSES-1)
            val cx = out0[0][i]
            val cy = out0[1][i]
            val w = out0[2][i]
            val h = out0[3][i]
            val obj = sigmoid(out0[4][i])

            // gather class scores (apply sigmoid)
            val classScores = FloatArray(NUM_CLASSES)
            var bestIdx = 0
            var bestClsScore = 0f
            // careful: ONNX head may have (C-1) class entries; we attempt to read NUM_CLASSES but clamp
            val maxClassChannels = C - 5
            val classesToRead = min(NUM_CLASSES, maxClassChannels)
            for (c in 0 until classesToRead) {
                val logit = out0[5 + c][i]
                val cls = sigmoid(logit)
                classScores[c] = cls
                if (cls > bestClsScore) {
                    bestClsScore = cls
                    bestIdx = c
                }
            }
            val score = obj * bestClsScore
            if (score < CONF_THRESHOLD) continue

            // decode using YOLOv8/11 style decode
            val grid = gridXY[i]
            val s = strideList[i]
            val bx = (sigmoid(cx) * 2f - 0.5f + grid[0]) * s
            val by = (sigmoid(cy) * 2f - 0.5f + grid[1]) * s
            val bw = ((sigmoid(w) * 2f) * (sigmoid(w) * 2f)) * s
            val bh = ((sigmoid(h) * 2f) * (sigmoid(h) * 2f)) * s

            val x1 = bx - bw / 2f
            val y1 = by - bh / 2f
            val x2 = bx + bw / 2f
            val y2 = by + bh / 2f

            // unscale / unpad to original image coordinates
            val rx1 = (x1 - prep.padX) / prep.scale
            val ry1 = (y1 - prep.padY) / prep.scale
            val rx2 = (x2 - prep.padX) / prep.scale
            val ry2 = (y2 - prep.padY) / prep.scale

            // clamp
            val left = max(0f, rx1)
            val top = max(0f, ry1)
            val right = max(left + 1f, rx2)
            val bottom = max(top + 1f, ry2)

            // map class index to label (simple mapping, change if you have labels file)
            val label = when (bestIdx) {
                0 -> "Asian Painted Frog"
                1 -> "Cane Toad"
                2 -> "Common Southeast Asian Tree Frog"
                3 -> "East Asian Bullfrog"
                4 -> "Paddy Field Frog"
                5 -> "Wood Frog"
                else -> "class_$bestIdx"
            }

            results.add(FrogDetectionResult(label, score, RectF(left, top, right, bottom)))
        }

        // apply NMS and return
        return nms(results)
    }

    fun close() {
        try { session.close() } catch (_: Exception) {}
        try { env.close() } catch (_: Exception) {}
    }
}
