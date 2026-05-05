package com.example.droneservicesapp.domain.geoawareness.validation

data class GeoZoneValidationIssue(
    val severity: GeoZoneValidationSeverity,
    val code: String,
    val message: String,
    val zoneId: String? = null,
    val field: String? = null
)
