// File: app/src/main/java/com/example/frogdetection/screens/CapturedHistoryScreen.kt
package com.example.frogdetection.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.frogdetection.utils.getReadableLocationFromOpenCage

@Composable
fun CapturedHistoryScreen(
    navController: androidx.navigation.NavController,
    viewModel: CapturedHistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Collect DB list safely
    val capturedFrogs by viewModel.capturedFrogs.collectAsState()

    var query by remember { mutableStateOf("") }
    var showDeleteDialogFor by remember { mutableStateOf<CapturedFrog?>(null) }
    var resolvingIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Search filter is stable
    val filtered = remember(capturedFrogs, query) {
        if (query.isBlank()) capturedFrogs
        else capturedFrogs.filter {
            it.speciesName.contains(query, ignoreCase = true) ||
                    (it.locationName?.contains(query, ignoreCase = true) ?: false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Captured Frogs",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = { viewModel.migrateMissingLocations() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search species or location") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text("${filtered.size}", style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(12.dp))

        if (capturedFrogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No captured frogs yet")
            }
            return
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = filtered,
                key = { it.id } // SAFE KEY
            ) { frog ->
                CapturedFrogCard(
                    frog = frog,
                    isResolving = resolvingIds.contains(frog.id),
                    onClick = { navController.navigate("resultScreen/${frog.id}") },
                    onMapClick = { navController.navigate("map/${frog.id}") },
                    onDelete = { showDeleteDialogFor = frog },
                    onResolveLocation = {
                        if (frog.latitude == null || frog.longitude == null ||
                            (frog.latitude == 0.0 && frog.longitude == 0.0)
                        ) return@CapturedFrogCard

                        scope.launch(Dispatchers.IO) {
                            resolvingIds = resolvingIds + frog.id

                            val apiKey = context.getString(
                                com.example.frogdetection.R.string.opencage_api_key
                            )

                            val resolved = try {
                                getReadableLocationFromOpenCage(
                                    context = context,
                                    latitude = frog.latitude,
                                    longitude = frog.longitude,
                                    apiKey = apiKey
                                )
                            } catch (e: Exception) {
                                ""
                            }

                            if (resolved.isNotBlank()) {
                                viewModel.updateLocation(frog.id, resolved)
                            }

                            resolvingIds = resolvingIds - frog.id
                        }
                    }
                )
            }
        }
    }

    if (showDeleteDialogFor != null) {
        val frog = showDeleteDialogFor!!
        AlertDialog(
            onDismissRequest = { showDeleteDialogFor = null },
            title = { Text("Delete capture") },
            text = { Text("Delete '${frog.speciesName}' from history?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { viewModel.deleteFrog(frog) }
                    showDeleteDialogFor = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogFor = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CapturedFrogCard(
    frog: CapturedFrog,
    isResolving: Boolean,
    onClick: () -> Unit,
    onMapClick: () -> Unit,
    onDelete: () -> Unit,
    onResolveLocation: () -> Unit
) {
    val dateStr = remember(frog.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(frog.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Image(
                painter = rememberAsyncImagePainter(Uri.parse(frog.imageUri)),
                contentDescription = frog.speciesName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(frog.speciesName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(frog.locationName ?: "Unknown location")
                Spacer(Modifier.height(4.dp))
                Text(dateStr, style = MaterialTheme.typography.bodySmall)
            }

            Column(horizontalAlignment = Alignment.End) {

                IconButton(onClick = onMapClick) {
                    Icon(Icons.Default.Place, contentDescription = "Map")
                }

                if (isResolving) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    TextButton(onClick = onResolveLocation) {
                        Text("Resolve")
                    }
                }

                Spacer(Modifier.height(4.dp))

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
