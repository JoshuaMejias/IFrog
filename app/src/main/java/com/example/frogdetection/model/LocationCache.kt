package com.example.frogdetection.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_cache")
data class LocationCache(
    @PrimaryKey val cacheKey: String,   // e.g., "9.1234,124.5678"
    val locationName: String,
    val timestamp: Long = System.currentTimeMillis()
)
