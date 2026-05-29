package com.example.droneservicesapp.data.geoawareness.source

import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetSourceType

interface GeoZoneDataSource {
    val sourceType: GeoZoneDatasetSourceType
    val displayName: String

    fun hasData(): Boolean

    @Throws(Exception::class)
    fun loadDatasets(): List<GeoZoneRawDataset>
}
