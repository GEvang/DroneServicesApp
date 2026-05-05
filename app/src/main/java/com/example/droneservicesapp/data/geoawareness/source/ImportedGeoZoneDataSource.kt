package com.example.droneservicesapp.data.geoawareness.source

import com.example.droneservicesapp.data.geoawareness.GeoZoneImportedFileDataSource
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetSourceType

class ImportedGeoZoneDataSource(
    private val importedFileDataSource: GeoZoneImportedFileDataSource
) : GeoZoneDataSource {

    override val sourceType: GeoZoneDatasetSourceType = GeoZoneDatasetSourceType.IMPORTED_FILE
    override val displayName: String = "Imported geo-zone datasets"

    override fun hasData(): Boolean = importedFileDataSource.hasImportedDatasets()

    override fun loadDatasets(): List<GeoZoneRawDataset> {
        return importedFileDataSource.listImportedDatasetFiles().map { file ->
            val metadata = importedFileDataSource.readMetadata(file.name)
            GeoZoneRawDataset(
                datasetId = metadata?.datasetId ?: file.nameWithoutExtension,
                displayName = metadata?.originalFileName ?: file.nameWithoutExtension,
                rawJson = importedFileDataSource.loadRawJson(file),
                sourceType = sourceType,
                storageFileName = file.name,
                originalFileName = metadata?.originalFileName,
                updatedAtMillis = metadata?.updatedAtMillis ?: file.lastModified().takeIf { it > 0L },
                importedAtMillis = metadata?.importedAtMillis ?: file.lastModified().takeIf { it > 0L }
            )
        }
    }
}
