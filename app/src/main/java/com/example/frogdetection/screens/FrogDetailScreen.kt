package com.example.frogdetection.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun FrogDetailScreen(navController: NavController, frogs: List<Frogs>, startIndex: Int) {
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { frogs.size })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFECFDF5), Color(0xFFD1FAE5))
                )
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Main Pager
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                val frog = frogs[page]
                var visible by remember { mutableStateOf(false) }

                LaunchedEffect(page) {
                    visible = false
                    visible = true
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    // Frog Image
                    AnimatedVisibility(
                        visible = visible,
                        enter = scaleIn(animationSpec = tween(600))
                    ) {
                        Image(
                            painter = painterResource(id = frog.imageResId),
                            contentDescription = frog.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(250.dp)
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .padding(6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // English Name
                    Text(
                        text = frog.displayName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF064E3B)
                        ),
                        textAlign = TextAlign.Center
                    )

                    // Scientific Name
                    Text(
                        text = frog.name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color(0xFF047857)
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Status Badge
                    Surface(
                        color = if (frog.status.lowercase() == "edible") Color(0xFF34D399) else Color(0xFFF87171),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = frog.status,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 🟢 Expandable Caution Card (auto-expanded if NOT edible)
                    var expanded by remember { mutableStateOf(frog.status.lowercase() != "edible") }
                    val cautionText = if (frog.status.lowercase() == "edible") {
                        "Although considered edible, proper preparation and cooking are necessary to ensure safety and avoid potential health risks. Consuming improperly cooked frog meat may expose individuals to parasites or bacteria."
                    } else {
                        "This species is not recommended for consumption due to possible toxins, ecological importance, or cultural reasons. Consuming it may pose health risks and disrupt local biodiversity."
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                2.dp,
                                if (frog.status.lowercase() == "edible") Color(0xFF10B981) else Color(0xFFDC2626),
                                RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (frog.status.lowercase() == "edible") Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                        ),
                        elevation = CardDefaults.cardElevation(6.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = !expanded }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Caution",
                                    tint = if (frog.status.lowercase() == "edible") Color(0xFF065F46) else Color(0xFF7F1D1D),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "⚠️ Caution",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (frog.status.lowercase() == "edible") Color(0xFF065F46) else Color(0xFF7F1D1D)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (expanded) "Collapse" else "Expand",
                                    tint = Color.Gray
                                )
                            }

                            AnimatedVisibility(
                                visible = expanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = cautionText,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Local Name Card
                    DetailCard(
                        title = "Local Name",
                        content = frog.localName,
                        color = Color(0xFFBBF7D0),
                        titleColor = Color(0xFF065F46)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description Card
                    DetailCard(
                        title = "Description",
                        content = frog.description,
                        color = Color(0xFFE0F2FE),
                        titleColor = Color(0xFF075985)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Habitat Card
                    DetailCard(
                        title = "Habitat",
                        content = frog.habitat,
                        color = Color(0xFFFDE68A),
                        titleColor = Color(0xFF92400E)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Physical Characteristics Card
                    DetailCard(
                        title = "Physical Characteristics",
                        content = frog.physicalCharacteristics,
                        color = Color(0xFFFBCFE8),
                        titleColor = Color(0xFF9D174D)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Behavior Card
                    DetailCard(
                        title = "Behavior",
                        content = frog.behavior,
                        color = Color(0xFFD9F99D),
                        titleColor = Color(0xFF365314)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Diet Card
                    DetailCard(
                        title = "Diet",
                        content = frog.diet,
                        color = Color(0xFFBAE6FD),
                        titleColor = Color(0xFF075985)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Conservation Status Card
                    DetailCard(
                        title = "Conservation Status",
                        content = frog.conservationStatus,
                        color = Color(0xFFE5E7EB),
                        titleColor = Color(0xFF374151)
                    )

//                    Spacer(modifier = Modifier.height(60.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 🔘 Page Indicator (Clickable Dots)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(frogs.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(if (isSelected) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color(0xFF10B981) else Color.LightGray
                            )
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Back Button
//            Button(
//                onClick = { navController.popBackStack() },
//                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
//                shape = RoundedCornerShape(12.dp),
//                modifier = Modifier
//                    .fillMaxWidth(0.5f)
//                    .align(Alignment.CenterHorizontally)
//            ) {
//                Text("Back", color = Color.White, fontWeight = FontWeight.Bold)
//            }
        }
    }
}

/**
 * Small reusable card for displaying frog details.
 */
@Composable
fun DetailCard(title: String, content: String, color: Color, titleColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
