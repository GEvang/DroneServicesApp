package com.example.droneservicesapp.ui.geoawareness

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationSeverity

/** Owns confirmations and readable user feedback for dataset operations. */
class GeoAwarenessDatasetDialogController(private val context: Context) {
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
            failureSummary(error, validation, R.string.geo_import_failed_message),
        )
    }

    fun showUpdateFailure(error: Throwable, validation: GeoZoneValidationResult?) {
        readable(
            context.getString(R.string.geo_update_failed),
            failureSummary(error, validation, R.string.geo_update_failed_message),
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

    fun formatValidationDetails(result: GeoZoneValidationResult): String {
        if (result.issues.isEmpty()) return context.getString(R.string.geo_dataset_validation_passed)
        val visibleIssues = result.issues.take(30)
        val remaining = result.issues.size - visibleIssues.size
        return buildString {
            GeoZoneValidationSeverity.values().forEach { severity ->
                val issues = visibleIssues.filter { it.severity == severity }
                if (issues.isEmpty()) return@forEach
                appendLine(severity.name)
                issues.forEach { issue ->
                    append("- [${issue.code}] ${issue.message}")
                    if (!issue.zoneId.isNullOrBlank()) {
                        append(" (zoneId=${issue.zoneId}")
                        if (!issue.field.isNullOrBlank()) append(", field=${issue.field}")
                        append(")")
                    } else if (!issue.field.isNullOrBlank()) append(" (field=${issue.field})")
                    appendLine()
                }
                appendLine()
            }
            if (remaining > 0) append("...and $remaining more.")
        }
    }

    private fun failureSummary(error: Throwable, result: GeoZoneValidationResult?, fallback: Int): String {
        val issueLines = result?.issues?.filter { it.severity == GeoZoneValidationSeverity.ERROR }?.take(10)
            ?.joinToString("\n") { "- [${it.code}] ${it.message}" }.orEmpty()
        return buildString {
            appendLine(error.message ?: context.getString(fallback))
            result?.let {
                appendLine()
                appendLine(context.getString(R.string.geo_validation_counts, it.errorCount, it.warningCount, it.infoCount))
            }
            if (issueLines.isNotBlank()) {
                appendLine()
                append(issueLines)
            }
        }.trim()
    }

    private fun confirmation(title: Int, message: String, positive: Int, onConfirm: () -> Unit) {
        val dialog = AlertDialog.Builder(context, R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(title).setMessage(message).setPositiveButton(positive) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
    }
}
