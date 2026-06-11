package com.example.droneservicesapp.domain.geoawareness

import com.example.droneservicesapp.domain.model.LatLon

sealed class GeoZoneGeometry {
    abstract val lowerLimitMeters: Double?
    abstract val upperLimitMeters: Double?
    abstract val lowerVerticalReference: GeoVerticalReference
    abstract val upperVerticalReference: GeoVerticalReference
    abstract val originalLowerLimit: Double?
    abstract val originalUpperLimit: Double?
    abstract val altitudeUnit: GeoAltitudeUnit

    data class Circle(
        val center: LatLon,
        val radiusMeters: Double,
        override val lowerLimitMeters: Double?,
        override val upperLimitMeters: Double?,
        override val lowerVerticalReference: GeoVerticalReference = GeoVerticalReference.AGL,
        override val upperVerticalReference: GeoVerticalReference = GeoVerticalReference.AGL,
        override val originalLowerLimit: Double? = lowerLimitMeters,
        override val originalUpperLimit: Double? = upperLimitMeters,
        override val altitudeUnit: GeoAltitudeUnit = GeoAltitudeUnit.M
    ) : GeoZoneGeometry()

    data class Polygon(
        val rings: List<List<LatLon>>,
        override val lowerLimitMeters: Double?,
        override val upperLimitMeters: Double?,
        override val lowerVerticalReference: GeoVerticalReference = GeoVerticalReference.AGL,
        override val upperVerticalReference: GeoVerticalReference = GeoVerticalReference.AGL,
        override val originalLowerLimit: Double? = lowerLimitMeters,
        override val originalUpperLimit: Double? = upperLimitMeters,
        override val altitudeUnit: GeoAltitudeUnit = GeoAltitudeUnit.M
    ) : GeoZoneGeometry()
}
