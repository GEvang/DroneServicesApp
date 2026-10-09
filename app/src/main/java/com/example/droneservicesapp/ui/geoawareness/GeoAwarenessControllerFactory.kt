package com.example.droneservicesapp.ui.geoawareness

import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.databinding.FragmentGeoAwarenessBinding
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.home.geoawareness.LiveGeoAwarenessStatusViewBinder
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

/** Creates and wires one view-scoped Geo Awareness controller graph. */
class GeoAwarenessControllerFactory {
    class Components internal constructor(
        private val datasetState: GeoAwarenessDatasetStateCoordinator,
        private val datasetWorkflow: GeoAwarenessDatasetWorkflowController,
        private val telemetryObserver: GeoAwarenessTelemetryObserver,
        private val liveController: GeoAwarenessLiveStatusController,
        private val livePresenter: GeoAwarenessLiveStatusPresenter,
        private val exportController: GeoAwarenessExportController,
        private val logDialogs: GeoAwarenessLogDialogController,
        private val verificationDialogs: GeoAwarenessVerificationDialogController,
        private val verificationController: GeoAwarenessVerificationController,
        private val picker: GeoAwarenessDatasetPickerController,
        private val actions: GeoAwarenessUiActionController,
        private val datasetRenderer: GeoAwarenessDatasetListRenderer,
        private val session: GeoAwarenessSessionState,
    ) {
        fun onResume() = datasetState.loadIfNeeded()

        fun handlePickerSelection(selection: GeoAwarenessDatasetPickerController.Selection) {
            datasetWorkflow.handleSelection(selection)
        }

        fun clear() {
            telemetryObserver.clear()
            liveController.clear()
            livePresenter.clear()
            exportController.clear()
            logDialogs.dismiss()
            verificationDialogs.dismiss()
            verificationController.clear()
            picker.clear()
            datasetWorkflow.clear()
            datasetState.clear()
            actions.clear()
            datasetRenderer.clear()
            session.clearViewState()
        }
    }

