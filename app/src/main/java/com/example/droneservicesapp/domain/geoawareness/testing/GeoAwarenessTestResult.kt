package com.example.droneservicesapp.domain.geoawareness.testing

data class GeoAwarenessTestResult(
    val id: String,
    val name: String,
    val status: GeoAwarenessTestStatus,
    val message: String,
    val details: Map<String, String> = emptyMap()
)
