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
import java.util.Locale

/**
 * Returns a human-readable location string using:
 *  1) local Room cache (LocationCache)
 *  2) OpenCage API (preferred)
 *  3) Nominatim reverse-geocoding (fallback for fine-grained parts like barangay)
 *
 * Output format attempts to be: "Barangay, Municipality/City, Province, Country"
 *
 * IMPORTANT:
 *  - Provide a valid OpenCage API key (apiKey parameter).
 *  - For Nominatim we set a User-Agent header — required by Nominatim usage policy.
 *  - We round coordinates to 4 decimal places for the cache key to avoid over-caching.
 */
suspend fun getReadableLocationFromOpenCage(
    context: Context,
    latitude: Double,
    longitude: Double,
    apiKey: String,
    nominatimUserAgent: String = "iFrogApp/1.0 (contact: your-email@example.com)" // replace with your app contact
): String = withContext(Dispatchers.IO) {
    try {
        // Validate coordinates
        if (latitude == 0.0 && longitude == 0.0) return@withContext "Unknown Location"

        // Cache key using rounded coordinates
        val key = "%.4f,%.4f".format(Locale.US, latitude, longitude)

        val db = CapturedFrogDatabase.getDatabase(context)
        val cacheDao = db.locationCacheDao()

        // 1) Check local cache
        cacheDao.getCachedLocation(key)?.let { cached ->
            Log.d("OpenCage+Nominatim", "Cache hit for $key -> $cached")
            return@withContext cached
        }

        // 2) Try OpenCage first
        val openCageResult = try {
            fetchOpenCage(context, latitude, longitude, apiKey)
        } catch (e: Exception) {
            Log.w("OpenCage+Nominatim", "OpenCage fetch failed: ${e.message}")
            null
        }

        // If OpenCage returned readable and contains barangay-like part, use it.
        if (!openCageResult.isNullOrBlank()) {
            val hasBarangay = containsBarangayLevel(openCageResult)
            if (hasBarangay) {
                // Save to cache and return
                cacheDao.insert(LocationCache(cacheKey = key, locationName = openCageResult))
                Log.d("OpenCage+Nominatim", "OpenCage result used for $key -> $openCageResult")
                return@withContext openCageResult
            } else {
                // We'll fallback to Nominatim to try get barangay; but keep OpenCage as fallback if Nominatim fails
                Log.d("OpenCage+Nominatim", "OpenCage returned no barangay; falling back to Nominatim for $key -> $openCageResult")
            }
        }

        // 3) Nominatim fallback (more detailed OSM-based reverse geocoding)
        val nominatimResult = try {
            fetchNominatimReverse(latitude, longitude, nominatimUserAgent)
        } catch (e: Exception) {
            Log.w("OpenCage+Nominatim", "Nominatim fetch failed: ${e.message}")
            null
        }

        val finalName = when {
            !nominatimResult.isNullOrBlank() -> nomineeUsefulName(nominatimResult)
            !openCageResult.isNullOrBlank() -> openCageResult // use OpenCage output if nominatim failed
            else -> "Unknown Location"
        }

        // Save final result in cache
        cacheDao.insert(LocationCache(cacheKey = key, locationName = finalName))
        Log.d("OpenCage+Nominatim", "Final geocode for $key -> $finalName")
        return@withContext finalName

    } catch (e: Exception) {
        Log.e("OpenCage+Nominatim", "Error resolving location: ${e.message}")
        return@withContext "Unknown Location"
    }
}

/**
 * Call OpenCage API and return a readable location string (barangay/city/state/country if present).
 * Throws exceptions on network/IO errors.
 */
