package com.example.droneservicesapp.data.geoawareness

import android.content.Context
import android.util.Log
import com.example.droneservicesapp.data.geoawareness.source.BundledGeoZoneDataSource
import com.example.droneservicesapp.data.geoawareness.source.ImportedGeoZoneDataSource

object GeoZoneSourceArchitectureDebugProbe {

    private const val TAG = "GeoSourceDebug"

    fun logSourceArchitectureValidation(context: Context) {
        try {
            val appContext = context.applicationContext
            val assetDataSource = GeoZoneAssetDataSource(appContext)
            val importedFileDataSource = GeoZoneImportedFileDataSource(appContext)
            val bundledSource = BundledGeoZoneDataSource(assetDataSource)
            val importedSource = ImportedGeoZoneDataSource(importedFileDataSource)
            val repository = GeoZoneRepository(
                assetDataSource = assetDataSource,
                importedFileDataSource = importedFileDataSource
            )

            val bundled = bundledSource.hasData() && bundledSource.loadDatasets().size == 1
            val currentLoad = repository.loadCurrentDataset()
            val currentFallback = if (importedSource.hasData()) {
                currentLoad.datasetRecords.isNotEmpty()
            } else {
                currentLoad.datasetRecords.size == 1 &&
                    currentLoad.datasetRecords.first().sourceType == com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetSourceType.BUNDLED_ASSET
            }
            val dummyLoad = repository.loadDummyRethymnoDataset().zones.isNotEmpty()
            val imported = if (importedSource.hasData()) {
                importedSource.loadDatasets().isNotEmpty()
            } else {
                true
            }

            Log.d(
                TAG,
                "Source architecture validation: bundled=$bundled currentFallback=$currentFallback dummyLoad=$dummyLoad imported=$imported"
            )
        } catch (error: Exception) {
            Log.e(TAG, "Source architecture validation failed", error)
        }
    }
}
