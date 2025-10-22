package com.example.frogdetection.utils

import android.content.Context
import android.util.Log
import com.example.frogdetection.dao.LocationCacheDao
import com.example.frogdetection.data.CapturedFrogDatabase
import com.example.frogdetection.model.LocationCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

suspend fun getReadableLocation(
    context: Context,
    latitude: Double?,
    longitude: Double?
): String = withContext(Dispatchers.IO) {
    if (latitude == null || longitude == null || (latitude == 0.0 && longitude == 0.0)) {
        return@withContext "Unknown Location"
    }

    val db = CapturedFrogDatabase.getDatabase(context)
    val cacheDao: LocationCacheDao = db.locationCacheDao()

    // Use rounded coordinates as cache key
    val cacheKey = "%.4f,%.4f".format(latitude, longitude)

    // ✅ 1. Try cached value first
    cacheDao.getCachedLocation(cacheKey)?.let {
        Log.d("getReadableLocation", "Cache hit for $cacheKey → $it")
        return@withContext it
    }

    Log.d("getReadableLocation", "Cache miss for $cacheKey → calling OpenCage API")

    // ✅ 2. Call OpenCage API if not cached
    val apiKey = "35ee311961ed494da02b3ba387e2f3c2" // Replace this
    val url =
        "https://api.opencagedata.com/geocode/v1/json?q=$latitude+$longitude&key=$apiKey&language=en&pretty=1"

    return@withContext try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)

        val results = json.optJSONArray("results")
        var locationName = "Lat: %.4f, Lon: %.4f".format(latitude, longitude)

        if (results != null && results.length() > 0) {
            val components = results.getJSONObject(0).getJSONObject("components")

            val barangay = components.optString("suburb", "")
            val village = components.optString("village", "")
            val city = components.optString("city", "")
            val municipality = components.optString("town", "")
            val province = components.optString("state", "")
            val country = components.optString("country", "")

            val parts = listOf(barangay, village, city, municipality, province, country)
                .filter { it.isNotBlank() }

            if (parts.isNotEmpty()) {
                locationName = parts.joinToString(", ")
            }
        }

        // ✅ 3. Save to cache
        cacheDao.insert(LocationCache(cacheKey = cacheKey, locationName = locationName))
        Log.d("getReadableLocation", "Cached location for $cacheKey → $locationName")

        locationName
    } catch (e: Exception) {
        Log.e("getReadableLocation", "OpenCage error: ${e.message}")
        "Lat: %.4f, Lon: %.4f".format(latitude, longitude)
    }
}
