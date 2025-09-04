package com.example.frogdetection.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.frogdetection.R

@Composable
fun FrogDictionaryScreen(navController: NavController, frogs: List<Frogs>) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredFrogs = frogs.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.localName.contains(searchQuery, ignoreCase = true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFECFDF5), Color(0xFFD1FAE5))
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ✅ Header with Logo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ifrog_logo),
                    contentDescription = "iFrog Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .padding(end = 8.dp),
                    tint = Color.Unspecified
                )
                Text(
                    text = "Frog Dictionary",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Color(0xFF065F46),
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // ✅ Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search frogs...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF10B981),
                    unfocusedBorderColor = Color(0xFF6EE7B7),
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.DarkGray,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ✅ Frog List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredFrogs.size) { index ->
                    val frog = filteredFrogs[index]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { navController.navigate("frogDetail/${frog.name}") },
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFBBF7D0))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = frog.imageResId),
                                contentDescription = frog.name,
                                modifier = Modifier
                                    .width(120.dp)   // wider
                                    .height(80.dp)   // shorter height (rectangle shape)
                                    .clip(RoundedCornerShape(12.dp)) // rectangular with rounded corners
                                    .background(Color.White, RoundedCornerShape(12.dp))
                                    .padding(5.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = frog.displayName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF064E3B)
                                    )
                                )
                                Text(
                                    text = frog.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color(0xFF047857)
                                    )
                                )
                                Text(
                                    text = "Local: ${frog.localName}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF065F46)
                                    )
                                )
                            }

                            // ✅ Status Tag
                            Surface(
                                color = if (frog.status.lowercase() == "edible") Color(0xFF34D399) else Color(0xFFF87171),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = frog.status,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
