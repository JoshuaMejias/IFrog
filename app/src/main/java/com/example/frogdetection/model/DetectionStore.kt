package com.example.frogdetection.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a single detection result saved inside Room as JSON.
 */
data class DetectionStore(
    @SerializedName("species") val species: String,
    @SerializedName("score") val score: Float,
    @SerializedName("left") val left: Float,
    @SerializedName("top") val top: Float,
    @SerializedName("right") val right: Float,
    @SerializedName("bottom") val bottom: Float
)
