package com.example.droneservicesapp.data.geoawareness

import android.util.Log
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult

class GeoZoneRepository(
    private val assetDataSource: GeoZoneAssetDataSource,
    private val parser: GeoZoneJsonParser = GeoZoneJsonParser()
) {

    fun loadDummyRethymnoZones(): List<GeoZone> {
        val rawJson = assetDataSource.loadRawAsset(GeoZoneAssetDataSource.DEFAULT_ASSET_PATH)
        val zones = parser.parse(rawJson)
        Log.d(TAG, "Loaded ${zones.size} dummy geo-awareness zones")
        return zones
    }

    fun loadDummyRethymnoDataset(): GeoZoneLoadResult {
        val rawJson = assetDataSource.loadRawAsset(GeoZoneAssetDataSource.DEFAULT_ASSET_PATH)
        val loadedAtMillis = System.currentTimeMillis()
        val zones = parser.parse(rawJson)
        val datasetInfo = parser.parseDatasetInfo(
            rawJson = rawJson,
            zones = zones,
            loadedAtMillis = loadedAtMillis
        )
        Log.d(
            TAG,
            "Loaded geo-awareness dataset title=${datasetInfo.title} version=${datasetInfo.version} zones=${datasetInfo.zoneCount} official=${datasetInfo.isOfficial} dummy=${datasetInfo.isDummy}"
        )
        return GeoZoneLoadResult(
            datasetInfo = datasetInfo,
            zones = zones
        )
    }

    companion object {
        private const val TAG = "GeoZoneRepository"
    }
}
