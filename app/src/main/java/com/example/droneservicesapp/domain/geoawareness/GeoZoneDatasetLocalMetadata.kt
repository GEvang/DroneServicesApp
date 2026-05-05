package com.example.droneservicesapp.domain.geoawareness

data class GeoZoneDatasetLocalMetadata(
    val datasetId: String,
    val originalFileName: String?,
    val storageFileName: String,
    val importedAtMillis: Long,
    val updatedAtMillis: Long,
    val lastValidatedAtMillis: Long?,
    val sourceType: GeoZoneDatasetSourceType
)
