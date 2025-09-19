package com.example.frogdetection.screens

import android.widget.Toast
import androidx.compose.runtime.*
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
        update = { mapView ->  // 🔧 ensures markers update instead of stacking
            mapView.overlays.clear()

            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            frogs.forEach { frog ->
                val point = GeoPoint(frog.latitude ?: 0.0, frog.longitude ?: 0.0)

                val locationName = frog.locationName

                val marker = Marker(mapView).apply {
                    position = point
                    icon = ContextCompat.getDrawable(context, R.drawable.frog_marker)
                    title = frog.speciesName
                    subDescription = buildString {
                        append(
                            "📍 ${
                                locationName ?: "Lat: %.4f, Lon: %.4f".format(
                                    frog.latitude,
                                    frog.longitude
                                )
                            }\n"
                        )
                        append("🕒 ${formatter.format(Date(frog.timestamp))}")
                    }
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

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
