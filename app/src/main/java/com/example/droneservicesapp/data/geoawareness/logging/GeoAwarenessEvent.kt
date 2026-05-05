package com.example.droneservicesapp.data.geoawareness.logging

data class GeoAwarenessEvent(
    val id: String,
    val timestampMillis: Long,
    val type: GeoAwarenessEventType,
    val severity: String,
    val message: String,
    val datasetTitle: String?,
    val datasetVersion: String?,
    val healthState: String?,
    val zoneIds: List<String>,
    val zoneNames: List<String>,
    val restriction: String?,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeMeters: Double?,
    val details: Map<String, String>
)
