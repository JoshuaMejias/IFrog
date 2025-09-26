package com.example.frogdetection.screens

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.frogdetection.R
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DistributionMapScreen(
    navController: NavController,
    viewModel: CapturedHistoryViewModel,
    focusedFrogId: String? = null
) {
    val frogs = viewModel.capturedFrogs.collectAsState(initial = emptyList()).value
    val context = LocalContext.current

    // Default center: Bohol
    var mapCenter = GeoPoint(9.85, 124.14)
    var zoomLevel = 11.0

    // Focused frog (if navigated from history)
    var focusedFrog: GeoPoint? = null
    focusedFrogId?.toIntOrNull()?.let { id ->
        frogs.find { it.id == id }?.let { frog ->
            focusedFrog = GeoPoint(frog.latitude ?: 0.0, frog.longitude ?: 0.0)
            mapCenter = focusedFrog!!
            zoomLevel = 14.0
        }
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true) // ✅ pinch zoom enabled
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)

                controller.setZoom(zoomLevel)
                controller.setCenter(mapCenter)

                focusedFrog?.let {
                    controller.animateTo(it, zoomLevel, 1500L)
                }
            }
        },
        update = { mapView ->
            mapView.overlays.clear()
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            frogs.forEach { frog ->
                val lat = frog.latitude ?: 0.0
                val lon = frog.longitude ?: 0.0
                val point = GeoPoint(lat, lon)

                val isFocused = frog.id.toString() == focusedFrogId
                val baseIcon = ContextCompat.getDrawable(context, R.drawable.frog_marker)!!

                val marker = Marker(mapView).apply {
                    position = point
                    title = frog.speciesName
                    subDescription = buildString {
                        append("📍 ${frog.locationName ?: "Lat: %.4f, Lon: %.4f".format(lat, lon)}\n")
                        append("🕒 ${formatter.format(Date(frog.timestamp))}")
                    }
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                    icon = if (isFocused) {
                        resizeAndStrokeMarker(baseIcon, 3.0f, Color.BLACK, 20f, true)
                    } else {
                        resizeAndStrokeMarker(baseIcon, 1.5f, Color.TRANSPARENT, 0f, false)
                    }

                    setOnMarkerClickListener { _, _ ->
                        val msg = "$title\n$subDescription"
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        true
                    }
                }

                mapView.overlays.add(marker)

                // ✅ Zoom in to focused marker
                if (isFocused) {
                    mapView.controller.animateTo(marker.position, 15.0, 1500L)
                }
            }

            mapView.invalidate()
        }
    )
}

/**
 * Resize and add stroke/shadow to the marker.
 */
fun resizeAndStrokeMarker(
    original: Drawable,
    scaleFactor: Float,
    strokeColor: Int,
    strokeWidth: Float,
    addShadow: Boolean
): Drawable {
    val width = (original.intrinsicWidth * scaleFactor).toInt()
    val height = (original.intrinsicHeight * scaleFactor).toInt()

    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    // Optional shadow for focus effect
    if (addShadow) {
        val shadowPaint = Paint().apply {
            color = Color.BLACK
            maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
        }
        val cx = width / 2f
        val cy = height / 2f
        val radius = width.coerceAtMost(height) / 2f
        canvas.drawCircle(cx, cy, radius, shadowPaint)
    }

    // Draw original marker scaled
    original.setBounds(0, 0, width, height)
    original.draw(canvas)

    // Draw stroke if needed
    if (strokeWidth > 0f) {
        val paint = Paint().apply {
            color = strokeColor
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            isAntiAlias = true
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (width.coerceAtMost(height) / 2f) - strokeWidth / 2

        canvas.drawCircle(centerX, centerY, radius, paint)
    }

    return BitmapDrawable(null, bmp)
}
