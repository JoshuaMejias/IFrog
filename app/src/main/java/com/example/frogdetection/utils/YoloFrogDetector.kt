package com.example.frogdetection.utils

import android.content.Context
import android.graphics.*
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.Detection

object YoloFrogDetector {

    private var detector: ObjectDetector? = null

    // ✅ Initialize once (call in ViewModel or Activity onCreate)
    fun initialize(context: Context) {
        if (detector == null) {
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setMaxResults(10)
                .setScoreThreshold(0.4f)
                .build()

            detector = ObjectDetector.createFromFileAndOptions(
                context,
                "best_float32.tflite",
                options
            )
        }
    }

    // ✅ Run detection on a captured image
    fun detect(bitmap: Bitmap): List<Detection> {
        val image = TensorImage.fromBitmap(bitmap)
        return detector?.detect(image) ?: emptyList()
    }

    // ✅ Draw bounding boxes + labels
    fun drawDetections(
        bitmap: Bitmap,
        detections: List<Detection>
    ): Bitmap {
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val boxPaint = Paint().apply {
            color = Color.parseColor("#4CAF50")
            strokeWidth = 6f
            style = Paint.Style.STROKE
        }
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 42f
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = 2f
        }

        detections.forEach { det ->
            val box = det.boundingBox
            val label = det.categories.firstOrNull()?.label ?: "Unknown"
            val score = det.categories.firstOrNull()?.score ?: 0f
            val labelText = "$label ${(score * 100).toInt()}%"

            canvas.drawRect(box, boxPaint)
            canvas.drawText(labelText, box.left + 8f, box.top - 10f, textPaint)
        }

        return output
    }

    // ✅ Clean up detector
    fun close() {
        detector?.close()
        detector = null
    }
}
