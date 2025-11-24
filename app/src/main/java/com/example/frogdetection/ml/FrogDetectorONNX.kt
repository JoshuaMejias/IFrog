// File: com/example/frogdetection/ml/FrogDetectorONNX.kt
package com.example.frogdetection.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.example.frogdetection.utils.FrogDetectionResult
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * CPU-only ONNX detector for YOLOv11n exported model.
 * - Input: "images" [1,3,640,640] float32 (R,G,B normalized 0..1) channel-first
 * - Output: [1,10,8400] channel-major (0:cx,1:cy,2:w,3:h,4..9:6 class scores)
 */
class FrogDetectorONNX(
    context: Context,
    private val modelAssetPath: String = "best.onnx"
) : AutoCloseable {

    companion object {
        private const val TAG = "FrogDetectorONNX"
        private const val INPUT_NAME = "images"
        private const val INPUT_SIZE = 640
        private const val CHANNELS = 10
        private const val CONF_THRESHOLD = 0.25f
        private const val IOU_THRESHOLD = 0.45f
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelBytes = context.assets.open(modelAssetPath).use { it.readBytes() }
        val opts = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        session = env.createSession(modelBytes, opts)
        Log.i(TAG, "ONNX model loaded from assets: $modelAssetPath")
    }

    /**
     * Run detection on a bitmap (must be ARGB_8888 or will be coerced by caller).
     * Returns list of FrogDetectionResult with boxes in original bitmap pixel coordinates.
     */
    fun detect(bitmap: Bitmap): List<FrogDetectionResult> {
        // Prepare input bitmap (resize to model input)
        val inputBmp = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        // Preprocess to FloatArray channel-first (R,G,B) normalized to [0,1]
        val floatInput = preprocessToFloatArray(inputBmp)

        // Create FloatBuffer and OnnxTensor
        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        val floatBuffer: FloatBuffer = FloatBuffer.wrap(floatInput)
        var ortTensor: OnnxTensor? = null
        var results: OrtSession.Result? = null

        try {
            ortTensor = OnnxTensor.createTensor(env, floatBuffer, shape)
            results = session.run(mapOf(INPUT_NAME to ortTensor))

            // robustly extract raw output -> expected batch-major array
            val rawOut = results[0].value
            val preds: Array<FloatArray> = parsePreds(rawOut)

            // decode + nms, scaling boxes back to original bitmap size (bitmap param)
            return decodeAndNms(preds, bitmap.width, bitmap.height)

        } catch (e: Exception) {
            Log.e(TAG, "Detection failed: ${e.message}", e)
            return emptyList()
        } finally {
            try { ortTensor?.close() } catch (_: Exception) {}
            try { results?.close() } catch (_: Exception) {}
        }
    }

    // Convert ARGB_8888 bitmap -> channel-first FloatArray (r,g,b) normalized
    private fun preprocessToFloatArray(bmp: Bitmap): FloatArray {
        val w = INPUT_SIZE
        val h = INPUT_SIZE
        val pxCount = w * h
        val pixels = IntArray(pxCount)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val out = FloatArray(3 * pxCount)
        var idx = 0
        for (i in 0 until pxCount) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255.0f
            val g = ((p shr 8) and 0xFF) / 255.0f
            val b = (p and 0xFF) / 255.0f

            out[idx] = r
            out[idx + pxCount] = g
            out[idx + 2 * pxCount] = b
            idx++
        }
        return out
    }

    // Parse various possible shapes returned by ONNX into Array<FloatArray> channel-major [10][8400]
    private fun parsePreds(rawOut: Any?): Array<FloatArray> {
        // Case A: rawOut is Array<*> and rawOut[0] is Array<*> representing channels
        if (rawOut is Array<*>) {
            // often rawOut is batch-array: out[0] => Array<FloatArray>
            val batch0 = rawOut.getOrNull(0)
            if (batch0 is Array<*>) {
                // batch0 is channels array; each element should be FloatArray
                val channelCount = batch0.size
                val result = Array(channelCount) { FloatArray(0) }
                for (c in 0 until channelCount) {
                    val ch = batch0[c]
                    if (ch is FloatArray) {
                        result[c] = ch
                    } else if (ch is Array<*>) {
                        // maybe nested Float[][] -> convert
                        val arr = ch.map { (it as Number).toFloat() }.toFloatArray()
                        result[c] = arr
                    } else {
                        throw IllegalArgumentException("Unsupported channel element type: ${ch?.javaClass}")
                    }
                }
                return result
            } else if (batch0 is FloatArray) {
                // possible shape: rawOut is Array<FloatArray> directly (no batch), treat as channels
                val channelCount = rawOut.size
                val result = Array(channelCount) { FloatArray(0) }
                for (c in 0 until channelCount) {
                    result[c] = rawOut[c] as? FloatArray ?: throw IllegalArgumentException("Expected FloatArray")
                }
                return result
            }
        }

        // Fallback: unsupported type
        throw IllegalArgumentException("Unexpected ONNX output type: ${rawOut?.javaClass}")
    }

    // Decode preds[channel][i] -> list of FrogDetectionResult scaled to origW/origH
    private fun decodeAndNms(preds: Array<FloatArray>, origW: Int, origH: Int): List<FrogDetectionResult> {
        if (preds.size != CHANNELS) {
            Log.w(TAG, "Unexpected channel count: ${preds.size}, expected $CHANNELS")
        }
        val channels = preds.size
        val numPreds = preds.getOrNull(0)?.size ?: 0
        if (numPreds == 0) return emptyList()

        val labels = listOf(
            "Asian Painted Frog",
            "Cane Toad",
            "Common Southeast Asian Tree Frog",
            "East Asian Bullfrog",
            "Paddy Field Frog",
            "Wood Frog"
        )
        val numClasses = labels.size
        val candidates = ArrayList<Candidate>(256)

        for (i in 0 until numPreds) {
            val cx = preds.getOrNull(0)?.getOrNull(i) ?: continue
            val cy = preds.getOrNull(1)?.getOrNull(i) ?: continue
            val w = preds.getOrNull(2)?.getOrNull(i) ?: continue
            val h = preds.getOrNull(3)?.getOrNull(i) ?: continue

            // find best class score among 6 classes at channels 4..9
            var bestClass = -1
            var bestScore = 0f
            for (c in 0 until numClasses) {
                val sc = preds.getOrNull(4 + c)?.getOrNull(i) ?: 0f
                if (sc > bestScore) {
                    bestScore = sc
                    bestClass = c
                }
            }

            if (bestClass >= 0 && bestScore >= CONF_THRESHOLD) {
                val left = cx - w / 2f
                val top = cy - h / 2f
                val right = left + w
                val bottom = top + h

                val scaleX = origW.toFloat() / INPUT_SIZE
                val scaleY = origH.toFloat() / INPUT_SIZE

                val sLeft = (left * scaleX).coerceAtLeast(0f)
                val sTop = (top * scaleY).coerceAtLeast(0f)
                val sRight = (right * scaleX).coerceAtMost(origW.toFloat())
                val sBottom = (bottom * scaleY).coerceAtMost(origH.toFloat())

                if (sRight - sLeft > 1f && sBottom - sTop > 1f) {
                    candidates.add(
                        Candidate(
                            labelIndex = bestClass,
                            score = bestScore,
                            left = sLeft,
                            top = sTop,
                            right = sRight,
                            bottom = sBottom
                        )
                    )
                }
            }
        }

        // NMS per-class
        val finalDetections = ArrayList<FrogDetectionResult>(32)
        val byLabel = candidates.groupBy { it.labelIndex }
        for ((labelIdx, list) in byLabel) {
            val sorted = list.sortedByDescending { it.score }.toMutableList()
            while (sorted.isNotEmpty()) {
                val keep = sorted.removeAt(0)
                finalDetections.add(
                    FrogDetectionResult(
                        label = labels.getOrNull(keep.labelIndex) ?: "Unknown",
                        score = keep.score,
                        box = RectF(keep.left, keep.top, keep.right, keep.bottom)
                    )
                )
                val itr = sorted.iterator()
                while (itr.hasNext()) {
                    val other = itr.next()
                    if (iou(keep.left, keep.top, keep.right, keep.bottom, other.left, other.top, other.right, other.bottom) > IOU_THRESHOLD) {
                        itr.remove()
                    }
                }
            }
        }

        return finalDetections
    }

    override fun close() {
        try {
            session.close()
            env.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close ONNX resources: ${e.message}")
        }
    }

    // Small helper types & functions
    private data class Candidate(
        val labelIndex: Int,
        val score: Float,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    private fun iou(l1: Float, t1: Float, r1: Float, b1: Float, l2: Float, t2: Float, r2: Float, b2: Float): Float {
        val left = max(l1, l2)
        val top = max(t1, t2)
        val right = min(r1, r2)
        val bottom = min(b1, b2)

        val interW = (right - left).coerceAtLeast(0f)
        val interH = (bottom - top).coerceAtLeast(0f)
        val inter = interW * interH

        val area1 = (r1 - l1).coerceAtLeast(0f) * (b1 - t1).coerceAtLeast(0f)
        val area2 = (r2 - l2).coerceAtLeast(0f) * (b2 - t2).coerceAtLeast(0f)
        val union = area1 + area2 - inter
        return if (union <= 0f) 0f else inter / union
    }
}
