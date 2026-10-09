package com.example.droneservicesapp.ui.geoawareness

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.droneservicesapp.data.geoawareness.GeoZoneRepository
import com.example.droneservicesapp.data.geoawareness.evidence.GeoAwarenessEvidencePackageExporter
import com.example.droneservicesapp.data.geoawareness.incident.GeoIncidentEncryptedLogStore
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.data.geoawareness.verification.GeoAwarenessVerificationStatusStore
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.testing.GeoAwarenessTestRunResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Generates export files and content-only share intents without retaining fragment views. */
class GeoAwarenessExportController(
    context: Context,
    private val scope: CoroutineScope,
    private val eventLogger: GeoAwarenessEventLogger,
    private val repositoryProvider: () -> GeoZoneRepository,
    private val verificationStoreProvider: () -> GeoAwarenessVerificationStatusStore,
    private val diagnosticsProvider: () -> GeoAwarenessTestRunResult?,
    private val datasetInfoProvider: () -> GeoZoneDatasetInfo?,
    private val healthProvider: () -> GeoAwarenessHealth?,
    private val callbacks: Callbacks,
) {
    enum class ExportKind { STANDARD_LOGS, EVIDENCE_PACKAGE, ENCRYPTED_INCIDENTS }

    data class ExportFailure(
        val kind: ExportKind,
        val title: String,
        val message: String,
        val cause: Throwable,
    )

    data class Callbacks(
        val launchChooser: (Intent) -> Unit,
        val onFailure: (ExportFailure) -> Unit,
        val onNoEncryptedIncidents: () -> Unit,
        val onAuditLogChanged: () -> Unit,
    )

    private val appContext = context.applicationContext
    private val authority = "${appContext.packageName}.fileprovider"
    private var exportJob: Job? = null
    private var active = true

    fun exportStandardLogs() = launchExport(ExportKind.STANDARD_LOGS) {
        val file = eventLogger.exportLogsToJson()
        singleShare(
            uri = contentUri(file),
            mimeType = "application/json",
            subject = "Geo-awareness event logs",
            body = "Geo-awareness event log export",
            chooserTitle = "Share geo-awareness logs",
        )
    }

    fun exportEvidencePackage() = launchExport(ExportKind.EVIDENCE_PACKAGE) {
        val file = GeoAwarenessEvidencePackageExporter(
            context = appContext,
            eventLogger = eventLogger,
            repository = repositoryProvider(),
            verificationStatusStore = verificationStoreProvider(),
            latestDiagnosticsResultProvider = diagnosticsProvider,
        ).exportEvidencePackage()
        eventLogger.logSimple(
            type = GeoAwarenessEventType.EVIDENCE_PACKAGE_EXPORTED,
            severity = "INFO",
            message = "Geo-awareness evidence package exported",
            category = "GEO",
            datasetTitle = datasetInfoProvider()?.title,
            datasetVersion = datasetInfoProvider()?.version,
            healthState = healthProvider()?.state?.name,
            details = mapOf("fileName" to file.name, "fileSizeBytes" to file.length().toString()),
        )
        singleShare(
            uri = contentUri(file),
            mimeType = "application/zip",
            subject = "Geo-awareness evidence package",
            body = "Geo-awareness evidence package export",
            chooserTitle = "Share geo-awareness evidence package",
        )
    }

    fun exportEncryptedIncidents() = launchExport(ExportKind.ENCRYPTED_INCIDENTS) {
        val files = GeoIncidentEncryptedLogStore(appContext).getEncryptedLogFiles()
        if (files.isEmpty()) return@launchExport null
        val uris = ArrayList(files.map(::contentUri))
        Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/octet-stream"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "Encrypted geo incident logs")
            putExtra(Intent.EXTRA_TEXT, "Encrypted geo incident logs export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share encrypted geo incident logs")
    }

    fun clear() {
        active = false
        exportJob?.cancel()
        exportJob = null
    }

    private fun launchExport(kind: ExportKind, buildIntent: suspend () -> Intent?) {
        if (!active || exportJob?.isActive == true) return
        exportJob = scope.launch {
            try {
                val chooser = withContext(Dispatchers.IO) { buildIntent() }
                if (!active) return@launch
                if (chooser == null) callbacks.onNoEncryptedIncidents() else callbacks.launchChooser(chooser)
                callbacks.onAuditLogChanged()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!active) return@launch
                if (kind == ExportKind.EVIDENCE_PACKAGE) logEvidenceFailure(error)
                callbacks.onFailure(failure(kind, error))
                callbacks.onAuditLogChanged()
            } finally {
                exportJob = null
            }
        }
    }

    private fun singleShare(uri: Uri, mimeType: String, subject: String, body: String, chooserTitle: String): Intent {
        return Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, chooserTitle)
    }

    private fun contentUri(file: java.io.File): Uri = FileProvider.getUriForFile(appContext, authority, file)

    private fun logEvidenceFailure(error: Throwable) {
        eventLogger.logSimple(
            type = GeoAwarenessEventType.EVIDENCE_PACKAGE_EXPORT_FAILED,
            severity = "ERROR",
            message = "Geo-awareness evidence package export failed",
            category = "GEO",
            datasetTitle = datasetInfoProvider()?.title,
            datasetVersion = datasetInfoProvider()?.version,
            healthState = healthProvider()?.state?.name,
            details = mapOf("error" to (error.message ?: error::class.java.simpleName)),
        )
    }

    private fun failure(kind: ExportKind, error: Throwable): ExportFailure = when (kind) {
        ExportKind.STANDARD_LOGS -> ExportFailure(kind, "Export geo-awareness logs", "Failed to share geo-awareness logs.", error)
        ExportKind.EVIDENCE_PACKAGE -> ExportFailure(kind, "Export evidence package", "Failed to export evidence package.", error)
        ExportKind.ENCRYPTED_INCIDENTS -> ExportFailure(kind, "Export encrypted geo incident logs", "Failed to export encrypted geo incident logs.", error)
    }
}
