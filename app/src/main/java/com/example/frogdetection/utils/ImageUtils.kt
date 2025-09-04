package com.example.frogdetection.utils


import android.content.Context
import android.media.ExifInterface
import android.net.Uri

fun getImageLocation(context: Context, uri: Uri): Pair<Double, Double>? {
    val inputStream = context.contentResolver.openInputStream(uri) ?: return null
    val exif = ExifInterface(inputStream)

    val lat = exif.getAttributeDouble(ExifInterface.TAG_GPS_LATITUDE, 0.0)
    val lon = exif.getAttributeDouble(ExifInterface.TAG_GPS_LONGITUDE, 0.0)
    val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
    val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)

    val latitude = if (latRef == "S") -lat else lat
    val longitude = if (lonRef == "W") -lon else lon

    return if (latitude != 0.0 && longitude != 0.0) Pair(latitude, longitude) else null
}
