package com.example.frogdetection.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "captured_frogs")
data class CapturedFrog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val imageUri: String,
    val latitude: Double?,
    val longitude: Double?,

    val speciesName: String,
    val score: Float = 0f,
    val detectionsJson: String? = null,

    val timestamp: Long,
    val locationName: String? = null,

    val uploaded: Boolean = false,

    /** Supabase ID (integer) */
    val remoteId: Int? = null
)
