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
import androidx.compose.ui.graphics.Color
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

    // Load frog entry from DB
    LaunchedEffect(frogId) {
        frog = viewModel.getFrogById(frogId.toInt())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color(0xFFE0FFE0), Color(0xFFB0FFB0))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (frog == null) {
            CircularProgressIndicator(color = Color(0xFF4CAF50))
        } else {
            val captured = frog!!

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Image
                Image(
                    painter = rememberAsyncImagePainter(Uri.parse(captured.imageUri)),
                    contentDescription = captured.speciesName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFEEEEEE))
                )

                Spacer(Modifier.height(16.dp))

                // Species Name
                Text(
                    text = captured.speciesName.ifBlank { "Unknown Species" },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                // Location (human readable or coords)
                val locationText = when {
                    !captured.locationName.isNullOrBlank() -> captured.locationName!!
                    captured.latitude != null && captured.longitude != null -> {
                        "Lat: %.4f\nLon: %.4f".format(
                            captured.latitude, captured.longitude
                        )
                    }
                    else -> "Unknown Location"
                }

                Text(
                    text = locationText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.DarkGray
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                // Timestamp
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(captured.timestamp))

                Text(
                    text = "Captured on: $dateStr",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
                )

                Spacer(Modifier.height(28.dp))

                // Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {

                    Button(
                        onClick = { navController.navigate("home") },
                        colors = ButtonDefaults.buttonColors(Color(0xFF90EE90))
                    ) {
                        Text("Home", color = Color(0xFF004400))
                    }

                    Button(
                        onClick = { navController.navigate("history") },
                        colors = ButtonDefaults.buttonColors(Color(0xFF4CAF50))
                    ) {
                        Text("History", color = Color.White)
                    }
                }
            }
        }
    }
}
