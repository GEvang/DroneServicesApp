package com.example.droneservicesapp.data.geoawareness

import android.util.Log
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneDatasetValidator

class GeoZoneRepository(
    private val assetDataSource: GeoZoneAssetDataSource,
    private val importedDataSource: GeoZoneImportedFileDataSource? = null,
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
        return buildLoadResult(
            rawJson = rawJson,
            fallbackSource = "Bundled asset",
            fallbackSourceUrl = "https://dagr.hasp.gov.gr/"
        )
    }

    fun loadCurrentDataset(): GeoZoneLoadResult {
        val importedRawJson = importedDataSource?.loadImportedRawJson()
        return if (importedRawJson != null) {
            buildLoadResult(
                rawJson = importedRawJson,
                fallbackSource = "Imported file",
                fallbackSourceUrl = null
            )
        } else {
            loadDummyRethymnoDataset()
        }
    }

    fun importDataset(rawJson: String): GeoZoneLoadResult {
        val loadResult = buildLoadResult(
            rawJson = rawJson,
            fallbackSource = "Imported file",
            fallbackSourceUrl = null
        )
        if (loadResult.validationResult.hasErrors) {
            throw GeoZoneDatasetValidationException(
                validationResult = loadResult.validationResult,
                message = "Imported geo-zone dataset failed validation."
            )
        }
        val source = importedDataSource
            ?: throw IllegalStateException("Imported geo-zone storage is unavailable.")
        source.saveImportedDataset(rawJson)
        return buildLoadResult(
            rawJson = source.loadImportedRawJson()
                ?: throw IllegalStateException("Imported geo-zone dataset could not be reloaded after save."),
            fallbackSource = "Imported file",
            fallbackSourceUrl = null
        )
    }

    fun resetToBundledDataset(): GeoZoneLoadResult {
        importedDataSource?.deleteImportedDataset()
        return loadDummyRethymnoDataset()
    }

    fun hasImportedDataset(): Boolean = importedDataSource?.hasImportedDataset() == true

    private fun buildLoadResult(
        rawJson: String,
        fallbackSource: String,
        fallbackSourceUrl: String?
    ): GeoZoneLoadResult {
        val loadedAtMillis = System.currentTimeMillis()
        val zones = parser.parse(rawJson)
        val datasetInfo = parser.parseDatasetInfo(
            rawJson = rawJson,
            zones = zones,
            loadedAtMillis = loadedAtMillis,
            fallbackSource = fallbackSource,
            fallbackSourceUrl = fallbackSourceUrl
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
