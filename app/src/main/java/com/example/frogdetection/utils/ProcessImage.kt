package com.example.frogdetection.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun processImage(bitmap: Bitmap, context: Context, navController: NavController) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            Log.d("ProcessImage", "Processing bitmap of size: ${bitmap.width}x${bitmap.height}")
            // Add your image processing logic here (e.g., ML model inference)
            withContext(Dispatchers.Main) {
                navController.navigate("resultScreen") // Ensure this route exists
            }
        } catch (e: Exception) {
            Log.e("ProcessImage", "Error processing image: $e")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error processing image", Toast.LENGTH_SHORT).show()
            }
        }
    }
}