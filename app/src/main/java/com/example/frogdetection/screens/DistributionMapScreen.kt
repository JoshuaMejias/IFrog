package com.example.frogdetection.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
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
            mapCenter = GeoPoint(frog.latitude, frog.longitude)
        }
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                controller.setZoom(11.0)
                controller.setCenter(mapCenter)

                val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                // Add red dots for frogs
                frogs.forEach { frog ->
                    val point = GeoPoint(frog.latitude, frog.longitude)
                    val overlay = object : Overlay() {
                        override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
                            val pt = osmv.projection.toPixels(point, null)
                            val paint = Paint().apply {
                                color = android.graphics.Color.RED
                                style = Paint.Style.FILL
                            }
                            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 8f, paint)
                        }

                        override fun onSingleTapConfirmed(e: android.view.MotionEvent?, mapView: MapView?): Boolean {
                            val projection = mapView?.projection ?: return false
                            val pt = projection.toPixels(point, null)
                            val dx = (e?.x ?: return false) - pt.x.toFloat()
                            val dy = (e?.y ?: return false) - pt.y.toFloat()

                            // Detect tap near the dot (within 30px)
                            if (dx * dx + dy * dy < 30 * 30) {
                                val msg = "${frog.speciesName}\nCaptured: ${formatter.format(Date(frog.timestamp))}"
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                return true
                            }
                            return false
                        }
                    }
                    overlays.add(overlay)
                }
            }
        }
    )
}
