package com.example.frogdetection.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.net.SupabaseService
import com.example.frogdetection.net.SupabaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A unified wrapper around:
 *  - SupabaseService (PostgREST)
 *  - SupabaseStorage (Storage bucket)
 *
 * Use this repository for ALL cloud sync operations.
 */
class FrogCloudRepository(context: Context) {

    companion object {
        private const val SUPABASE_URL = "https://pdfcvwuketwptqtnjhbc.supabase.co"
        private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBkZmN2d3VrZXR3cHRxdG5qaGJjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjQwNjMxOTQsImV4cCI6MjA3OTYzOTE5NH0.SOA5H2A6iCzFgPX-rNG9u6KmRZUXEmDJWSk7v_bZujY"
    }

    private val storage = SupabaseStorage(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY,
        bucket = "frog-images"
    )

    private val postgrest = SupabaseService(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY,
        tableName = "frog_records"
    )

    private val appContext = context.applicationContext

    // --------------------------------------------------------------------
    // UPLOAD FROG TO CLOUD (STORAGE + DATABASE)
    // --------------------------------------------------------------------
    suspend fun uploadFrog(frog: CapturedFrog): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1) Upload image
            val uri = Uri.parse(frog.imageUri)
            val fileName = "frog_${frog.id}_${UUID.randomUUID()}.jpg"

            val publicUrl = storage.uploadImage(appContext, uri, fileName)

            // 2) Upload metadata
            val json = JSONObject().apply {
                put("local_id", frog.id)
                put("species_name", frog.speciesName)
                put("latitude", frog.latitude)
                put("longitude", frog.longitude)
                put("timestamp", frog.timestamp)
                put("location_name", frog.locationName)
                put("image_url", publicUrl)
            }

            return@withContext postgrest.insertRecord(json)

        } catch (e: Exception) {
            Log.e("FrogCloudRepo", "Upload failed: ${e.message}")
            return@withContext false
        }
    }

    // --------------------------------------------------------------------
    // DELETE RECORD FROM CLOUD
    // --------------------------------------------------------------------
    suspend fun deleteFromCloud(localId: Int): Boolean {
        return postgrest.deleteRecord(localId)
    }

    // --------------------------------------------------------------------
    // QUERY FROGS FOR MAP (bounding box)
    // --------------------------------------------------------------------
    suspend fun queryMapFrogs(
        south: Double,
        north: Double,
        west: Double,
        east: Double
    ): JSONArray {
        return postgrest.queryDetections(south, north, west, east)
    }
}
