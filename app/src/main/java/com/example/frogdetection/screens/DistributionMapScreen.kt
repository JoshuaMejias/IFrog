// File: app/src/main/java/com/example/frogdetection/screens/DistributionMapScreen.kt
package com.example.frogdetection.screens

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.frogdetection.R
import com.example.frogdetection.data.MapFocusStore
import com.example.frogdetection.net.SupabaseService
import kotlinx.coroutines.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

// -------------------------------
// Data
// -------------------------------
data class OnlineFrog(
    val id: Int,
    val species: String,
    val locationName: String?,
    val timestamp: Long,
    val lat: Double,
    val lon: Double,
    val imageUrl: String?
)

// -------------------------------
// Drawable helpers
// -------------------------------
fun drawableToBitmapDrawableSafe(context: Context, drawable: Drawable, fallbackPx: Int = 96): BitmapDrawable {
    val wrapped = DrawableCompat.wrap(drawable).mutate()
    try {
        DrawableCompat.setTintList(wrapped, null)
        wrapped.clearColorFilter()
    } catch (_: Throwable) {}
    val w = wrapped.intrinsicWidth.takeIf { it > 0 } ?: fallbackPx
    val h = wrapped.intrinsicHeight.takeIf { it > 0 } ?: fallbackPx
    wrapped.setBounds(0, 0, w, h)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    wrapped.draw(canvas)
    return BitmapDrawable(context.resources, bitmap).apply {
        setBounds(0, 0, w, h)
        setTargetDensity(context.resources.displayMetrics)
    }
}

fun scaleBitmapDrawable(context: Context, src: BitmapDrawable, scale: Float): BitmapDrawable {
    val bmp = src.bitmap
    val newW = (bmp.width * scale).toInt().coerceAtLeast(1)
    val newH = (bmp.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(bmp, newW, newH, true)
    return BitmapDrawable(context.resources, scaled).apply {
        setBounds(0, 0, newW, newH)
        setTargetDensity(context.resources.displayMetrics)
    }
}

fun addStrokeToBitmapDrawable(
    context: Context,
    drawable: BitmapDrawable,
    strokePx: Int = 6,
    strokeColor: Int = android.graphics.Color.BLACK
): BitmapDrawable {
    val src = drawable.bitmap
    val w = src.width
    val h = src.height
    val outW = w + strokePx * 2
    val outH = h + strokePx * 2
    val outBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(outBitmap)
    canvas.drawBitmap(src, strokePx.toFloat(), strokePx.toFloat(), null)
    val paint = Paint().apply {
        color = strokeColor
        style = Paint.Style.STROKE
        strokeWidth = strokePx.toFloat()
        isAntiAlias = true
    }
    val cx = outW / 2f
    val cy = outH * 0.42f
    val radius = max(w, h) * 0.32f + strokePx
    canvas.drawCircle(cx, cy, radius, paint)
    return BitmapDrawable(context.resources, outBitmap).apply {
        setBounds(0, 0, outW, outH)
        setTargetDensity(context.resources.displayMetrics)
    }
}

// -------------------------------
// PopOverlay — draws scaled marker image at geo point (guaranteed visible)
// -------------------------------
class PopOverlay(
    private val context: Context
) : Overlay() {
    private var center: GeoPoint? = null
    private var bmp: Bitmap? = null
    private var scale = 1f
    private var visible = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var job: Job? = null

    fun startOnce(map: MapView, point: GeoPoint, baseBmp: Bitmap, scope: CoroutineScope) {
        center = point
        bmp = baseBmp
        job?.cancel()
        visible = true
        job = scope.launch(Dispatchers.Main) {
            // sequence like pop: 1 -> 1.35 -> 0.85 -> 1
            val seq = floatArrayOf(1f, 1.35f, 0.85f, 1f)
            for (s in seq) {
                scale = s
                map.invalidate()
                delay(80L)
            }
            // linger a touch
            delay(120L)
            visible = false
            center = null
            map.invalidate()
        }
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (!visible) return
        val c = center ?: return
        val bmpLocal = bmp ?: return
        val pt = mapView.projection.toPixels(c, null)
        val w = (bmpLocal.width * scale).toInt()
        val h = (bmpLocal.height * scale).toInt()
        val left = pt.x - w / 2
        val top = (pt.y - h).toInt() - (h / 6) // small uplift for pointer look
        // optional drop shadow
        paint.setShadowLayer(8f, 0f, 3f, android.graphics.Color.argb(80, 0, 0, 0))
        canvas.drawBitmap(Bitmap.createScaledBitmap(bmpLocal, w, h, true), left.toFloat(), top.toFloat(), paint)
        paint.clearShadowLayer()
    }

    fun cancel() {
        job?.cancel()
        job = null
        visible = false
        center = null
        bmp = null
    }
}

// -------------------------------
// RippleOverlay (radiating rings) — soft eye-pleasing color
// -------------------------------
class RippleOverlay(private val context: Context) : Overlay() {
    private var center: GeoPoint? = null
    private var ringRadius = 0f
    private var alpha = 180
    private var job: Job? = null
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = android.graphics.Color.parseColor("#66A8E6CF") // soft pastel
    }

    fun start(map: MapView, point: GeoPoint, scope: CoroutineScope) {
        center = point
        job?.cancel()
        job = scope.launch(Dispatchers.Main) {
            val maxR = 220f
            repeat(20) {
                ringRadius += maxR / 12f
                alpha = (200 * (1f - it / 20f)).toInt().coerceAtLeast(20)
                paint.alpha = alpha
                map.invalidate()
                delay(40L)
            }
            center = null
            ringRadius = 0f
            paint.alpha = 180
            map.invalidate()
        }
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        val c = center ?: return
        val pt = mapView.projection.toPixels(c, null)
        canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), ringRadius, paint)
    }

    fun cancel() {
        job?.cancel()
        job = null
        center = null
        ringRadius = 0f
    }
}

