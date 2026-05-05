package com.example.droneservicesapp.domain.geoawareness

import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult

data class GeoZoneDatasetRecord(
    val datasetId: String,
    val displayName: String,
    val storageFileName: String?,
    val sourceType: GeoZoneDatasetSourceType,
    val datasetInfo: GeoZoneDatasetInfo,
    val validationResult: GeoZoneValidationResult,
    val zoneCount: Int,
    val importedAtMillis: Long?
)
