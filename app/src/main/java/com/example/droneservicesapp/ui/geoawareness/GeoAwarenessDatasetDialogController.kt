package com.example.droneservicesapp.ui.geoawareness

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult

/** Owns confirmations and readable user feedback for dataset operations. */
class GeoAwarenessDatasetDialogController(
    private val context: Context,
    private val validationPresenter: GeoAwarenessValidationPresenter,
) {
    fun confirmRemoveAll(onConfirm: () -> Unit) {
        confirmation(R.string.geo_remove_all_title, context.getString(R.string.geo_remove_all_message), R.string.geo_remove_all, onConfirm)
    }

    fun confirmRemove(record: GeoZoneDatasetRecord, onConfirm: () -> Unit) {
        confirmation(
            R.string.geo_remove_one_title,
            context.getString(R.string.geo_remove_one_message, record.displayName),
            R.string.geo_awareness_remove_dataset,
            onConfirm,
        )
    }

    fun showImportSuccess(result: GeoZoneLoadResult) {
        val warningSuffix = if (result.validationResult.warningCount > 0) {
            "\nValidation warnings: ${result.validationResult.warningCount}"
        } else ""
        readable(
            "Import complete",
            "${context.getString(R.string.geo_awareness_import_success)}\n\nZones loaded: ${result.datasetInfo.zoneCount}$warningSuffix",
        )
    }

    fun showUpdateSuccess() {
        Toast.makeText(context, R.string.geo_dataset_updated, Toast.LENGTH_SHORT).show()
    }

    fun showRemoveAllSuccess() {
        Toast.makeText(context, R.string.geo_imported_removed, Toast.LENGTH_SHORT).show()
    }

    fun showImportFailure(error: Throwable, validation: GeoZoneValidationResult?) {
        readable(
            context.getString(R.string.geo_import_failed),
            validationPresenter.failureSummary(error, validation, R.string.geo_import_failed_message),
        )
    }

    fun showUpdateFailure(error: Throwable, validation: GeoZoneValidationResult?) {
        readable(
            context.getString(R.string.geo_update_failed),
            validationPresenter.failureSummary(error, validation, R.string.geo_update_failed_message),
        )
    }

    fun showRemoveFailure(removeAll: Boolean, error: Throwable) {
        readable(
            context.getString(R.string.geo_remove_failed),
            error.message ?: context.getString(if (removeAll) R.string.geo_remove_all_failed else R.string.geo_remove_one_failed),
        )
    }

    fun readable(title: String, message: String) {
        val dialog = AlertDialog.Builder(context, R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(title).setMessage(message).setPositiveButton(android.R.string.ok, null).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
    }

    private fun confirmation(title: Int, message: String, positive: Int, onConfirm: () -> Unit) {
        val dialog = AlertDialog.Builder(context, R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(title).setMessage(message).setPositiveButton(positive) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
    }
}
