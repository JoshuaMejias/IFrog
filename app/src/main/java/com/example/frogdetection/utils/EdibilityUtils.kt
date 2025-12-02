package com.example.frogdetection.utils

data class EdibilityInfo(
    val label: String,
    val color: androidx.compose.ui.graphics.Color,
    val emoji: String
)

fun classifyEdibility(species: String): EdibilityInfo {
    return when (species.trim()) {

        // 🟢 Commonly Harvested (Best to eat)
        "Paddy Field Frog",
        "East Asian Bullfrog" ->
            EdibilityInfo(
                label = "Edible — Commonly Harvested",
                color = androidx.compose.ui.graphics.Color(0xFF4CAF50), // green
                emoji = "🟢"
            )

        // 🟠 Edible but Not Advisable
        "Asian Painted Frog",
        "Common Southeast Asian Tree Frog" ->
            EdibilityInfo(
                label = "Edible — Not Advisable",
                color = androidx.compose.ui.graphics.Color(0xFFFF9800), // orange
                emoji = "🟠"
            )

        // 🔴 Highly Not Advisable!
        "Cane Toad",
        "Wood Frog" ->
            EdibilityInfo(
                label = "Highly Not Advisable!",
                color = androidx.compose.ui.graphics.Color(0xFFF44336), // red
                emoji = "🔴"
            )

        // fallback
        else -> EdibilityInfo(
            label = "Unknown Edibility",
            color = androidx.compose.ui.graphics.Color.Gray,
            emoji = "⚪"
        )
    }
}
