package com.example.frogdetection.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import coil.compose.rememberAsyncImagePainter
import com.example.frogdetection.R
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.frogdetection.utils.getReadableLocationFromOpenCageOrNominatim

@Composable
fun CapturedHistoryScreen(
    navController: androidx.navigation.NavController
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current!!

    val viewModel = ViewModelProvider(
        owner,
        ViewModelProvider.AndroidViewModelFactory(context.applicationContext as android.app.Application)
    )[CapturedHistoryViewModel::class.java]

    val scope = rememberCoroutineScope()

    val capturedFrogs by viewModel.capturedFrogs.collectAsState()

    var query by remember { mutableStateOf("") }
    var showDeleteDialogFor by remember { mutableStateOf<CapturedFrog?>(null) }
    var resolvingIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    val filteredList = remember(capturedFrogs, query) {
        if (query.isBlank()) capturedFrogs
        else capturedFrogs.filter {
            it.speciesName.contains(query, true) ||
                    (it.locationName?.contains(query, true) ?: false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {

        // ----------------------------
        // HEADER
        // ----------------------------
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
                Icon(Icons.Default.Refresh, "refresh")
            }
        }

        Spacer(Modifier.height(8.dp))

        // ----------------------------
        // SEARCH BAR
        // ----------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search species or location") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text("${filteredList.size}")
        }

        Spacer(Modifier.height(12.dp))

        if (capturedFrogs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No captured frogs yet")
            }
            return
        }

        // ----------------------------
        // LIST OF FROGS
        // ----------------------------
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filteredList, key = { it.id }) { frog ->
                CapturedFrogCard(
                    frog = frog,
                    isResolving = frog.id in resolvingIds,
                    onClick = { navController.navigate("resultScreen/${frog.id}") },
                    onResolveLocation = {
                        val lat = frog.latitude ?: 0.0
                        val lon = frog.longitude ?: 0.0

                        if (lat == 0.0 && lon == 0.0) return@CapturedFrogCard

                        scope.launch(Dispatchers.IO) {
                            resolvingIds = resolvingIds + frog.id

                            val apiKey = context.getString(R.string.opencage_api_key)

                            val resolved = try {
                                getReadableLocationFromOpenCageOrNominatim(
                                    context = context,
                                    lat = lat,
                                    lon = lon,
                                    apiKey = apiKey
                                )
                            } catch (_: Exception) { "" }

                            if (resolved.isNotBlank()) {
                                viewModel.updateLocation(frog.id, resolved)
                            }

                            resolvingIds = resolvingIds - frog.id
                        }
                    },
                    onDelete = { showDeleteDialogFor = frog }
                )
            }
        }
    }

    // ----------------------------
    // DELETE CONFIRMATION
    // ----------------------------
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
                TextButton(onClick = { showDeleteDialogFor = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}


// ========================================================================
// CARD UI
// ========================================================================
@Composable
private fun CapturedFrogCard(
    frog: CapturedFrog,
    isResolving: Boolean,
    onClick: () -> Unit,
    onResolveLocation: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(frog.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(frog.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // IMAGE
            Image(
                painter = rememberAsyncImagePainter(Uri.parse(frog.imageUri)),
                contentDescription = null,
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

                // Resolve button (reverse geocode)
                if (isResolving) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    TextButton(onClick = onResolveLocation) {
                        Text("Resolve")
                    }
                }

                // Delete button
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
