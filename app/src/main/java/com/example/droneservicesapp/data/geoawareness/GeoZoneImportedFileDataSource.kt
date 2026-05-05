package com.example.droneservicesapp.data.geoawareness

import android.content.Context
import java.io.File

class GeoZoneImportedFileDataSource(private val context: Context) {

    fun hasImportedDataset(): Boolean = getImportedFile().exists()

    fun saveImportedDataset(rawJson: String): File {
        try {
            val file = getImportedFile()
            file.parentFile?.mkdirs()
            file.writeText(rawJson, Charsets.UTF_8)
            return file
        } catch (error: Exception) {
            throw IllegalStateException("Failed to save imported geo-zone dataset.", error)
        }
    }

    fun loadImportedRawJson(): String? {
        val file = getImportedFile()
        if (!file.exists()) {
            return null
        }
        return try {
            file.readText(Charsets.UTF_8)
        } catch (error: Exception) {
            throw IllegalStateException("Failed to read imported geo-zone dataset.", error)
        }
    }

    fun deleteImportedDataset(): Boolean {
        val file = getImportedFile()
        return try {
            !file.exists() || file.delete()
        } catch (error: Exception) {
            throw IllegalStateException("Failed to delete imported geo-zone dataset.", error)
        }
    }

    fun getImportedFile(): File {
        return File(File(context.filesDir, IMPORT_DIR), CURRENT_IMPORTED_FILE)
    }

    companion object {
        private const val IMPORT_DIR = "geo_awareness/imported"
        private const val CURRENT_IMPORTED_FILE = "current_geozones.json"
    }
}
