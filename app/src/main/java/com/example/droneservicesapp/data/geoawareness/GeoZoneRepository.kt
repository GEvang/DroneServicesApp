package com.example.droneservicesapp.data.geoawareness

import android.util.Log
import com.example.droneservicesapp.domain.geoawareness.GeoZone

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

    companion object {
        private const val TAG = "GeoZoneRepository"
    }
}
