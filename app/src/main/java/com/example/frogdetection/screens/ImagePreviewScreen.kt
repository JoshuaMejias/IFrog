package com.example.frogdetection.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
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
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.frogdetection.R
import com.example.frogdetection.data.CapturedFrogDatabase
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.utils.FrogDetectionHelper
import com.example.frogdetection.utils.FrogDetectionResult
import com.example.frogdetection.utils.getReadableLocation
import kotlinx.coroutines.*
import kotlin.math.min

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
    var displaySize by remember { mutableStateOf(IntSize(0, 0)) }
    var inferenceTimeMs by remember { mutableStateOf(0.0) }

    // --- Reverse geocode location ---
    LaunchedEffect(latitude, longitude) {
        if (latitude != null && longitude != null && latitude != 0.0 && longitude != 0.0) {
            resolvedLocation = getReadableLocation(context, latitude, longitude)
        }
    }

    // --- Load bitmap + run detection once ---
    LaunchedEffect(imageUri) {
        if (imageUri != null && bitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(Uri.parse(imageUri)).use { input ->
                        val bmp = BitmapFactory.decodeStream(input)
                        bitmap = bmp
                        val start = System.nanoTime()
                        val results = FrogDetectionHelper.detectFrogs(context, bmp)
                        val end = System.nanoTime()
                        inferenceTimeMs = (end - start) / 1_000_000.0
                        withContext(Dispatchers.Main) {
                            detections = results
                            val top = results.maxByOrNull { it.score }
                            detectedSpecies =
                                if (top != null && top.score >= 0.35f) top.label else "Unknown Species"
                        }
                    }
                } catch (e: Exception) {
                    Log.e("🐸ImagePreview", "Detection failed", e)
                    withContext(Dispatchers.Main) {
                        detectedSpecies = "Detection failed"
                        detections = emptyList()
                    }
                }
            }
        }
    }

    // --- Layout ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(ComposeColor(0xFFE0FFE0), ComposeColor(0xFFB0FFB0))
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
                tint = ComposeColor.Unspecified
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

            // --- Image with bounding boxes ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .onGloballyPositioned { coords -> displaySize = coords.size }
            ) {
                val painter = rememberAsyncImagePainter(Uri.parse(imageUri))
                Image(
                    painter = painter,
                    contentDescription = "Previewed Frog",
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Fit
                )

                bitmap?.let { bmp ->
                    Canvas(modifier = Modifier.matchParentSize()) {
                        if (displaySize.width == 0 || displaySize.height == 0) return@Canvas

                        val imgW = bmp.width.toFloat()
                        val imgH = bmp.height.toFloat()
                        val dispW = size.width
                        val dispH = size.height
                        val scale = min(dispW / imgW, dispH / imgH)
                        val scaledW = imgW * scale
                        val scaledH = imgH * scale
                        val offsetX = (dispW - scaledW) / 2f
                        val offsetY = (dispH - scaledH) / 2f

                        detections.forEach { det ->
                            val box = det.boundingBox
                            val confidence = det.score
                            val left = box.left * scale + offsetX
                            val top = box.top * scale + offsetY
                            val right = box.right * scale + offsetX
                            val bottom = box.bottom * scale + offsetY

                            val strokeColor = when {
                                confidence >= 0.85f -> ComposeColor(0xFF00FF00)
                                confidence >= 0.7f -> ComposeColor(0xFFFFFF00)
                                else -> ComposeColor(0xFFFF0000)
                            }

                            val strokeWidth = 2f + (confidence * 6f)
                            drawRect(
                                color = strokeColor,
                                topLeft = Offset(left, top),
                                size = Size(
                                    (right - left).coerceAtLeast(1f),
                                    (bottom - top).coerceAtLeast(1f)
                                ),
                                style = Stroke(width = strokeWidth)
                            )

                            // Draw label background + text
                            val labelText = "${det.label} ${(det.score * 100).toInt()}%"
                            val textColor = ComposeColor.White
                            val shadowColor = ComposeColor.Black
                            val backgroundColor = ComposeColor(0x96000000)

                            drawContext.canvas.nativeCanvas.apply {
                                val bgPaint = android.graphics.Paint().apply {
                                    color = backgroundColor.toArgb()
                                    style = android.graphics.Paint.Style.FILL
                                    isAntiAlias = true
                                }

                                val textPaint = android.graphics.Paint().apply {
                                    color = textColor.toArgb()
                                    textSize = 28f
                                    setShadowLayer(4f, 2f, 2f, shadowColor.toArgb())
                                    isAntiAlias = true
                                    style = android.graphics.Paint.Style.FILL
                                    typeface = android.graphics.Typeface.create(
                                        android.graphics.Typeface.DEFAULT,
                                        android.graphics.Typeface.BOLD
                                    )
                                }

                                val textWidth = textPaint.measureText(labelText)
                                val textHeight = textPaint.textSize
                                val bx = left
                                val by = (top - textHeight - 8f).coerceAtLeast(0f)
                                val bRight = (bx + textWidth + 12f).coerceAtMost(size.width)

                                drawRect(bx, by, bRight, by + textHeight + 6f, bgPaint)
                                drawText(labelText, bx + 6f, top - 8f, textPaint)
                            }
                        }
                    }

                    // 🧠 Overlay inference time & FPS
                    Text(
                        text = "Inference: %.1f ms (%.1f FPS)".format(
                            inferenceTimeMs,
                            if (inferenceTimeMs > 0) 1000.0 / inferenceTimeMs else 0.0
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(ComposeColor(0x66000000))
                            .padding(4.dp),
                        color = ComposeColor.White,
                        fontWeight = FontWeight.Bold
                    )
                } ?: Text("No image available", color = ComposeColor.DarkGray)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = resolvedLocation ?: "Locating...",
                style = MaterialTheme.typography.bodyLarge,
                color = ComposeColor.DarkGray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Detected Species: $detectedSpecies",
                style = MaterialTheme.typography.titleMedium,
                color = ComposeColor(0xFF2E7D32),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- Buttons ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { navController.popBackStack() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeColor(0xFF90EE90)
                    )
                ) {
                    Text("Cancel", color = ComposeColor(0xFF004400))
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeColor(0xFF66BB6A)
                    )
                ) {
                    Text("Save & Continue", color = ComposeColor.White)
                }
            }
        }
    }
}
