package com.example.frogdetection.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "captured_frogs")
data class CapturedFrog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val imageUri: String,   // store URI as String
    val latitude: Double?,
    val longitude: Double?,
    val speciesName: String,
    val timestamp: Long,
    val locationName: String? = null // ✅ Human-readable address cached
)

// Optional converter for Uri (if you want to use Uri directly in your models)
class Converters {
    @TypeConverter
    fun fromUri(uri: Uri?): String? = uri?.toString()

    @TypeConverter
    fun toUri(uriString: String?): Uri? = uriString?.let { Uri.parse(it) }
}
