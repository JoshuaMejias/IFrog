// File: app/src/main/java/com/example/frogdetection/data/MapFocusStore.kt
package com.example.frogdetection.data

/**
 * Shared in-memory store used to tell the map screen which frog marker
 * should be centered, zoomed, animated, and highlighted when opened.
 */
object MapFocusStore {

    /** The specific frog ID to highlight (pop + glow + bring-to-front). */
    var focusId: Int? = null

    /** Coordinates to center & zoom the map to. */
    var focusLat: Double? = null
    var focusLon: Double? = null

    /** Clears all focus data after the map consumes it. */
    fun clear() {
        focusId = null
        focusLat = null
        focusLon = null
    }
}
