// File: app/src/main/java/com/example/frogdetection/model/CapturedFrog.kt
package com.example.frogdetection.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "captured_frogs")
data class CapturedFrog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val imageUri: String,
    val latitude: Double?,
    val longitude: Double?,
    val speciesName: String,
    val score: Float = 0f,                // detection confidence 0..1
    val timestamp: Long,
    val locationName: String? = null,
    val uploaded: Boolean = false,        // remote upload flag
    val remoteId: String? = null          // remote DB id (optional)
)

// Optional converter for Uri if needed
class Converters {
    @TypeConverter
    fun fromUri(uri: Uri?): String? = uri?.toString()

    @TypeConverter
    fun toUri(uriString: String?): Uri? = uriString?.let { Uri.parse(it) }
}
