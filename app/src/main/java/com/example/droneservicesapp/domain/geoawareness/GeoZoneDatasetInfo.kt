package com.example.droneservicesapp.domain.geoawareness

data class GeoZoneDatasetInfo(
    val title: String,
    val description: String?,
    val version: String?,
    val source: String?,
    val sourceUrl: String?,
    val country: String?,
    val isOfficial: Boolean,
    val isDummy: Boolean,
    val loadedAtMillis: Long,
    val zoneCount: Int,
    val circleGeometryCount: Int,
    val polygonGeometryCount: Int
)
