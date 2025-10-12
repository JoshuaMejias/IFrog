package com.example.frogdetection.utils

import android.content.Context
import android.util.Log
import com.example.frogdetection.data.CapturedFrogDatabase
import com.example.frogdetection.model.LocationCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Returns a human-readable location (Barangay → City → Province → Country)
 * using OpenCage API, with Room-based local caching.
 */
suspend fun getReadableLocationFromOpenCage(
    context: Context,
    latitude: Double,
    longitude: Double,
    apiKey: String
): String = withContext(Dispatchers.IO) {
    try {
        // ✅ Cache key (rounded coordinates)
        val key = "%.4f,%.4f".format(latitude, longitude)

        // ✅ Access local cache first
        val db = CapturedFrogDatabase.getDatabase(context)
        val cacheDao = db.locationCacheDao()

        val cached = cacheDao.getCachedLocation(key)
        if (cached != null) {
            Log.d("OpenCage", "Cache hit for $key → $cached")
            return@withContext cached
        }

        // ✅ Build request to OpenCage API
        val query = URLEncoder.encode("$latitude,$longitude", "UTF-8")
        val urlStr = "https://api.opencagedata.com/geocode/v1/json?q=$query&key=$apiKey&language=en"

        val connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val json = JSONObject(response)
        val results = json.getJSONArray("results")
        if (results.length() == 0) {
            Log.w("OpenCage", "No results for $latitude, $longitude")
            return@withContext "Unknown Location"
        }

        // ✅ Parse readable address
        val components = results.getJSONObject(0).getJSONObject("components")
        val barangay = components.optString("suburb")
            .ifEmpty { components.optString("village") }
            .ifEmpty { components.optString("neighbourhood") }
        val city = components.optString("city")
            .ifEmpty { components.optString("town") }
            .ifEmpty { components.optString("municipality") }
        val province = components.optString("state")
            .ifEmpty { components.optString("region") }
        val country = components.optString("country")

        val readableName = listOf(barangay, city, province, country)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifEmpty { results.getJSONObject(0).optString("formatted", "Unknown Location") }

        // ✅ Save to cache
        cacheDao.insert(LocationCache(cacheKey = key, locationName = readableName))

        Log.d("OpenCage", "Fetched from API: $readableName")
        return@withContext readableName

    } catch (e: Exception) {
        Log.e("OpenCage", "Error: ${e.message}")
        return@withContext "Unknown Location"
    }
}
