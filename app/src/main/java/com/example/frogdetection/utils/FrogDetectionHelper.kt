package com.example.frogdetection.utils

import android.content.Context
import android.graphics.*
import android.util.Log
import ai.onnxruntime.*
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.*
import android.graphics.RectF

data class FrogDetectionResult(
    val label: String,
    val score: Float,
    val box: RectF
)
/**
 * Final YOLO11 Android Inference Helper (ONNX Runtime 1.17.0)
 */
object FrogDetectionHelper {

    private const val TAG = "🐸FrogDetection"
    private const val SIZE = 640
    private const val NUM_CLASSES = 6
    private const val CONF_THRES = 0.40f
    private const val IOU_THRES = 0.50f
    private const val MAX_RESULTS = 50

    // Labels
    private val LABELS = arrayOf(
        "Asian Painted Frog",
        "Cane Toad",
        "Common Southeast Asian Tree Frog",
        "East Asian Bullfrog",
        "Paddy Field Frog",
        "Wood Frog"
    )

    // ORT objects
    private lateinit var env: OrtEnvironment
    private lateinit var session: OrtSession

    // Grid + stride arrays
    private lateinit var grid: Array<FloatArray>
    private lateinit var strideMap: FloatArray

    private var initialized = false

    // ----------------------------------------------------
    // INIT
    // ----------------------------------------------------
    fun init(context: Context) {
        if (initialized) return
        initialized = true

        env = OrtEnvironment.getEnvironment()
        val opts = createSessionOptions()

        // Copy ONNX model from assets → internal storage
        val modelFile = loadModel(context, "frog_yolo11_detect_simplified.onnx")

        session = env.createSession(modelFile.absolutePath, opts)
        Log.d(TAG, "✅ ONNX session created")

        val (g, s) = buildGrid()
        grid = g
        strideMap = s

        Log.d(TAG, "📦 Grid cells=${grid.size}")
    }

    private fun loadModel(context: Context, name: String): File {
        val out = File(context.filesDir, name)
        if (!out.exists()) {
            context.assets.open(name).use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
        }
        Log.d(TAG, "Model copied: ${out.absolutePath}")
        return out
    }

    private fun createSessionOptions(): OrtSession.SessionOptions {
        val opts = OrtSession.SessionOptions()

        // CPU Execution Provider (default)
        try {
            opts.addCPU(true) // true = use arena allocator
        } catch (e: Exception) {
            Log.w(TAG, "CPU EP add failed: ${e.message}")
        }

        // Optional: set thread counts
        opts.setIntraOpNumThreads(1)
        opts.setInterOpNumThreads(1)

        // Optional: tune performance
        opts.addConfigEntry("session.intra_op_thread_affinities", "0")

        Log.d(TAG, "ORT 1.17.0 CPU session created")
        return opts
    }


    // ----------------------------------------------------
    // BUILD YOLO11 GRID + STRIDES
    // ----------------------------------------------------
    private fun buildGrid(): Pair<Array<FloatArray>, FloatArray> {
        val list = ArrayList<FloatArray>()
        val strideList = ArrayList<Float>()

        val STRIDES = intArrayOf(8, 16, 32)

        for (s in STRIDES) {
            val fm = SIZE / s
            for (y in 0 until fm) {
                for (x in 0 until fm) {
                    list.add(floatArrayOf(x.toFloat(), y.toFloat()))
                    strideList.add(s.toFloat())
                }
            }
        }

        return Pair(list.toTypedArray(), strideList.toFloatArray())
    }

    // ----------------------------------------------------
    // PREPROCESS (LETTERBOX)
    // ----------------------------------------------------
    private data class Prep(
        val tensor: OnnxTensor,
        val scale: Float,
        val padX: Float,
        val padY: Float
    )

    private fun preprocess(img: Bitmap): Prep {
        val w = img.width
        val h = img.height

        val scale = min(SIZE / w.toFloat(), SIZE / h.toFloat())
        val newW = (w * scale).roundToInt()
        val newH = (h * scale).roundToInt()

        val resized = Bitmap.createScaledBitmap(img, newW, newH, true)
        val padded = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(padded)
        canvas.drawColor(Color.rgb(114, 114, 114))
        val padX = (SIZE - newW) / 2f
        val padY = (SIZE - newH) / 2f
        canvas.drawBitmap(resized, padX, padY, null)

        val pixels = IntArray(SIZE * SIZE)
        padded.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)

