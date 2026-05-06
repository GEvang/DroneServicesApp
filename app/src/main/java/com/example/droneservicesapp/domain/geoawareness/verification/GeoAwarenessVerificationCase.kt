package com.example.droneservicesapp.domain.geoawareness.verification

data class GeoAwarenessVerificationCase(
    val id: String,
    val title: String,
    val purpose: String,
    val preconditions: List<String>,
    val steps: List<String>,
    val expectedResult: String,
    val evidenceToCapture: List<String>,
    val category: String
)
