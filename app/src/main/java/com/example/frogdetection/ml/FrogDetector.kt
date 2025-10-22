package com.example.frogdetection.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector

class FrogDetector(private val context: Context) {

    private var detector: ObjectDetector? = null

    init {
        // Load YOLOv11 model
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(5) // show top 5 detections
            .setScoreThreshold(0.4f) // confidence threshold
            .build()

        detector = ObjectDetector.createFromFileAndOptions(
            context,
            "best_float32.tflite", // model file in assets
            options
        )
    }

    fun detectFrogs(bitmap: Bitmap): List<Detection> {
        val image = TensorImage.fromBitmap(bitmap)
        return detector?.detect(image) ?: emptyList()
    }

    fun close() {
        detector?.close()
    }
}
