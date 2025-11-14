package com.example.frogdetection.ml

import android.graphics.*
import com.example.frogdetection.utils.FrogDetectionResult

/**
 * Draws ONNX YOLO detection results on a Bitmap.
 */
fun drawDetections(
    original: Bitmap,
    detections: List<FrogDetectionResult>
): Bitmap {
    val mutableBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)

    val boxPaint = Paint().apply {
        color = Color.parseColor("#2E7D32") // 🟩 Green for frogs
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

    val bgPaint = Paint().apply {
        color = Color.parseColor("#66000000")
        style = Paint.Style.FILL
    }

    for (det in detections) {
        val rect = det.boundingBox
        val label = det.label
        val score = det.score

        // Draw bounding box
        canvas.drawRect(rect, boxPaint)

        // Draw label background
        val caption = "$label ${(score * 100).toInt()}%"
        val textWidth = textPaint.measureText(caption)
        val textPadding = 8f
        val left = rect.left
        val top = rect.top - textPaint.textSize - textPadding * 2

        canvas.drawRect(
            left,
            top,
            left + textWidth + textPadding * 2,
            rect.top,
            bgPaint
        )

        // Draw text
        canvas.drawText(caption, left + textPadding, rect.top - textPadding, textPaint)
    }

    return mutableBitmap
}
