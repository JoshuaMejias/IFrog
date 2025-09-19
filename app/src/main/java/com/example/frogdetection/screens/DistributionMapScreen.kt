package com.example.frogdetection.screens

import android.graphics.drawable.ColorDrawable
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.*

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

    // Center on selected frog if available
    focusedFrogId?.toIntOrNull()?.let { id ->
        frogs.find { it.id == id }?.let { frog ->
            mapCenter = GeoPoint(frog.latitude ?: 0.0, frog.longitude ?: 0.0)
        }
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                controller.setZoom(11.0)
                controller.setCenter(mapCenter)
            }
        },
        update = { mapView ->
            // 🔧 Clear overlays before re-drawing
            mapView.overlays.clear()

            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            frogs.forEach { frog ->
                val lat = frog.latitude ?: 0.0
                val lon = frog.longitude ?: 0.0
                val point = GeoPoint(lat, lon)

                val locationName = frog.locationName

                val marker = Marker(mapView).apply {
                    position = point
                    // ✅ Use red dot instead of frog drawable
                    icon = ColorDrawable(android.graphics.Color.RED)
                    title = frog.speciesName
                    subDescription = buildString {
                        append(
                            "📍 ${
                                locationName ?: "Lat: %.4f, Lon: %.4f".format(lat, lon)
                            }\n"
                        )
                        append("🕒 ${formatter.format(Date(frog.timestamp))}")
                    }
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                    // Show details in toast
                    setOnMarkerClickListener { _, _ ->
                        val msg = "$title\n$subDescription"
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        true
                    }
                }

                mapView.overlays.add(marker)
            }

            mapView.invalidate()
        }
    )
}