// -------------------------------
// Marker animation attempt (OSMDroid route) — may work on some devices
// -------------------------------
suspend fun markerPopAttempt(context: Context, marker: Marker, mapView: MapView) {
    withContext(Dispatchers.Main) {
        val base = marker.icon as? BitmapDrawable ?: return@withContext
        val stroked = addStrokeToBitmapDrawable(context, base, 6)
        val seq = floatArrayOf(1f, 1.35f, 0.85f, 1f)
        for (s in seq) {
            val scaled = scaleBitmapDrawable(context, stroked, s)
            marker.setIcon(scaled)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            // force layout recompute & redraw
            marker.setPosition(marker.position)
            mapView.invalidate()
            delay(80L)
        }
        marker.setIcon(stroked)
        marker.setPosition(marker.position)
        mapView.invalidate()
    }
}

// -------------------------------
// Combined sequence using both overlay pop + ripple + marker attempt
// -------------------------------
suspend fun runCombinedPop(
    mapView: MapView,
    marker: Marker,
    lat: Double,
    lon: Double,
    context: Context,
    scope: CoroutineScope,
    popOverlay: PopOverlay,
    rippleOverlay: RippleOverlay
) {
    withContext(Dispatchers.Main) {
        val point = GeoPoint(lat, lon)

        // Prepare base bitmap for overlay pop (use marker.icon bitmap)
        val baseBmp = (marker.icon as? BitmapDrawable)?.bitmap
            ?: return@withContext

        // Add overlays above markers so they are visible
        try {
            mapView.overlays.add(popOverlay)
            mapView.overlays.add(rippleOverlay)
        } catch (_: Exception) { }

        popOverlay.startOnce(mapView, point, baseBmp, scope)
        rippleOverlay.start(mapView, point, scope)

        // Start marker attempt in parallel (may show stroke on some devices)
        scope.launch {
            markerPopAttempt(context, marker, mapView)
        }

        // wait for overlays/animations to finish (safe margin)
        delay(900L)

        // cleanup
        try {
            mapView.overlays.remove(popOverlay)
            mapView.overlays.remove(rippleOverlay)
        } catch (_: Exception) { }

        mapView.invalidate()
    }
}

