package com.example.droneservicesapp.data.geoawareness

import android.util.Log
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetSourceType
import com.example.droneservicesapp.domain.geoawareness.GeoZoneGeometry
import com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneDatasetValidator
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import java.io.File

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
        return buildSingleDatasetResult(
            rawJson = rawJson,
            fallbackSource = "Bundled asset",
            fallbackSourceUrl = "https://dagr.hasp.gov.gr/",
            sourceType = GeoZoneDatasetSourceType.BUNDLED_ASSET,
            storageFileName = null,
            importedAtMillis = null
        )
    }

    fun loadCurrentDataset(): GeoZoneLoadResult {
        val importedFiles = importedDataSource?.listImportedDatasetFiles().orEmpty()
        if (importedFiles.isEmpty()) {
            return loadDummyRethymnoDataset()
        }

        val validResults = mutableListOf<SingleDatasetLoad>()
        importedFiles.forEach { file ->
            runCatching { loadImportedDatasetFile(file) }
                .onSuccess { single ->
                    if (single.validationResult.hasErrors) {
                        Log.w(TAG, "Skipping imported dataset with validation errors file=${file.name}")
                    } else {
                        validResults += single
                    }
                }
                .onFailure { error ->
                    Log.w(TAG, "Skipping unreadable imported dataset file=${file.name}", error)
                }
        }

        if (validResults.isEmpty()) {
            throw IllegalStateException("No valid imported geo-zone datasets could be loaded.")
        }

        return buildCombinedResult(validResults)
    }

    fun importDataset(rawJson: String): GeoZoneLoadResult {
        val preview = buildSingleDatasetResult(
            rawJson = rawJson,
            fallbackSource = "Imported file",
            fallbackSourceUrl = null,
            sourceType = GeoZoneDatasetSourceType.IMPORTED_FILE,
            storageFileName = null,
            importedAtMillis = System.currentTimeMillis()
        )
        if (preview.validationResult.hasErrors) {
            throw GeoZoneDatasetValidationException(
                validationResult = preview.validationResult,
                message = "Imported geo-zone dataset failed validation."
            )
        }
        val source = importedDataSource
            ?: throw IllegalStateException("Imported geo-zone storage is unavailable.")
        source.saveImportedDataset(rawJson, suggestedName = preview.datasetInfo.title)
        return loadCurrentDataset()
    }

    fun removeImportedDataset(storageFileName: String): GeoZoneLoadResult {
        val source = importedDataSource
            ?: throw IllegalStateException("Imported geo-zone storage is unavailable.")
        if (!source.deleteImportedDataset(storageFileName)) {
            throw IllegalStateException("Failed to remove imported geo-zone dataset: $storageFileName")
        }
        return loadCurrentDataset()
    }

    fun resetToBundledDataset(): GeoZoneLoadResult {
        importedDataSource?.deleteAllImportedDatasets()
        return loadDummyRethymnoDataset()
    }

    fun hasImportedDatasets(): Boolean = importedDataSource?.hasImportedDatasets() == true

    private fun loadImportedDatasetFile(file: File): SingleDatasetLoad {
        val rawJson = importedDataSource?.loadRawJson(file)
            ?: throw IllegalStateException("Imported geo-zone storage is unavailable.")
        val importedAtMillis = file.lastModified().takeIf { it > 0L }
        return buildSingleDatasetLoad(
            rawJson = rawJson,
            fallbackSource = "Imported file",
            fallbackSourceUrl = null,
            sourceType = GeoZoneDatasetSourceType.IMPORTED_FILE,
            storageFileName = file.name,
            importedAtMillis = importedAtMillis
        )
    }

    private fun buildSingleDatasetResult(
        rawJson: String,
        fallbackSource: String,
        fallbackSourceUrl: String?,
        sourceType: GeoZoneDatasetSourceType,
        storageFileName: String?,
        importedAtMillis: Long?
    ): GeoZoneLoadResult {
        val single = buildSingleDatasetLoad(
            rawJson = rawJson,
            fallbackSource = fallbackSource,
            fallbackSourceUrl = fallbackSourceUrl,
            sourceType = sourceType,
            storageFileName = storageFileName,
            importedAtMillis = importedAtMillis
        )
        return GeoZoneLoadResult(
            datasetInfo = single.datasetInfo,
            zones = single.zones,
            validationResult = single.validationResult,
            datasetRecords = listOf(single.record)
        )
    }

    private fun buildSingleDatasetLoad(
        rawJson: String,
        fallbackSource: String,
        fallbackSourceUrl: String?,
        sourceType: GeoZoneDatasetSourceType,
        storageFileName: String?,
        importedAtMillis: Long?
    ): SingleDatasetLoad {
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
        val datasetId = storageFileName?.substringBeforeLast('.') ?: "${sourceType.name.lowercase()}_${loadedAtMillis}"
        val record = GeoZoneDatasetRecord(
            datasetId = datasetId,
            displayName = datasetInfo.title,
            storageFileName = storageFileName,
            sourceType = sourceType,
            datasetInfo = datasetInfo,
            validationResult = validationResult,
            zoneCount = zones.size,
            importedAtMillis = importedAtMillis
        )
        logLoad(datasetInfo, validationResult)
        return SingleDatasetLoad(
            datasetInfo = datasetInfo,
            zones = zones,
            validationResult = validationResult,
            record = record
        )
    }

    private fun buildCombinedResult(results: List<SingleDatasetLoad>): GeoZoneLoadResult {
        if (results.size == 1) {
            val single = results.first()
            return GeoZoneLoadResult(
                datasetInfo = single.datasetInfo,
                zones = single.zones,
                validationResult = single.validationResult,
                datasetRecords = listOf(single.record)
            )
        }

        val zones = results.flatMap { it.zones }
        val combinedValidation = GeoZoneValidationResult.combine(results.map { it.validationResult })
        val datasetRecords = results.map { it.record }
        val countries = results.mapNotNull { it.datasetInfo.country?.takeIf(String::isNotBlank) }.distinct()
        val loadedAtMillis = System.currentTimeMillis()
        val circleCount = results.sumOf { it.datasetInfo.circleGeometryCount }
        val polygonCount = results.sumOf { it.datasetInfo.polygonGeometryCount }
        val combinedInfo = GeoZoneDatasetInfo(
            title = "Combined geo-awareness datasets",
            description = "Combined active imported geo-zone datasets.",
            version = "multiple",
            source = "Imported files",
            sourceUrl = null,
            country = when {
                countries.isEmpty() -> null
                countries.size == 1 -> countries.first()
                else -> "Multiple"
            },
            isOfficial = results.all { it.datasetInfo.isOfficial } && results.none { it.datasetInfo.isDummy },
            isDummy = results.all { it.datasetInfo.isDummy },
            loadedAtMillis = loadedAtMillis,
            zoneCount = zones.size,
            circleGeometryCount = circleCount,
            polygonGeometryCount = polygonCount
        )
        Log.d(
            TAG,
            "Loaded combined geo-awareness datasets count=${datasetRecords.size} zones=${zones.size} warnings=${combinedValidation.warningCount} errors=${combinedValidation.errorCount}"
        )
        return GeoZoneLoadResult(
            datasetInfo = combinedInfo,
            zones = zones,
            validationResult = combinedValidation,
            datasetRecords = datasetRecords
        )
    }

    private fun logLoad(
        datasetInfo: GeoZoneDatasetInfo,
        validationResult: GeoZoneValidationResult
    ) {
        Log.d(
            TAG,
            "Loaded geo-awareness dataset title=${datasetInfo.title} version=${datasetInfo.version} zones=${datasetInfo.zoneCount} official=${datasetInfo.isOfficial} dummy=${datasetInfo.isDummy}"
        )
        Log.d(
            TAG,
            "Validated geo-awareness dataset errors=${validationResult.errorCount} warnings=${validationResult.warningCount} valid=${validationResult.isValid}"
        )
    }

    private data class SingleDatasetLoad(
        val datasetInfo: GeoZoneDatasetInfo,
        val zones: List<GeoZone>,
        val validationResult: GeoZoneValidationResult,
        val record: GeoZoneDatasetRecord
    )

    companion object {
        private const val TAG = "GeoZoneRepository"
    }
}
