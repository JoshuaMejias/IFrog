package com.example.frogdetection.net

import android.content.Context
import android.net.Uri
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class SupabaseStorage(
    private val supabaseUrl: String,
    private val supabaseKey: String,
    private val bucket: String
) {

    private val client = OkHttpClient()

    suspend fun uploadImage(context: Context, uri: Uri, fileName: String): String {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Failed to open file stream")

        return try {
            val bytes = inputStream.readBytes()
            val mediaType = "image/jpeg".toMediaType()
            val body: RequestBody = bytes.toRequestBody(mediaType)

            // Correct PUT URL for Supabase Storage
            val uploadUrl =
                "$supabaseUrl/storage/v1/object/$bucket/$fileName"

            val request = Request.Builder()
                .url(uploadUrl)
                .header("Authorization", "Bearer $supabaseKey")
                .header("apikey", supabaseKey)
                .put(body)
                .build()

            val response = client.newCall(request).execute()

            val responseText = response.body?.string()

            if (!response.isSuccessful) {
                Log.e("SupabaseStorage", "Upload failed: $responseText")
                throw IOException("Upload failed: HTTP ${response.code}")
            }

            // Return public URL
            "$supabaseUrl/storage/v1/object/public/$bucket/$fileName"

        } catch (e: Exception) {
            Log.e("SupabaseStorage", "Upload failed: ${e.message}", e)
            throw e
        } finally {
            try {
                inputStream.close()
            } catch (_: Exception) {}
        }
    }
}
