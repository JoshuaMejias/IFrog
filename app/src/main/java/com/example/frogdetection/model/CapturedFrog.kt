package com.example.frogdetection.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "captured_frogs")
data class CapturedFrog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val imageUri: String,   // store URI as String
    val latitude: Double?,
    val longitude: Double?,
    val speciesName: String,
    val timestamp: Long,
    val locationName: String? = null, // Human-readable address cached
    val confidence: Float? = null     // NEW: detection confidence 0..1
)
