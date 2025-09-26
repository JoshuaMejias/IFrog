package com.example.frogdetection.screens

import com.example.frogdetection.R

data class Frogs(
    val name: String,          // Scientific name
    val displayName: String,   // English/common name
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

// ✅ Complete frog list
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
        conservationStatus = "Least Concern (but invasive)"
    ),
    Frogs(
        name = "Hoplobatrachus rugulosus",
        displayName = "Chinese Bullfrog",
        status = "Edible",
        description = "A large edible frog widely consumed as food, often farmed in parts of Asia.",
        imageResId = R.drawable.hoplobatrachus_rugulosus,
        localName = "Bakbak",
        habitat = "Rice paddies, ponds, ditches, and marshy fields.",
        physicalCharacteristics = "Large, robust body; green or brown with warty skin.",
        behavior = "Active at night; strong jumper and loud croaker.",
        diet = "Feeds on insects, small fish, and other amphibians.",
        conservationStatus = "Least Concern, but vulnerable to overharvesting"
    ),
    Frogs(
        name = "Polypedates leucomystax",
        displayName = "Common Tree Frog",
        status = "Edible",
        description = "A tree-dwelling frog often found near houses and gardens; sometimes consumed locally.",
        imageResId = R.drawable.polypedates_leucomystax,
        localName = "Tuko-tuko",
        habitat = "Gardens, trees, shrubs, and forest edges.",
        physicalCharacteristics = "Slender body, light brown or yellow with darker markings.",
        behavior = "Arboreal; active at night; loud distinctive call during rains.",
        diet = "Feeds on insects like moths, crickets, and flies.",
        conservationStatus = "Least Concern"
    ),
    Frogs(
        name = "Fejervarya limnocharis",
        displayName = "Rice Field Frog",
        status = "Edible",
        description = "A common frog in rice fields and agricultural areas, frequently caught for food and widely distributed in Southeast Asia.",
        imageResId = R.drawable.fejervarya_vittigera, // replace with your correct drawable name
        localName = "Palakang Bukid",
        habitat = "Rice fields, ditches, ponds, and grassy areas near water sources.",
        physicalCharacteristics = "Small to medium size (males up to 4 cm, females up to 6 cm), brown or olive body with irregular dark spots, rough skin with scattered tubercles, and long hind legs for jumping.",
        behavior = "Often active at night and during rains; strong jumper; typically found near water and commonly seen in groups in rice fields.",
        diet = "Feeds on small insects, larvae, and other invertebrates.",
        conservationStatus = "Least Concern"
    ),
            Frogs(
        name = "Platymantis species",
        displayName = "Forest Frog",
        status = "Non-edible",
        description = "Endemic forest-dwelling frogs important for biodiversity. Not consumed due to conservation value.",
        imageResId = R.drawable.platymantis_species,
        localName = "Kumkom",
        habitat = "Lowland and montane forests, usually in leaf litter or on rocks.",
        physicalCharacteristics = "Small to medium size; color varies (brown, green, mottled).",
        behavior = "Secretive; terrestrial and nocturnal.",
        diet = "Feeds on ants, termites, and small invertebrates.",
        conservationStatus = "Varies by species; some are threatened"
    )
)
