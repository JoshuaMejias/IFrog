package com.example.frogdetection.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class FrogDetectionResult(
    val label: String,
    val score: Float,
    val boundingBox: android.graphics.RectF
)

object FrogDetectionHelper {

    private const val TAG = "🐸FrogDetection"

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        Log.d(TAG, "Loading model from assets: $modelPath")
        val fileDescriptor = context.assets.openFd(modelPath)
        FileInputStream(fileDescriptor.fileDescriptor).use { input ->
            val fileChannel = input.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
    }

    fun detectFrogs(context: Context, image: Bitmap): List<FrogDetectionResult> {
        return try {
            Log.d(TAG, "Starting frog detection...")

            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setMaxResults(5)
                .setScoreThreshold(0.25f)
                .build()

            val modelBuffer = loadModelFile(context, "best_float32.tflite")
            val detector = ObjectDetector.createFromBufferAndOptions(modelBuffer, options)

            val tensorImage = TensorImage.fromBitmap(image)
            val results = detector.detect(tensorImage)

            Log.d(TAG, "Detection complete — found ${results.size} objects")

            results.mapNotNull { detection: Detection ->
                val category = detection.categories.firstOrNull()
                category?.let {
                    FrogDetectionResult(
                        label = it.label,
                        score = it.score,
                        boundingBox = detection.boundingBox
                    )
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Detection failed: ${e.message}", e)
            emptyList()
        }
    }
}
