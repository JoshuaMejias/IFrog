package com.example.frogdetection.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun ResultScreen(navController: NavController, result: String?, bitmap: Bitmap?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F0F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Frog Analysis Result",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))

            // Display the image if available
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Processed Frog Image",
                    modifier = Modifier
                        .size(200.dp)
                        .background(Color.White, shape = MaterialTheme.shapes.medium)
                        .padding(8.dp)
                )
                Spacer(Modifier.height(16.dp))
            }

            // Display the result
            Text(
                text = result ?: "No result available",
                style = MaterialTheme.typography.bodyLarge,
                color = if (result == "Safe") Color.Green else Color.Red,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))

            // Back button
            Button(
                onClick = { navController.popBackStack() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF90EE90))
            ) {
                Text("Back to Home")
            }
        }
    }
}