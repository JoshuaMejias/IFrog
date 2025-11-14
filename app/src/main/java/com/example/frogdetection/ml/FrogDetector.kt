package com.example.frogdetection.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import ai.onnxruntime.*
import com.example.frogdetection.utils.FrogDetectionResult
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer

class FrogDetector(private val context: Context) {

    companion object {
        private const val TAG = "🐸FrogDetector"
        private const val IMAGE_SIZE = 640
        private const val CONF_THRESHOLD = 0.4f
    }

    private var env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession

    init {
        // Load ONNX model from assets into local storage
        val onnxFile = loadModelFile(context, "best.onnx")
        Log.d(TAG, "✅ ONNX model loaded: ${onnxFile.absolutePath}")
        session = env.createSession(onnxFile.absolutePath, OrtSession.SessionOptions())
    }

    private fun loadModelFile(context: Context, fileName: String): File {
        val tempFile = File(context.filesDir, fileName)
        if (!tempFile.exists()) {
            context.assets.open(fileName).use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return tempFile
    }

    private fun preprocess(bitmap: Bitmap): FloatBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)
        val buffer = FloatBuffer.allocate(1 * 3 * IMAGE_SIZE * IMAGE_SIZE)
        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        scaled.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)

        // Convert image to normalized RGB channels
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            buffer.put(r)
            buffer.put(g)
            buffer.put(b)
        }
        buffer.rewind()
        return buffer
    }

    fun detectFrogs(bitmap: Bitmap): List<FrogDetectionResult> {
        val inputBuffer = preprocess(bitmap)
        val shape = longArrayOf(1, 3, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong())
        val inputTensor = OnnxTensor.createTensor(env, inputBuffer, shape)

        val inputName = session.inputNames.iterator().next()
        Log.d(TAG, "🐸 Running ONNX inference...")

        val result = session.run(mapOf(inputName to inputTensor))
        val output = result[0].value as Array<FloatArray>

        val detections = mutableListOf<FrogDetectionResult>()
        for (det in output) {
            if (det.size >= 6) {
                val x1 = det[0]
                val y1 = det[1]
                val x2 = det[2]
                val y2 = det[3]
                val conf = det[4]
                val cls = det[5]

                if (conf >= CONF_THRESHOLD) {
                    detections.add(
                        FrogDetectionResult(
                            label = "frog_${cls.toInt()}",
                            score = conf,
                            boundingBox = RectF(x1, y1, x2, y2)
                        )
                    )
                }
            }
        }


        Log.d(TAG, "✅ Detection complete — found ${detections.size} frogs")
        return detections
    }

    fun close() {
        session.close()
        env.close()
        Log.d(TAG, "🧹 ONNX session closed")
    }
}