// -------------------------------
// Main Composable
// -------------------------------
@Composable
fun DistributionMapScreen(navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val service = SupabaseService(
        context.getString(R.string.supabase_url),
        context.getString(R.string.supabase_anon_key),
        "frog_detections"
    )

    var map by remember { mutableStateOf<MapView?>(null) }
    var frogs by remember { mutableStateOf<List<OnlineFrog>>(emptyList()) }

    val speciesList = listOf(
        "Asian Painted Frog",
        "Cane Toad",
        "Common Southeast Asian Tree Frog",
        "East Asian Bullfrog",
        "Paddy Field Frog",
        "Wood Frog"
    )

    var filterExpanded by remember { mutableStateOf(false) }
    var activeFilters by remember { mutableStateOf(speciesList.toSet()) }

    val speciesColors = mapOf(
        "Asian Painted Frog" to R.drawable.ic_pin_blue,
        "Cane Toad" to R.drawable.ic_pin_red,
        "Common Southeast Asian Tree Frog" to R.drawable.ic_pin_orange,
        "East Asian Bullfrog" to R.drawable.ic_pin_yellow,
        "Paddy Field Frog" to R.drawable.ic_pin_green,
        "Wood Frog" to R.drawable.ic_pin_purple
    )

    val markersById = remember { ConcurrentHashMap<Int, Marker>() }

    // overlays instances (created when MapView available)
    var popOverlay by remember { mutableStateOf<PopOverlay?>(null) }
    var rippleOverlay by remember { mutableStateOf<RippleOverlay?>(null) }

    fun refreshAll() {
        scope.launch {
            val json = service.queryDetectionsAll()
            frogs = List(json.length()) { i ->
                val o = json.getJSONObject(i)
                OnlineFrog(
                    id = o.getInt("id"),
                    species = o.getString("species"),
                    locationName = o.optString("location_name"),
                    timestamp = o.optLong("timestamp"),
                    lat = o.getDouble("latitude"),
                    lon = o.getDouble("longitude"),
                    imageUrl = o.optString("image_url")
                )
            }
            Log.d("MAP", "Fetched ${frogs.size} frogs from Supabase")
            map?.let { addMarkers(it, context, frogs, activeFilters, speciesColors, markersById) }
        }
    }

    LaunchedEffect(Unit) { refreshAll() }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(12.0)
                    controller.setCenter(GeoPoint(9.85, 124.14))

                    // create overlay instances (not added yet)
                    popOverlay = PopOverlay(ctx)
                    rippleOverlay = RippleOverlay(ctx)

                    map = this
                }
            },
            update = {
                map = it
                addMarkers(it, context, frogs, activeFilters, speciesColors, markersById)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top controls (filter + refresh)
        Row(
            Modifier.padding(12.dp).align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = { filterExpanded = !filterExpanded },
                modifier = Modifier.size(55.dp).clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) { Icon(Icons.Default.FilterList, null) }

            IconButton(
                onClick = { refreshAll() },
                modifier = Modifier.size(55.dp).clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
            ) { Icon(Icons.Default.Refresh, null) }
        }

        // Filter panel
        if (filterExpanded) {
            Card(
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 75.dp).width(260.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Filters", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    speciesList.forEach { species ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                activeFilters = if (species in activeFilters) activeFilters - species else activeFilters + species
                                map?.let { addMarkers(it, context, frogs, activeFilters, speciesColors, markersById) }
                            }) {
                            Checkbox(checked = species in activeFilters, onCheckedChange = {
                                activeFilters = if (it) activeFilters + species else activeFilters - species
                                map?.let { addMarkers(it, context, frogs, activeFilters, speciesColors, markersById) }
                            })
                            Text(species)
                        }
                    }
                }
            }
        }

        // Legend
        LegendComposable(speciesList = speciesList, speciesColors = speciesColors,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
    }

    // Focus handling: wait for marker then run combined pop + ripple
    LaunchedEffect(frogs.size, map) {
        val mv = map ?: return@LaunchedEffect
        val id = MapFocusStore.focusId ?: return@LaunchedEffect
        val lat = MapFocusStore.focusLat ?: return@LaunchedEffect
        val lon = MapFocusStore.focusLon ?: return@LaunchedEffect

        Log.d("FOCUS", "Requested focus id=$id lat=$lat lon=$lon")

        // wait attempts
        repeat(50) {
            val marker = markersById[id]
            if (marker != null) {
                Log.d("FOCUS", "Found marker for id=$id — running combined animation")
                val pop = popOverlay
                val ripple = rippleOverlay
                scope.launch {
                    // center + zoom
                    mv.controller.animateTo(GeoPoint(lat, lon))
                    delay(150)
                    mv.controller.setZoom(16.5)
                    delay(120)

                    // ensure marker draws last
                    mv.overlays.remove(marker)
                    mv.overlays.add(marker)
                    mv.invalidate()

                    if (pop != null && ripple != null) {
                        runCombinedPop(
                            mapView = mv,
                            marker = marker,
                            lat = lat,
                            lon = lon,
                            context = context,
                            scope = scope,
                            popOverlay = pop,
                            rippleOverlay = ripple
                        )
                    } else {
                        // fallback
                        markerPopAttempt(context, marker, mv)
                    }
                }
                MapFocusStore.clear()
                return@LaunchedEffect
            }
            delay(50L)
        }

        Log.e("FOCUS", "Marker not found for id=$id even after waiting.")
    }
}

