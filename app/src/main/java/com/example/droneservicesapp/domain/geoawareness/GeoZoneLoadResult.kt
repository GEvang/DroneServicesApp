package com.example.droneservicesapp.domain.geoawareness

import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult

data class GeoZoneLoadResult(
    val datasetInfo: GeoZoneDatasetInfo,
    val zones: List<GeoZone>,
    val validationResult: GeoZoneValidationResult
)
