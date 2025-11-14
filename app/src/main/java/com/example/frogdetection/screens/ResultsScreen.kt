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
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ResultScreen(
    navController: NavController,
    frogId: Long,
    viewModel: CapturedHistoryViewModel
) {
    var frog by remember { mutableStateOf<CapturedFrog?>(null) }

    // ✅ Load frog record from DB
    LaunchedEffect(frogId) {
        frog = viewModel.getFrogById(frogId.toInt())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(ComposeColor(0xFFE0FFE0), ComposeColor(0xFFB0FFB0))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (frog == null) {
            CircularProgressIndicator(color = ComposeColor(0xFF4CAF50))
        } else {
            frog?.let { capturedFrog ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // ✅ Image Preview
                    capturedFrog.imageUri?.let { uriString ->
                        Image(
                            painter = rememberAsyncImagePainter(Uri.parse(uriString)),
                            contentDescription = "Captured Frog",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(280.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ComposeColor(0xFFEEEEEE))
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ✅ Species name
                    Text(
                        text = capturedFrog.speciesName.ifBlank { "Unknown Species" },
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = ComposeColor(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // ✅ Location text
                    val locationText = when {
                        !capturedFrog.locationName.isNullOrBlank() -> capturedFrog.locationName!!
                        (capturedFrog.latitude != null && capturedFrog.longitude != null &&
                                capturedFrog.latitude != 0.0 && capturedFrog.longitude != 0.0) ->
                            "Lat: %.4f, Lon: %.4f".format(
                                capturedFrog.latitude,
                                capturedFrog.longitude
                            )
                        else -> "Unknown Location"
                    }

                    Text(
                        text = locationText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = ComposeColor.DarkGray,
                            textAlign = TextAlign.Center
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // ✅ Capture timestamp
                    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val dateString = formatter.format(Date(capturedFrog.timestamp))
                    Text(
                        text = "Captured on: $dateString",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = ComposeColor.Gray
                        )
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // ✅ Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { navController.navigate("home") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ComposeColor(0xFF90EE90)
                            )
                        ) {
                            Text("Home", color = ComposeColor(0xFF004400))
                        }

                        Button(
                            onClick = { navController.navigate("history") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ComposeColor(0xFF4CAF50)
                            )
                        ) {
                            Text("History", color = ComposeColor.White)
                        }
                    }
                }
            }
        }
    }
}
