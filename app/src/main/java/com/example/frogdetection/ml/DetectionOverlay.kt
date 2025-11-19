package com.example.frogdetection.ml

import android.graphics.Bitmap
import com.example.frogdetection.utils.FrogDrawUtils
import com.example.frogdetection.utils.FrogDetectionResult

/** Convenience: draw detections on a bitmap and return annotated bitmap */
fun overlayDetections(original: Bitmap, detections: List<FrogDetectionResult>): Bitmap {
    return FrogDrawUtils.drawDetections(original, detections)
}
