package com.example.frogdetection.utils

import android.content.Context
import android.location.Geocoder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.*

suspend fun getReadableLocation(
    context: Context,
    latitude: Double?,
    longitude: Double?,
    apiKey: String? = null
): String = withContext(Dispatchers.IO) {
    if (latitude == null || longitude == null || (latitude == 0.0 && longitude == 0.0)) {
        return@withContext "Unknown Location"
    }

    // 1️⃣ Try Android Geocoder (offline)
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocation(latitude, longitude, 1)
        if (!addresses.isNullOrEmpty()) {
            val addr = addresses[0]

            val barangay = addr.subLocality ?: ""
            val municipality = addr.locality ?: addr.subAdminArea ?: ""
            val province = addr.adminArea ?: ""
            val country = addr.countryName ?: ""

            val formatted = listOf(barangay, municipality, province, country)
                .filter { it.isNotBlank() }
                .joinToString(", ")

            if (barangay.isNotBlank()) {
                return@withContext formatted  // ✅ Barangay found, good enough
            }
        }
    } catch (e: Exception) {
        Log.w("getReadableLocation", "Geocoder failed: ${e.message}")
    }

    // 2️⃣ Fallback: Google Geocoding API (online, needs API key)
    if (!apiKey.isNullOrBlank()) {
        try {
            val url =
                "https://maps.googleapis.com/maps/api/geocode/json?latlng=$latitude,$longitude&key=$apiKey&language=en"
            val response = URL(url).readText()
            val json = JSONObject(response)
            val results = json.getJSONArray("results")
            if (results.length() > 0) {
                val formattedAddress = results.getJSONObject(0).getString("formatted_address")
                return@withContext formattedAddress
            }
        } catch (e: Exception) {
            Log.e("getReadableLocation", "Google Geocoding failed: ${e.message}")
        }
    }

    // 3️⃣ Fallback: Just lat/lon
    return@withContext "Lat: %.4f, Lon: %.4f".format(latitude, longitude)
}
