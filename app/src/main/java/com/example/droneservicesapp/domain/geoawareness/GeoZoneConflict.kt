package com.example.droneservicesapp.domain.geoawareness

data class GeoZoneConflict(
    val zone: GeoZone,
    val conflictType: GeoConflictType,
    val restriction: GeoZoneRestriction,
    val message: String?,
    val affectedGeometryCount: Int
)
