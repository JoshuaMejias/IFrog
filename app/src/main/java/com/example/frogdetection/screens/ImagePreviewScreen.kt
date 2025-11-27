package com.example.frogdetection.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.frogdetection.ml.DetectionOverlay
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.utils.FrogDetectionResult
import com.example.frogdetection.utils.ImageUtils
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ImagePreviewScreen(
    navController: NavHostController,
    imageUri: Uri,
    latitude: Double?,
    longitude: Double?,
    viewModel: CapturedHistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detections by remember { mutableStateOf<List<FrogDetectionResult>>(emptyList()) }
    var species by remember { mutableStateOf("Detecting…") }
    var detectDone by remember { mutableStateOf(false) }
    var locationName by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // ------------------------------------------------------------
    // LOAD & ROTATE BITMAP
    // ------------------------------------------------------------
    LaunchedEffect(imageUri) {
        detectDone = false
        bitmap = withContext(Dispatchers.IO) {
            try {
                ImageUtils.loadCorrectedBitmap(context, imageUri)
            } catch (e: Exception) {
                errorMsg = "Unable to load image: ${e.message}"
                null
            }
        }
    }

    // ------------------------------------------------------------
    // RUN DETECTOR
    // ------------------------------------------------------------
    LaunchedEffect(bitmap) {
        val bmp = bitmap ?: return@LaunchedEffect
        detectDone = false

        scope.launch {
            try {
                val detector = com.example.frogdetection.ml.FrogDetectorProvider.get(context)
                val results = withContext(Dispatchers.Default) { detector.detect(bmp) }

                detections = results
                species = results.firstOrNull()?.label ?: "Unknown Species"
            } catch (e: Exception) {
                errorMsg = "Detection failed: ${e.message}"
            }
            detectDone = true
        }
    }

    // ------------------------------------------------------------
    // REVERSE GEOCODING — OPTION 1 (instant inside screen)
    // ------------------------------------------------------------
    LaunchedEffect(latitude, longitude) {
        val lat = latitude ?: return@LaunchedEffect
        val lon = longitude ?: return@LaunchedEffect
        if (lat == 0.0 && lon == 0.0) return@LaunchedEffect

        scope.launch(Dispatchers.IO) {
            val name = com.example.frogdetection.utils.getReadableLocationFromOpenCage(
                context = context,
                latitude = lat,
                longitude = lon,
                apiKey = context.getString(com.example.frogdetection.R.string.opencage_api_key)
            )
            locationName = name
        }
    }

    // ------------------------------------------------------------
    // UI
    // ------------------------------------------------------------
    val scroll = rememberScrollState()

    Scaffold(
        bottomBar = {
            if (detectDone) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Spacer(Modifier.weight(1f))

                    // Cancel
                    TextButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel")
                    }

                    Spacer(Modifier.width(12.dp))

                    // Save
                    Button(
                        onClick = {
                            val frog = CapturedFrog(
                                imageUri = imageUri.toString(),
                                latitude = latitude,
                                longitude = longitude,
                                speciesName = species,
                                timestamp = System.currentTimeMillis(),
                                locationName = locationName
                            )

                            viewModel.insert(frog) {
                                navController.navigate("history")
                            }
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
                    }

                    Spacer(Modifier.weight(1f))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scroll)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {

            // Header gradient
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        )
                    ),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    "Preview & Detection",
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            Spacer(Modifier.height(16.dp))

            // IMAGE
            val bmp = bitmap
            if (bmp != null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .height(400.dp)
                        .clip(MaterialTheme.shapes.medium)
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    DetectionOverlay(
                        bitmap = bmp,
                        detections = detections,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // SUMMARY CARD
            Card(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Detection Summary", fontWeight = FontWeight.Bold)

                    Spacer(Modifier.height(12.dp))
                    Text("Species: $species", style = MaterialTheme.typography.bodyLarge)

                    if (latitude != null && longitude != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Latitude: %.5f".format(latitude))
                        Text("Longitude: %.5f".format(longitude))
                    }

                    Spacer(Modifier.height(8.dp))

                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(Date(System.currentTimeMillis()))
                    Text("Timestamp: $dateStr")

                    Spacer(Modifier.height(8.dp))

                    Text("Location: ${locationName ?: "Resolving…"}")
                }
            }
        }
    }
}
