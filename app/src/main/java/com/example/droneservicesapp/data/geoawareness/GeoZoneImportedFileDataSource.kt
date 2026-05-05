package com.example.droneservicesapp.data.geoawareness

import android.content.Context
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

    fun saveImportedDataset(rawJson: String, suggestedName: String? = null): File {
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
            return file
        } catch (error: Exception) {
            throw IllegalStateException("Failed to save imported geo-zone dataset.", error)
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
            !target.exists() || target.delete()
        } catch (error: Exception) {
            throw IllegalStateException("Failed to delete imported geo-zone dataset: $fileName", error)
        }
    }

    fun deleteAllImportedDatasets(): Int {
        var removed = 0
        listImportedDatasetFiles().forEach { file ->
            if (file.delete()) {
                removed += 1
            }
        }
        return removed
    }

    fun getImportedDatasetsDir(): File {
        return File(File(context.filesDir, IMPORT_ROOT_DIR), DATASETS_DIR)
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
