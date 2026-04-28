package com.example.droneservicesapp.domain.geoawareness

import com.example.droneservicesapp.domain.model.LatLon

sealed class GeoZoneGeometry {
    abstract val lowerLimitMeters: Double?
    abstract val upperLimitMeters: Double?

    data class Circle(
        val center: LatLon,
        val radiusMeters: Double,
        override val lowerLimitMeters: Double?,
        override val upperLimitMeters: Double?
    ) : GeoZoneGeometry()

    data class Polygon(
        val rings: List<List<LatLon>>,
        override val lowerLimitMeters: Double?,
        override val upperLimitMeters: Double?
    ) : GeoZoneGeometry()
}
