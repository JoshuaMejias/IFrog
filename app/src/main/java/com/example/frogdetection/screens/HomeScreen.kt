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
import com.google.android.gms.location.LocationServices
import java.io.File

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    // Permissions
    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val storagePermissionState = rememberPermissionState(storagePermission)
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // Location provider
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    // Keep Uri for camera image
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
            if (success && cameraImageUri != null) {

                val hasPermission = ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        val lat = location?.latitude ?: 0.0
                        val lon = location?.longitude ?: 0.0
                        processImage(context, navController, cameraImageUri!!, lat, lon)
                    }
                } else {
                    Toast.makeText(context, "Location permission required for mapping", Toast.LENGTH_SHORT).show()
                    processImage(context, navController, cameraImageUri!!, 0.0, 0.0)
                }
            }
        }


    // Gallery launcher
    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {

                val hasPermission = ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        val lat = location?.latitude ?: 0.0
                        val lon = location?.longitude ?: 0.0
                        processImage(context, navController, it, lat, lon)
                    }
                } else {
                    Toast.makeText(context, "Location permission required for mapping", Toast.LENGTH_SHORT).show()
                    processImage(context, navController, it, 0.0, 0.0)
                }
            }
        }


    // Logo animation
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
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
            // Animated Logo
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
                        // Camera button
                        FilledTonalButton(
                            onClick = {
                                when {
                                    !hasCamera -> Toast.makeText(context, "No camera available", Toast.LENGTH_SHORT).show()
                                    cameraPermissionState.status.isGranted -> {
                                        val photoFile = File(
                                            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                                            "frog_${System.currentTimeMillis()}.jpg"
                                        )
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            photoFile
                                        )
                                        cameraImageUri = uri
                                        cameraLauncher.launch(uri)
                                    }
                                    cameraPermissionState.status.shouldShowRationale ->
                                        Toast.makeText(context, "Grant camera permission", Toast.LENGTH_SHORT).show()
                                    else -> cameraPermissionState.launchPermissionRequest()
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

                        // Gallery button
                        FilledTonalButton(
                            onClick = {
                                when {
                                    storagePermissionState.status.isGranted -> galleryLauncher.launch("image/*")
                                    storagePermissionState.status.shouldShowRationale ->
                                        Toast.makeText(context, "Grant gallery permission", Toast.LENGTH_SHORT).show()
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
