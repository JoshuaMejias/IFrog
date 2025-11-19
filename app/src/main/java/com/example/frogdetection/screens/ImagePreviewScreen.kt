// File: com/example/frogdetection/screens/ImagePreviewScreen.kt
package com.example.frogdetection.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.util.Log
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.frogdetection.R
import com.example.frogdetection.data.CapturedFrogDatabase
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.utils.FrogDetectionHelper
import com.example.frogdetection.utils.FrogDetectionResult
import com.example.frogdetection.utils.getReadableLocation
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.graphics.nativeCanvas


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePreviewScreen(
    navController: NavController,
    imageUri: String?,
    latitude: Double?,
    longitude: Double?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var resolvedLocation by remember { mutableStateOf<String?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detections by remember { mutableStateOf<List<FrogDetectionResult>>(emptyList()) }
    var detectedSpecies by remember { mutableStateOf("Unknown Species") }
    var inferenceMs by remember { mutableStateOf(0.0) }
    var displaySize by remember { mutableStateOf(IntSize(0, 0)) }
    var loading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?> (null) }

    // Reverse-geocode if available
    LaunchedEffect(latitude, longitude) {
        if (latitude != null && longitude != null && latitude != 0.0 && longitude != 0.0) {
            try {
                resolvedLocation = getReadableLocation(context, latitude, longitude)
            } catch (e: Exception) {
                Log.w("ImagePreview", "Reverse geocode failed: ${e.message}")
            }
        }
    }

    // Load image once and run detection
    LaunchedEffect(imageUri) {
        if (imageUri == null) {
            statusText = "No image supplied"
            return@LaunchedEffect
        }

        // Avoid re-running if bitmap already loaded
        if (bitmap != null) return@LaunchedEffect

        loading = true
        statusText = "Loading image..."
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(Uri.parse(imageUri)).use { stream ->
                    if (stream == null) throw Exception("Unable to open image stream")
                    val bmp = BitmapFactory.decodeStream(stream)
                        ?: throw Exception("Bitmap decode failed")
                    bitmap = bmp
                }

                val results = withContext(Dispatchers.IO) {
                    try {
                        FrogDetectionHelper.detectFrogs(bitmap!!)
                    } catch (e: Exception) {
                        Log.e("ImagePreview", "detectFrogs error", e)
                        emptyList<FrogDetectionResult>()
                    }
                }

                // update UI state on main thread
                withContext(Dispatchers.Main) {
                    detections = results
                    val best = results.maxByOrNull { it.score }
                    detectedSpecies = if (best != null && best.score >= 0.35f) best.label else "Unknown Species"
                    statusText = "Done"
                }
            } catch (e: Exception) {
                Log.e("ImagePreview", "Image load/detect failed", e)
                withContext(Dispatchers.Main) {
                    statusText = "Failed: ${e.message ?: "error"}"
                    detections = emptyList()
                    detectedSpecies = "Detection failed"
                }
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Image Preview") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Icon(
                painter = rememberAsyncImagePainter(model = R.drawable.ifrog_logo),
                contentDescription = "iFrog Logo",
                modifier = Modifier
                    .size(96.dp)
                    .padding(bottom = 8.dp),
                tint = ComposeColor.Unspecified
            )

            Text(
                text = "Preview & Detect",
                style = MaterialTheme.typography.headlineSmall,
                color = ComposeColor(0xFF2E7D32)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Image box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ComposeColor.White),
                contentAlignment = Alignment.Center
            ) {
                // Painter fallback while bitmap not available
                val painter = rememberAsyncImagePainter(model = imageUri ?: R.drawable.ic_launcher_foreground)

                Image(
                    painter = painter,
                    contentDescription = "Previewed image",
                    modifier = Modifier
                        .matchParentSize()
                        .onGloballyPositioned { coords ->
                            displaySize = coords.size
                        },
                    contentScale = ContentScale.Fit
                )

                // If we have a loaded bitmap, draw detection overlays
                bitmap?.let { bmp ->
                    // Compose Canvas overlay that maps model boxes -> displayed image coordinates
                    androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
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

                        // Draw boxes
                        for (d in detections) {
                            val rect = d.box
                            val left = rect.left * scale + offsetX
                            val top = rect.top * scale + offsetY
                            val right = rect.right * scale + offsetX
                            val bottom = rect.bottom * scale + offsetY

                            val conf = d.score
                            val strokeColor = when {
                                conf >= 0.85f -> ComposeColor(0xFF00FF00)
                                conf >= 0.7f -> ComposeColor(0xFFFFFF00)
                                else -> ComposeColor(0xFFFF0000)
                            }
                            val strokeWidth = 2f + (conf * 6f)

                            drawRect(
                                color = strokeColor,
                                topLeft = Offset(left, top),
                                size = Size(
                                    width = (right - left).coerceAtLeast(1f),
                                    height = (bottom - top).coerceAtLeast(1f)
                                ),
                                style = Stroke(width = strokeWidth)
                            )

                            // Draw label background + text using Android native canvas for crisp text
                            val labelText = "${d.label} ${(d.score * 100).toInt()}%"
                            drawContext.canvas.nativeCanvas.apply {
                                val bgPaint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.argb(160, 0, 0, 0)
                                    style = android.graphics.Paint.Style.FILL
                                    isAntiAlias = true
                                }
                                val textPaint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    textSize = 28f
                                    isAntiAlias = true
                                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                    setShadowLayer(3f, 1f, 1f, android.graphics.Color.BLACK)
                                }
                                val tw = textPaint.measureText(labelText)
                                val padding = 8f
                                val bx = left.coerceAtLeast(0f)
                                var by = top - (textPaint.textSize + padding * 2)
                                if (by < 0f) by = top + padding
                                drawRect(bx, by, bx + tw + padding * 2, by + textPaint.textSize + padding * 2, bgPaint)
                                drawText(labelText, bx + padding, by + textPaint.textSize + padding / 2f, textPaint)
                            }
                        }
                    }
                }

                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                // small status on top-left
                Text(
                    text = statusText ?: (if (loading) "Working..." else ""),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(ComposeColor(0x66000000))
                        .padding(6.dp),
                    color = ComposeColor.White,
                    style = MaterialTheme.typography.bodySmall
                )

                // inference info top-right
                Text(
                    text = "Inference: ${"%.1f".format(inferenceMs)} ms",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(ComposeColor(0x66000000))
                        .padding(6.dp),
                    color = ComposeColor.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = resolvedLocation ?: "Locating...",
                style = MaterialTheme.typography.bodyMedium,
                color = ComposeColor.DarkGray
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Detected: $detectedSpecies",
                color = ComposeColor(0xFF2E7D32),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Buttons row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(
                    onClick = { navController.popBackStack() },
                    colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF90EE90))
                ) {
                    Text("Cancel", color = ComposeColor(0xFF004400))
                }

                Button(
                    onClick = {
                        // Save captured frog entry
                        if (imageUri != null) {
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val dao = CapturedFrogDatabase.getDatabase(context).capturedFrogDao()
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
                                } catch (e: Exception) {
                                    Log.e("ImagePreview", "Save failed", e)
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF66BB6A))
                ) {
                    Text("Save & Continue", color = ComposeColor.White)
                }
            }
        }
    }
}
