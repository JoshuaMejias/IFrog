package com.example.frogdetection.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.example.frogdetection.R
import com.example.frogdetection.utils.processImage
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val fusedClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // Track permission state
    var locationGranted by remember { mutableStateOf(false) }

    // CAMERA OUTPUT
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // -------------------------------------------------------
    // REQUEST LOCATION PERMISSION (Required!)
    // -------------------------------------------------------
    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            locationGranted = granted
            if (!granted) {
                Toast.makeText(context, "Location is required to scan frogs.", Toast.LENGTH_LONG)
                    .show()
            }
        }

    // Ask permission on first load
    LaunchedEffect(Unit) {
        val perm = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (perm == PackageManager.PERMISSION_GRANTED)
            locationGranted = true
        else
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // -------------------------------------------------------
    // CAMERA LAUNCHER
    // -------------------------------------------------------
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success || cameraImageUri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val (lat, lon) = getPreciseLocation(context, fusedClient)

            processImage(
                context,
                navController,
                cameraImageUri!!,
                lat,
                lon
            )
        }
    }

    // -------------------------------------------------------
    // GALLERY LAUNCHER
    // -------------------------------------------------------
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult

        scope.launch {
            val (lat, lon) = getPreciseLocation(context, fusedClient)

            processImage(
                context,
                navController,
                uri,
                lat,
                lon
            )
        }
    }

    // -------------------------------------------------------
    // UI
    // -------------------------------------------------------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFE6FFE6), Color(0xFFCCFFCC))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Logo Animation
            val infiniteTransition = rememberInfiniteTransition()
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Reverse
                )
            )

            Image(
                painter = painterResource(id = R.drawable.ifrog_logo),
                contentDescription = "iFrog Logo",
                modifier = Modifier
                    .size(200.dp)
                    .scale(scale)
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Scan a frog to check if it is edible",
                color = Color(0xFF006400),
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))

            Card(
                modifier = Modifier.fillMaxWidth(0.85f),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDFFFD9))
            ) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        "Import Frog Image",
                        color = Color(0xFF006400),
                        fontSize = 20.sp
                    )

                    Spacer(Modifier.height(16.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        // -------------------------------
                        // CAMERA BUTTON
                        // -------------------------------
                        FilledTonalButton(
                            onClick = {
                                if (!locationGranted) {
                                    locationPermissionLauncher.launch(
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                    return@FilledTonalButton
                                }

                                val file = File(
                                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                                    "frog_${System.currentTimeMillis()}.jpg"
                                )
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file
                                )
                                cameraImageUri = uri
                                cameraLauncher.launch(uri)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF90EE90))
                        ) {
                            Icon(
                                painterResource(android.R.drawable.ic_menu_camera),
                                contentDescription = null,
                                tint = Color(0xFF006400)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Camera", color = Color(0xFF006400))
                        }

                        // -------------------------------
                        // GALLERY BUTTON
                        // -------------------------------
                        FilledTonalButton(
                            onClick = {
                                if (!locationGranted) {
                                    locationPermissionLauncher.launch(
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                    return@FilledTonalButton
                                }

                                galleryLauncher.launch("image/*")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF90EE90))
                        ) {
                            Icon(
                                painterResource(android.R.drawable.ic_menu_gallery),
                                contentDescription = null,
                                tint = Color(0xFF006400)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Gallery", color = Color(0xFF006400))
                        }
                    }
                }
            }
        }
    }
}

/* ========================================================================================= */
/*     HIGH ACCURACY LOCATION — FIXES 0.0 LAT & LON PERMANENTLY                             */
/* ========================================================================================= */

suspend fun getPreciseLocation(
    context: Context,
    fused: FusedLocationProviderClient
): Pair<Double, Double> = withContext(Dispatchers.IO) {

    val permission = ActivityCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    )
    if (permission != PackageManager.PERMISSION_GRANTED) {
        return@withContext Pair(0.0, 0.0)
    }

    try {
        // 1️⃣ High accuracy request first
        val token = CancellationTokenSource()
        val loc = fused.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            token.token
        ).await()

        if (loc != null) {
            return@withContext Pair(loc.latitude, loc.longitude)
        }
    } catch (_: Exception) { }

    // 2️⃣ Fallback to last location
    try {
        val last = fused.lastLocation.await()
        if (last != null) {
            return@withContext Pair(last.latitude, last.longitude)
        }
    } catch (_: Exception) { }

    // 3️⃣ Still nothing → 0.0 / 0.0 (rare)
    return@withContext Pair(0.0, 0.0)
}
