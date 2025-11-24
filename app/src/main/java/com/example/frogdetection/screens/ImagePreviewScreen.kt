package com.example.frogdetection.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.frogdetection.ml.DetectionOverlay
import com.example.frogdetection.ml.FrogDetectorProvider
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.utils.FrogDetectionResult
import com.example.frogdetection.utils.ImageUtils
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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
    var selectedSpecies by remember { mutableStateOf("Detecting...") }
    var detectDone by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // For delete confirmation
    var showDeleteDialog by remember { mutableStateOf(false) }
    // For Snackbar / undo
    val snackbarHostState = remember { SnackbarHostState() }
    var lastSavedId by remember { mutableStateOf<Long?>(null) }

    // ---------------------------
    // LOAD BITMAP (with EXIF fix)
    // ---------------------------
    LaunchedEffect(imageUri) {
        detectDone = false
        detections = emptyList()
        errorMsg = null

        bitmap = withContext(Dispatchers.IO) {
            try {
                ImageUtils.loadCorrectedBitmap(context, imageUri)
            } catch (e: Exception) {
                errorMsg = "Failed to load image: ${e.message}"
                null
            }
        }
    }

    // ---------------------------
    // RUN DETECTOR
    // ---------------------------
    LaunchedEffect(bitmap) {
        val bmp = bitmap ?: return@LaunchedEffect
        detectDone = false

        scope.launch {
            val detector = FrogDetectorProvider.get(context)
            val resultList = try {
                withContext(Dispatchers.Default) { detector.detect(bmp) }
            } catch (e: Exception) {
                errorMsg = "Detection failed: ${e.message}"
                emptyList()
            }

            detections = resultList
            selectedSpecies = resultList.firstOrNull()?.label ?: "Unknown Species"
            detectDone = true
        }
    }

    // ---------------------------
    // UI
    // ---------------------------
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (detectDone && bitmap != null) {
                Row(
                    modifier = Modifier.padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Delete button
                    FloatingActionButton(
                        containerColor = MaterialTheme.colorScheme.error,
                        onClick = { showDeleteDialog = true }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }

                    // Save button
                    FloatingActionButton(
                        containerColor = MaterialTheme.colorScheme.primary,
                        onClick = {
                            // compute top confidence
                            val topScore = detections.maxOfOrNull { it.score } ?: 0f
                            val frog = CapturedFrog(
                                imageUri = imageUri.toString(),
                                latitude = latitude,
                                longitude = longitude,
                                speciesName = selectedSpecies,
                                timestamp = System.currentTimeMillis(),
                                locationName = null,
                                confidence = topScore
                            )

                            // insert and show snackbar with undo
                            scope.launch {
                                viewModel.insert(frog) { newId ->
                                    lastSavedId = newId
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Saved",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            // Undo -> delete the saved item if it exists
                                            lastSavedId?.let { id ->
                                                scope.launch {
                                                    val saved = viewModel.getFrogById(id.toInt())
                                                    saved?.let { viewModel.deleteFrog(it) }
                                                }
                                            }
                                        } else {
                                            // Optionally navigate to history
                                            // navController.navigate("history")
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            }
        }
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .background(MaterialTheme.colorScheme.surface),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Preview & Detection",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(12.dp))

            // ---------------------------
            // IMAGE CARD
            // ---------------------------
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(440.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        DetectionOverlay(
                            bitmap = bmp,
                            detections = detections,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Species label overlay
                        if (detectDone) {
                            Surface(
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = selectedSpecies,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            // ---------------------------
            // DETECTION LIST
            // ---------------------------
            if (detectDone && detections.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text("Detections:", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    detections.take(3).forEach { det ->
                        Text("• ${det.label} (${String.format("%.1f", det.score * 100)}%)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // ---------------------------
            // DETECTION SUMMARY CARD
            // ---------------------------
            if (detectDone && bitmap != null) {
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(System.currentTimeMillis()))

                Card(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Detection Summary", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        Text("Species: $selectedSpecies", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(6.dp))
                        if (latitude != null && longitude != null) {
                            Text("Latitude: %.5f".format(latitude), style = MaterialTheme.typography.bodyMedium)
                            Text("Longitude: %.5f".format(longitude), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(6.dp))
                        }
                        Text("Timestamp: $dateStr", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        val cachedName = viewModel.capturedFrogs.value.find { it.imageUri == imageUri.toString() }?.locationName
                        Text("Location: ${cachedName ?: "Not yet resolved"}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }

    // ---------------------------
    // DELETE confirmation dialog
    // ---------------------------
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Image") },
            text = { Text("Discard this image and detected results?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    // simply navigate back (image not saved)
                    navController.popBackStack()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
