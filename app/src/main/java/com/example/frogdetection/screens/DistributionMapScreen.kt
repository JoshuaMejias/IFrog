package com.example.frogdetection.screens

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.frogdetection.R
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Color mapping for the 6 frog species.
 * Use Android color ints for Bitmap drawing; convert Compose Color when needed.
 */
private val SpeciesColorMap = mapOf(
    "Asian Painted Frog" to android.graphics.Color.parseColor("#2196F3"),          // Blue
    "Cane Toad" to android.graphics.Color.parseColor("#F44336"),                  // Red
    "Common Southeast Asian Tree Frog" to android.graphics.Color.parseColor("#4CAF50"), // Green
    "East Asian Bullfrog" to android.graphics.Color.parseColor("#FFEB3B"),        // Yellow
    "Paddy Field Frog" to android.graphics.Color.parseColor("#9C27B0"),          // Purple
    "Wood Frog" to android.graphics.Color.parseColor("#FF9800")                  // Orange
)

/**
 * Simple floating legend (top-right).
 */
@Composable
private fun SpeciesLegend(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(12.dp)
            .wrapContentWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Text("Legend", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 6.dp))
        SpeciesColorMap.forEach { (species, colorInt) ->
            Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // color swatch
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(androidx.compose.ui.graphics.Color(colorInt))
                )
                Spacer(Modifier.width(8.dp))
                Text(species, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * DistributionMapScreen:
 * - colored photo-pin markers (per species)
 * - cached marker bitmaps keyed by (imageUri + species)
 * - top-right legend
 * - pop-up card on marker tap
 */
@Composable
fun DistributionMapScreen(
    navController: NavController,
    viewModel: CapturedHistoryViewModel,
    focusedFrogId: String? = null
) {
    val frogs by viewModel.capturedFrogs.collectAsState(initial = emptyList())
    val context = LocalContext.current

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var selectedFrog by remember { mutableStateOf<CapturedFrog?>(null) }
    var selectedPoint by remember { mutableStateOf<GeoPoint?>(null) }

    // Marker bitmap cache: key = "$imageUri|$species"
    val markerCache = remember { mutableStateMapOf<String, Bitmap>() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(11.0)
                    controller.setCenter(GeoPoint(9.85, 124.14))
                    mapViewRef = this
                }
            },
            update = { mapView ->
                mapViewRef = mapView
            }
        )

        // Update markers when frogs list changes
        LaunchedEffect(frogs) {
            val mapView = mapViewRef ?: return@LaunchedEffect
            withContext(Dispatchers.IO) {
                val markers = frogs.mapNotNull { frog ->
                    val lat = frog.latitude ?: return@mapNotNull null
                    val lon = frog.longitude ?: return@mapNotNull null
                    val species = frog.speciesName
                    val key = "${frog.imageUri}|$species"

                    val bmp = markerCache.getOrPut(key) {
                        val photo = loadBitmapFromUri(context, frog.imageUri)
                        createSpeciesPinMarker(context, photo, species, isFocused = (frog.id.toString() == focusedFrogId))
                    }

                    val marker = Marker(mapView).apply {
                        position = GeoPoint(lat, lon)
                        title = species
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = BitmapDrawable(context.resources, bmp)
                        setOnMarkerClickListener { _, _ ->
                            selectedFrog = frog
                            selectedPoint = GeoPoint(lat, lon)
                            true
                        }
                    }
                    marker
                }

                withContext(Dispatchers.Main) {
                    mapView.overlays.clear()
                    mapView.overlays.addAll(markers)

                    // overlay to dismiss popup on tap
                    mapView.overlays.add(object : Overlay() {
                        override fun onSingleTapConfirmed(e: android.view.MotionEvent?, mapView: MapView?): Boolean {
                            selectedFrog = null
                            return super.onSingleTapConfirmed(e, mapView)
                        }
                    })

                    mapView.invalidate()
                }
            }
        }

        // Focus on a frog if requested
        LaunchedEffect(frogs, focusedFrogId) {
            val mapView = mapViewRef ?: return@LaunchedEffect
            val frog = frogs.find { it.id.toString() == focusedFrogId }
            if (frog != null && frog.latitude != null && frog.longitude != null) {
                // wait a moment for markers to be placed
                kotlinx.coroutines.delay(250)
                mapView.controller.animateTo(GeoPoint(frog.latitude, frog.longitude), 14.0, 800L)
            }
        }

        // Top-right floating legend
        Box(modifier = Modifier.fillMaxSize()) {
            SpeciesLegend(modifier = Modifier.align(Alignment.TopEnd))
        }

        // Popup card when a marker is selected
        if (selectedFrog != null && selectedPoint != null) {
            PopupFrogCard(selectedFrog!!, selectedPoint!!, navController) {
                selectedFrog = null
            }
        }
    }
}

