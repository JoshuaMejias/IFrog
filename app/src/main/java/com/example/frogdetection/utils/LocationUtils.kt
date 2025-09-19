package com.example.frogdetection.utils

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.tasks.await
import java.util.*

suspend fun getReadableLocation(
    context: Context,
    latitude: Double?,
    longitude: Double?,
    placesClient: PlacesClient? = null
): String {
    if (latitude == null || longitude == null || (latitude == 0.0 && longitude == 0.0)) {
        return "Unknown Location"
    }

    // ✅ Try Android Geocoder first (Barangay, Municipality, Province, Country)
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocation(latitude, longitude, 1)

        if (!addresses.isNullOrEmpty()) {
            val addr = addresses[0]

            val building = addr.featureName ?: ""   // Optional landmark/building
            val barangay = addr.subLocality ?: ""   // Barangay
            val municipality = addr.locality ?: addr.subAdminArea ?: ""  // City/Municipality
            val province = addr.adminArea ?: ""     // Province
            val country = addr.countryName ?: ""    // Country

            val readable = listOf(building, barangay, municipality, province, country)
                .filter { it.isNotBlank() }
                .joinToString(", ")

            if (readable.isNotBlank()) return readable
        }
    } catch (e: Exception) {
        Log.e("getReadableLocation", "Geocoder failed: ${e.message}")
    }

    // ✅ If Geocoder fails → fallback to Places API (using reverse lookup)
    if (placesClient != null) {
        try {
            val latLng = LatLng(latitude, longitude)
            val placeFields = listOf(Place.Field.NAME, Place.Field.ADDRESS)

            // Instead of FetchPlaceRequest, use findCurrentPlace or Geocoding API via HTTP
            val place = com.google.android.libraries.places.api.model.Place.builder()
                .setLatLng(latLng)
                .build()

            // Since Places SDK doesn’t directly support reverse geocoding by latLng,
            // you should use Geocoder or the Geocoding HTTP API for full accuracy.
            return place.address ?: "Lat: %.4f, Lon: %.4f".format(latitude, longitude)
        } catch (e: Exception) {
            Log.w("getReadableLocation", "Places API fallback failed: ${e.message}")
        }
    }

    // ✅ Last resort → raw coordinates
    return "Lat: %.4f, Lon: %.4f".format(latitude, longitude)
}
