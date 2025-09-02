package com.example.frogdetection.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.frogdetection.R
import com.example.frogdetection.utils.processImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    var isVisible by remember { mutableStateOf(false) }
    var showCameraRationale by remember { mutableStateOf(false) }
    var showGalleryRationale by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf<String?>(null) }
    val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    // Dynamically select storage permission based on Android version
    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // Launchers for camera and gallery
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            Log.d("HomeScreen", "Camera bitmap received: ${bitmap.width}x${bitmap.height}")
            processImage(bitmap, context, navController)
        } else {
            Log.e("HomeScreen", "Camera bitmap is null")
            Toast.makeText(context, "Failed to capture image", Toast.LENGTH_SHORT).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
                Log.d("HomeScreen", "Gallery bitmap received: ${bitmap.width}x${bitmap.height}")
                processImage(bitmap, context, navController)
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error loading gallery image: $e")
                Toast.makeText(context, "Error loading image", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Log.e("HomeScreen", "Gallery URI is null")
            Toast.makeText(context, "No image selected", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission states
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val storagePermissionState = rememberPermissionState(storagePermission)

    // Animation trigger
    LaunchedEffect(Unit) {
        isVisible = true
    }

    // Camera Rationale Dialog
    if (showCameraRationale) {
        AlertDialog(
            onDismissRequest = { showCameraRationale = false },
            title = { Text("Camera Permission Needed") },
            text = { Text("This app needs camera access to take pictures of frogs.") },
            confirmButton = {
                Button(onClick = {
                    showCameraRationale = false
                    cameraPermissionState.launchPermissionRequest()
                }) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                Button(onClick = { showCameraRationale = false }) {
                    Text("Deny")
                }
            }
        )
    }

    // Gallery Rationale Dialog
    if (showGalleryRationale) {
        AlertDialog(
            onDismissRequest = { showGalleryRationale = false },
            title = { Text("Gallery Permission Needed") },
            text = { Text("This app needs access to your gallery to select frog images.") },
            confirmButton = {
                Button(onClick = {
                    showGalleryRationale = false
                    storagePermissionState.launchPermissionRequest()
                }) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                Button(onClick = { showGalleryRationale = false }) {
                    Text("Deny")
                }
            }
        )
    }

    // Settings Dialog for Permanently Denied Permissions
    showSettingsDialog?.let { permission ->
        AlertDialog(
            onDismissRequest = { showSettingsDialog = null },
            title = { Text("Permission Required") },
            text = { Text("$permission permission is required. Please enable it in the app settings.") },
            confirmButton = {
                Button(onClick = {
                    showSettingsDialog = null
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text("Go to Settings")
                }
            },
            dismissButton = {
                Button(onClick = { showSettingsDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFE0FFE0), Color(0xFFB0FFB0))
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
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 500)),
                exit = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.ifrog_logo),
                        contentDescription = "iFrog Logo",
                        modifier = Modifier.size(200.dp)
                    )
//                    Spacer(Modifier.height(16.dp))
//                    Text(
//                        "iFrog",
//                        style = MaterialTheme.typography.headlineLarge.copy(color = MaterialTheme.colorScheme.onPrimary)
//                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Scan a frog to check if it's safe to eat!",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(48.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(0.8f),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Import Frog Image",
                                style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onPrimary)
                            )
                            Spacer(Modifier.height(16.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp), // space between buttons
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        Log.d(
                                            "HomeScreen",
                                            "Camera button clicked, permission granted: ${cameraPermissionState.status.isGranted}"
                                        )
                                        if (!hasCamera) {
                                            Toast.makeText(
                                                context,
                                                "No camera available on this device",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else if (cameraPermissionState.status.isGranted) {
                                            cameraLauncher.launch(null)
                                        } else if (cameraPermissionState.status.shouldShowRationale) {
                                            showCameraRationale = true
                                        } else {
                                            showSettingsDialog = "Camera"
                                            cameraPermissionState.launchPermissionRequest()
                                        }
                                    },
                                    enabled = hasCamera,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(
                                            0xFF90EE90
                                        )
                                    )
                                ) {
                                    Icon(
                                        painter = painterResource(android.R.drawable.ic_menu_camera),
                                        contentDescription = "Open Camera"
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Camera")
                                }

                                FilledTonalButton(
                                    onClick = {
                                        Log.d(
                                            "HomeScreen",
                                            "Gallery button clicked, permission granted: ${storagePermissionState.status.isGranted}"
                                        )
                                        if (storagePermissionState.status.isGranted) {
                                            galleryLauncher.launch("image/*")
                                        } else if (storagePermissionState.status.shouldShowRationale) {
                                            showGalleryRationale = true
                                        } else {
                                            showSettingsDialog = "Gallery"
                                            storagePermissionState.launchPermissionRequest()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(
                                            0xFF90EE90
                                        )
                                    )
                                ) {
                                    Icon(
                                        painter = painterResource(android.R.drawable.ic_menu_gallery),
                                        contentDescription = "Open Gallery"
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Gallery")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}