package com.example.frogdetection.ml

import android.content.Context

/**
 * Lazy-initialized singleton ONNX detector.
 * Ensures only one ONNX session is created in the entire app.
 */
object FrogDetectorProvider {

    @Volatile
    private var INSTANCE: FrogDetectorONNX? = null

    fun get(context: Context): FrogDetectorONNX {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: FrogDetectorONNX(context.applicationContext).also {
                INSTANCE = it
            }
        }
    }

    fun close() {
        INSTANCE?.close()
        INSTANCE = null
    }
}
