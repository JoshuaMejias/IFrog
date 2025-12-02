@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.frogdetection.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.frogdetection.R
import com.example.frogdetection.ml.DetectionOverlay
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.model.DetectionStore
import com.example.frogdetection.utils.FrogDetectionResult
import com.example.frogdetection.utils.ImageUtils
import com.example.frogdetection.utils.classifyEdibility
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
import com.google.gson.Gson
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
    viewModel: CapturedHistoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detections by remember { mutableStateOf<List<FrogDetectionResult>>(emptyList()) }
    var species by remember { mutableStateOf("Detecting…") }
    var detectDone by remember { mutableStateOf(false) }
    var locationName by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Playful hop animation
    val infiniteTransition = rememberInfiniteTransition()
    val hopScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.065f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Load & rotate image
    LaunchedEffect(imageUri) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                ImageUtils.loadCorrectedBitmap(context, imageUri)
            } catch (e: Exception) {
                errorMsg = "Unable to load image: ${e.message}"
                null
            }
        }
    }

    // Detect frogs
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
            } finally {
                detectDone = true
            }
        }
    }

    // Reverse geocoding
    LaunchedEffect(latitude, longitude) {
        val lat = latitude ?: return@LaunchedEffect
        val lon = longitude ?: return@LaunchedEffect

        if (lat == 0.0 && lon == 0.0) return@LaunchedEffect

        scope.launch(Dispatchers.IO) {
            try {
                val readable = com.example.frogdetection.utils.getReadableLocationFromOpenCage(
                    context = context,
                    latitude = lat,
                    longitude = lon,
                    apiKey = context.getString(R.string.opencage_api_key)
                )
                locationName = readable
            } catch (e: Exception) {
                locationName = null
            }
        }
    }

    // Snackbar for errors
    LaunchedEffect(errorMsg) {
        errorMsg?.let {
            snackbarHostState.showSnackbar(it)
            errorMsg = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ifrog_logo),
                            contentDescription = "iFrog",
                            modifier = Modifier
                                .size(48.dp)
                                .scale(hopScale)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Preview & Detection", style = MaterialTheme.typography.titleLarge)
                            AnimatedContent(targetState = species) { s ->
                                Text(
                                    s,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = detectDone) {
                ExtendedFloatingActionButton(
                    onClick = {
                        isSaving = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                val gson = Gson()

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
                                    scope.launch {
                                        isSaving = false
                                        navController.navigate("resultScreen/$newId") {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                isSaving = false
                                snackbarHostState.showSnackbar("Save failed: ${e.message}")
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Check, contentDescription = "Save") },
                    text = { Text(if (isSaving) "Saving…" else "Save") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        val scroll = rememberScrollState()

        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scroll)
                .fillMaxSize()
                .padding(16.dp)
        ) {

            // Image preview card
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Box(Modifier.fillMaxSize()) {
                    val bmp = bitmap
                    if (bmp != null) {
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

                        val edibility = classifyEdibility(species)

                        SuggestionChip(
                            onClick = {},
                            label = { Text("${edibility.emoji} ${edibility.label}") },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = edibility.color
                            ),
                            modifier = Modifier
                                .padding(12.dp)
                                .align(Alignment.TopStart)
                        )

                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text("Loading image…")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Detection summary
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(Modifier.padding(16.dp)) {

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Detection Summary", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))

                        if (!detectDone) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("${detections.size} detected")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    val top = detections.maxByOrNull { it.score }
                    Text("Top species: ${top?.label ?: species}")
                    top?.let { Text("Confidence: ${(it.score * 100).toInt()}%") }

                    Spacer(Modifier.height(12.dp))

                    Row {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = Color(0xFF2196F3)  // Material Blue
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(locationName ?: "Resolving location…")
                            if (latitude != null && longitude != null) {
                                Text("Lat: %.5f, Lon: %.5f".format(latitude, longitude))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Detection list
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Detected Frogs", fontWeight = FontWeight.Bold)

                    if (!detectDone) {
                        Text("Detecting…")
                    } else if (detections.isEmpty()) {
                        Text("No frogs detected.")
                    } else {
                        detections.forEachIndexed { index, d ->
                            Spacer(Modifier.height(8.dp))
                            Text("• #${index + 1} — ${d.label} (${(d.score * 100).toInt()}%)")
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    BackHandler { navController.popBackStack() }
}
