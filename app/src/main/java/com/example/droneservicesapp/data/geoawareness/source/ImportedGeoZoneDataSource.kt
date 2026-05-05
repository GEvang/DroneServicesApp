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
            GeoZoneRawDataset(
                datasetId = file.nameWithoutExtension,
                displayName = file.nameWithoutExtension,
                rawJson = importedFileDataSource.loadRawJson(file),
                sourceType = sourceType,
                storageFileName = file.name,
                importedAtMillis = file.lastModified().takeIf { it > 0L }
            )
        }
    }
}