    fun create(
        fragment: Fragment,
        binding: FragmentGeoAwarenessBinding,
        owner: LifecycleOwner,
        activityViewModel: MainActivityViewModel,
        droneViewModel: DroneViewModel,
        session: GeoAwarenessSessionState,
        picker: GeoAwarenessDatasetPickerController,
    ): Components {
        val context = fragment.requireContext()
        val scope = owner.lifecycleScope
        val logger = GeoAwarenessEventLogger(context.applicationContext)
        val datasetRenderer = GeoAwarenessDatasetListRenderer(context)
        val statusPresenter = GeoAwarenessStatusPresenter(context)
        val validationPresenter = GeoAwarenessValidationPresenter(context)
        val datasetDialogs = GeoAwarenessDatasetDialogController(context, validationPresenter)
        val logPresenter = GeoAwarenessEventLogPresenter(context, logger)
        val logDialogs = GeoAwarenessLogDialogController(context)
        val auditFormatter = GeoAwarenessDatasetAuditFormatter()
        val actions = GeoAwarenessUiActionController()
        val observer = GeoAwarenessObserverCoordinator()
        val telemetryObserver = GeoAwarenessTelemetryObserver(session)

        fun refreshEvents() = logPresenter.refresh(binding)
        fun renderSource() {
            val state = session.dataset
            statusPresenter.renderSource(
                binding,
                GeoAwarenessStatusPresenter.SourceState(state.importedActive, state.datasetInfo, state.records, state.validation),
            )
        }
        fun setBusy(busy: Boolean, message: String?) {
            statusPresenter.renderBusy(binding, busy, message ?: context.getString(R.string.geo_loading_dataset))
            datasetRenderer.setBusy(busy)
        }

        var workflowRef: GeoAwarenessDatasetWorkflowController? = null
        var liveControllerRef: GeoAwarenessLiveStatusController? = null

        fun renderDatasets() {
            datasetRenderer.render(
                binding.geoAwarenessDatasetRecordsContainer,
                session.dataset.records,
                GeoAwarenessDatasetListRenderer.Actions(
                    update = picker::launchUpdate,
                    remove = { record -> workflowRef?.confirmRemove(record) },
                    validationDetails = { record ->
                        datasetDialogs.readable(
                            context.getString(R.string.geo_dataset_validation_named, record.displayName),
                            validationPresenter.datasetDetails(record),
                        )
                    },
                ),
            )
        }

        fun applySnapshot(snapshot: GeoAwarenessDatasetStateCoordinator.Snapshot) {
            session.apply(snapshot)
            renderSource()
            renderDatasets()
            statusPresenter.renderValidation(binding, snapshot.validation)
            statusPresenter.renderHealth(binding, snapshot.health)
            liveControllerRef?.updateDataset(snapshot)
        }

        fun logRefresh(result: GeoZoneLoadResult) {
            logger.logSimple(
                type = GeoAwarenessEventType.DATASET_STATUS_REFRESHED,
                severity = "INFO",
                message = "Geo-zone dataset status refreshed",
                datasetTitle = result.datasetInfo.title,
                datasetVersion = result.datasetInfo.version,
                healthState = session.dataset.health?.state?.name,
                details = mapOf(
                    "activeDatasetCount" to result.datasetRecords.size.toString(),
                    "totalZones" to result.datasetInfo.zoneCount.toString(),
                    "staleDatasetCount" to result.datasetRecords.count { it.isStale }.toString(),
                    "errorCount" to result.validationResult.errorCount.toString(),
                    "warningCount" to result.validationResult.warningCount.toString(),
                ) + auditFormatter.standardDetails("status_refresh", result, "not_applicable", null),
            )
            refreshEvents()
            Toast.makeText(context, R.string.geo_status_refreshed, Toast.LENGTH_SHORT).show()
        }

        val datasetState = GeoAwarenessDatasetStateCoordinator(
            context = context,
            scope = scope,
            sharedState = activityViewModel,
            eventLogger = logger,
            session = session,
            callbacks = GeoAwarenessDatasetStateCoordinator.Callbacks(
                onSnapshot = ::applySnapshot,
                onBusyChanged = { busy, message -> if (workflowRef?.isBusy != true) setBusy(busy, message) },
                onRefreshSucceeded = ::logRefresh,
                onFailure = { error, manual ->
                    if (manual) datasetDialogs.readable(
                        context.getString(R.string.geo_refresh_failed),
                        error.message ?: context.getString(R.string.geo_refresh_failed_message),
                    )
                },
                onAuditLogChanged = ::refreshEvents,
            ),
        )
        val workflow = GeoAwarenessDatasetWorkflowController(
            context = context,
            scope = scope,
            stateCoordinator = datasetState,
            dialogs = datasetDialogs,
            logger = logger,
            auditFormatter = auditFormatter,
            currentRecords = { session.dataset.records },
            currentHealth = { session.dataset.health },
            callbacks = GeoAwarenessDatasetWorkflowController.Callbacks(
                onBusyChanged = ::setBusy,
                onAuditLogChanged = ::refreshEvents,
            ),
        )
        workflowRef = workflow
        val verification = GeoAwarenessVerificationController(
            context = context,
            scope = scope,
            repositoryProvider = workflow::repositoryForEvidenceExport,
            eventLogger = logger,
            session = session,
            onAuditLogChanged = ::refreshEvents,
        )
        val verificationDialogs = GeoAwarenessVerificationDialogController(context, verification)
        val livePresenter = GeoAwarenessLiveStatusPresenter(
            context,
            LiveGeoAwarenessStatusViewBinder(context, binding.geoAwarenessLiveStatusChip),
        )
        val liveController = GeoAwarenessLiveStatusController(scope, logger, livePresenter::present, ::refreshEvents)
        liveControllerRef = liveController
        val export = GeoAwarenessExportController(
            context = context,
            scope = scope,
            eventLogger = logger,
            repositoryProvider = workflow::repositoryForEvidenceExport,
            verificationStoreProvider = verification::statusStoreForEvidenceExport,
            diagnosticsProvider = { session.lastAutomatedTestResult },
            datasetInfoProvider = { session.dataset.datasetInfo },
            healthProvider = { session.dataset.health },
            callbacks = GeoAwarenessExportController.Callbacks(
                launchChooser = { intent -> fragment.startActivity(intent) },
                onFailure = logDialogs::showExportFailure,
                onNoEncryptedIncidents = logDialogs::showNoEncryptedIncidents,
                onAuditLogChanged = ::refreshEvents,
            ),
        )

        actions.bind(
            binding,
            GeoAwarenessUiActionController.Actions(
                overlayVisibilityChanged = { visible ->
                    if (activityViewModel.geoAwarenessLayerVisible.value != visible) activityViewModel.geoAwarenessLayerVisible.value = visible
                },
                importDataset = picker::launchImport,
                removeAllDatasets = workflow::confirmRemoveAll,
                refreshStatus = { if (!workflow.isBusy) datasetState.refresh(manual = true) },
                showValidation = {
                    val validation = session.dataset.validation ?: GeoZoneValidationResult.ok()
                    datasetDialogs.readable(context.getString(R.string.geo_dataset_validation), validationPresenter.details(validation))
                },
                exportLogs = export::exportStandardLogs,
                showDetailedLogs = { logDialogs.showDetailedPreview(logPresenter.detailedPreview()) },
                exportEvidence = export::exportEvidencePackage,
                exportEncryptedIncidents = export::exportEncryptedIncidents,
                showVerification = verificationDialogs::show,
            ),
        )
        observer.observe(
            owner,
            activityViewModel,
            GeoAwarenessObserverCoordinator.Callbacks(
                onOverlayVisibility = { binding.geoAwarenessOverlaySwitch.isChecked = it },
                onDatasetInfo = { info ->
                    if (session.dataset.datasetInfo != info) {
                        session.dataset = session.dataset.copy(datasetInfo = info)
                        renderSource()
                    }
                },
                onValidation = { result ->
                    if (session.dataset.validation != result) {
                        session.dataset = session.dataset.copy(validation = result)
                        statusPresenter.renderValidation(binding, result)
                        renderSource()
                    }
                },
                onRecords = { records ->
                    if (session.dataset.records != records) {
                        session.dataset = session.dataset.copy(records = records)
                        renderDatasets()
                        renderSource()
                    }
                },
                onImportedActive = { imported ->
                    if (session.dataset.importedActive != imported) {
                        session.dataset = session.dataset.copy(importedActive = imported)
                        renderSource()
                    }
                },
                onHealth = { health ->
                    if (session.dataset.health != health) {
                        session.dataset = session.dataset.copy(health = health)
                        health?.let { statusPresenter.renderHealth(binding, it) }
                    }
                },
                onReloadToken = datasetState::handleReloadToken,
            ),
        )
        telemetryObserver.observe(owner, droneViewModel, liveController::updateTelemetry)
        livePresenter.showInitialUnknown()
        renderSource()
        renderDatasets()
        statusPresenter.renderValidation(binding, activityViewModel.geoZoneValidationResult.value)
        activityViewModel.geoAwarenessHealth.value?.let { statusPresenter.renderHealth(binding, it) }
        refreshEvents()
        datasetState.loadIfNeeded()

        return Components(
            datasetState,
            workflow,
            telemetryObserver,
            liveController,
            livePresenter,
            export,
            logDialogs,
            verificationDialogs,
            verification,
            picker,
            actions,
            datasetRenderer,
            session,
        )
    }
}
