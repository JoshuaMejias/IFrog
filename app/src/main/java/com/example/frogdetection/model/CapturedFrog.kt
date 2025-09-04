package com.example.frogdetection.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.frogdetection.data.UriTypeConverter

@Entity(tableName = "captured_frogs")
@TypeConverters(UriTypeConverter::class)
data class CapturedFrog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0, // Room will generate ID automatically

    val imageUri: Uri,
    val latitude: Double,
    val longitude: Double,
    val speciesName: String,
    val timestamp: Long // store capture time in millis
)