// Add markers
fun addMarkers(
    map: MapView,
    context: Context,
    frogs: List<OnlineFrog>,
    filters: Set<String>,
    speciesColors: Map<String, Int>,
    markersById: ConcurrentHashMap<Int, Marker>
) {
    // remove only markers (keep overlays)
    val toRemove = map.overlays.filterIsInstance<Marker>()
    map.overlays.removeAll(toRemove)
    markersById.clear()

    frogs.filter { it.species in filters }.forEach { frog ->
        val iconRes = speciesColors[frog.species] ?: R.drawable.ic_pin_blue
        val raw = AppCompatResources.getDrawable(context, iconRes) ?: return@forEach
        try { DrawableCompat.setTintList(raw, null); raw.clearColorFilter() } catch (_: Throwable) {}
        val bmpDrawable = drawableToBitmapDrawableSafe(context, raw)
        val marker = Marker(map).apply {
            position = GeoPoint(frog.lat, frog.lon)
            title = frog.species
            setIcon(bmpDrawable)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        markersById[frog.id] = marker
        map.overlays.add(marker)

        val resName = try { context.resources.getResourceEntryName(iconRes) } catch (_: Exception) { "?" }
        Log.d("MARKER", "Species='${frog.species}' id=${frog.id} resId=$iconRes resName=$resName bmp=${bmpDrawable.bitmap.width}x${bmpDrawable.bitmap.height}")
    }

    map.invalidate()
}

// Legend composable (unchanged)
@Composable
fun LegendComposable(
    speciesList: List<String>,
    speciesColors: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(modifier = modifier.width(230.dp), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("Legend", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            speciesList.forEach { species ->
                val iconRes = speciesColors[species] ?: return@forEach
                val raw = AppCompatResources.getDrawable(context, iconRes) ?: return@forEach
                try { DrawableCompat.setTintList(raw, null); raw.clearColorFilter() } catch (_: Throwable) {}
                val bmp = drawableToBitmapDrawableSafe(context, raw).bitmap

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Icon(bitmap = bmp.asImageBitmap(), contentDescription = null, tint = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(species)
                }
            }
        }
    }
}
