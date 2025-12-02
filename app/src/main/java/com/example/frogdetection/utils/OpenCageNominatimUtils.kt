package com.example.frogdetection.utils

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Workflow:
 * 1. Try OpenCage → if it returns barangay → use it.
 * 2. Otherwise call Nominatim with deep zoom (20) for PH-level detail.
 */
suspend fun getReadableLocationFromOpenCageOrNominatim(
    context: Context,
    lat: Double,
    lon: Double,
    apiKey: String
): String {

    val oc = getFromOpenCage(lat, lon, apiKey)

    if (oc != null && !oc.barangay.isNullOrBlank()) {
        return buildAddress(oc)
    }

    val nom = getFromNominatim(lat, lon)
    if (nom != null) {
        return buildAddress(nom)
    }

    return "Unknown Location"
}

// ============================================================================
// OPEN CAGE
// ============================================================================
private fun getFromOpenCage(
    lat: Double,
    lon: Double,
    apiKey: String
): AddressParts? {
    return try {
        val query = URLEncoder.encode("$lat,$lon", "UTF-8")
        val url = URL(
            "https://api.opencagedata.com/geocode/v1/json?q=$query&key=$apiKey&language=en"
        )

        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 7000
        conn.readTimeout = 7000

        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val json = JSONObject(text)
        val results = json.getJSONArray("results")
        if (results.length() == 0) return null

        val comp = results.getJSONObject(0).getJSONObject("components")

        AddressParts(
            barangay = extractBarangay(
                comp.optString("suburb"),
                comp.optString("village"),
                comp.optString("neighbourhood"),
                comp.optString("county"),        // Many PH barangays appear here
                comp.optString("quarter"),
                comp.optString("hamlet"),
                comp.optString("residential")
            ),

            municipality = comp.optString("city")
                .ifEmpty { comp.optString("town") }
                .ifEmpty { comp.optString("municipality") },

            province = comp.optString("state")
                .ifEmpty { comp.optString("region") },

            country = comp.optString("country")
        )

    } catch (e: Exception) {
        Log.e("OpenCage", "Error: ${e.message}")
        null
    }
}

// ============================================================================
// NOMINATIM
// ============================================================================
private fun getFromNominatim(lat: Double, lon: Double): AddressParts? {
    return try {

        val url = URL(
            "https://nominatim.openstreetmap.org/reverse" +
                    "?format=json&lat=$lat&lon=$lon&zoom=20&addressdetails=1"
        )

        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "iFrog/1.0")
        conn.connectTimeout = 7000
        conn.readTimeout = 7000

        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val json = JSONObject(text)
        val addr = json.getJSONObject("address")

        AddressParts(
            barangay = extractBarangay(
                addr.optString("neighbourhood"),
                addr.optString("suburb"),
                addr.optString("village"),
                addr.optString("residential"),    // Very common for PH
                addr.optString("county"),         // Many barangays appear here
                addr.optString("quarter"),
                addr.optString("hamlet"),
                addr.optString("locality")
            ),

            municipality = addr.optString("city")
                .ifEmpty { addr.optString("town") }
                .ifEmpty { addr.optString("municipality") },

            province = addr.optString("state")
                .ifEmpty { addr.optString("region") },

            country = addr.optString("country")
        )

    } catch (e: Exception) {
        Log.e("Nominatim", "Error: ${e.message}")
        null
    }
}

// ============================================================================
// BARANGAY EXTRACTION (Unified for both APIs)
// ============================================================================
private fun extractBarangay(vararg fields: String): String? {
    return fields.firstOrNull { it.isNotBlank() }
}

// ============================================================================
// FORMATTER
// ============================================================================
private fun buildAddress(parts: AddressParts?): String {
    if (parts == null) return "Unknown Location"

    return listOf(
        parts.barangay,
        parts.municipality,
        parts.province,
        parts.country
    )
        .filter { !it.isNullOrBlank() }
        .joinToString(", ")
}

// ============================================================================
// DATA
// ============================================================================
private data class AddressParts(
    val barangay: String?,
    val municipality: String?,
    val province: String?,
    val country: String?
)
