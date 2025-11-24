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
import androidx.compose.ui.unit.dp
import com.example.frogdetection.utils.FrogDetectionResult
import kotlin.math.max
import kotlin.math.min

/**
 * Draws bounding boxes correctly even when Image() uses ContentScale.Crop.
 */
@Composable
fun DetectionOverlay(
    bitmap: Bitmap,
    detections: List<FrogDetectionResult>,
    modifier: Modifier
) {
    Canvas(modifier = modifier) {

        val viewW = size.width
        val viewH = size.height

        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()

        // Aspect ratios
        val viewRatio = viewW / viewH
        val bmpRatio = bmpW / bmpH

        // Compute how bitmap is scaled and centered inside the view when ContentScale.Crop is used.
        // CROP = fill entire view, center align, extra cropped.
        val scale: Float
        val dx: Float
        val dy: Float

        if (bmpRatio > viewRatio) {
            // bitmap is wider → height fits, width crops
            scale = viewH / bmpH
            val scaledWidth = bmpW * scale
            dx = (viewW - scaledWidth) / 2f  // negative → cropped
            dy = 0f
        } else {
            // bitmap is taller → width fits, height crops
            scale = viewW / bmpW
            val scaledHeight = bmpH * scale
            dx = 0f
            dy = (viewH - scaledHeight) / 2f // negative → cropped
        }

        // Box appearance
        val dashedBorder = PathEffect.dashPathEffect(floatArrayOf(14f, 14f), 0f)

        detections.forEach { det ->
            val b = det.box

            // Map original bitmap box → cropped display coordinates
            val left   = b.left * scale + dx
            val top    = b.top * scale + dy
            val right  = b.right * scale + dx
            val bottom = b.bottom * scale + dy

            // Prevent drawing outside view
            if (right < 0 || left > viewW || bottom < 0 || top > viewH) return@forEach

            // Dynamic color by confidence
            val boxColor = when {
                det.score >= 0.8f -> Color(0xFF00FF00)
                det.score >= 0.6f -> Color(0xFFFFFF00)
                else -> Color(0xFFFF0000)
            }

            // Draw rectangle border
            drawRect(
                color = boxColor,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = 4f, pathEffect = dashedBorder)
            )

            // Draw label background + text
            val caption = "${det.label} ${(det.score * 100).toInt()}%"
            val textPadding = 8f

            drawIntoCanvas { canvas ->
                val bgPaint = Paint().apply {
                    color = android.graphics.Color.argb(170, 0, 0, 0)
                    style = Paint.Style.FILL
                }

                val textPaint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 38f
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT,
                        android.graphics.Typeface.BOLD
                    )
                }

                val textWidth = textPaint.measureText(caption)
                val textHeight = textPaint.fontMetrics.bottom - textPaint.fontMetrics.top

                val rectLeft = left
                var rectTop = top - (textHeight + textPadding * 2)

                // Keep label within screen
                if (rectTop < 0) rectTop = top

                val rect = android.graphics.RectF(
                    rectLeft,
                    rectTop,
                    rectLeft + textWidth + textPadding * 2,
                    rectTop + textHeight + textPadding * 2
                )

                canvas.nativeCanvas.drawRoundRect(rect, 14f, 14f, bgPaint)

                canvas.nativeCanvas.drawText(
                    caption,
                    rect.left + textPadding,
                    rect.bottom - textPadding - 6f,
                    textPaint
                )
            }
        }
    }
}
