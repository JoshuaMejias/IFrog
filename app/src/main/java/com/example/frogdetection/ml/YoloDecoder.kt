// File: com/example/frogdetection/ml/YoloDecoder.kt
package com.example.frogdetection.ml

import android.graphics.RectF
import com.example.frogdetection.utils.FrogDetectionResult
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight YOLOv11 decoder for output shape [1, 10, 8400]
 *
 * Assumptions:
 * - preds[channel][i] layout (channel-major): 0:cx,1:cy,2:w,3:h,4..9:class scores
 * - coordinates are in the same pixel-space as the ONNX export (640 reference).
 * - no separate objectness channel (class scores are the detection confidences).
 */
internal object YoloDecoder {

    private const val INPUT_SIZE = 640f
    private const val CHANNELS = 10
    private const val CONF_THRESHOLD = 0.25f
    private const val IOU_THRESHOLD = 0.45f

    private data class Candidate(
        val labelIndex: Int,
        val score: Float,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    /**
     * Decode raw ONNX output into list of DetectionResults scaled to the original image size.
     *
     * @param rawPreds array-of-channels: FloatArray for each channel length = numPreds (8400)
     *                 expected shape: [10][8400]
     * @param origW original bitmap width in pixels
     * @param origH original bitmap height in pixels
     */
    fun decodeAndNms(
        rawPreds: Array<FloatArray>,
        origW: Int,
        origH: Int
    ): List<FrogDetectionResult> {
        val channels = rawPreds.size
        require(channels == CHANNELS) { "Expected $CHANNELS channels in preds, got $channels" }

        val numPreds = rawPreds[0].size
        val labels = FrogLabels.LABELS
        val numClasses = labels.size

        val candidates = ArrayList<Candidate>(256)

        // Decode predictions
        for (i in 0 until numPreds) {
            val cx = rawPreds[0][i]
            val cy = rawPreds[1][i]
            val w = rawPreds[2][i]
            val h = rawPreds[3][i]

            // find best class
            var bestClass = -1
            var bestScore = 0f
            for (c in 0 until numClasses) {
                val sc = rawPreds[4 + c][i]
                if (sc > bestScore) {
                    bestScore = sc
                    bestClass = c
                }
            }

            if (bestClass >= 0 && bestScore >= CONF_THRESHOLD) {
                // Convert center x,y,w,h (in 640-space) -> left,top,right,bottom (still in 640-space)
                val left = cx - w / 2f
                val top = cy - h / 2f
                val right = left + w
                val bottom = top + h

                // Scale to original image size
                val scaleX = origW / INPUT_SIZE
                val scaleY = origH / INPUT_SIZE

                val sLeft = (left * scaleX).coerceAtLeast(0f)
                val sTop = (top * scaleY).coerceAtLeast(0f)
                val sRight = (right * scaleX).coerceAtMost(origW.toFloat())
                val sBottom = (bottom * scaleY).coerceAtMost(origH.toFloat())

                // discard degenerate boxes
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

        // Perform NMS per-class (greedy)
        val finalDetections = ArrayList<FrogDetectionResult>(32)

        // group by label
        val byLabel = candidates.groupBy { it.labelIndex }
        for ((labelIdx, list) in byLabel) {
            val sorted = list.sortedByDescending { it.score }.toMutableList()
            while (sorted.isNotEmpty()) {
                val keep = sorted.removeAt(0)
                finalDetections.add(
                    FrogDetectionResult(
                        label = FrogLabels.LABELS[keep.labelIndex],
                        score = keep.score,
                        box = RectF(keep.left, keep.top, keep.right, keep.bottom)
                    )
                )

                val itr = sorted.iterator()
                while (itr.hasNext()) {
                    val other = itr.next()
                    if (iou(
                            keep.left,
                            keep.top,
                            keep.right,
                            keep.bottom,
                            other.left,
                            other.top,
                            other.right,
                            other.bottom
                        ) > IOU_THRESHOLD
                    ) {
                        itr.remove()
                    }
                }
            }
        }

        return finalDetections
    }

    // IoU of two rectangles (l,t,r,b)
    private fun iou(
        l1: Float, t1: Float, r1: Float, b1: Float,
        l2: Float, t2: Float, r2: Float, b2: Float
    ): Float {
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
