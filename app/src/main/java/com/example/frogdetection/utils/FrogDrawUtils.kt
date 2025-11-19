package com.example.frogdetection.utils

import android.graphics.*
import kotlin.math.max
import kotlin.math.min

object FrogDrawUtils {
    /**
     * Draw detection results onto a bitmap and return a mutable annotated copy.
     */
    fun drawDetections(bitmap: Bitmap, detections: List<FrogDetectionResult>): Bitmap {
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)

        val baseScale = bitmap.width / 640f.coerceAtLeast(1f)

        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f * baseScale
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 30f * baseScale
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            setShadowLayer(4f * baseScale, 2f * baseScale, 2f * baseScale, Color.BLACK)
        }

        val bgPaint = Paint().apply {
            color = Color.argb(160, 0, 0, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        for (d in detections) {
            val box = d.box
            val conf = d.score
            val label = d.label

            // color by confidence
            boxPaint.color = when {
                conf >= 0.85f -> Color.rgb(0, 200, 0)
                conf >= 0.7f -> Color.rgb(255, 200, 0)
                else -> Color.rgb(255, 0, 0)
            }
            boxPaint.strokeWidth = (4f + conf * 6f) * baseScale

            // clamp within image
            val left = max(0f, box.left)
            val top = max(0f, box.top)
            val right = min(bitmap.width.toFloat(), box.right)
            val bottom = min(bitmap.height.toFloat(), box.bottom)

            val rectF = RectF(left, top, right, bottom)
            canvas.drawRect(rectF, boxPaint)

            val caption = "$label ${(conf * 100).toInt()}%"
            val tw = textPaint.measureText(caption)
            val th = textPaint.textSize

            val padding = 8f * baseScale
            var tagTop = top - (th + padding * 2)
            var tagBottom = top
            if (tagTop < 0f) {
                tagTop = top
                tagBottom = top + (th + padding * 2)
            }
            val tagRect = RectF(left, tagTop, left + tw + padding * 2, tagBottom)
            canvas.drawRoundRect(tagRect, 8f * baseScale, 8f * baseScale, bgPaint)

            // draw text
            canvas.drawText(caption, tagRect.left + padding, tagRect.bottom - padding - 4f * baseScale, textPaint)
        }
        return out
    }
}
