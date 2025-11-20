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
    val encoded = Uri.encode(imageUri.toString())
    navController.navigate("preview/$encoded/$lat/$lon")
}
