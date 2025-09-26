package com.example.frogdetection.utils

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
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

    // ✅ Prefer Geocoder (Barangay → Municipality → Province → Country)
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocation(latitude, longitude, 1)
        if (!addresses.isNullOrEmpty()) {
            val addr = addresses[0]

            val building = addr.featureName ?: ""       // May contain landmark/POI
            val barangay = addr.subLocality ?: ""       // Barangay
            val municipality = addr.locality ?: addr.subAdminArea ?: "" // City/Municipality
            val province = addr.adminArea ?: ""         // Province
            val country = addr.countryName ?: ""        // Country

            return listOf(building, barangay, municipality, province, country)
                .filter { it.isNotBlank() }
                .joinToString(", ")
        }
    } catch (e: Exception) {
        Log.e("getReadableLocation", "Geocoder failed: ${e.message}")
    }

    // ✅ Fallback: Google Places API
    if (placesClient != null) {
        try {
            val placeFields = listOf(
                Place.Field.NAME,       // POI/Building
                Place.Field.ADDRESS     // Full formatted address
            )
            val request = FetchPlaceRequest.newInstance(
                "$latitude,$longitude", placeFields
            )

            val response = placesClient.fetchPlace(request).await()
            val place = response.place

            val building = place.name ?: ""
            val address = place.address ?: ""

            return if (building.isNotBlank()) {
                "$building, $address"
            } else {
                address
            }
        } catch (e: Exception) {
            Log.w("getReadableLocation", "Google Places failed: ${e.message}")
        }
    }

    // ✅ Final fallback
    return "Lat: %.4f, Lon: %.4f".format(latitude, longitude)
}
