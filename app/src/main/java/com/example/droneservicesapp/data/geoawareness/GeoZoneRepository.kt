package com.example.droneservicesapp.data.geoawareness

import android.util.Log
import com.example.droneservicesapp.data.geoawareness.source.BundledGeoZoneDataSource
import com.example.droneservicesapp.data.geoawareness.source.GeoZoneRawDataset
import com.example.droneservicesapp.data.geoawareness.source.ImportedGeoZoneDataSource
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetSourceType
import com.example.droneservicesapp.domain.geoawareness.GeoZoneGeometry
import com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneDatasetValidator
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
class GeoZoneRepository(
    private val assetDataSource: GeoZoneAssetDataSource,
    private val importedFileDataSource: GeoZoneImportedFileDataSource? = null
) {
    private val parser = GeoZoneJsonParser()
    private val bundledSource = BundledGeoZoneDataSource(assetDataSource)
    private val importedSource = importedFileDataSource?.let { ImportedGeoZoneDataSource(it) }

    fun loadDummyRethymnoZones(): List<GeoZone> {
        val rawJson = bundledSource.loadDatasets().first().rawJson
        val zones = parser.parse(rawJson)
        Log.d(TAG, "Loaded ${zones.size} dummy geo-awareness zones")
        return zones
    }

    fun loadDummyRethymnoDataset(): GeoZoneLoadResult {
        return buildLoadResultFromRawDatasets(bundledSource.loadDatasets())
    }

    fun loadCurrentDataset(): GeoZoneLoadResult {
        val source = importedSource
        return if (source != null && source.hasData()) {
            buildLoadResultFromRawDatasets(
                rawDatasets = source.loadDatasets(),
                combinedTitle = "Combined geo-awareness datasets"
            )
        } else {
            loadDummyRethymnoDataset()
        }
    }

    fun importDataset(rawJson: String): GeoZoneLoadResult {
        val preview = buildLoadResultFromRawDatasets(
            rawDatasets = listOf(
                GeoZoneRawDataset(
                    datasetId = "import-preview",
                    displayName = "Imported file",
                    rawJson = rawJson,
                    sourceType = GeoZoneDatasetSourceType.IMPORTED_FILE,
                    storageFileName = null,
                    importedAtMillis = System.currentTimeMillis()
                )
            )
        )
        val previewRecord = preview.datasetRecords.firstOrNull()
        val previewValidation = previewRecord?.validationResult ?: preview.validationResult
        if (previewValidation.hasErrors) {
            throw GeoZoneDatasetValidationException(
                validationResult = previewValidation,
                message = "Imported geo-zone dataset failed validation."
            )
        }
        val source = importedFileDataSource
            ?: throw IllegalStateException("Imported geo-zone storage is unavailable.")
        source.saveImportedDataset(rawJson, suggestedName = preview.datasetInfo.title)
        return loadCurrentDataset()
    }

    fun removeImportedDataset(storageFileName: String): GeoZoneLoadResult {
        val source = importedFileDataSource
            ?: throw IllegalStateException("Imported geo-zone storage is unavailable.")
        if (!source.deleteImportedDataset(storageFileName)) {
            throw IllegalStateException("Failed to remove imported geo-zone dataset: $storageFileName")
        }
        return loadCurrentDataset()
    }

    fun resetToBundledDataset(): GeoZoneLoadResult {
        importedFileDataSource?.deleteAllImportedDatasets()
        return loadDummyRethymnoDataset()
    }

    fun hasImportedDatasets(): Boolean = importedFileDataSource?.hasImportedDatasets() == true

    private fun buildLoadResultFromRawDatasets(
        rawDatasets: List<GeoZoneRawDataset>,
        combinedTitle: String? = null
    ): GeoZoneLoadResult {
        val validResults = mutableListOf<SingleDatasetLoad>()
        rawDatasets.forEach { rawDataset ->
            runCatching { buildSingleDatasetLoad(rawDataset) }
                .onSuccess { single ->
                    if (single.validationResult.hasErrors) {
                        Log.w(
                            TAG,
                            "Skipping dataset with validation errors sourceType=${rawDataset.sourceType} datasetId=${rawDataset.datasetId} storageFileName=${rawDataset.storageFileName}"
                        )
                    } else {
                        validResults += single
                    }
                }
                .onFailure { error ->
                    Log.w(
                        TAG,
                        "Skipping unreadable dataset sourceType=${rawDataset.sourceType} datasetId=${rawDataset.datasetId} storageFileName=${rawDataset.storageFileName}",
                        error
                    )
                }
        }

        if (validResults.isEmpty()) {
            throw IllegalStateException("No valid geo-zone datasets could be loaded.")
        }

        if (validResults.size == 1) {
            val single = validResults.first()
            return GeoZoneLoadResult(
                datasetInfo = single.datasetInfo,
                zones = single.zones,
                validationResult = single.validationResult,
                datasetRecords = listOf(single.record)
            )
        }

        return buildCombinedResult(validResults, combinedTitle)
    }

    private fun buildSingleDatasetLoad(rawDataset: GeoZoneRawDataset): SingleDatasetLoad {
        val loadedAtMillis = System.currentTimeMillis()
        val zones = parser.parse(rawDataset.rawJson)
        val datasetInfo = parser.parseDatasetInfo(
            rawJson = rawDataset.rawJson,
            zones = zones,
            loadedAtMillis = loadedAtMillis,
            fallbackSource = when (rawDataset.sourceType) {
                GeoZoneDatasetSourceType.BUNDLED_ASSET -> "Bundled asset"
                GeoZoneDatasetSourceType.IMPORTED_FILE -> "Imported file"
            },
            fallbackSourceUrl = when (rawDataset.sourceType) {
                GeoZoneDatasetSourceType.BUNDLED_ASSET -> "https://dagr.hasp.gov.gr/"
                GeoZoneDatasetSourceType.IMPORTED_FILE -> null
            }
        )
        val validationResult = GeoZoneDatasetValidator.validate(
            rawJson = rawDataset.rawJson,
            datasetInfo = datasetInfo,
            zones = zones
        )
        val record = GeoZoneDatasetRecord(
            datasetId = rawDataset.datasetId,
            displayName = datasetInfo.title,
            storageFileName = rawDataset.storageFileName,
            sourceType = rawDataset.sourceType,
            datasetInfo = datasetInfo,
            validationResult = validationResult,
            zoneCount = zones.size,
            importedAtMillis = rawDataset.importedAtMillis
        )
        logLoad(datasetInfo, validationResult)
        return SingleDatasetLoad(
            datasetInfo = datasetInfo,
            zones = zones,
            validationResult = validationResult,
            record = record
        )
    }

    private fun buildCombinedResult(
        results: List<SingleDatasetLoad>,
        combinedTitle: String? = null
    ): GeoZoneLoadResult {
        val zones = results.flatMap { it.zones }
        val combinedValidation = GeoZoneValidationResult.combine(results.map { it.validationResult })
        val datasetRecords = results.map { it.record }
        val countries = results.mapNotNull { it.datasetInfo.country?.takeIf(String::isNotBlank) }.distinct()
        val loadedAtMillis = System.currentTimeMillis()
        val circleCount = results.sumOf { it.datasetInfo.circleGeometryCount }
        val polygonCount = results.sumOf { it.datasetInfo.polygonGeometryCount }
        val combinedInfo = GeoZoneDatasetInfo(
            title = combinedTitle ?: "Combined geo-awareness datasets",
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
