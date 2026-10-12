package com.example.droneservicesapp.ui.geoawareness

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import com.example.droneservicesapp.domain.geoawareness.verification.GeoAwarenessVerificationCase
import com.example.droneservicesapp.domain.geoawareness.verification.GeoAwarenessVerificationStatus
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Configuration-safe owner for the complete Geo-awareness screen workflow. */
class GeoAwarenessViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val session = GeoAwarenessSessionState()
    private val logger = GeoAwarenessEventLogger(application)
    private val auditFormatter = GeoAwarenessDatasetAuditFormatter()
    private val validationFormatter = GeoAwarenessValidationPresenter(application)
    private val liveFormatter = GeoAwarenessLiveStatusFormatter(application)
    private val observer = GeoAwarenessObserverCoordinator()
    private val telemetryObserver = GeoAwarenessTelemetryObserver(session)
    private val mutableState = MutableStateFlow(GeoAwarenessUiState())
    private val effectChannel = Channel<GeoAwarenessEffect>(Channel.BUFFERED)

    private var initialized = false
    private var sharedState: MainActivityViewModel? = null
    private var datasetState: GeoAwarenessDatasetStateCoordinator? = null
    private var workflow: GeoAwarenessDatasetWorkflowController? = null
    private var liveController: GeoAwarenessLiveStatusController? = null
    private var verificationController: GeoAwarenessVerificationController? = null
    private var exportController: GeoAwarenessExportController? = null

    val uiState = mutableState.asStateFlow()
    val effects = effectChannel.receiveAsFlow()

    fun initialize(activityState: MainActivityViewModel, droneState: DroneViewModel) {
        if (initialized) return
        initialized = true
        sharedState = activityState

        val live = GeoAwarenessLiveStatusController(
            scope = viewModelScope,
            eventLogger = logger,
            onState = { status -> mutableState.value = mutableState.value.copy(liveStatus = status) },
            onAuditLogChanged = ::refreshEvents,
        )
        liveController = live

        var workflowReference: GeoAwarenessDatasetWorkflowController? = null
        val stateCoordinator = GeoAwarenessDatasetStateCoordinator(
            context = app,
            scope = viewModelScope,
            sharedState = activityState,
            eventLogger = logger,
            session = session,
            callbacks = GeoAwarenessDatasetStateCoordinator.Callbacks(
                onSnapshot = { snapshot ->
                    session.apply(snapshot)
                    mutableState.value = mutableState.value.copy(
                        importedDatasetActive = snapshot.importedActive,
                        datasetInfo = snapshot.datasetInfo,
                        datasetRecords = snapshot.records,
                        validation = snapshot.validation ?: GeoZoneValidationResult.ok(),
                        health = snapshot.health,
                        errorMessage = snapshot.loadError?.message,
                    )
                    live.updateDataset(snapshot)
                },
                onBusyChanged = { busy, message ->
                    if (workflowReference?.isBusy != true) setBusy(busy, message)
                },
                onRefreshSucceeded = ::onRefreshSucceeded,
                onFailure = { error, manual ->
                    if (manual) showMessage(
                        app.getString(R.string.geo_refresh_failed),
                        error.message ?: app.getString(R.string.geo_refresh_failed_message),
                    )
                },
                onAuditLogChanged = ::refreshEvents,
            ),
        )
        datasetState = stateCoordinator

        val datasetWorkflow = GeoAwarenessDatasetWorkflowController(
            context = app,
            scope = viewModelScope,
            stateCoordinator = stateCoordinator,
            logger = logger,
            auditFormatter = auditFormatter,
            currentRecords = { mutableState.value.datasetRecords },
            currentHealth = { mutableState.value.health },
            callbacks = GeoAwarenessDatasetWorkflowController.Callbacks(
                onBusyChanged = ::setBusy,
                onAuditLogChanged = ::refreshEvents,
                onOperationResult = ::onOperationResult,
            ),
        )
        workflowReference = datasetWorkflow
        workflow = datasetWorkflow

        val verification = GeoAwarenessVerificationController(
            context = app,
            scope = viewModelScope,
            repositoryProvider = datasetWorkflow::repositoryForEvidenceExport,
            eventLogger = logger,
            session = session,
            onAuditLogChanged = ::refreshEvents,
        )
        verification.setStateListener { value ->
            mutableState.value = mutableState.value.copy(verification = value)
        }
        verificationController = verification

        exportController = GeoAwarenessExportController(
            context = app,
            scope = viewModelScope,
            eventLogger = logger,
            repositoryProvider = datasetWorkflow::repositoryForEvidenceExport,
            verificationStoreProvider = verification::statusStoreForEvidenceExport,
            diagnosticsProvider = { verification.lastTestRunResult },
            datasetInfoProvider = { mutableState.value.datasetInfo },
            healthProvider = { mutableState.value.health },
            callbacks = GeoAwarenessExportController.Callbacks(
                launchChooser = { effect(GeoAwarenessEffect.LaunchIntent(it)) },
                onFailure = { failure -> showMessage(failure.title, "${failure.message}\n\n${failure.cause.message ?: "Unknown error"}") },
                onNoEncryptedIncidents = {
                    showMessage(
                        "Export encrypted geo incident logs",
                        app.getString(R.string.geo_awareness_no_encrypted_incidents),
                    )
                },
                onAuditLogChanged = ::refreshEvents,
            ),
        )

        observer.observe(
            activityState,
            GeoAwarenessObserverCoordinator.Callbacks(
                onOverlayVisibility = { visible -> mutableState.value = mutableState.value.copy(overlayVisible = visible) },
                onDatasetInfo = { info ->
                    session.dataset = session.dataset.copy(datasetInfo = info)
                    mutableState.value = mutableState.value.copy(datasetInfo = info)
                },
                onValidation = { result ->
                    session.dataset = session.dataset.copy(validation = result)
                    mutableState.value = mutableState.value.copy(validation = result ?: GeoZoneValidationResult.ok())
                },
                onRecords = { records ->
                    session.dataset = session.dataset.copy(records = records)
                    mutableState.value = mutableState.value.copy(datasetRecords = records)
                },
                onImportedActive = { imported ->
                    session.dataset = session.dataset.copy(importedActive = imported)
                    mutableState.value = mutableState.value.copy(importedDatasetActive = imported)
                },
                onHealth = { health ->
                    session.dataset = session.dataset.copy(health = health)
                    mutableState.value = mutableState.value.copy(health = health)
                },
                onReloadToken = stateCoordinator::handleReloadToken,
            ),
        )
        telemetryObserver.observe(droneState, live::updateTelemetry)
        refreshEvents()
        stateCoordinator.loadIfNeeded()
    }

    fun dispatch(action: GeoAwarenessAction) {
        when (action) {
            is GeoAwarenessAction.SetOverlayVisibility -> {
                mutableState.value = mutableState.value.copy(overlayVisible = action.visible)
                sharedState?.geoAwarenessLayerVisible?.value = action.visible
            }
            GeoAwarenessAction.ImportDataset -> requestPicker(GeoAwarenessSessionState.PickerMode.IMPORT_NEW)
            is GeoAwarenessAction.UpdateDataset -> requestPicker(
                GeoAwarenessSessionState.PickerMode.UPDATE_EXISTING,
                action.record.storageFileName,
            )
            GeoAwarenessAction.RemoveAllDatasets ->
                mutableState.value = mutableState.value.copy(dialog = GeoAwarenessDialog.ConfirmRemoveAll)
            is GeoAwarenessAction.RemoveDataset ->
                mutableState.value = mutableState.value.copy(dialog = GeoAwarenessDialog.ConfirmRemove(action.record))
            GeoAwarenessAction.RefreshStatus -> if (workflow?.isBusy != true) datasetState?.refresh(manual = true)
            GeoAwarenessAction.ShowValidation -> showMessage(
                app.getString(R.string.geo_dataset_validation),
                validationFormatter.details(mutableState.value.validation),
            )
            is GeoAwarenessAction.ShowDatasetValidation -> showMessage(
                app.getString(R.string.geo_dataset_validation_named, action.record.displayName),
                validationFormatter.datasetDetails(action.record),
            )
            GeoAwarenessAction.ShowLiveStatus -> showMessage(
                liveFormatter.title(mutableState.value.liveStatus),
                liveFormatter.details(mutableState.value.liveStatus),
            )
            GeoAwarenessAction.ShowDetailedLogs -> showMessage(
                "Detailed geo-awareness logs",
                GeoAwarenessEventLogPresentationMapper.detailedPreview(mutableState.value.events),
            )
            GeoAwarenessAction.ExportLogs -> exportController?.exportStandardLogs()
            GeoAwarenessAction.ExportEvidence -> exportController?.exportEvidencePackage()
            GeoAwarenessAction.ExportEncryptedIncidents -> exportController?.exportEncryptedIncidents()
            GeoAwarenessAction.ShowVerification ->
                mutableState.value = mutableState.value.copy(dialog = GeoAwarenessDialog.Verification)
            GeoAwarenessAction.RunVerificationTests -> verificationController?.runTests()
            GeoAwarenessAction.ResetVerification ->
                mutableState.value = mutableState.value.copy(dialog = GeoAwarenessDialog.ConfirmVerificationReset)
            GeoAwarenessAction.DismissDialog -> dismissDialog()
        }
    }

    fun handlePickerResult(uri: Uri?) {
        val request = mutableState.value.pendingPicker
        mutableState.value = mutableState.value.copy(pendingPicker = null)
        if (uri == null || request == null) return
        if (request.mode == GeoAwarenessSessionState.PickerMode.UPDATE_EXISTING && request.updateStorageFileName.isNullOrBlank()) return
        runCatching {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        workflow?.handleSelection(
            GeoAwarenessDatasetSelection(
                mode = request.mode,
                uri = uri,
                originalFileName = resolveDisplayName(uri),
                updateStorageFileName = request.updateStorageFileName,
            )
        )
    }

    fun confirmDialog() {
        when (val dialog = mutableState.value.dialog) {
            GeoAwarenessDialog.ConfirmRemoveAll -> {
                dismissDialog()
                workflow?.removeAll()
            }
            is GeoAwarenessDialog.ConfirmRemove -> {
                dismissDialog()
                workflow?.remove(dialog.record)
            }
            GeoAwarenessDialog.ConfirmVerificationReset -> {
                verificationController?.reset()
                mutableState.value = mutableState.value.copy(dialog = GeoAwarenessDialog.Verification)
            }
            else -> dismissDialog()
        }
    }

    fun showVerificationCase(case: GeoAwarenessVerificationCase) {
        mutableState.value = mutableState.value.copy(dialog = GeoAwarenessDialog.VerificationCaseDetails(case))
    }

    fun returnToVerification() {
        mutableState.value = mutableState.value.copy(dialog = GeoAwarenessDialog.Verification)
    }

    fun updateVerification(case: GeoAwarenessVerificationCase, status: GeoAwarenessVerificationStatus) {
        verificationController?.updateStatus(case, status)
    }

    fun liveStatusLabel(): String = liveFormatter.label(mutableState.value.liveStatus)

    fun liveStatusTone(): GeoAwarenessStatusTone = liveFormatter.tone(mutableState.value.liveStatus)

    private fun requestPicker(mode: GeoAwarenessSessionState.PickerMode, storageFileName: String? = null) {
        if (mutableState.value.busy) return
        if (mode == GeoAwarenessSessionState.PickerMode.UPDATE_EXISTING && storageFileName.isNullOrBlank()) return
        mutableState.value = mutableState.value.copy(
            pendingPicker = GeoAwarenessPickerRequest(mode, storageFileName),
        )
        effect(GeoAwarenessEffect.LaunchFilePicker(FILE_MIME_TYPES))
    }

    private fun onOperationResult(result: GeoAwarenessDatasetWorkflowController.OperationResult) {
        when (result) {
            is GeoAwarenessDatasetWorkflowController.OperationResult.Success -> when (result.operation) {
                GeoAwarenessDatasetWorkflowController.Operation.IMPORT -> showMessage(
                    "Import complete",
                    buildString {
                        append(app.getString(R.string.geo_awareness_import_success))
                        append("\n\nZones loaded: ${result.loadResult.datasetInfo.zoneCount}")
                        val warnings = result.loadResult.validationResult.warningCount
                        if (warnings > 0) append("\nValidation warnings: $warnings")
                    },
                )
                GeoAwarenessDatasetWorkflowController.Operation.UPDATE ->
                    effect(GeoAwarenessEffect.Message(app.getString(R.string.geo_dataset_updated)))
                GeoAwarenessDatasetWorkflowController.Operation.REMOVE_ALL ->
                    effect(GeoAwarenessEffect.Message(app.getString(R.string.geo_imported_removed)))
                GeoAwarenessDatasetWorkflowController.Operation.REMOVE_ONE ->
                    effect(GeoAwarenessEffect.Message(app.getString(R.string.geo_awareness_remove_dataset)))
            }
            is GeoAwarenessDatasetWorkflowController.OperationResult.Failed -> {
                val title = when (result.operation) {
                    GeoAwarenessDatasetWorkflowController.Operation.IMPORT -> app.getString(R.string.geo_import_failed)
                    GeoAwarenessDatasetWorkflowController.Operation.UPDATE -> app.getString(R.string.geo_update_failed)
                    GeoAwarenessDatasetWorkflowController.Operation.REMOVE_ONE,
                    GeoAwarenessDatasetWorkflowController.Operation.REMOVE_ALL -> app.getString(R.string.geo_remove_failed)
                }
                val fallback = when (result.operation) {
                    GeoAwarenessDatasetWorkflowController.Operation.IMPORT -> R.string.geo_import_failed_message
                    GeoAwarenessDatasetWorkflowController.Operation.UPDATE -> R.string.geo_update_failed_message
                    GeoAwarenessDatasetWorkflowController.Operation.REMOVE_ONE -> R.string.geo_remove_one_failed
                    GeoAwarenessDatasetWorkflowController.Operation.REMOVE_ALL -> R.string.geo_remove_all_failed
                }
                showMessage(
                    title,
                    validationFormatter.failureSummary(result.failure.error, result.failure.validation, fallback),
                )
            }
        }
    }

    private fun onRefreshSucceeded(result: GeoZoneLoadResult) {
        logger.logSimple(
            type = GeoAwarenessEventType.DATASET_STATUS_REFRESHED,
            severity = "INFO",
            message = "Geo-zone dataset status refreshed",
            datasetTitle = result.datasetInfo.title,
            datasetVersion = result.datasetInfo.version,
            healthState = mutableState.value.health?.state?.name,
            details = mapOf(
                "activeDatasetCount" to result.datasetRecords.size.toString(),
                "totalZones" to result.datasetInfo.zoneCount.toString(),
                "staleDatasetCount" to result.datasetRecords.count { it.isStale }.toString(),
                "errorCount" to result.validationResult.errorCount.toString(),
                "warningCount" to result.validationResult.warningCount.toString(),
            ) + auditFormatter.standardDetails("status_refresh", result, "not_applicable", null),
        )
        refreshEvents()
        effect(GeoAwarenessEffect.Message(app.getString(R.string.geo_status_refreshed)))
    }

    private fun refreshEvents() {
        mutableState.value = mutableState.value.copy(events = logger.readEvents(maxLines = Int.MAX_VALUE))
    }

    private fun setBusy(busy: Boolean, message: String?) {
        mutableState.value = mutableState.value.copy(
            busy = busy,
            busyMessage = message.takeIf { busy },
        )
    }

    private fun showMessage(title: String, message: String) {
        mutableState.value = mutableState.value.copy(dialog = GeoAwarenessDialog.Message(title, message))
    }

    private fun dismissDialog() {
        mutableState.value = mutableState.value.copy(dialog = null)
    }

    private fun resolveDisplayName(uri: Uri): String? =
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))?.takeIf(String::isNotBlank)
            } else null
        }

    private fun effect(value: GeoAwarenessEffect) {
        viewModelScope.launch { effectChannel.send(value) }
    }

    override fun onCleared() {
        observer.clear()
        telemetryObserver.clear()
        liveController?.clear()
        exportController?.clear()
        verificationController?.clear()
        workflow?.clear()
        datasetState?.clear()
        super.onCleared()
    }

    private companion object {
        val FILE_MIME_TYPES = arrayOf("application/json", "application/geo+json", "text/json", "text/plain", "*/*")
    }
}
