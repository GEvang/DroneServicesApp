package com.example.droneservicesapp.data.geoawareness.source

import com.example.droneservicesapp.data.geoawareness.GeoZoneAssetDataSource
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetSourceType

class BundledGeoZoneDataSource(
    private val assetDataSource: GeoZoneAssetDataSource
) : GeoZoneDataSource {

    override val sourceType: GeoZoneDatasetSourceType = GeoZoneDatasetSourceType.BUNDLED_ASSET
    override val displayName: String = "Bundled Rethymno dummy dataset"

    override fun hasData(): Boolean = true

    override fun loadDatasets(): List<GeoZoneRawDataset> {
        return listOf(
            GeoZoneRawDataset(
                datasetId = "bundled-rethymno-dummy",
                displayName = displayName,
                rawJson = assetDataSource.loadRawAsset(),
                sourceType = sourceType,
                storageFileName = null
            )
        )
    }
}
