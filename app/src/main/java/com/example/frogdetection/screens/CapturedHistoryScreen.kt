package com.example.frogdetection.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.frogdetection.R
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CapturedHistoryScreen(
    navController: NavController,
    viewModel: CapturedHistoryViewModel
) {
    val capturedFrogs = viewModel.capturedFrogs.collectAsState(initial = emptyList()).value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.ifrog_logo),
                contentDescription = "iFrog Logo",
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Captured Frogs", style = MaterialTheme.typography.headlineMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (capturedFrogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No captured frogs yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(capturedFrogs) { frog ->
                    CapturedFrogItem(
                        frog = frog,
                        onClick = { navController.navigate("map/${frog.id}") },
                        onDelete = { viewModel.deleteFrog(frog) }
                    )
                }
            }
        }
    }
}

@Composable
fun CapturedFrogItem(
    frog: CapturedFrog,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val dateString = formatter.format(Date(frog.timestamp))
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Delete Capture") },
            text = { Text("Are you sure you want to delete this frog record?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onClick() },
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(frog.imageUri),
                    contentDescription = frog.speciesName,
                    modifier = Modifier.size(80.dp)
                )
                Column {
                    Text(frog.speciesName, style = MaterialTheme.typography.titleMedium)

                    // ✅ Display locationName directly (resolved in ViewModel)
                    val locationText = when {
                        !frog.locationName.isNullOrBlank() -> frog.locationName!!
                        (frog.latitude != null && frog.longitude != null &&
                                frog.latitude != 0.0 && frog.longitude != 0.0) ->
                            "Lat: %.4f, Lon: %.4f".format(frog.latitude, frog.longitude)
                        else -> "Locating..."
                    }
                    Text(locationText, style = MaterialTheme.typography.bodyMedium)

                    Text("Captured: $dateString", style = MaterialTheme.typography.bodySmall)
                }
            }

            IconButton(onClick = { showDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Frog",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
