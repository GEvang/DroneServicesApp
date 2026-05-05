package com.example.droneservicesapp.data.geoawareness

import android.util.Log
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneDatasetValidator

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
        val validationResult = GeoZoneDatasetValidator.validate(
            rawJson = rawJson,
            datasetInfo = datasetInfo,
            zones = zones
        )
        Log.d(
            TAG,
            "Loaded geo-awareness dataset title=${datasetInfo.title} version=${datasetInfo.version} zones=${datasetInfo.zoneCount} official=${datasetInfo.isOfficial} dummy=${datasetInfo.isDummy}"
        )
        Log.d(
            TAG,
            "Validated geo-awareness dataset errors=${validationResult.errorCount} warnings=${validationResult.warningCount} valid=${validationResult.isValid}"
        )
        return GeoZoneLoadResult(
            datasetInfo = datasetInfo,
            zones = zones,
            validationResult = validationResult
        )
    }

    companion object {
        private const val TAG = "GeoZoneRepository"
    }
}
