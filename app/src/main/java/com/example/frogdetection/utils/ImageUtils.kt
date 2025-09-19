package com.example.frogdetection.utils

import android.content.Context
import android.media.ExifInterface
import android.net.Uri

fun getImageLocation(context: Context, uri: Uri): Pair<Double, Double>? {
    val inputStream = context.contentResolver.openInputStream(uri) ?: return null
    val exif = ExifInterface(inputStream)

    val lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
    val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
    val lon = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
    val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)

    if (lat != null && latRef != null && lon != null && lonRef != null) {
        val latitude = convertToDegree(lat)
        val longitude = convertToDegree(lon)

        return Pair(
            if (latRef == "S") -latitude else latitude,
            if (lonRef == "W") -longitude else longitude
        )
    }
    return null
}

private fun convertToDegree(stringDMS: String): Double {
    val dms = stringDMS.split(",")
    val d = dms[0].split("/").let { it[0].toDouble() / it[1].toDouble() }
    val m = dms[1].split("/").let { it[0].toDouble() / it[1].toDouble() }
    val s = dms[2].split("/").let { it[0].toDouble() / it[1].toDouble() }

    return d + (m / 60) + (s / 3600)
}
