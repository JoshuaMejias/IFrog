package com.example.frogdetection.screens

data class Frogs(
    val name: String,          // scientific name
    val displayName: String,   // English name
    val status: String,
    val description: String,
    val imageResId: Int,
    val localName: String      // Local name in Clarin
)