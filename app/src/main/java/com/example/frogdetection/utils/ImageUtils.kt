package com.example.frogdetection.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Utility for loading and correcting bitmap images:
 *  - Safe URI decoding
 *  - EXIF rotation correction
 *  - Guaranteed ARGB_8888 format (required for ML inference)
 */
object ImageUtils {

    /**
     * Loads a bitmap from the given URI, fixes orientation via EXIF,
     * and ensures ARGB_8888 config.
     */
    fun loadCorrectedBitmap(context: Context, uri: Uri): Bitmap {
        // Decode bitmap safely
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open URI: $uri")

        val rawBitmap = BitmapFactory.decodeStream(inputStream)
            ?: throw IllegalArgumentException("Cannot decode Bitmap from URI: $uri")

        inputStream.close()

        // Reopen stream for EXIF analysis
        val exifStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot reopen URI for EXIF: $uri")

        val exif = ExifInterface(exifStream)
        exifStream.close()

        // Determine correct rotation
        val rotation = when (
            exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        // Apply rotation only when needed
        val rotated = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(
                rawBitmap, 0, 0,
                rawBitmap.width, rawBitmap.height,
                matrix, true
            )
        } else {
            rawBitmap
        }

        // ONNX requires ARGB_8888 for reliable tensor creation
        return rotated.copy(Bitmap.Config.ARGB_8888, true)
    }

    /**
     * Extracts GPS EXIF metadata from an image if available.
     * Returns Pair(latitude, longitude) or null.
     */
    fun getImageLocation(context: Context, uri: Uri): Pair<Double, Double>? {
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        val exif = ExifInterface(stream)
        stream.close()

        val lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
        val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
        val lon = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
        val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)

        if (lat == null || latRef == null || lon == null || lonRef == null)
            return null

        val latitude = dmsToDecimal(lat)
        val longitude = dmsToDecimal(lon)

        return Pair(
            if (latRef.equals("S", true)) -latitude else latitude,
            if (lonRef.equals("W", true)) -longitude else longitude
        )
    }

    /**
     * Converts EXIF DMS format (e.g. "123/1,45/1,678/10") to decimal degrees.
     */
    private fun dmsToDecimal(dms: String): Double {
        val parts = dms.split(",")

        val d = parts[0].split("/").let { it[0].toDouble() / it[1].toDouble() }
        val m = parts[1].split("/").let { it[0].toDouble() / it[1].toDouble() }
        val s = parts[2].split("/").let { it[0].toDouble() / it[1].toDouble() }

        return d + (m / 60.0) + (s / 3600.0)
    }
}
