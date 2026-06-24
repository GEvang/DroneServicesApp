package com.example.droneservicesapp.domain.geoawareness

data class LiveGeoAwarenessProximityResult(
    val nearestZone: GeoZone,
    val distanceMeters: Double,
    val restriction: GeoZoneRestriction,
    val configuredThresholdMeters: Double = 100.0,
    val effectiveThresholdMeters: Double = configuredThresholdMeters,
    val requiredWarningSeconds: Double = 3.0,
    val minimumWarningDistanceMeters: Double? = null,
    val groundSpeedMetersPerSecond: Double? = null,
    val headingDegrees: Double? = null,
    val closingSpeedMetersPerSecond: Double? = null,
    val timeToBoundarySeconds: Double? = null,
    val verticalDistanceMeters: Double? = null,
    val verticalClosingSpeedMetersPerSecond: Double? = null,
    val verticalTimeToBoundarySeconds: Double? = null,
    val verticalBoundaryReference: GeoVerticalReference? = null,
    val warningMeetsRequiredTime: Boolean? = null,
    val warningMode: String = "FIXED_DISTANCE_100M",
    val verticalRelevance: Boolean = true
)
