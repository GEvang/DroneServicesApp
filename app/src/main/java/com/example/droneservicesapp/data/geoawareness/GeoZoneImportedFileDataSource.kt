package com.example.droneservicesapp.data.geoawareness

import android.content.Context
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetLocalMetadata
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetSourceType
import org.json.JSONObject
import java.io.File
import java.util.UUID

class GeoZoneImportedFileDataSource(private val context: Context) {

    fun listImportedDatasetFiles(): List<File> {
        migrateLegacyImportedDatasetIfNeeded()
        val dir = getImportedDatasetsDir()
        if (!dir.exists()) {
            return emptyList()
        }
        return dir.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    fun hasImportedDatasets(): Boolean = listImportedDatasetFiles().isNotEmpty()

    fun saveImportedDataset(
        rawJson: String,
        suggestedName: String? = null,
        originalFileName: String? = null
    ): File {
        try {
            val dir = getImportedDatasetsDir()
            dir.mkdirs()
            val datasetId = UUID.randomUUID().toString()
            val safePrefix = suggestedName
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
                ?.take(40)
            val fileName = if (safePrefix.isNullOrBlank()) {
                "$datasetId.json"
            } else {
                "${safePrefix}_$datasetId.json"
            }
            val file = File(dir, fileName)
            file.writeText(rawJson, Charsets.UTF_8)
            val now = System.currentTimeMillis()
            writeMetadata(
                GeoZoneDatasetLocalMetadata(
                    datasetId = file.nameWithoutExtension,
                    originalFileName = originalFileName,
                    storageFileName = file.name,
                    importedAtMillis = now,
                    updatedAtMillis = now,
                    lastValidatedAtMillis = now,
                    sourceType = GeoZoneDatasetSourceType.IMPORTED_FILE
                )
            )
            return file
        } catch (error: Exception) {
            throw IllegalStateException("Failed to save imported geo-zone dataset.", error)
        }
    }

    fun updateImportedDataset(
        storageFileName: String,
        rawJson: String,
        originalFileName: String? = null
    ): File {
        try {
            val file = File(getImportedDatasetsDir(), storageFileName)
            if (!file.exists()) {
                throw IllegalStateException("Imported geo-zone dataset file is missing: $storageFileName")
            }
            val previousMetadata = readMetadata(storageFileName)
            file.writeText(rawJson, Charsets.UTF_8)
            val now = System.currentTimeMillis()
            writeMetadata(
                GeoZoneDatasetLocalMetadata(
                    datasetId = previousMetadata?.datasetId ?: file.nameWithoutExtension,
                    originalFileName = originalFileName ?: previousMetadata?.originalFileName,
                    storageFileName = file.name,
                    importedAtMillis = previousMetadata?.importedAtMillis ?: (file.lastModified().takeIf { it > 0L } ?: now),
                    updatedAtMillis = now,
                    lastValidatedAtMillis = now,
                    sourceType = GeoZoneDatasetSourceType.IMPORTED_FILE
                )
            )
            return file
        } catch (error: Exception) {
            throw IllegalStateException("Failed to update imported geo-zone dataset: $storageFileName", error)
        }
    }

    fun loadRawJson(file: File): String {
        if (!file.exists()) {
            throw IllegalStateException("Imported geo-zone dataset file is missing: ${file.name}")
        }
        return try {
            file.readText(Charsets.UTF_8)
        } catch (error: Exception) {
            throw IllegalStateException("Failed to read imported geo-zone dataset: ${file.name}", error)
        }
    }

    fun deleteImportedDataset(fileName: String): Boolean {
        return try {
            val target = File(getImportedDatasetsDir(), fileName)
            val deleted = !target.exists() || target.delete()
            deleteMetadata(fileName)
            deleted
        } catch (error: Exception) {
            throw IllegalStateException("Failed to delete imported geo-zone dataset: $fileName", error)
        }
    }

    fun deleteAllImportedDatasets(): Int {
        var removed = 0
        listImportedDatasetFiles().forEach { file ->
            if (file.delete()) {
                deleteMetadata(file.name)
                removed += 1
            }
        }
        return removed
    }

    fun getImportedDatasetsDir(): File {
        return File(File(context.filesDir, IMPORT_ROOT_DIR), DATASETS_DIR)
    }

    fun readMetadata(storageFileName: String): GeoZoneDatasetLocalMetadata? {
        val file = File(getImportedDatasetsDir(), storageFileName)
        val metadataFile = getMetadataFileFor(storageFileName)
        if (metadataFile.exists()) {
            return try {
                val root = JSONObject(metadataFile.readText(Charsets.UTF_8))
                GeoZoneDatasetLocalMetadata(
                    datasetId = root.optString("datasetId").ifBlank { file.nameWithoutExtension },
                    originalFileName = root.optString("originalFileName").takeIf { it.isNotBlank() },
                    storageFileName = root.optString("storageFileName").ifBlank { storageFileName },
                    importedAtMillis = root.optLong("importedAtMillis").takeIf { it > 0L }
                        ?: (file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()),
                    updatedAtMillis = root.optLong("updatedAtMillis").takeIf { it > 0L }
                        ?: (file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()),
                    lastValidatedAtMillis = root.optLong("lastValidatedAtMillis").takeIf { it > 0L },
                    sourceType = runCatching {
                        GeoZoneDatasetSourceType.valueOf(root.optString("sourceType"))
                    }.getOrDefault(GeoZoneDatasetSourceType.IMPORTED_FILE)
                )
            } catch (error: Exception) {
                throw IllegalStateException("Failed to read imported geo-zone metadata: $storageFileName", error)
            }
        }

        if (!file.exists()) {
            return null
        }
        val fallbackTime = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
        return GeoZoneDatasetLocalMetadata(
            datasetId = file.nameWithoutExtension,
            originalFileName = file.name,
            storageFileName = file.name,
            importedAtMillis = fallbackTime,
            updatedAtMillis = fallbackTime,
            lastValidatedAtMillis = null,
            sourceType = GeoZoneDatasetSourceType.IMPORTED_FILE
        )
    }

    fun writeMetadata(metadata: GeoZoneDatasetLocalMetadata) {
        try {
            val metadataFile = getMetadataFileFor(metadata.storageFileName)
            metadataFile.parentFile?.mkdirs()
            val root = JSONObject().apply {
                put("datasetId", metadata.datasetId)
                put("originalFileName", metadata.originalFileName)
                put("storageFileName", metadata.storageFileName)
                put("importedAtMillis", metadata.importedAtMillis)
                put("updatedAtMillis", metadata.updatedAtMillis)
                put("lastValidatedAtMillis", metadata.lastValidatedAtMillis)
                put("sourceType", metadata.sourceType.name)
            }
            metadataFile.writeText(root.toString(2), Charsets.UTF_8)
        } catch (error: Exception) {
            throw IllegalStateException("Failed to write imported geo-zone metadata: ${metadata.storageFileName}", error)
        }
    }

    fun deleteMetadata(storageFileName: String) {
        val metadataFile = getMetadataFileFor(storageFileName)
        if (metadataFile.exists()) {
            metadataFile.delete()
        }
    }

    fun getMetadataFileFor(storageFileName: String): File {
        val metadataFileName = storageFileName.substringBeforeLast('.') + ".meta.json"
        return File(getImportedDatasetsDir(), metadataFileName)
    }

    private fun migrateLegacyImportedDatasetIfNeeded() {
        val legacyFile = File(File(context.filesDir, IMPORT_ROOT_DIR), LEGACY_IMPORTED_FILE)
        val datasetsDir = getImportedDatasetsDir()
        if (!legacyFile.exists()) {
            return
        }
        val hasNewFiles = datasetsDir.exists() &&
            (datasetsDir.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }?.isNotEmpty() == true)
        if (hasNewFiles) {
            return
        }
        try {
            datasetsDir.mkdirs()
            val migratedFile = File(datasetsDir, "${UUID.randomUUID()}.json")
            legacyFile.copyTo(migratedFile, overwrite = false)
            val migratedAtMillis = legacyFile.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
            writeMetadata(
                GeoZoneDatasetLocalMetadata(
                    datasetId = migratedFile.nameWithoutExtension,
                    originalFileName = legacyFile.name,
                    storageFileName = migratedFile.name,
                    importedAtMillis = migratedAtMillis,
                    updatedAtMillis = migratedAtMillis,
                    lastValidatedAtMillis = null,
                    sourceType = GeoZoneDatasetSourceType.IMPORTED_FILE
                )
            )
            legacyFile.delete()
        } catch (error: Exception) {
            throw IllegalStateException("Failed to migrate previously imported geo-zone dataset.", error)
        }
    }

    companion object {
        private const val IMPORT_ROOT_DIR = "geo_awareness/imported"
        private const val DATASETS_DIR = "datasets"
        private const val LEGACY_IMPORTED_FILE = "current_geozones.json"
    }
}
