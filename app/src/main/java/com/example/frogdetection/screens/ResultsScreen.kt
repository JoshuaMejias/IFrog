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
                    colors = listOf(Color(0xFFE6FFE6), Color(0xFFB9FBB9))
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

                // Image preview
                Image(
                    painter = rememberAsyncImagePainter(Uri.parse(captured.imageUri)),
                    contentDescription = captured.speciesName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFEFEFEF))
                )

                Spacer(Modifier.height(16.dp))

                // Species name
                Text(
                    text = captured.speciesName.ifBlank { "Unknown Species" },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF2D7A2F)
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                // Confidence score (0..1)
                val confidenceText =
                    "Confidence: ${(captured.score * 100).toInt()}%"

                Text(
                    text = confidenceText,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF444444))
                )

                Spacer(Modifier.height(12.dp))

                // Location
                val locationText = when {
                    !captured.locationName.isNullOrBlank() -> captured.locationName!!
                    captured.latitude != null && captured.longitude != null ->
                        "Lat: %.5f\nLon: %.5f".format(captured.latitude, captured.longitude)
                    else -> "Unknown Location"
                }

                Text(
                    text = locationText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color(0xFF444444)
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                // Timestamp
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(Date(captured.timestamp))

                Text(
                    text = "Captured on: $dateStr",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF666666)
                    )
                )

                Spacer(Modifier.height(24.dp))

                // Buttons: Home, History, Show on Map
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Button(
                        onClick = { navController.navigate("home") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA8F0A8))
                    ) {
                        Text("Home", color = Color(0xFF005500))
                    }

                    Button(
                        onClick = { navController.navigate("history") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("History", color = Color.White)
                    }

                    Button(
                        onClick = { navController.navigate("map") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4DB6AC))
                    ) {
                        Text("Show on Map", color = Color.White)
                    }
                }
            }
        }
    }
}
