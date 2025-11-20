package com.example.frogdetection.screens

import android.graphics.Bitmap
import android.graphics.Matrix
//import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.utils.FrogDetectionHelper
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
import java.util.*

// --------------------------------------------------
// FIXED: load bitmap with EXIF orientation corrected
// --------------------------------------------------
fun loadCorrectedBitmap(context: android.content.Context, uri: Uri): Bitmap {
    val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)

    // Read EXIF orientation
    val input = context.contentResolver.openInputStream(uri)
    val exif = ExifInterface(input!!)
    val rotation = when (
        exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
    input.close()

    // Apply rotation
    val rotatedBitmap = if (rotation != 0) {
        val matrix = Matrix()
        matrix.postRotate(rotation.toFloat())
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else bitmap

    // ONNX must receive ARGB_8888
    return rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true)
}

// --------------------------------------------------
// MAIN SCREEN
// --------------------------------------------------
@Composable
fun ImagePreviewScreen(
    navController: NavController,
    imageUri: String?,
    latitude: Double?,
    longitude: Double?,
    viewModel: CapturedHistoryViewModel
) {
    val context = LocalContext.current

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detectedSpecies by remember { mutableStateOf("Detecting...") }
    var detectionDone by remember { mutableStateOf(false) }

    // Load + fix EXIF rotation
    LaunchedEffect(imageUri) {
        imageUri?.let {
            val uri = Uri.parse(it)
            bitmap = loadCorrectedBitmap(context, uri)
        }
    }

    // Run YOLO detection ONCE
    LaunchedEffect(bitmap) {
        bitmap?.let {
            FrogDetectionHelper.init(context) // ensure ONNX loaded
            val results = FrogDetectionHelper.detectFrogs(it)
            detectedSpecies = results.firstOrNull()?.label ?: "Unknown"
            detectionDone = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8F5E9)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // IMAGE PREVIEW
            if (imageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(Uri.parse(imageUri)),
                    contentDescription = "Preview",
                    modifier = Modifier
                        .size(320.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFEEEEEE)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(20.dp))

            // SPECIES TEXT
            Text(
                text = detectedSpecies,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
            )

            Spacer(Modifier.height(30.dp))

            // BUTTONS
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // ❌ DISCARD
                OutlinedButton(
                    onClick = { navController.navigate("home") },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFB71C1C)
                    )
                ) {
                    Text("Discard")
                }

                // ✅ SAVE RESULT
                Button(
                    onClick = {
                        if (imageUri != null) {
                            // Save frog in Room DB
                            val frog = CapturedFrog(
                                imageUri = imageUri,
                                latitude = latitude,
                                longitude = longitude,
                                speciesName = detectedSpecies,
                                timestamp = System.currentTimeMillis(),
                                locationName = null
                            )

                            viewModel.insert(frog) { newId ->
                                navController.navigate("resultScreen/$newId")
                            }
                        }
                    },
                    enabled = detectionDone,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF66BB6A)
                    )
                ) {
                    Text("Save", color = Color.White)
                }
            }
        }
    }
}