/**
 * Small popup card for a selected frog marker.
 * Clicking navigates to result screen.
 */
@Composable
private fun PopupFrogCard(
    frog: CapturedFrog,
    position: GeoPoint,
    navController: NavController,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(bottom = 120.dp)
                .width(220.dp)
                .wrapContentHeight()
                .clickable {
                    onDismiss()
                    navController.navigate("resultScreen/${frog.id}")
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(10.dp)
            ) {
                val painter = rememberAsyncImagePainter(Uri.parse(frog.imageUri))
                Image(
                    painter = painter,
                    contentDescription = frog.speciesName,
                    modifier = Modifier.size(100.dp)
                )

                Spacer(Modifier.height(6.dp))

                Text(frog.speciesName, style = MaterialTheme.typography.titleMedium)
                Text(frog.locationName ?: "Unknown", style = MaterialTheme.typography.bodySmall)

                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(Date(frog.timestamp))

                Text(dateStr, style = MaterialTheme.typography.bodySmall)

                Spacer(Modifier.height(6.dp))

                Text(
                    "Tap for details",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}


/**
 * Load a bitmap safely from a URI string (uses Coil).
 * Returns null on failure.
 */
suspend fun loadBitmapFromUri(context: android.content.Context, uriString: String?): Bitmap? {
    if (uriString.isNullOrEmpty()) return null
    return try {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(Uri.parse(uriString))
            .allowHardware(false)
            .build()
        val result = (loader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable
        result?.bitmap
    } catch (e: Exception) {
        null
    }
}

/**
 * Create a teardrop photo-pin marker where the outer body color is chosen by species.
 * - photo may be null (fallback icon is used)
 * - isFocused toggles an outer white stroke
 */
fun createSpeciesPinMarker(context: android.content.Context, photo: Bitmap?, species: String, isFocused: Boolean): Bitmap {
    val pinWidth = 100
    val pinHeight = (pinWidth * 1.35f).toInt()
    val bitmap = Bitmap.createBitmap(pinWidth, pinHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val baseColor = SpeciesColorMap[species] ?: android.graphics.Color.parseColor("#4CAF50")
    paint.color = baseColor
    paint.style = Paint.Style.FILL

    val cx = pinWidth / 2f
    val cy = pinHeight / 2.7f
    val radius = pinWidth / 3.2f

    // Draw teardrop body path
    val path = Path().apply {
        moveTo(cx, pinHeight.toFloat())
        quadTo(pinWidth.toFloat(), pinHeight * 0.22f, cx + radius, cy)
        arcTo(RectF(cx - radius, cy - radius, cx + radius, cy + radius), 0f, 180f, false)
        quadTo(0f, pinHeight * 0.22f, cx, pinHeight.toFloat())
        close()
    }
    canvas.drawPath(path, paint)

    // White frame circle for photo
    val innerRadius = pinWidth / 4.6f
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(cx, cy, innerRadius + 5f, paint)

    // Draw photo or fallback inside frame
    if (photo != null) {
        val scaled = Bitmap.createScaledBitmap(photo, (innerRadius * 2).toInt(), (innerRadius * 2).toInt(), true)
        val shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        paint.shader = shader
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, innerRadius, paint)
        paint.shader = null
    } else {
        val fallback = ContextCompat.getDrawable(context, R.drawable.frog_marker)
        if (fallback != null) {
            val fb = Bitmap.createBitmap(
                fallback.intrinsicWidth.coerceAtLeast(1),
                fallback.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val fbCanvas = Canvas(fb)
            fallback.setBounds(0, 0, fbCanvas.width, fbCanvas.height)
            fallback.draw(fbCanvas)
            canvas.drawBitmap(fb, null, RectF(cx - innerRadius, cy - innerRadius, cx + innerRadius, cy + innerRadius), null)
        }
    }

    // Focus outline
    if (isFocused) {
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawPath(path, strokePaint)
    }

    return bitmap
}
