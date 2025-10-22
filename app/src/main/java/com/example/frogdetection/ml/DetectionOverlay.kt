package com.example.frogdetection.ml

import android.graphics.*
import org.tensorflow.lite.task.vision.detector.Detection

/**
 * Draws YOLO detection results over the original bitmap.
 */
fun drawDetections(
    original: Bitmap,
    detections: List<Detection>
): Bitmap {
    val mutableBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)

    val boxPaint = Paint().apply {
        color = Color.parseColor("#2E7D32") // ✅ Green for frogs
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        style = Paint.Style.FILL
        setShadowLayer(5f, 2f, 2f, Color.BLACK)
    }

    for (det in detections) {
        val category = det.categories.firstOrNull()
        val label = category?.label ?: "Unknown"
        val score = category?.score ?: 0f
        val rect = det.boundingBox

        // 🟩 Draw bounding box
        canvas.drawRect(rect, boxPaint)

        // 🏷️ Draw label
        val caption = "$label ${(score * 100).toInt()}%"
        val textBackground = Paint().apply {
            color = Color.parseColor("#66000000")
            style = Paint.Style.FILL
        }

        val textWidth = textPaint.measureText(caption)
        val textPadding = 8f

        val left = rect.left
        val top = rect.top - textPaint.textSize - textPadding * 2

        canvas.drawRect(
            left,
            top,
            left + textWidth + textPadding * 2,
            rect.top,
            textBackground
        )

        canvas.drawText(caption, left + textPadding, rect.top - textPadding, textPaint)
    }

    return mutableBitmap
}
