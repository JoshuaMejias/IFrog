package com.example.frogdetection.screens

import com.example.frogdetection.R

data class Frogs(
    val name: String,          // Scientific name
    val displayName: String,   // English name
    val status: String,        // Edible / Non-edible
    val description: String,
    val imageResId: Int,
    val localName: String,     // Local name in Clarin
    val habitat: String,
    val physicalCharacteristics: String,
    val behavior: String,
    val diet: String,
    val conservationStatus: String
)

// ✅ Sample frog data
val frogList = listOf(
    Frogs(
        name = "Kaloula pulchra",
        displayName = "Asian Painted Frog",
        status = "Edible",
        description = "Commonly found in rice fields and gardens. It is edible and often consumed locally.",
        imageResId = R.drawable.kaloula_pulchra,
        localName = "Bangkal Frog",
        habitat = "Moist gardens, rice paddies, under leaf litter and logs.",
        physicalCharacteristics = "Stocky body, dark brown with lighter stripes, rounded snout.",
        behavior = "Nocturnal; burrows during the day and emerges at night during rains.",
        diet = "Feeds mainly on ants, termites, and small insects.",
        conservationStatus = "Least Concern"
    ),
    Frogs(
        name = "Rhinella marina",
        displayName = "Cane Toad",
        status = "Non-edible",
        description = "An invasive species known for its toxicity. Dangerous to humans and animals if consumed.",
        imageResId = R.drawable.rhinella_marina,
        localName = "Kamprag",
        habitat = "Urban areas, gardens, forests, and near water bodies.",
        physicalCharacteristics = "Large, bumpy skin, brownish color, prominent parotoid glands.",
        behavior = "Highly adaptable; active at night and often found near lights hunting insects.",
        diet = "Eats insects, small mammals, and even other frogs.",
        conservationStatus = "Least Concern, but invasive"
    )
)
