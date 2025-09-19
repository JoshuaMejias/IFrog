package com.example.frogdetection.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
import com.example.frogdetection.utils.getReadableLocation
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ResultScreen(
    navController: NavController,
    frogId: Long,
    viewModel: CapturedHistoryViewModel
) {
    var frog by remember { mutableStateOf<CapturedFrog?>(null) }
    var resolvedLocation by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current   // ✅ Correct way to get Context in Compose

    // ✅ Load frog from DB
    LaunchedEffect(frogId) {
        frog = viewModel.getFrogById(frogId.toInt())
    }

    // ✅ Try resolving missing location
    LaunchedEffect(frog) {
        val f = frog
        if (f != null && f.locationName.isNullOrBlank()
            && f.latitude != null && f.longitude != null
            && f.latitude != 0.0 && f.longitude != 0.0
        ) {
            val readable = getReadableLocation(
                context,
                f.latitude,
                f.longitude,
                viewModel.placesClient   // ✅ use Places API first
            )
            if (!readable.isNullOrBlank()) {
                resolvedLocation = readable
                // Save back to DB so history/map uses it too
                scope.launch {
                    viewModel.insert(f.copy(locationName = readable))
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (frog == null) {
            Text("Loading frog details...")
        } else {
            frog?.let { capturedFrog ->
                capturedFrog.imageUri?.let { uriString ->
                    Image(
                        painter = rememberAsyncImagePainter(Uri.parse(uriString)),
                        contentDescription = "Captured Frog",
                        modifier = Modifier
                            .size(250.dp)
                            .padding(bottom = 16.dp)
                    )
                }

                Text(
                    text = capturedFrog.speciesName,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // ✅ Location fallback priority
                val locationText = when {
                    !capturedFrog.locationName.isNullOrBlank() -> capturedFrog.locationName!!
                    !resolvedLocation.isNullOrBlank() -> resolvedLocation!!
                    (capturedFrog.latitude != null && capturedFrog.longitude != null &&
                            capturedFrog.latitude != 0.0 && capturedFrog.longitude != 0.0) ->
                        "Lat: %.4f, Lon: %.4f".format(capturedFrog.latitude, capturedFrog.longitude)
                    else -> "Locating..."
                }

                Text(locationText, style = MaterialTheme.typography.bodyMedium)

                Spacer(modifier = Modifier.height(12.dp))

                // ✅ Format timestamp
                val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val dateString = formatter.format(Date(capturedFrog.timestamp))
                Text("Captured: $dateString", style = MaterialTheme.typography.bodySmall)

                Spacer(modifier = Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { navController.navigate("home") }) {
                        Text("Back to Home")
                    }
                    Button(onClick = { navController.navigate("history") }) {
                        Text("View History")
                    }
                }
            }
        }
    }
}
