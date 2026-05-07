package com.example.droneservicesapp.data.geoawareness.incident

data class GeoIncidentEvent(
    val id: String,
    val timestampMillis: Long,
    val timestampIsoUtc: String,
    val type: GeoIncidentEventType,
    val zoneIds: List<String>,
    val zoneNames: List<String>,
    val highestRestriction: String?,
    val restrictions: List<String>,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeMeters: Double?,
    val datasetTitle: String?,
    val datasetVersion: String?,
    val healthState: String?,
    val source: String,
    val message: String,
    val details: Map<String, String> = emptyMap()
)
