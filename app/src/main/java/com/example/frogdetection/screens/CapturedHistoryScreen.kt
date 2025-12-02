@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.frogdetection.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.example.frogdetection.R
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.utils.getReadableLocationFromOpenCageOrNominatim
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
    var resolvingIds by remember { mutableStateOf(setOf<Int>()) }

    val snackbarHostState = remember { SnackbarHostState() }

    val filteredList = remember(capturedFrogs, query) {
        if (query.isBlank()) capturedFrogs
        else capturedFrogs.filter {
            it.speciesName.contains(query, true) ||
                    (it.locationName?.contains(query, true) ?: false)
        }
    }

    // subtle logo hop
    val infinite = rememberInfiniteTransition()
    val hop by infinite.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = rememberAsyncImagePainter(model = R.drawable.ifrog_logo),
                            contentDescription = "iFrog",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .scale(hop)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Captured Frogs", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${capturedFrogs.size} total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            viewModel.migrateMissingLocations()
                            Toast.makeText(context, "Refreshing…", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {

            // Search row
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search species or location") },
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            TextButton(onClick = { query = "" }) { Text("Clear") }
                        }
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            if (capturedFrogs.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No captures yet", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Use Camera or Gallery to add your first frog capture.",
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { navController.navigate("home") }) {
                            Text("Go to Home")
                        }
                    }
                }
                return@Scaffold
            }

            // List
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filteredList, key = { it.id }) { frog ->
                    Crossfade(targetState = frog) { f ->
                        CapturedFrogCard(
                            frog = f,
                            isResolving = f.id in resolvingIds,
                            onClick = {
                                navController.navigate("resultScreen/${f.id}")
                            },
                            onResolveLocation = {
                                val lat = f.latitude ?: 0.0
                                val lon = f.longitude ?: 0.0

                                if (lat == 0.0 && lon == 0.0) {
                                    scope.launch { snackbarHostState.showSnackbar("No GPS coordinates for this capture") }
                                    return@CapturedFrogCard
                                }

                                scope.launch(Dispatchers.IO) {
                                    resolvingIds = resolvingIds + f.id
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
                                        viewModel.updateLocation(f.id, resolved)
                                        scope.launch { snackbarHostState.showSnackbar("Location resolved") }
                                    } else {
                                        scope.launch { snackbarHostState.showSnackbar("Unable to resolve location") }
                                    }

                                    resolvingIds = resolvingIds - f.id
                                }
                            },
                            onDelete = { showDeleteDialogFor = f }
                        )
                    }
                }
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
                    scope.launch {
                        viewModel.deleteFrog(frog)
                        snackbarHostState.showSnackbar("Deleted ${frog.speciesName}")
                    }
                    showDeleteDialogFor = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogFor = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/* ---------------------- Card Component ---------------------- */
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
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            AsyncImage(
                model = Uri.parse(frog.imageUri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {

                Text(frog.speciesName, style = MaterialTheme.typography.titleMedium)

                Spacer(Modifier.height(6.dp))

                Text(
                    frog.locationName ?: "Unknown location",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                // Uploaded / Local badge
                if (frog.uploaded) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Uploaded") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                tint = Color(0xFF2196F3)
                            )
                        }
                    )
                } else {
                    AssistChip(
                        onClick = {},
                        label = { Text("Local") }
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {

                if (isResolving) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    IconButton(onClick = { onResolveLocation() }) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = "Resolve",
                            tint = Color(0xFF2196F3)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                IconButton(onClick = { onDelete() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