private fun fetchOpenCage(context: Context, latitude: Double, longitude: Double, apiKey: String): String? {
    val query = URLEncoder.encode("$latitude,$longitude", "UTF-8")
    val urlStr = "https://api.opencagedata.com/geocode/v1/json?q=$query&key=$apiKey&language=en"
    val url = URL(urlStr)
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8000
        readTimeout = 8000
    }

    return try {
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        val results = json.optJSONArray("results")
        if (results != null && results.length() > 0) {
            val comp = results.getJSONObject(0).optJSONObject("components") ?: JSONObject()

            val barangay = comp.optString("suburb").ifEmpty { comp.optString("village").ifEmpty { comp.optString("neighbourhood") } }
            val city = comp.optString("city").ifEmpty { comp.optString("town").ifEmpty { comp.optString("municipality") } }
            val province = comp.optString("state").ifEmpty { comp.optString("region") }
            val country = comp.optString("country")

            val parts = listOf(barangay, city, province, country).filter { it.isNotBlank() }
            if (parts.isNotEmpty()) parts.joinToString(", ") else results.getJSONObject(0).optString("formatted", null)
        } else null
    } finally {
        try { connection.disconnect() } catch (_: Exception) {}
    }
}

/**
 * Query Nominatim reverse endpoint for a more detailed breakdown.
 * Returns raw JSON string (as returned by Nominatim).
 *
 * IMPORTANT:
 * - We set a proper User-Agent header (Nominatim policy requirement).
 * - Keep usage respectful (throttle on your side, cache results).
 */
private fun fetchNominatimReverse(latitude: Double, longitude: Double, userAgent: String): String? {
    val qLat = URLEncoder.encode(latitude.toString(), "UTF-8")
    val qLon = URLEncoder.encode(longitude.toString(), "UTF-8")
    // format=jsonv2 gives nicer structure, address breakdown available
    val urlStr = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$qLat&lon=$qLon&addressdetails=1"
    val url = URL(urlStr)
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 9000
        readTimeout = 9000
        setRequestProperty("User-Agent", userAgent)
        // Optional: Accept-Language
        setRequestProperty("Accept-Language", "en")
    }

    return try {
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        try { connection.disconnect() } catch (_: Exception) {}
    }
}

/**
 * Determine if OpenCage-formatted string likely contains barangay-level info.
 * It's a heuristic: check for substrings commonly used for barangay/locality.
 */
private fun containsBarangayLevel(readable: String): Boolean {
    val lower = readable.lowercase(Locale.US)
    // look for common locality tokens (barangay, poblacion, sitio, purok, village, subdivision, barangay names often short)
    val tokens = listOf("barangay", "poblacion", "suburb", "village", "neighbourhood", "sitio", "purok", "barangay ")
    return tokens.any { lower.contains(it) }
}

/**
 * Build a human-readable string based on Nominatim JSON (raw string).
 * Tries to prefer: barangay/locality -> city/town/municipality -> state -> country.
 */
private fun nomineeUsefulName(nominatimJsonStr: String): String {
    try {
        val json = JSONObject(nominatimJsonStr)
        val address = json.optJSONObject("address") ?: JSONObject()

        // Nominatim keys we may use (in priority order for fine locality)
        val barangay = address.optString("suburb")
            .ifEmpty { address.optString("hamlet") }
            .ifEmpty { address.optString("village") }
            .ifEmpty { address.optString("neighbourhood") }
            .ifEmpty { address.optString("quarter") }
            .ifEmpty { address.optString("locality") }

        val city = address.optString("city")
            .ifEmpty { address.optString("town") }
            .ifEmpty { address.optString("municipality") }

        val county = address.optString("county") // sometimes useful
        val province = address.optString("state")
            .ifEmpty { address.optString("region") }

        val country = address.optString("country")

        val parts = listOf(barangay, city.ifEmpty { county }, province, country)
            .filter { it.isNotBlank() }
        if (parts.isNotEmpty()) return parts.joinToString(", ")
    } catch (e: Exception) {
        Log.w("OpenCage+Nominatim", "nomineeUsefulName parse error: ${e.message}")
    }
    // fallback
    return "Unknown Location"
}
