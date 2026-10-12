package com.example.droneservicesapp.ui.home.geoawareness

/** One live geo-awareness threat row, as produced by [LiveGeoThreatPresenter]. */
data class LiveGeoThreatUiModel(
    val label: String,
    val colorHex: String,
    val directionText: String,
    val distanceText: String,
    val altitudeText: String,
    val verticalArrowText: String = "",
    val radialDistanceRatio: Float = 1f,
    val bearingDegrees: Double? = null,
    val verticalIndicator: VerticalIndicator = VerticalIndicator.NONE,
    val showCompassMarker: Boolean = true,
    val isInsideZone: Boolean = false,
)

enum class VerticalIndicator { NONE, UP, DOWN }
