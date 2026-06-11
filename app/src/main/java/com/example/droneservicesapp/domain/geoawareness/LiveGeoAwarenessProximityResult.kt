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
    val timeToBoundarySeconds: Double? = null,
    val warningMeetsRequiredTime: Boolean? = null
)
