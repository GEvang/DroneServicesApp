package com.example.droneservicesapp.data.geoawareness.source

import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetSourceType

data class GeoZoneRawDataset(
    val datasetId: String,
    val displayName: String,
    val rawJson: String,
    val sourceType: GeoZoneDatasetSourceType,
    val storageFileName: String?,
    val originalFileName: String? = null,
    val updatedAtMillis: Long? = null,
    val importedAtMillis: Long? = null
)
