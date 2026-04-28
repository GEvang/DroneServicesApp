package com.example.droneservicesapp.domain.geoawareness

data class GeoZone(
    val id: String,
    val country: String,
    val name: String,
    val type: String?,
    val restriction: GeoZoneRestriction,
    val reason: List<String>,
    val otherReasonInfo: String?,
    val message: String?,
    val applicability: List<GeoZoneApplicability>,
    val authorities: List<GeoZoneAuthority>,
    val geometries: List<GeoZoneGeometry>,
    val colorHex: String?,
    val arc: String?,
    val isDummy: Boolean
)
