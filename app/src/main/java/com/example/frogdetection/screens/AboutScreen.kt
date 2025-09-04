package com.example.frogdetection.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.frogdetection.R

@Composable
fun AboutScreen(navController: NavController) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFECFDF5), Color(0xFFBBF7D0)) // pastel green gradient
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            item {
                // Header with Logo
                Icon(
                    painter = painterResource(id = R.drawable.ifrog_logo),
                    contentDescription = "iFrog Logo",
                    modifier = Modifier
                        .size(140.dp)
                        .padding(bottom = 12.dp),
                    tint = Color.Unspecified
                )
                Text(
                    text = "About iFrog",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = Color(0xFF065F46),
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(28.dp))
            }

            @Composable// Reusable card component
            fun InfoCard(
                title: String,
                text: String,
                iconRes: Int,
                extraContent: @Composable (() -> Unit)? = null
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .padding(vertical = 10.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = Color(0xFFBBF7D0)
                            ) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = title,
                                    tint = Color(0xFF047857),
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color(0xFF065F46),
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF064E3B),
                                textAlign = TextAlign.Justify,
                                lineHeight = 20.sp
                            )
                        )

                        // Add extra composables if provided
                        extraContent?.invoke()
                    }
                }
            }

            item {
                InfoCard(
                    title = "Our Mission",
                    text = "iFrog promotes safe and sustainable frog consumption in Clarin, Bohol. It helps identify edible species while warning against toxic ones, protecting both health and biodiversity.",
                    iconRes = android.R.drawable.ic_menu_info_details
                )
            }

            item {
                InfoCard(
                    title = "Meet the Team",
                    text = "Developed by Joshua Mejias, Jeremiah Degamo, and Almiera C. Mangubat, students of Bohol Island State University, Clarin Campus.",
                    iconRes = android.R.drawable.ic_menu_myplaces
                )
            }

            item {
                InfoCard(
                    title = "Technology Behind iFrog",
                    text = "Powered by YOLO-based deep learning, trained on a custom dataset and deployed via TensorFlow Lite for mobile. Developed in Android Studio for real-time frog species detection.",
                    iconRes = android.R.drawable.ic_menu_manage
                )
            }

            item {
                InfoCard(
                    title = "Get Involved",
                    text = "For feedback or to learn more about sustainable practices, reach out to us or visit our university website.",
                    iconRes = android.R.drawable.ic_menu_send,
                    extraContent = {
                        Spacer(modifier = Modifier.height(12.dp))

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:ifrogteam@bisu.edu.ph")
                                        putExtra(Intent.EXTRA_SUBJECT, "Feedback on iFrog App")
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("📩 Contact Us")
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://bisu.edu.ph"))
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF34D399),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🌐 Visit BISU Website")
                            }
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "🐸 Thank you for supporting iFrog 🐸",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color(0xFF065F46),
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
