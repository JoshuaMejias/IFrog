@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.frogdetection.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import coil.compose.AsyncImage
import com.example.frogdetection.R
import com.example.frogdetection.data.MapFocusStore
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.utils.classifyEdibility
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
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
    val scroll = rememberScrollState()

    // Frog hop animation in header
    val infinite = rememberInfiniteTransition()
    val hop by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ifrog_logo),
                            contentDescription = "iFrog",
                            modifier = Modifier.size(48.dp).scale(hop)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Detection Result", style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = {
                    IconButton({ navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            Row(
                Modifier
                    .padding(end = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                // Upload Button
                // Upload Button
                Button(
                    onClick = {
                        scope.launch {
                            val ok = viewModel.uploadFrogToCloud(f)
                            if (ok) {

                                // Reload the updated frog from the database
                                frog = viewModel.getFrogById(f.id)

                                // OPTIONAL: snackbar/toast
                                // snackbarHostState.showSnackbar("Uploaded!")
                            }
                        }
                    },
                    enabled = !f.uploaded,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.CloudUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (f.uploaded) "Uploaded" else "Upload")
                }



                Spacer(Modifier.width(12.dp))

                // View On Map button
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
                    Text("View Map")
                }
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scroll)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Image Card
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                AsyncImage(
                    model = Uri.parse(f.imageUri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(16.dp))

            // Edibility badge
            SuggestionChip(
                onClick = {},
                label = { Text("${edibility.emoji} ${edibility.label}") },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = edibility.color
                )
            )

            Spacer(Modifier.height(20.dp))

            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(Modifier.padding(20.dp)) {

                    Text("Species: ${f.speciesName}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))

                    Text("Confidence: ${(f.score * 100).toInt()}%")
                    Spacer(Modifier.height(6.dp))

                    Text("Location: ${f.locationName ?: "Unknown"}")
                    Spacer(Modifier.height(6.dp))

                    Text("Latitude: %.5f".format(f.latitude))
                    Text("Longitude: %.5f".format(f.longitude))
                    Spacer(Modifier.height(6.dp))

                    val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(Date(f.timestamp))
                    Text("Captured: $ts")

                    Spacer(Modifier.height(16.dp))

                    if (f.uploaded) {
                        Text(
                            "Uploaded to Map ✔",
                            color = Color(0xFF1B5E20), // darker green
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            "Not Uploaded",
                            color = MaterialTheme.colorScheme.error, // stays red
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}
