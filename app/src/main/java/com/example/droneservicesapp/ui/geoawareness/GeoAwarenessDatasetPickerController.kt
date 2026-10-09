package com.example.droneservicesapp.ui.geoawareness

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord

/** Owns document-picker intent state and guarantees that an update target cannot become an import. */
class GeoAwarenessDatasetPickerController(
    private val fragment: Fragment,
    private val onSelection: (Selection) -> Unit,
) {
    enum class Mode { IMPORT_NEW, UPDATE_EXISTING }

    data class Selection(
        val mode: Mode,
        val uri: Uri,
        val originalFileName: String?,
        val updateStorageFileName: String? = null,
    )

    private data class PendingRequest(val mode: Mode, val updateStorageFileName: String? = null)

    private var pending: PendingRequest? = null
    private val launcher = fragment.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val request = pending
        pending = null
        if (uri == null || request == null) return@registerForActivityResult
        if (request.mode == Mode.UPDATE_EXISTING && request.updateStorageFileName.isNullOrBlank()) return@registerForActivityResult
        onSelection(
            Selection(
                mode = request.mode,
                uri = uri,
                originalFileName = resolveDisplayName(uri),
                updateStorageFileName = request.updateStorageFileName,
            ),
        )
    }

    fun launchImport() {
        pending = PendingRequest(Mode.IMPORT_NEW)
        launcher.launch(MIME_TYPES)
    }

    fun launchUpdate(record: GeoZoneDatasetRecord) {
        val storageFileName = record.storageFileName ?: return
        pending = PendingRequest(Mode.UPDATE_EXISTING, storageFileName)
        launcher.launch(MIME_TYPES)
    }

    fun clear() {
        pending = null
    }

    private fun resolveDisplayName(uri: Uri): String? {
        val resolver = fragment.context?.contentResolver ?: return null
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))?.takeIf(String::isNotBlank)
            } else null
        }
    }

    private companion object {
        val MIME_TYPES = arrayOf("application/json", "text/json", "text/plain", "*/*")
    }
}
