// File: app/src/main/java/com/example/frogdetection/screens/DistributionMapScreen.kt
package com.example.frogdetection.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import com.example.frogdetection.R
import com.example.frogdetection.net.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.*


/**
 * Minimal online-only Distribution Map screen:
 * - Manual "Refresh" button
 * - Queries SupabaseService.queryDetections(south,north,west,east)
 * - Shows simple colored vector pin icons (from drawable resources)
 * - Simple popup card when a marker is tapped
 */

@Composable
fun DistributionMapScreen(
    navController: NavController,
    supabaseUrl: String = "https://pdfcvwuketwptqtnjhbc.supabase.co",
    supabaseKey: String = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBkZmN2d3VrZXR3cHRxdG5qaGJjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjQwNjMxOTQsImV4cCI6MjA3OTYzOTE5NH0.SOA5H2A6iCzFgPX-rNG9u6KmRZUXEmDJWSk7v_bZujY" // replace or inject from BuildConfig/resources
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // MapView reference
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Selected online frog for popup
    var selected by remember { mutableStateOf<OnlineFrog?>(null) }

    // Loading state for network fetch
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Instantiate SupabaseService (uses OkHttp in your project)
    val supabaseService = remember {
        SupabaseService(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseKey,
            tableName = "frog_records"
        )
    }

    // species -> drawable mapping (match your drawable filenames)
    val speciesIconMap = mapOf(
        "Asian Painted Frog" to R.drawable.ic_pin_blue,
        "Cane Toad" to R.drawable.ic_pin_red,
        "Common Southeast Asian Tree Frog" to R.drawable.ic_pin_green,
        "East Asian Bullfrog" to R.drawable.ic_pin_yellow,
        "Paddy Field Frog" to R.drawable.ic_pin_purple,
        "Wood Frog" to R.drawable.ic_pin_orange
    )

    Box(modifier = Modifier.fillMaxSize()) {

        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(11)  // GOOD level for Bohol province
                    controller.setCenter(GeoPoint(9.84999, 124.14354))  // Tagbilaran City center

                    mapViewRef = this
                }
            },
            update = { mv ->
                // keep reference updated
                mapViewRef = mv
            },
            modifier = Modifier.fillMaxSize()
        )

        // Floating refresh button (top-right)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Top
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    errorMsg = null
                    loading = true
                    // Run fetch in coroutine
                    scope.launch {
                        val mv = mapViewRef
                        if (mv == null) {
                            errorMsg = "Map not ready"
                            loading = false
                            return@launch
                        }

                        // get bounding box from map view
                        val bb: BoundingBox = mv.boundingBox
                        // bounding box provides latNorth/latSouth/lonEast/lonWest
                        val south = bb.latSouth
                        val north = bb.latNorth
                        val west = bb.lonWest
                        val east = bb.lonEast

                        try {
                            val arr: JSONArray = withContext(Dispatchers.IO) {
                                supabaseService.queryDetections(south, north, west, east, limit = 1000)
                            }
                            // Update markers on UI thread
                            withContext(Dispatchers.Main) {
                                // clear old markers (except tile overlays)
                                mv.overlays.filterIsInstance<Marker>().forEach { mv.overlays.remove(it) }
                                // parse and add markers
                                for (i in 0 until arr.length()) {
                                    val obj = arr.getJSONObject(i)
                                    val lat = obj.optDouble("latitude", Double.NaN)
                                    val lon = obj.optDouble("longitude", Double.NaN)
                                    if (lat.isNaN() || lon.isNaN()) continue

                                    val species = obj.optString("species_name", "Unknown")
                                    val locationName = obj.optString("location_name", "")
                                    val ts = obj.optLong("timestamp", 0L)
                                    val imageUrl = obj.optString("image_url", "")

                                    val geo = GeoPoint(lat, lon)
                                    val marker = Marker(mv)
                                    marker.position = geo
                                    marker.title = species
                                    marker.subDescription = locationName
                                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                                    // Choose icon (fallback to default)
                                    val drawableId = speciesIconMap[species] ?: R.drawable.ic_pin_green
                                    val drawable = ContextCompat.getDrawable(context, drawableId)
                                    drawable?.let {
                                        marker.icon = drawableToBitmapDrawable(context, it)
                                    }

                                    marker.setOnMarkerClickListener { m, _ ->
                                        selected = OnlineFrog(
                                            id = obj.optInt("id", -1),
                                            species = species,
                                            locationName = locationName,
                                            timestamp = ts,
                                            latitude = lat,
                                            longitude = lon,
                                            imageUrl = imageUrl
                                        )
                                        true
                                    }

                                    mv.overlays.add(marker)
                                }
                                mv.invalidate()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            errorMsg = "Failed to load markers: ${e.message}"
                        } finally {
                            loading = false
                        }
                    }
                },
                icon = { Icon(androidx.compose.material.icons.Icons.Default.Refresh, contentDescription = null) },
                text = { Text("Refresh Markers") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (loading) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(4.dp)
                ) {
                    Text("Loading...", modifier = Modifier.padding(8.dp))
                }
            }

            if (errorMsg != null) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(4.dp)
                ) {
                    Text("Error: $errorMsg", modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Popup card for selected marker
        selected?.let { frog ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .padding(12.dp)
                        .widthIn(min = 220.dp, max = 340.dp)
                        .clickable {
                            // navigate to details screen if desired
                            // navController.navigate("resultScreen/${frog.id}")
                        },
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(frog.species, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(frog.locationName.ifBlank { "Unknown location" }, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        val dateStr = if (frog.timestamp > 0) {
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(frog.timestamp))
                        } else "Unknown time"
                        Text(dateStr, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                selected = null
                            }) {
                                Text("Close")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                // navigate to details
                                selected = null
                                // navController.navigate("resultScreen/${frog.id}")
                            }) {
                                Text("Details")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Simple data holder for online frog record returned from Supabase.
 */
private data class OnlineFrog(
    val id: Int,
    val species: String,
    val locationName: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String
)

/**
 * Convert a Drawable (vector) into BitmapDrawable for osmdroid Marker.icon
 */
private fun drawableToBitmapDrawable(context: android.content.Context, drawable: Drawable): BitmapDrawable {
    if (drawable is BitmapDrawable) return drawable
    val width = (drawable.intrinsicWidth.takeIf { it > 0 } ?: 48)
    val height = (drawable.intrinsicHeight.takeIf { it > 0 } ?: 48)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return BitmapDrawable(context.resources, bitmap)
}
