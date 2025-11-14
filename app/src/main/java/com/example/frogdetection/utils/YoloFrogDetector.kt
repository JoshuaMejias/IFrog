package com.example.frogdetection.utils

import android.content.Context
import android.graphics.*
import android.util.Log
import ai.onnxruntime.*
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import com.example.frogdetection.utils.FrogDetectionResult


//data class FrogDetectionResult(
//    val label: String,
//    val score: Float,
//    val boundingBox: RectF
//)

object YoloFrogDetector {

    private const val TAG = "🐸YoloFrogDetector"
    private const val IMAGE_SIZE = 640
    private const val CONF_THRESHOLD = 0.4f

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null

    fun initialize(context: Context) {
        if (session != null) return // already initialized

        env = OrtEnvironment.getEnvironment()
        val modelFile = loadModelFile(context, "best.onnx")

        // 🔍 Debug info — confirm ONNX model is correctly copied from assets
        Log.d(TAG, "Assets path: ${context.filesDir.absolutePath}")
        Log.d(TAG, "Model file path: ${modelFile.absolutePath}")
        Log.d(TAG, "Model file exists: ${modelFile.exists()}, size: ${modelFile.length()} bytes")

        val opts = OrtSession.SessionOptions()
        session = env!!.createSession(modelFile.absolutePath, opts)
        Log.d(TAG, "✅ ONNX model loaded successfully")
    }


    // ✅ Load ONNX model from assets into temp file
    private fun loadModelFile(context: Context, modelName: String): File {
        val outFile = File(context.filesDir, modelName)
        if (!outFile.exists()) {
            context.assets.open(modelName).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile
    }

    // ✅ Preprocess image → FloatBuffer [1,3,640,640]
    private fun preprocess(bitmap: Bitmap): FloatBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)
        val buffer = FloatBuffer.allocate(1 * 3 * IMAGE_SIZE * IMAGE_SIZE)
        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        scaled.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)

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

    // ✅ Run ONNX inference and return frog detections
    fun detect(bitmap: Bitmap): List<FrogDetectionResult> {
        val sess = session ?: throw IllegalStateException("YoloFrogDetector not initialized.")
        val inputName = sess.inputNames.iterator().next()

        val inputTensor = OnnxTensor.createTensor(
            env,
            preprocess(bitmap),
            longArrayOf(1, 3, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong())
        )

        Log.d(TAG, "🐸 Running ONNX inference...")
        val outputs = sess.run(mapOf(inputName to inputTensor))
        val outputArray = outputs[0].value as Array<FloatArray>

        val results = mutableListOf<FrogDetectionResult>()
        for (det in outputArray) {
            if (det.size >= 6) {
                val x1 = det[0]
                val y1 = det[1]
                val x2 = det[2]
                val y2 = det[3]
                val conf = det[4]
                val cls = det[5]
                if (conf >= CONF_THRESHOLD) {
                    results.add(
                        FrogDetectionResult(
                            label = "frog_${cls.toInt()}",
                            score = conf,
                            boundingBox = RectF(x1, y1, x2, y2)
                        )
                    )
                }
            }
        }

        Log.d(TAG, "✅ Detection complete — found ${results.size} frogs")
        return results
    }

    // ✅ Draw bounding boxes and labels
    fun drawDetections(bitmap: Bitmap, detections: List<FrogDetectionResult>): Bitmap {
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val boxPaint = Paint().apply {
            color = Color.parseColor("#4CAF50")
            strokeWidth = 6f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            setShadowLayer(5f, 2f, 2f, Color.BLACK)
        }

        val bgPaint = Paint().apply {
            color = Color.parseColor("#66000000")
            style = Paint.Style.FILL
        }

        detections.forEach { det ->
            val rect = det.boundingBox
            val labelText = "${det.label} ${(det.score * 100).toInt()}%"
            val textWidth = textPaint.measureText(labelText)
            val textPadding = 8f

            val left = rect.left
            val top = rect.top - textPaint.textSize - textPadding * 2

            // Draw label background
            canvas.drawRect(
                left,
                top,
                left + textWidth + textPadding * 2,
                rect.top,
                bgPaint
            )

            // Draw bounding box
            canvas.drawRect(rect, boxPaint)

            // Draw label text
            canvas.drawText(labelText, left + textPadding, rect.top - textPadding, textPaint)
        }

        return output
    }

    // ✅ Release ONNX resources
    fun close() {
        session?.close()
        env?.close()
        session = null
        env = null
        Log.d(TAG, "🧹 YoloFrogDetector closed.")
    }
}
