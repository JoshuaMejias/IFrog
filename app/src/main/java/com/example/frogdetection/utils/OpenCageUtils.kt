// File: app/src/main/java/com/example/frogdetection/utils/OpenCageUtils.kt
package com.example.frogdetection.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Returns a human-readable location (Barangay → City → Province → Country)
 * using OpenCage API — NO caching, fully clean.
 */
suspend fun getReadableLocationFromOpenCage(
    context: Context,
    latitude: Double,
    longitude: Double,
    apiKey: String
): String = withContext(Dispatchers.IO) {

    try {
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
        if (results.length() == 0) return@withContext "Unknown Location"

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

        return@withContext listOf(barangay, city, province, country)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifEmpty { results.getJSONObject(0).optString("formatted", "Unknown Location") }

    } catch (e: Exception) {
        Log.e("OpenCage", "Error: ${e.message}")
        return@withContext "Unknown Location"
    }
}
