package com.example.frogdetection.utils

import android.content.Context
import android.net.Uri
import androidx.navigation.NavController

fun processImage(
    context: Context,
    navController: NavController,
    imageUri: Uri,
    lat: Double,
    lon: Double
) {
    // Encode both Uri and coordinates safely
    val encodedUri = Uri.encode(imageUri.toString())
    navController.navigate("preview/$encodedUri/$lat/$lon")
}
