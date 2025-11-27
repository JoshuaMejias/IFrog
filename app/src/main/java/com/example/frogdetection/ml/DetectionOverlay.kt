package com.example.frogdetection.ml

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.example.frogdetection.utils.FrogDetectionResult
import kotlin.math.max
import kotlin.math.min

/**
 * Crop-aware detection overlay. Accepts a labelTextSizeMultiplier to shrink/enlarge label text.
 * - bitmap: original image used to compute scaling/crop
 * - detections: list of boxes in bitmap coordinates
 * - labelTextSizeMultiplier: 1.0 = default, values <1 shrink text (we use 0.85 in preview)
 */
@Composable
fun DetectionOverlay(
    bitmap: Bitmap,
    detections: List<FrogDetectionResult>,
    modifier: Modifier = Modifier,
    labelTextSizeMultiplier: Float = 0.85f
) {
    Canvas(modifier = modifier) {
        val viewW = size.width
        val viewH = size.height

        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()

        val viewRatio = viewW / viewH
        val bmpRatio = bmpW / bmpH

        val scale: Float
        val dx: Float
        val dy: Float

        if (bmpRatio > viewRatio) {
            // bitmap is wider → height fits, width crops
            scale = viewH / bmpH
            val scaledWidth = bmpW * scale
            dx = (viewW - scaledWidth) / 2f
            dy = 0f
        } else {
            // bitmap is taller → width fits, height crops
            scale = viewW / bmpW
            val scaledHeight = bmpH * scale
            dx = 0f
            dy = (viewH - scaledHeight) / 2f
        }

        val dashed = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)

        detections.forEach { det ->
            val b = det.box

            val left = b.left * scale + dx
            val top = b.top * scale + dy
            val right = b.right * scale + dx
            val bottom = b.bottom * scale + dy

            // skip if offscreen
            if (right < 0f || left > viewW || bottom < 0f || top > viewH) return@forEach

            val boxColor = when {
                det.score >= 0.8f -> Color(0xFF4CAF50) // green
                det.score >= 0.6f -> Color(0xFFFFC107) // amber
                else -> Color(0xFFF44336) // red
            }

            drawRect(
                color = boxColor,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = 3f, pathEffect = dashed)
            )

            // caption
            val caption = "${det.label} ${(det.score * 100).toInt()}%"
            val textPadding = 6f * labelTextSizeMultiplier

            drawIntoCanvas { canvas ->
                val bgPaint = Paint().apply {
                    color = android.graphics.Color.argb(170, 0, 0, 0)
                    style = Paint.Style.FILL
                }

                val textPaint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = (36f * labelTextSizeMultiplier)
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }

                val textWidth = textPaint.measureText(caption)
                val textHeight = textPaint.fontMetrics.bottom - textPaint.fontMetrics.top

                val bgLeft = left
                var bgTop = top - (textHeight + textPadding * 2)
                if (bgTop < 0f) bgTop = top

                val bgRect = android.graphics.RectF(
                    bgLeft,
                    bgTop,
                    bgLeft + textWidth + textPadding * 2,
                    bgTop + textHeight + textPadding * 2
                )

                canvas.nativeCanvas.drawRoundRect(bgRect, 10f, 10f, bgPaint)
                canvas.nativeCanvas.drawText(
                    caption,
                    bgRect.left + textPadding,
                    bgRect.bottom - textPadding - 6f,
                    textPaint
                )
            }
        }
    }
}
