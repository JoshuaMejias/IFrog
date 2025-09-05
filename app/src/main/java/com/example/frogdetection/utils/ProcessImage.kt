package com.example.frogdetection.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

fun processImage(bitmap: Bitmap, context: Context, navController: NavController) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // Save bitmap to cache directory
            val file = File(context.cacheDir, "frog_preview.png")
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            // Convert file to content:// URI using FileProvider
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider", // authority from Manifest
                file
            )

            Log.d("ProcessImage", "Image saved at: $uri")

            withContext(Dispatchers.Main) {
                navController.navigate("imagePreviewScreen/${Uri.encode(uri.toString())}")
            }
        } catch (e: Exception) {
            Log.e("ProcessImage", "Error processing image: $e")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error processing image", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
