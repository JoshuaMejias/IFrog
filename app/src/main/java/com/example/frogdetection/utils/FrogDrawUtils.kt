package com.example.frogdetection.utils

import android.graphics.*
import kotlin.math.max
import kotlin.math.min

object FrogDrawUtils {

    /**
     * Draw detection results onto a bitmap.
     *
     * @param bitmap Original image
     * @param detections List of FrogDetectionResult
     * @return A new bitmap with bounding boxes and labels drawn
     */
    fun drawDetections(
        bitmap: Bitmap,
        detections: List<FrogDetectionResult>
    ): Bitmap {
        if (detections.isEmpty()) return bitmap

        // Create mutable copy for drawing
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        // Set up Paint for boxes and labels
        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 36f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }

        val backgroundPaint = Paint().apply {
            color = Color.argb(150, 0, 0, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Draw each detection
        for (det in detections) {
            val box = det.boundingBox
            val conf = det.score
            val label = det.label

            // 🟩 Adjust color based on confidence
            val color = when {
                conf >= 0.85f -> Color.rgb(0, 255, 0)     // Bright green
                conf >= 0.6f -> Color.rgb(255, 255, 0)    // Yellow
                else -> Color.rgb(255, 0, 0)              // Red
            }
            boxPaint.color = color
            boxPaint.strokeWidth = 3f + (conf * 6f)

            // 🧭 Clamp box within image bounds
            val left = max(0f, box.left)
            val top = max(0f, box.top)
            val right = min(bitmap.width.toFloat(), box.right)
            val bottom = min(bitmap.height.toFloat(), box.bottom)

            val rect = RectF(left, top, right, bottom)

            // 🟩 Draw bounding box
            canvas.drawRect(rect, boxPaint)

            // 🏷️ Draw label background + text
            val text = "${label} ${(conf * 100).toInt()}%"
            val textWidth = textPaint.measureText(text)
            val textHeight = textPaint.textSize

            val padding = 8f
            val bgRect = RectF(
                left,
                top - textHeight - padding * 2,
                left + textWidth + padding * 2,
                top
            )

            // Draw semi-transparent background for readability
            canvas.drawRoundRect(bgRect, 8f, 8f, backgroundPaint)

            // Draw label text
            canvas.drawText(text, left + padding, top - padding, textPaint)
        }

        return mutableBitmap
    }
}
