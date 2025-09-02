package com.example.frogdetection.screens

import android.Manifest
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.frogdetection.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.opencsv.CSVReader
import android.util.Log
import com.google.accompanist.permissions.isGranted
import java.io.InputStreamReader

data class FrogSpecies(
    val name: String,
    val locations: List<GeoPoint>
)

fun loadFrogLocations(context: Context, speciesName: String): List<GeoPoint> {
    val locations = mutableListOf<GeoPoint>()
    try {
        val inputStream = context.assets.open("frog_data.csv")
        CSVReader(InputStreamReader(inputStream)).use { reader ->
            reader.readAll().drop(1).forEach { row -> // Skip header
                if (row[0] == speciesName) {
                    locations.add(GeoPoint(row[1].toDouble(), row[2].toDouble()))
                }
            }
        }
    } catch (e: Exception) {
        Log.e("DistributionMapScreen", "Error loading CSV: $e")
        // Fallback to sample data
        val sampleData = mapOf(
            "Kaloula pulchra" to listOf(
                GeoPoint(9.85, 124.14), // Clarin, Bohol, Philippines
                GeoPoint(13.75, 100.50), // Thailand
                GeoPoint(1.35, 103.82)   // Singapore
            ),
            "Rhinella marina" to listOf(
                GeoPoint(-16.92, 145.77), // Queensland, Australia
                GeoPoint(-12.46, 130.84), // Darwin, Australia
                GeoPoint(21.30, -157.86)  // Hawaii
            )
        )
        locations.addAll(sampleData[speciesName] ?: emptyList())
    }
    return locations
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DistributionMapScreen(navController: NavController, speciesName: String?) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize osmdroid configuration
    Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

    // Load species locations
    val selectedSpecies = FrogSpecies(
        name = speciesName ?: "Kaloula pulchra",
        locations = loadFrogLocations(context, speciesName ?: "Kaloula pulchra")
    )

    // Permission handling
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val storagePermissionState = rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    var showPermissionDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!locationPermissionState.status.isGranted) {
            locationPermissionState.launchPermissionRequest()
        }
        if (!storagePermissionState.status.isGranted) {
            storagePermissionState.launchPermissionRequest()
        }
    }

    showPermissionDialog?.let { permission ->
        AlertDialog(
            onDismissRequest = { showPermissionDialog = null },
            title = { Text("$permission Permission Needed") },
            text = { Text("This app needs $permission access to display map data.") },
            confirmButton = {
                Button(onClick = {
                    showPermissionDialog = null
                    if (permission == "Location") {
                        locationPermissionState.launchPermissionRequest()
                    } else {
                        storagePermissionState.launchPermissionRequest()
                    }
                }) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                Button(onClick = { showPermissionDialog = null }) {
                    Text("Deny")
                }
            }
        )
    }

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
                    .size(120.dp)
                    .padding(bottom = 16.dp),
                tint = Color.Unspecified
            )
            Text(
                text = "${selectedSpecies.name} Distribution",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setBuiltInZoomControls(true)
                        setMultiTouchControls(true)
                        controller.setZoom(10.0) // Closer zoom for Bohol
                        controller.setCenter(selectedSpecies.locations.firstOrNull() ?: GeoPoint(9.85, 124.14)) // Clarin, Bohol
                        selectedSpecies.locations.forEach { location ->
                            val marker = Marker(this)
                            marker.position = location
                            marker.title = selectedSpecies.name
                            marker.snippet = if (selectedSpecies.name == "Kaloula pulchra") "Safe to eat" else "Toxic"
                            overlays.add(marker)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                update = { mapView -> mapView.invalidate() }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .fillMaxWidth(0.9f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF90EE90))
            ) {
                Text("Back")
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            Configuration.getInstance().save(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        }
    }
}