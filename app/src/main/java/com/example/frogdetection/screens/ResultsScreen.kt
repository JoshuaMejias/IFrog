// File: app/src/main/java/com/example/frogdetection/screens/ResultScreen.kt
package com.example.frogdetection.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import coil.compose.rememberAsyncImagePainter
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.utils.classifyEdibility
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
import com.example.frogdetection.data.MapFocusStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ResultScreen(
    navController: androidx.navigation.NavController,
    frogId: Int
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current!!
    val viewModel = ViewModelProvider(
        owner,
        ViewModelProvider.AndroidViewModelFactory(context.applicationContext as android.app.Application)
    )[CapturedHistoryViewModel::class.java]

    val scope = rememberCoroutineScope()

    var frog by remember { mutableStateOf<CapturedFrog?>(null) }
    val scroll = rememberScrollState()

    LaunchedEffect(frogId) {
        frog = viewModel.getFrogById(frogId)
    }

    if (frog == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val f = frog!!
    val edibility = classifyEdibility(f.speciesName)

    Scaffold(
        floatingActionButton = {
            Row(Modifier.padding(end = 16.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val ok = viewModel.uploadFrogToCloud(f)
                            if (ok) {
                                MapFocusStore.focusLat = f.latitude
                                MapFocusStore.focusLon = f.longitude
                                MapFocusStore.focusId = f.id  // NEW: tell map which marker to focus
                                navController.navigate("map")
                            }
                        }
                    },
                    enabled = !f.uploaded,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload")
                }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = {
                        MapFocusStore.focusLat = f.latitude
                        MapFocusStore.focusLon = f.longitude
                        MapFocusStore.focusId = f.remoteId
                        navController.navigate("map")
                    },
                    enabled = f.uploaded,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Place, null)
                    Spacer(Modifier.width(8.dp))
                    Text("View on Map")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scroll)
                .padding(20.dp)
        ) {
            Image(
                painter = rememberAsyncImagePainter(Uri.parse(f.imageUri)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clip(RectangleShape)
            )

            Spacer(Modifier.height(16.dp))

            SuggestionChip(
                onClick = {},
                label = { Text("${edibility.emoji} ${edibility.label}") },
                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = edibility.color)
            )

            Spacer(Modifier.height(20.dp))
            Text("Species: ${f.speciesName}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text("Confidence: ${(f.score * 100).toInt()}%")
            Spacer(Modifier.height(6.dp))
            Text("Location: ${f.locationName ?: "Unknown"}")
            Spacer(Modifier.height(6.dp))
            Text("Latitude: %.5f".format(f.latitude))
            Text("Longitude: %.5f".format(f.longitude))
            Spacer(Modifier.height(6.dp))
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(f.timestamp))
            Text("Captured: $ts")
            Spacer(Modifier.height(20.dp))
            if (f.uploaded) {
                Text("Already uploaded to map ✔", color = MaterialTheme.colorScheme.primary)
            } else {
                Text("Not uploaded to map", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(100.dp))
        }
    }
}
