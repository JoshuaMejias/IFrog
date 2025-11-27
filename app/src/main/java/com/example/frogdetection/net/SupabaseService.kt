package com.example.frogdetection.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class SupabaseService(
    private val supabaseUrl: String,
    private val supabaseKey: String,
    private val tableName: String = "frog_records"
) {

    private val client = OkHttpClient()

    /**
     * ---------------------------------------------------------
     * 1) INSERT (upload to Supabase)
     * ---------------------------------------------------------
     */
    @Throws(IOException::class)
    suspend fun insertRecord(json: JSONObject): Boolean = withContext(Dispatchers.IO) {

        val url = "$supabaseUrl/rest/v1/$tableName".toHttpUrlOrNull()
            ?: throw IOException("Invalid Supabase URL: $supabaseUrl")

        val requestBody = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val req = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("apikey", supabaseKey)
            .addHeader("Authorization", "Bearer $supabaseKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .build()

        val response = client.newCall(req).execute()
        val success = response.isSuccessful
        if (!success) {
            Log.e("SupabaseService", "Insert failed: ${response.code} ${response.body?.string()}")
        }
        response.close()
        return@withContext success
    }

    /**
     * ---------------------------------------------------------
     * 2) DELETE by local_id
     * ---------------------------------------------------------
     */
    @Throws(IOException::class)
    suspend fun deleteRecord(localId: Int): Boolean = withContext(Dispatchers.IO) {

        val base = "$supabaseUrl/rest/v1/$tableName"
        val httpBase = base.toHttpUrlOrNull()
            ?: throw IOException("Invalid URL: $base")

        val url = httpBase.newBuilder()
            .addQueryParameter("local_id", "eq.$localId")
            .build()

        val req = Request.Builder()
            .url(url)
            .delete()
            .addHeader("apikey", supabaseKey)
            .addHeader("Authorization", "Bearer $supabaseKey")
            .build()

        val response = client.newCall(req).execute()
        val ok = response.isSuccessful

        if (!ok) {
            Log.e("SupabaseService", "Delete failed: ${response.code} ${response.body?.string()}")
        }

        response.close()
        return@withContext ok
    }

    /**
     * ---------------------------------------------------------
     * 3) QUERY frog records inside map bounding box
     * This powers the Distribution Map
     * ---------------------------------------------------------
     */
    @Throws(IOException::class)
    suspend fun queryDetections(
        south: Double,
        north: Double,
        west: Double,
        east: Double,
        limit: Int = 500
    ): JSONArray = withContext(Dispatchers.IO) {

        val base = "$supabaseUrl/rest/v1/$tableName"

        val httpBase = base.toHttpUrlOrNull()
            ?: throw IOException("Invalid Supabase URL: $base")

        val url = httpBase.newBuilder()
            .addQueryParameter("select", "*")
            .addQueryParameter("latitude",  "gte.$south")
            .addQueryParameter("latitude",  "lte.$north")
            .addQueryParameter("longitude", "gte.$west")
            .addQueryParameter("longitude", "lte.$east")
            .addQueryParameter("limit", limit.toString())
            .build()

        val req = Request.Builder()
            .url(url)
            .get()
            .addHeader("apikey", supabaseKey)
            .addHeader("Authorization", "Bearer $supabaseKey")
            .build()

        val response = client.newCall(req).execute()
        val body = response.body?.string() ?: "[]"

        if (!response.isSuccessful) {
            Log.e("SupabaseService", "Query failed: $body")
            response.close()
            return@withContext JSONArray()
        }

        response.close()
        return@withContext JSONArray(body)
    }
}
