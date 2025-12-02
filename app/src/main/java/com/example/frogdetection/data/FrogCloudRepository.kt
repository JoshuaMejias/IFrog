package com.example.frogdetection.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.frogdetection.model.CapturedFrog
import com.example.frogdetection.net.SupabaseService
import com.example.frogdetection.net.SupabaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

class FrogCloudRepository(private val context: Context) {

    companion object {
        private const val SUPABASE_URL =
            "https://pdfcvwuketwptqtnjhbc.supabase.co"

        private const val SUPABASE_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBkZmN2d3VrZXR3cHRxdG5qaGJjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjQwNjMxOTQsImV4cCI6MjA3OTYzOTE5NH0.SOA5H2A6iCzFgPX-rNG9u6KmRZUXEmDJWSk7v_bZujY"
    }

    private val storage = SupabaseStorage(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY,
        bucket = "frog-images"
    )

    private val postgrest = SupabaseService(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY,
        tableName = "frog_detections"
    )

    private val appContext = context.applicationContext

    /**
     * Upload frog → storage + Supabase record
     */
    suspend fun uploadFrogAndReturnId(frog: CapturedFrog): Int? =
        withContext(Dispatchers.IO) {

            try {
                val uri = Uri.parse(frog.imageUri)
                val fileName = "frog_${frog.id}_${UUID.randomUUID()}.jpg"

                // 1) Upload image
                val publicUrl = storage.uploadImage(appContext, uri, fileName)

                // 2) Create JSON
                val json = JSONObject().apply {
                    put("user_id", JSONObject.NULL)
                    put("image_url", publicUrl)
                    put("species", frog.speciesName)
                    put("confidence", frog.score.toDouble())
                    put("latitude", frog.latitude ?: 0.0)
                    put("longitude", frog.longitude ?: 0.0)
                    put("location_name", frog.locationName ?: JSONObject.NULL)
                    put("timestamp", frog.timestamp)
                }

                val result = postgrest.insertRecordReturning(json)
                return@withContext result

            } catch (e: Exception) {
                Log.e("FrogCloudRepo", "Upload error: ${e.message}", e)
                return@withContext null
            }
        }


    /**
     * Delete from Supabase (optional cleanup)
     */
    suspend fun deleteFrogFromCloud(id: Long): Boolean =
        withContext(Dispatchers.IO) {

            try {
                val ok = postgrest.deleteRecord(id)
                if (!ok) {
                    Log.e("FrogCloudRepo", "Cloud delete failed for id=$id")
                }
                return@withContext ok

            } catch (e: Exception) {
                Log.e("FrogCloudRepo", "deleteFrogFromCloud error: ${e.message}")
                return@withContext false
            }
        }
}
