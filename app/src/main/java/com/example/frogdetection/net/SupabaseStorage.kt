package com.example.frogdetection.net

import android.content.Context
import android.net.Uri
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class SupabaseStorage(
    private val supabaseUrl: String,
    private val supabaseKey: String,
    private val bucket: String = "frog-images"
) {

    private val client = OkHttpClient()

    /**
     * Upload an image to Supabase Storage
     */
    @Throws(IOException::class)
    fun uploadImage(context: Context, uri: Uri, fileName: String): String {

        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open URI $uri")

        val bytes = inputStream.readBytes()
        inputStream.close()

        val url = "$supabaseUrl/storage/v1/object/$bucket/$fileName"

        val body = bytes.toRequestBody("image/jpeg".toMediaType())

        val req = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer $supabaseKey")
            .addHeader("apikey", supabaseKey)
            .build()

        val res = client.newCall(req).execute()
        if (!res.isSuccessful) {
            val err = res.body?.string()
            Log.e("SupabaseStorage", "Upload failed: $err")
            res.close()
            throw IOException("Upload failed: ${res.code}")
        }
        res.close()

        // Return *public* URL
        return "$supabaseUrl/storage/v1/object/public/$bucket/$fileName"
    }
}
