package com.example.frogdetection.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class SupabaseService(
    private val supabaseUrl: String,
    private val supabaseKey: String,
    private val tableName: String = "frog_detections"
) {

    private val client = OkHttpClient()

    // ======================================================
    // INSERT RECORD
    // ======================================================
    suspend fun insertRecordReturning(json: JSONObject): Int? = withContext(Dispatchers.IO) {

        val url = "$supabaseUrl/rest/v1/$tableName".toHttpUrlOrNull()
            ?: throw IOException("Invalid Supabase URL")

        val body = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("apikey", supabaseKey)
            .addHeader("Authorization", "Bearer $supabaseKey")
            .addHeader("Prefer", "return=representation") // ⭐ GET BACK INSERTED ROW
            .build()

        val response = client.newCall(request).execute()
        val raw = response.body?.string() ?: "[]"

        if (!response.isSuccessful) {
            Log.e("SupabaseService", "Insert FAILED: $raw")
            response.close()
            return@withContext null
        }

        val arr = JSONArray(raw)
        val id = arr.getJSONObject(0).getInt("id")

        response.close()
        return@withContext id
    }


    // ======================================================
    // DELETE RECORD (by ID)
    // ======================================================
    suspend fun deleteRecord(id: Long): Boolean = withContext(Dispatchers.IO) {

        val base = "$supabaseUrl/rest/v1/$tableName"
        val httpBase = base.toHttpUrlOrNull()
            ?: throw IOException("Invalid URL: $base")

        val url = httpBase.newBuilder()
            .addQueryParameter("id", "eq.$id")
            .build()

        val request = Request.Builder()
            .url(url)
            .delete()
            .addHeader("apikey", supabaseKey)
            .addHeader("Authorization", "Bearer $supabaseKey")
            .build()

        val response = client.newCall(request).execute()
        val ok = response.isSuccessful

        if (!ok) {
            Log.e("SupabaseService", "Delete FAILED: ${response.code} ${response.body?.string()}")
        }

        response.close()
        return@withContext ok
    }

    // ======================================================
    // QUERY ALL DETECTIONS (No bounding box)
    // Used by DistributionMapScreen
    // ======================================================
    suspend fun queryDetectionsAll(limit: Int = 2000): JSONArray = withContext(Dispatchers.IO) {

        val base = "$supabaseUrl/rest/v1/$tableName"
        val httpBase = base.toHttpUrlOrNull()
            ?: throw IOException("Invalid Supabase URL")

        val url = httpBase.newBuilder()
            .addQueryParameter("select", "*")
            .addQueryParameter("order", "timestamp.desc")
            .addQueryParameter("limit", limit.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("apikey", supabaseKey)
            .addHeader("Authorization", "Bearer $supabaseKey")
            .build()

        val response = client.newCall(request).execute()
        val raw = response.body?.string() ?: "[]"

        if (!response.isSuccessful) {
            Log.e("SupabaseService", "QueryAll FAILED: $raw")
            response.close()
            return@withContext JSONArray()
        }

        response.close()
        return@withContext JSONArray(raw)
    }
}
