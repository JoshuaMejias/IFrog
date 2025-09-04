package com.example.frogdetection.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.frogdetection.R
import androidx.compose.ui.graphics.graphicsLayer


@Composable
fun WelcomeScreen(navController: NavController) {
    // Animation for floating logo
    val infiniteTransition = rememberInfiniteTransition()
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFECFDF5), Color(0xFFD1FAE5)) // Green pastel gradient
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Logo
            Icon(
                painter = painterResource(id = R.drawable.ifrog_logo),
                contentDescription = "iFrog Logo",
                modifier = Modifier
                    .size(150.dp)
                    .offset(y = offsetY.dp)
                    .graphicsLayer(rotationZ = rotation),
                tint = Color.Unspecified
            )

            Spacer(Modifier.height(16.dp))

            // Headline
            Text(
                text = "Welcome to iFrog!",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = Color(0xFF064E3B),
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )

            // Tagline
            Text(
                text = "Your Smart Guide to Safe Frog Consumption",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color(0xFF047857),
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Description Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFBBF7D0))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Discover which frogs are safe to eat with iFrog! Using cutting-edge AI, our app identifies edible species like Kaloula pulchra and warns against toxic ones like Rhinella marina. Promote sustainability and protect your health in Bohol's local communities.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF065F46),
                            textAlign = TextAlign.Center
                        ),
                        lineHeight = 20.sp
                    )
                }
            }

            // Call-to-Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { navController.navigate("home") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Get Started", color = Color.White, fontSize = 16.sp)
                }
                OutlinedButton(
                    onClick = { navController.navigate("about") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Learn More", fontSize = 16.sp)
                }
            }
        }
    }
}
