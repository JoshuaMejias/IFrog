package com.example.frogdetection.utils

import android.graphics.RectF

/**
 * Final unified detection result model
 * used by FrogDetectorTFLite, overlay, and UI components.
 */
data class FrogDetectionResult(
    val label: String,
    val score: Float,
    val box: RectF
)
