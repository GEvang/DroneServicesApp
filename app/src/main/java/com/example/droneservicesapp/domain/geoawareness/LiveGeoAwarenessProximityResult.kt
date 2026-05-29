package com.example.droneservicesapp.domain.geoawareness

data class LiveGeoAwarenessProximityResult(
    val nearestZone: GeoZone,
    val distanceMeters: Double,
    val restriction: GeoZoneRestriction
)
