package com.example.frogdetection.ml

import android.graphics.*
import com.example.frogdetection.utils.FrogDetectionResult
import kotlin.math.max
import kotlin.math.min

/**
 * Draw detection results onto a bitmap (mutable copy returned).
 */
fun drawDetections(original: Bitmap, detections: List<FrogDetectionResult>): Bitmap {
    val out = original.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)

    val scaleFactor = max(1f, original.width / 640f)

    val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * scaleFactor
        isAntiAlias = true
    }
    val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f * scaleFactor
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        setShadowLayer(4f * scaleFactor, 2f * scaleFactor, 2f * scaleFactor, Color.BLACK)
    }
    val bgPaint = Paint().apply {
        color = Color.argb(170, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    for (d in detections) {
        val rect = d.box
        val conf = d.score
        val label = d.label
        boxPaint.color = when {
            conf >= 0.85f -> Color.rgb(0, 200, 0)
            conf >= 0.7f -> Color.rgb(255, 200, 0)
            else -> Color.rgb(220, 0, 0)
        }
        boxPaint.strokeWidth = (3f + conf * 6f) * scaleFactor

        val left = max(0f, rect.left)
        val top = max(0f, rect.top)
        val right = min(out.width.toFloat(), rect.right)
        val bottom = min(out.height.toFloat(), rect.bottom)
        val drawRect = RectF(left, top, right, bottom)
        canvas.drawRect(drawRect, boxPaint)

        // label background
        val caption = "$label ${(conf * 100).toInt()}%"
        val tw = textPaint.measureText(caption)
        val padding = 6f * scaleFactor
        var by = top - (textPaint.textSize + padding * 2)
        if (by < 0) by = top
        val bgRect = RectF(left, by, left + tw + padding * 2, by + textPaint.textSize + padding * 2)
        canvas.drawRoundRect(bgRect, 8f * scaleFactor, 8f * scaleFactor, bgPaint)

        // text
        canvas.drawText(caption, bgRect.left + padding, bgRect.bottom - padding - 4f * scaleFactor, textPaint)
    }

    return out
}