        val fb = FloatBuffer.allocate(1 * 3 * SIZE * SIZE)
        val area = SIZE * SIZE

        // R Channel
        for (i in 0 until area) fb.put(((pixels[i] shr 16) and 0xFF) / 255f)
        // G Channel
        for (i in 0 until area) fb.put(((pixels[i] shr 8) and 0xFF) / 255f)
        // B Channel
        for (i in 0 until area) fb.put((pixels[i] and 0xFF) / 255f)

        fb.rewind()
        val tensor = OnnxTensor.createTensor(env, fb, longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong()))
        return Prep(tensor, scale, padX, padY)
    }

    private fun sigmoid(x: Float) = (1f / (1f + exp(-x)))

    // ----------------------------------------------------
    // MAIN DETECTION
    // ----------------------------------------------------
    fun detectFrogs(img: Bitmap): List<FrogDetectionResult> {
        if (!initialized) throw IllegalStateException("FrogDetectionHelper.init() not called!")

        val prep = preprocess(img)
        val inputName = session.inputNames.first()

        val outputs = session.run(mapOf(inputName to prep.tensor))
        prep.tensor.close()

        val raw = (outputs[0].value as Array<Array<FloatArray>>)[0]  // [C, N]
        outputs.close()

        val C = raw.size       // 10 channels
        val N = raw[0].size    // 8400 cells

        val results = ArrayList<FrogDetectionResult>()

        for (i in 0 until N) {

            val cx = raw[0][i]
            val cy = raw[1][i]
            val w = raw[2][i]
            val h = raw[3][i]

            val obj = sigmoid(raw[4][i])
            if (obj < 0.1f) continue

            var bestClass = 0
            var bestProb = 0f

            for (c in 0 until NUM_CLASSES) {
                val p = sigmoid(raw[5 + c][i])
                if (p > bestProb) {
                    bestProb = p
                    bestClass = c
                }
            }

            val score = obj * bestProb
            if (score < CONF_THRES) continue

            val g = grid[i]
            val s = strideMap[i]

            val bx = (sigmoid(cx) * 2 - 0.5f + g[0]) * s
            val by = (sigmoid(cy) * 2 - 0.5f + g[1]) * s
            val bw = (sigmoid(w) * 2).pow(2) * s
            val bh = (sigmoid(h) * 2).pow(2) * s

            val x1 = bx - bw / 2
            val y1 = by - bh / 2
            val x2 = bx + bw / 2
            val y2 = by + bh / 2

            val rx1 = (x1 - prep.padX) / prep.scale
            val ry1 = (y1 - prep.padY) / prep.scale
            val rx2 = (x2 - prep.padX) / prep.scale
            val ry2 = (y2 - prep.padY) / prep.scale

            results.add(
                FrogDetectionResult(
                    label = LABELS[bestClass],
                    score = score,
                    box = RectF(rx1, ry1, rx2, ry2)
                )
            )
        }

        return nms(results)
    }

    // ----------------------------------------------------
    // NMS
    // ----------------------------------------------------
    private fun iou(a: RectF, b: RectF): Float {
        val x1 = max(a.left, b.left)
        val y1 = max(a.top, b.top)
        val x2 = min(a.right, b.right)
        val y2 = min(a.bottom, b.bottom)

        val w = max(0f, x2 - x1)
        val h = max(0f, y2 - y1)
        val inter = w * h
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun nms(src: List<FrogDetectionResult>): List<FrogDetectionResult> {
        val sorted = src.sortedByDescending { it.score }.toMutableList()
        val keep = ArrayList<FrogDetectionResult>()

        while (sorted.isNotEmpty() && keep.size < MAX_RESULTS) {
            val best = sorted.removeAt(0)
            keep.add(best)

            val it = sorted.iterator()
            while (it.hasNext()) {
                val other = it.next()
                if (iou(best.box, other.box) > IOU_THRES) it.remove()
            }
        }

        return keep
    }
}
