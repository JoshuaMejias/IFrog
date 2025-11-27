package com.example.frogdetection.screens

import android.Manifest
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.android.gms.location.*
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val fusedClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    // Permissions
    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val storagePermissionState = rememberPermissionState(storagePermission)
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // Store captured photo URI
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // ---------------------------------------------------------
    // Camera Launcher
    // ---------------------------------------------------------
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            scope.launch {
                val loc = getAccurateLocation(context, fusedClient)
                val lat = loc?.first ?: 0.0
                val lon = loc?.second ?: 0.0

                processImage(context, navController, cameraImageUri!!, lat, lon)
            }
        }
    }

    // ---------------------------------------------------------
    // Gallery Launcher
    // ---------------------------------------------------------
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                val loc = getAccurateLocation(context, fusedClient)
                val lat = loc?.first ?: 0.0
                val lon = loc?.second ?: 0.0

                processImage(context, navController, it, lat, lon)
            }
        }
    }

    // ---------------------------------------------------------
    // Logo Floating Animation
    // ---------------------------------------------------------
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // ---------------------------------------------------------
    // UI
    // ---------------------------------------------------------
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

            Image(
                painter = painterResource(id = R.drawable.ifrog_logo),
                contentDescription = "iFrog Logo",
                modifier = Modifier
                    .size(200.dp)
                    .scale(scale)
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "Scan a frog to check if it's safe to eat!",
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
                    modifier = Modifier.padding(16.dp),
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

                        // ---------------------------------------------------------
                        // Camera Button
                        // ---------------------------------------------------------
                        FilledTonalButton(
                            onClick = {
                                when {
                                    !hasCamera -> {
                                        Toast.makeText(context, "No camera available", Toast.LENGTH_SHORT).show()
                                    }

                                    cameraPermission.status.isGranted -> {
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
                                    }

                                    cameraPermission.status.shouldShowRationale ->
                                        Toast.makeText(context, "Camera permission needed", Toast.LENGTH_SHORT).show()

                                    else -> cameraPermission.launchPermissionRequest()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF90EE90))
                        ) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_camera),
                                contentDescription = "Camera",
                                tint = Color(0xFF006400)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Camera", color = Color(0xFF006400))
                        }

                        // ---------------------------------------------------------
                        // Gallery Button
                        // ---------------------------------------------------------
                        FilledTonalButton(
                            onClick = {
                                when {
                                    storagePermissionState.status.isGranted ->
                                        galleryLauncher.launch("image/*")

                                    storagePermissionState.status.shouldShowRationale ->
                                        Toast.makeText(context, "Gallery permission required", Toast.LENGTH_SHORT).show()

                                    else -> storagePermissionState.launchPermissionRequest()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF90EE90))
                        ) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_gallery),
                                contentDescription = "Gallery",
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

// =============================================================================================
// ACCURATE LOCATION FUNCTION
// =============================================================================================
suspend fun getAccurateLocation(
    context: android.content.Context,
    fused: FusedLocationProviderClient
): Pair<Double, Double>? = suspendCancellableCoroutine { cont ->

    val hasPerm = ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasPerm) {
        cont.resume(null)
        return@suspendCancellableCoroutine
    }

    // Try last known location first
    fused.lastLocation
        .addOnSuccessListener { last ->
            if (last != null) {
                cont.resume(Pair(last.latitude, last.longitude))
            } else {
                // Request fresh location
                val token = com.google.android.gms.tasks.CancellationTokenSource()
                fused.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    token.token
                ).addOnSuccessListener { loc ->
                    cont.resume(loc?.let { Pair(it.latitude, it.longitude) })
                }.addOnFailureListener {
                    cont.resume(null)
                }
                cont.invokeOnCancellation { token.cancel() }
            }
        }
        .addOnFailureListener { cont.resume(null) }
}
