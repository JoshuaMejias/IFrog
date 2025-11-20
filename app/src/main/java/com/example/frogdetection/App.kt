package com.example.frogdetection

import android.app.Application
import com.example.frogdetection.utils.FrogDetectionHelper

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // GLOBAL ONNX initialization (happens ONCE)
        FrogDetectionHelper.init(this)
    }
}
