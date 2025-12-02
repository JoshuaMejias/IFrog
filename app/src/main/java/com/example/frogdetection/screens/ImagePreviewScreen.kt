package com.example.frogdetection.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.example.frogdetection.ml.DetectionOverlay
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.model.DetectionStore
import com.example.frogdetection.utils.FrogDetectionResult
import com.example.frogdetection.utils.ImageUtils
import com.example.frogdetection.utils.classifyEdibility
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ImagePreviewScreen(
    navController: NavHostController,
    imageUri: Uri,
    latitude: Double?,
    longitude: Double?,
    viewModel: CapturedHistoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
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
    // Load & rotate image
    // ------------------------------------------------------------
    LaunchedEffect(imageUri) {
        detectDone = false
        bitmap = withContext(Dispatchers.IO) {
            try { ImageUtils.loadCorrectedBitmap(context, imageUri) }
            catch (e: Exception) {
                errorMsg = "Unable to load image: ${e.message}"
                null
            }
        }
    }

    // ------------------------------------------------------------
    // Run detector
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
    // Reverse geocode
    // ------------------------------------------------------------
    // Reverse geocode (OpenCage → Nominatim fallback)
    LaunchedEffect(latitude, longitude) {
        val lat = latitude ?: return@LaunchedEffect
        val lon = longitude ?: return@LaunchedEffect

        if (lat == 0.0 && lon == 0.0) return@LaunchedEffect

        scope.launch(Dispatchers.IO) {
            val readable = com.example.frogdetection.utils.getReadableLocationFromOpenCage(
                context = context,
                latitude = lat,
                longitude = lon,
                apiKey = context.getString(com.example.frogdetection.R.string.opencage_api_key)
            )
            locationName = readable
        }
    }


    val scroll = rememberScrollState()

    Scaffold(
        bottomBar = {
            if (detectDone) {
                BottomAppBar(containerColor = MaterialTheme.colorScheme.surface) {

                    Spacer(Modifier.weight(1f))

                    TextButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel")
                    }

                    Spacer(Modifier.width(12.dp))

                    Button(
                        onClick = {
                            val gson = Gson()

                            // Convert detection list → JSON
                            val detectionStoreList = detections.map {
                                DetectionStore(
                                    species = it.label,
                                    score = it.score,
                                    left = it.box.left,
                                    top = it.box.top,
                                    right = it.box.right,
                                    bottom = it.box.bottom
                                )
                            }

                            val detectionsJson = gson.toJson(detectionStoreList)

                            val top = detectionStoreList.maxByOrNull { it.score }

                            val frog = CapturedFrog(
                                imageUri = imageUri.toString(),
                                latitude = latitude ?: 0.0,
                                longitude = longitude ?: 0.0,
                                speciesName = top?.species ?: "Unknown",
                                score = top?.score ?: 0f,
                                detectionsJson = detectionsJson,
                                timestamp = System.currentTimeMillis(),
                                locationName = locationName
                            )

                            viewModel.insert(frog) { newId ->
                                navController.navigate("resultScreen/$newId")
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

            // Header
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

            // ⭐ Edibility Badge
            val edibility = classifyEdibility(species)

            SuggestionChip(
                onClick = {},
                label = {
                    Text(
                        "${edibility.emoji} ${edibility.label}",
                        color = Color.White
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = edibility.color
                ),
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 10.dp)
            )

            // Image + Boxes
            val bmp = bitmap
            if (bmp != null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .height(400.dp)
                        .clip(RoundedCornerShape(16.dp))
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

            // ⭐ Detection List UI
            Card(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Detected Frogs", fontWeight = FontWeight.Bold)

                    if (detections.isEmpty()) {
                        Text("No frogs detected.")
                    } else {
                        detections.forEachIndexed { index, d ->
                            Spacer(Modifier.height(10.dp))
                            Text("• #${index + 1} — ${d.label} (${(d.score * 100).toInt()}%)")
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Summary
            Card(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Detection Summary", fontWeight = FontWeight.Bold)

                    Spacer(Modifier.height(12.dp))
                    Text("Species: $species")

                    if (latitude != null && longitude != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Latitude: %.5f".format(latitude))
                        Text("Longitude: %.5f".format(longitude))
                    }

                    Spacer(Modifier.height(8.dp))

                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(Date())
                    Text("Timestamp: $dateStr")

                    Spacer(Modifier.height(8.dp))

                    Text("Location: ${locationName ?: "Resolving…"}")
                }
            }
        }
    }
}
