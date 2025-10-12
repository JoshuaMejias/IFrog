package com.example.frogdetection.screens

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DistributionMapScreen(
    navController: NavController,
    viewModel: CapturedHistoryViewModel,
    focusedFrogId: String? = null
) {
    val frogs by viewModel.capturedFrogs.collectAsState(initial = emptyList())
    val context = LocalContext.current
    var selectedFrog by remember { mutableStateOf<CapturedFrog?>(null) }
    var markerPosition by remember { mutableStateOf<GeoPoint?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Cache for marker bitmaps (by URI)
    val markerBitmaps = remember { mutableStateMapOf<String, Bitmap>() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(11.0)
                    controller.setCenter(GeoPoint(9.85, 124.14))
                    mapViewRef = this
                }
            },
            update = { mapView ->
                mapViewRef = mapView
            }
        )

        // 🧠 Auto-refresh markers whenever frogs data changes
        LaunchedEffect(frogs) {
            val mapView = mapViewRef ?: return@LaunchedEffect
            withContext(Dispatchers.IO) {
                val markers = frogs.mapNotNull { frog ->
                    val lat = frog.latitude ?: return@mapNotNull null
                    val lon = frog.longitude ?: return@mapNotNull null
                    val point = GeoPoint(lat, lon)
                    val isFocused = frog.id.toString() == focusedFrogId

                    val pinBitmap = markerBitmaps[frog.imageUri] ?: run {
                        val img = loadBitmapFromUri(context, frog.imageUri)
                        val bmp = createPhotoPinMarker(context, img, isFocused)
                        markerBitmaps[frog.imageUri ?: ""] = bmp
                        bmp
                    }

                    Marker(mapView).apply {
                        position = point
                        title = frog.speciesName
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = BitmapDrawable(context.resources, pinBitmap)
                        setOnMarkerClickListener { _, _ ->
                            selectedFrog = frog
                            markerPosition = point
                            true
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    mapView.overlays.clear()
                    mapView.overlays.addAll(markers)

                    // Dismiss popup on empty tap
                    mapView.overlays.add(object : Overlay() {
                        override fun onSingleTapConfirmed(
                            e: android.view.MotionEvent?,
                            mapView: MapView?
                        ): Boolean {
                            selectedFrog = null
                            return super.onSingleTapConfirmed(e, mapView)
                        }
                    })

                    mapView.invalidate()
                }
            }
        }

        // 🧭 Focus on frog when clicked from history
        LaunchedEffect(frogs, focusedFrogId) {
            val mapView = mapViewRef ?: return@LaunchedEffect
            val frog = frogs.find { it.id.toString() == focusedFrogId }
            if (frog != null && frog.latitude != null && frog.longitude != null) {
                delay(300) // wait for markers
                val point = GeoPoint(frog.latitude!!, frog.longitude!!)
                mapView.controller.animateTo(point, 15.0, 1200L)
            }
        }

        // 🐸 Popup info card
        if (selectedFrog != null && markerPosition != null) {
            FrogPopupCard(
                frog = selectedFrog!!,
                navController = navController,
                position = markerPosition!!
            )
        }
    }
}

@Composable
fun FrogPopupCard(frog: CapturedFrog, navController: NavController, position: GeoPoint) {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 120.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(220.dp)
                .wrapContentHeight(),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(12.dp)
                    .clickable { navController.navigate("resultScreen/${frog.id}") }
            ) {
                Image(
                    painter = rememberAsyncImagePainter(frog.imageUri),
                    contentDescription = frog.speciesName,
                    modifier = Modifier
                        .size(100.dp)
                        .padding(4.dp)
                )
                Text(frog.speciesName, style = MaterialTheme.typography.titleMedium)
                Text(frog.locationName ?: "Unknown", style = MaterialTheme.typography.bodySmall)
                Text(formatter.format(Date(frog.timestamp)), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
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
 * Safely loads a bitmap from a URI.
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
 * 🟢 Custom teardrop-style green frog marker.
 */
fun createPhotoPinMarker(context: android.content.Context, photo: Bitmap?, isFocused: Boolean): Bitmap {
    val pinWidth = 100
    val pinHeight = (pinWidth * 1.4f).toInt()
    val bitmap = Bitmap.createBitmap(pinWidth, pinHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val baseColor = if (isFocused) Color.parseColor("#2E7D32") else Color.parseColor("#4CAF50")
    paint.color = baseColor

    val cx = pinWidth / 2f
    val cy = pinHeight / 2.8f
    val radius = pinWidth / 3.2f

    // Draw teardrop body
    val path = Path().apply {
        moveTo(cx, pinHeight.toFloat())
        quadTo(pinWidth.toFloat(), pinHeight * 0.25f, cx + radius, cy)
        arcTo(RectF(cx - radius, cy - radius, cx + radius, cy + radius), 0f, 180f, false)
        quadTo(0f, pinHeight * 0.25f, cx, pinHeight.toFloat())
        close()
    }
    canvas.drawPath(path, paint)

    // White circle frame
    val innerRadius = pinWidth / 4.5f
    paint.color = Color.WHITE
    canvas.drawCircle(cx, cy, innerRadius + 5f, paint)

    // Frog photo or fallback
    photo?.let {
        val scaled = Bitmap.createScaledBitmap(it, (innerRadius * 2).toInt(), (innerRadius * 2).toInt(), true)
        val shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        paint.shader = shader
        canvas.drawCircle(cx, cy, innerRadius, paint)
        paint.shader = null
    } ?: run {
        val fallbackDrawable = ContextCompat.getDrawable(context, R.drawable.frog_marker)
        if (fallbackDrawable != null) {
            val fallbackBitmap = Bitmap.createBitmap(
                fallbackDrawable.intrinsicWidth.coerceAtLeast(1),
                fallbackDrawable.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val fallbackCanvas = Canvas(fallbackBitmap)
            fallbackDrawable.setBounds(0, 0, fallbackCanvas.width, fallbackCanvas.height)
            fallbackDrawable.draw(fallbackCanvas)

            canvas.drawBitmap(
                fallbackBitmap,
                null,
                RectF(cx - innerRadius, cy - innerRadius, cx + innerRadius, cy + innerRadius),
                null
            )
        }
    }

    // Focused marker outline
    if (isFocused) {
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawPath(path, strokePaint)
    }

    return bitmap
}
