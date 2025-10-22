package com.example.frogdetection.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.frogdetection.R
import com.example.frogdetection.data.CapturedFrogDatabase
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.utils.FrogDetectionHelper
import com.example.frogdetection.utils.FrogDetectionResult
import com.example.frogdetection.utils.getReadableLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImagePreviewScreen(
    navController: NavController,
    imageUri: String?,
    latitude: Double?,
    longitude: Double?
) {
    val context = navController.context

    var resolvedLocation by remember { mutableStateOf<String?>(null) }
    var detections by remember { mutableStateOf<List<FrogDetectionResult>>(emptyList()) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detectedSpecies by remember { mutableStateOf("Unknown Species") }

    // 🌍 Get readable location via OpenCage
    LaunchedEffect(latitude, longitude) {
        if (latitude != null && longitude != null && latitude != 0.0 && longitude != 0.0) {
            resolvedLocation = getReadableLocation(context, latitude, longitude)
        }
    }

    // 🧠 Run YOLO detection
    LaunchedEffect(imageUri) {
        if (imageUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    println("🐸 Running YOLO detection for $imageUri ...")
                    val input = context.contentResolver.openInputStream(Uri.parse(imageUri))
                    val bmp = BitmapFactory.decodeStream(input)
                    bitmap = bmp

                    val results = FrogDetectionHelper.detectFrogs(context, bmp)
                    println("🐸 Detections found: ${results.size}")
                    results.forEach {
                        println(" - ${it.label}: ${(it.score * 100).toInt()}%")
                    }

                    withContext(Dispatchers.Main) {
                        detections = results

                        // ✅ If highest confidence < 0.7 → "Unknown Species"
                        val top = results.maxByOrNull { it.score }
                        detectedSpecies = if (top != null && top.score >= 0.7f) {
                            top.label
                        } else {
                            "Unknown Species"
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    detectedSpecies = "Detection failed"
                }
            }
        }
    }

    // 🖼️ UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFE0FFE0), Color(0xFFB0FFB0))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ifrog_logo),
                contentDescription = "iFrog Logo",
                modifier = Modifier
                    .size(100.dp)
                    .padding(bottom = 16.dp),
                tint = Color.Unspecified
            )

            Text(
                text = "Image Preview",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 🟢 Image with bounding boxes
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                bitmap?.let { bmp ->
                    Image(
                        painter = rememberAsyncImagePainter(Uri.parse(imageUri)),
                        contentDescription = "Previewed Frog",
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )

                    Canvas(modifier = Modifier.matchParentSize()) {
                        val scaleX = size.width / bmp.width
                        val scaleY = size.height / bmp.height

                        detections.forEach { det ->
                            val box = det.boundingBox
                            val confidence = det.score

                            // 🎨 Confidence-based color
                            val color = when {
                                confidence >= 0.85f -> Color(0xFF00FF00) // Bright green
                                confidence >= 0.7f -> Color(0xFFFFFF00) // Yellow
                                else -> Color(0xFFFF0000) // Red
                            }
                            val strokeWidth = (2f + confidence * 8f)

                            // 🟩 Draw bounding box
                            drawRect(
                                color = color,
                                topLeft = Offset(box.left * scaleX, box.top * scaleY),
                                size = Size(box.width() * scaleX, box.height() * scaleY),
                                style = Stroke(width = strokeWidth)
                            )

                            // 🏷️ Draw label + confidence text
                            drawContext.canvas.nativeCanvas.drawText(
                                "${det.label} ${(det.score * 100).toInt()}%",
                                box.left * scaleX,
                                (box.top * scaleY) - 8,
                                android.graphics.Paint().apply {
                                    setColor(Color.White.toArgb())
                                    textSize = 36f
                                    setShadowLayer(4f, 2f, 2f, Color.Black.toArgb())
                                    isAntiAlias = true
                                    style = android.graphics.Paint.Style.FILL
                                    typeface = android.graphics.Typeface.create(
                                        android.graphics.Typeface.DEFAULT,
                                        android.graphics.Typeface.BOLD
                                    )
                                }
                            )
                        }
                    }
                } ?: Text("No image available", color = Color.DarkGray)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 🧭 Location
            Text(
                text = resolvedLocation ?: "Locating...",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.DarkGray
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 🧪 Detected species
            Text(
                text = "Detected Species: $detectedSpecies",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { navController.popBackStack() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF90EE90))
                ) {
                    Text("Cancel", color = Color(0xFF004400))
                }

                Button(
                    onClick = {
                        if (imageUri != null) {
                            val dao = CapturedFrogDatabase.getDatabase(context).capturedFrogDao()
                            CoroutineScope(Dispatchers.IO).launch {
                                val frog = CapturedFrog(
                                    imageUri = imageUri,
                                    latitude = latitude ?: 0.0,
                                    longitude = longitude ?: 0.0,
                                    locationName = resolvedLocation,
                                    speciesName = detectedSpecies,
                                    timestamp = System.currentTimeMillis()
                                )
                                val newId = dao.insert(frog)
                                withContext(Dispatchers.Main) {
                                    navController.navigate("resultScreen/$newId") {
                                        popUpTo("preview/{imageUri}/{lat}/{lon}") { inclusive = true }
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66BB6A))
                ) {
                    Text("Save & Continue", color = Color.White)
                }
            }
        }
    }
}
