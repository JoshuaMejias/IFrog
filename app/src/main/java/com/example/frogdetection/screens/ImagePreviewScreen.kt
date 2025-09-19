package com.example.frogdetection.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.frogdetection.R
import com.example.frogdetection.database.CapturedFrogDatabase
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.utils.getReadableLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImagePreviewScreen(
    navController: NavController,
    imageUri: String?,
    latitude: Double?,
    longitude: Double?
) {
    val context = navController.context
    var resolvedLocation by remember { mutableStateOf<String?>(null) }

    // ✅ Resolve location using LocationUtils (OSM → Google → raw lat/lon)
    LaunchedEffect(latitude, longitude) {
        if (latitude != null && longitude != null &&
            latitude != 0.0 && longitude != 0.0
        ) {
            resolvedLocation = getReadableLocation(context, latitude, longitude)
        }
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ifrog_logo),
                contentDescription = "iFrog Logo",
                modifier = Modifier
                    .size(100.dp)
                    .padding(bottom = 16.dp),
                tint = Color.Unspecified
            )
            Text(
                text = "Image Preview",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (imageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(Uri.parse(imageUri)),
                    contentDescription = "Previewed Frog",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text("No image available", color = Color.DarkGray)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ✅ Show resolved location
            Text(
                text = resolvedLocation ?: "Locating...",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.DarkGray
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { navController.popBackStack() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF90EE90))
                ) {
                    Text("Cancel", color = Color(0xFF004400))
                }

                Button(
                    onClick = {
                        if (imageUri != null) {
                            val dao = CapturedFrogDatabase.getDatabase(context).capturedFrogDao()
                            CoroutineScope(Dispatchers.IO).launch {
                                val frog = CapturedFrog(
                                    imageUri = imageUri,
                                    latitude = latitude ?: 0.0,
                                    longitude = longitude ?: 0.0,
                                    locationName = resolvedLocation,
                                    speciesName = "Unknown Species", // TODO: Replace with YOLO
                                    timestamp = System.currentTimeMillis()
                                )
                                val newId = dao.insert(frog)
                                withContext(Dispatchers.Main) {
                                    navController.navigate("resultScreen/$newId") {
                                        popUpTo("preview/{imageUri}/{lat}/{lon}") { inclusive = true }
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66BB6A))
                ) {
                    Text("Save & Continue", color = Color.White)
                }
            }
        }
    }
}
