package com.example.droneservicesapp.ui.geoawareness

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.example.droneservicesapp.R

/** Owns readable log previews and normalized export error presentation. */
class GeoAwarenessLogDialogController(private val context: Context) {
    private var dialog: AlertDialog? = null

    fun showDetailedPreview(message: String) = readable("Detailed geo-awareness logs", message)

    fun showNoEncryptedIncidents() = readable(
        "Export encrypted geo incident logs",
        context.getString(R.string.geo_awareness_no_encrypted_incidents),
    )

    fun showExportFailure(failure: GeoAwarenessExportController.ExportFailure) {
        readable(failure.title, "${failure.message}\n\n${failure.cause.message ?: "Unknown error"}")
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    private fun readable(title: String, message: String) {
        dialog?.dismiss()
        dialog = AlertDialog.Builder(context, R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(title).setMessage(message).setPositiveButton(android.R.string.ok, null).show()
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
    }
}
