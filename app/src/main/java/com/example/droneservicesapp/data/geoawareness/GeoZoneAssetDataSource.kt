package com.example.droneservicesapp.data.geoawareness

import android.content.Context
import java.io.IOException

class GeoZoneAssetDataSource(private val context: Context) {

    fun loadRawAsset(assetPath: String = DEFAULT_ASSET_PATH): String {
        return try {
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (error: IOException) {
            throw IllegalStateException("Failed to load geozone asset: $assetPath", error)
        }
    }

    companion object {
        const val DEFAULT_ASSET_PATH = "geozones/rethymno_dummy_geozones.json"
    }
}
