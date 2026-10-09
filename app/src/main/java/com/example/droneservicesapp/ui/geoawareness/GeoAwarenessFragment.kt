package com.example.droneservicesapp.ui.geoawareness

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.databinding.FragmentGeoAwarenessBinding
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthEvaluator
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.home.geoawareness.LiveGeoAwarenessStatusViewBinder
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

class GeoAwarenessFragment : Fragment() {

    private var _binding: FragmentGeoAwarenessBinding? = null
    private val binding get() = _binding!!

    private lateinit var activityViewModel: MainActivityViewModel
    private lateinit var droneViewModel: DroneViewModel
    private var datasetInfo: GeoZoneDatasetInfo? = null
    private var geoZones: List<GeoZone> = emptyList()
    private var geoAwarenessHealth: GeoAwarenessHealth? = null
    private var geoAwarenessLoadError: Throwable? = null
    private var validationResult: GeoZoneValidationResult? = null
    private var importedDatasetActive: Boolean = false
    private var datasetRecords: List<GeoZoneDatasetRecord> = emptyList()
    private lateinit var geoEventLogger: GeoAwarenessEventLogger
    private val telemetryObserver = GeoAwarenessTelemetryObserver()
    private lateinit var liveStatusController: GeoAwarenessLiveStatusController
    private lateinit var liveStatusPresenter: GeoAwarenessLiveStatusPresenter
    private val uiActionController = GeoAwarenessUiActionController()
    private lateinit var datasetListRenderer: GeoAwarenessDatasetListRenderer
    private lateinit var statusPresenter: GeoAwarenessStatusPresenter
    private lateinit var datasetStateCoordinator: GeoAwarenessDatasetStateCoordinator
    private lateinit var datasetWorkflowController: GeoAwarenessDatasetWorkflowController
    private lateinit var datasetDialogController: GeoAwarenessDatasetDialogController
    private lateinit var validationPresenter: GeoAwarenessValidationPresenter
    private lateinit var verificationController: GeoAwarenessVerificationController
    private lateinit var verificationDialogController: GeoAwarenessVerificationDialogController
    private lateinit var eventLogPresenter: GeoAwarenessEventLogPresenter
    private lateinit var logDialogController: GeoAwarenessLogDialogController
    private lateinit var exportController: GeoAwarenessExportController
    private val datasetAuditFormatter = GeoAwarenessDatasetAuditFormatter()
    private val observerCoordinator = GeoAwarenessObserverCoordinator()
    private val datasetPickerController = GeoAwarenessDatasetPickerController(this) { selection ->
        if (::datasetWorkflowController.isInitialized) {
            datasetWorkflowController.handleSelection(selection)
        }
    }

    companion object {
        private const val DEFAULT_NEAR_ZONE_THRESHOLD_METERS = 100.0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGeoAwarenessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<android.widget.ImageView>(R.id.more_header_icon).setImageResource(R.drawable.ic_status_gps_pin_24)
        view.findViewById<android.widget.TextView>(R.id.more_header_title).setText(R.string.geo_awareness_title)
        view.findViewById<android.widget.TextView>(R.id.more_header_subtitle).setText(R.string.geo_awareness_subtitle)
        activityViewModel = ViewModelProvider(requireActivity())[MainActivityViewModel::class.java]
        droneViewModel = ViewModelProvider(requireActivity())[DroneViewModel::class.java]
        geoEventLogger = GeoAwarenessEventLogger(requireContext().applicationContext)
        eventLogPresenter = GeoAwarenessEventLogPresenter(requireContext(), geoEventLogger)
        logDialogController = GeoAwarenessLogDialogController(requireContext())
        datasetListRenderer = GeoAwarenessDatasetListRenderer(requireContext())
        statusPresenter = GeoAwarenessStatusPresenter(requireContext())
        validationPresenter = GeoAwarenessValidationPresenter(requireContext())
        datasetDialogController = GeoAwarenessDatasetDialogController(requireContext(), validationPresenter)
        datasetStateCoordinator = GeoAwarenessDatasetStateCoordinator(
            context = requireContext(),
            scope = viewLifecycleOwner.lifecycleScope,
            sharedState = activityViewModel,
            eventLogger = geoEventLogger,
            callbacks = GeoAwarenessDatasetStateCoordinator.Callbacks(
                onSnapshot = ::applyDatasetSnapshot,
                onBusyChanged = { busy, message ->
                    if (_binding != null && (!::datasetWorkflowController.isInitialized || !datasetWorkflowController.isBusy)) {
                        setDatasetBusyState(busy, message ?: getString(R.string.geo_loading_dataset))
                    }
                },
                onRefreshSucceeded = ::handleDatasetRefreshSuccess,
                onFailure = ::handleDatasetStateFailure,
                onAuditLogChanged = ::refreshEventLogCount,
            ),
        )
        datasetWorkflowController = GeoAwarenessDatasetWorkflowController(
            context = requireContext(),
            scope = viewLifecycleOwner.lifecycleScope,
            stateCoordinator = datasetStateCoordinator,
            dialogs = datasetDialogController,
            logger = geoEventLogger,
            auditFormatter = datasetAuditFormatter,
            currentRecords = { datasetRecords },
            currentHealth = { geoAwarenessHealth },
            callbacks = GeoAwarenessDatasetWorkflowController.Callbacks(
                onBusyChanged = { busy, message ->
                    if (_binding != null) setDatasetBusyState(busy, message ?: getString(R.string.geo_loading_dataset))
                },
                onAuditLogChanged = ::refreshEventLogCount,
            ),
        )
        verificationController = GeoAwarenessVerificationController(
            context = requireContext(),
            scope = viewLifecycleOwner.lifecycleScope,
            repositoryProvider = datasetWorkflowController::repositoryForEvidenceExport,
            eventLogger = geoEventLogger,
            onAuditLogChanged = ::refreshEventLogCount,
        )
        verificationDialogController = GeoAwarenessVerificationDialogController(requireContext(), verificationController)
        exportController = GeoAwarenessExportController(
            context = requireContext(),
            scope = viewLifecycleOwner.lifecycleScope,
            eventLogger = geoEventLogger,
            repositoryProvider = datasetWorkflowController::repositoryForEvidenceExport,
            verificationStoreProvider = verificationController::statusStoreForEvidenceExport,
            diagnosticsProvider = { verificationController.lastTestRunResult },
            datasetInfoProvider = { datasetInfo },
            healthProvider = { geoAwarenessHealth },
            callbacks = GeoAwarenessExportController.Callbacks(
                launchChooser = { intent -> startActivity(intent) },
                onFailure = logDialogController::showExportFailure,
                onNoEncryptedIncidents = logDialogController::showNoEncryptedIncidents,
                onAuditLogChanged = ::refreshEventLogCount,
            ),
        )

        requireActivity().findViewById<View>(R.id.bottom_nav_view)?.isVisible = false
        val liveStatusBinder = LiveGeoAwarenessStatusViewBinder(
            requireContext(),
            binding.geoAwarenessLiveStatusChip
        )
        liveStatusPresenter = GeoAwarenessLiveStatusPresenter(requireContext(), liveStatusBinder)
        liveStatusController = GeoAwarenessLiveStatusController(
            scope = viewLifecycleOwner.lifecycleScope,
            eventLogger = geoEventLogger,
            onState = liveStatusPresenter::present,
            onAuditLogChanged = ::refreshEventLogCount,
        )
        liveStatusPresenter.showInitialUnknown()

        uiActionController.bind(
            binding = binding,
            actions = GeoAwarenessUiActionController.Actions(
                overlayVisibilityChanged = { checked ->
                    if (activityViewModel.geoAwarenessLayerVisible.value != checked) {
                        activityViewModel.geoAwarenessLayerVisible.value = checked
                    }
                },
                importDataset = datasetPickerController::launchImport,
                removeAllDatasets = datasetWorkflowController::confirmRemoveAll,
                refreshStatus = { refreshGeoAwarenessStatus(manual = true) },
                showValidation = ::showValidationDetails,
                exportLogs = exportController::exportStandardLogs,
                showDetailedLogs = { logDialogController.showDetailedPreview(eventLogPresenter.detailedPreview()) },
                exportEvidence = exportController::exportEvidencePackage,
                exportEncryptedIncidents = exportController::exportEncryptedIncidents,
                showVerification = verificationDialogController::show,
            ),
        )
        updateCurrentSourceSummary()
        renderDatasetRecords()
        renderValidationStatus(activityViewModel.geoZoneValidationResult.value)
        refreshEventLogCount()
        observeSharedState()
        telemetryObserver.observe(viewLifecycleOwner, droneViewModel, liveStatusController::updateTelemetry)
        renderHealthStatus(
            activityViewModel.geoAwarenessHealth.value ?: GeoAwarenessHealthEvaluator.evaluate(
                datasetInfo = datasetInfo,
                zones = geoZones,
                datasetRecords = datasetRecords,
                validationResult = validationResult,
                loadError = geoAwarenessLoadError
            )
        )
        datasetStateCoordinator.loadIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        datasetStateCoordinator.loadIfNeeded()
    }

    private fun observeSharedState() {
        observerCoordinator.observe(
            owner = viewLifecycleOwner,
            state = activityViewModel,
            callbacks = GeoAwarenessObserverCoordinator.Callbacks(
                onOverlayVisibility = { binding.geoAwarenessOverlaySwitch.isChecked = it },
                onDatasetInfo = { info ->
                    datasetInfo = info
                    updateCurrentSourceSummary()
                },
                onValidation = { result ->
                    validationResult = result
                    renderValidationStatus(result)
                    updateCurrentSourceSummary()
                },
                onRecords = { records ->
                    datasetRecords = records
                    renderDatasetRecords()
                    updateCurrentSourceSummary()
                },
                onImportedActive = { imported ->
                    importedDatasetActive = imported
                    updateCurrentSourceSummary()
                },
                onHealth = { health ->
                    geoAwarenessHealth = health
                    renderHealthStatus(health)
                },
                onReloadToken = datasetStateCoordinator::handleReloadToken,
            ),
        )
    }

    private fun applyDatasetSnapshot(snapshot: GeoAwarenessDatasetStateCoordinator.Snapshot) {
        if (_binding == null) return
        datasetInfo = snapshot.datasetInfo
        geoZones = snapshot.zones
        datasetRecords = snapshot.records
        validationResult = snapshot.validation
        geoAwarenessHealth = snapshot.health
        importedDatasetActive = snapshot.importedActive
        geoAwarenessLoadError = snapshot.loadError
        updateCurrentSourceSummary()
        renderDatasetRecords()
        renderValidationStatus(snapshot.validation)
        renderHealthStatus(snapshot.health)
        liveStatusController.updateDataset(snapshot)
    }

    private fun handleDatasetStateFailure(error: Throwable, manualRefresh: Boolean) {
        if (_binding == null || !manualRefresh) return
        showReadableDialog(
            getString(R.string.geo_refresh_failed),
            error.message ?: getString(R.string.geo_refresh_failed_message),
        )
    }

    private fun handleDatasetRefreshSuccess(loadResult: GeoZoneLoadResult) {
        if (_binding == null) return
        geoEventLogger.logSimple(
            type = GeoAwarenessEventType.DATASET_STATUS_REFRESHED,
            severity = "INFO",
            message = "Geo-zone dataset status refreshed",
            datasetTitle = loadResult.datasetInfo.title,
            datasetVersion = loadResult.datasetInfo.version,
            healthState = geoAwarenessHealth?.state?.name,
            details = mapOf(
                "activeDatasetCount" to loadResult.datasetRecords.size.toString(),
                "totalZones" to loadResult.datasetInfo.zoneCount.toString(),
                "staleDatasetCount" to loadResult.datasetRecords.count { it.isStale }.toString(),
                "errorCount" to loadResult.validationResult.errorCount.toString(),
                "warningCount" to loadResult.validationResult.warningCount.toString(),
            ) + datasetAuditFormatter.standardDetails(
                operation = "status_refresh",
                loadResult = loadResult,
                requestedUri = "not_applicable",
                originalFileName = null,
            ),
        )
        refreshEventLogCount()
        Toast.makeText(requireContext(), R.string.geo_status_refreshed, Toast.LENGTH_SHORT).show()
    }

    private fun updateCurrentSourceSummary() {
        if (_binding == null) return
        statusPresenter.renderSource(
            binding,
            GeoAwarenessStatusPresenter.SourceState(
                importedActive = importedDatasetActive,
                datasetInfo = datasetInfo,
                records = datasetRecords,
                validation = validationResult,
            ),
        )
    }

    private fun renderDatasetRecords() {
        if (_binding == null) return
        datasetListRenderer.render(
            binding.geoAwarenessDatasetRecordsContainer,
            datasetRecords,
            GeoAwarenessDatasetListRenderer.Actions(
                update = datasetPickerController::launchUpdate,
                remove = datasetWorkflowController::confirmRemove,
                validationDetails = ::showValidationDetails,
            ),
        )
    }

    private fun renderHealthStatus(health: GeoAwarenessHealth?) {
        val resolvedHealth = health ?: GeoAwarenessHealthEvaluator.evaluate(
            datasetInfo = datasetInfo,
            zones = geoZones,
            datasetRecords = datasetRecords,
            validationResult = validationResult,
            loadError = geoAwarenessLoadError
        )
        geoAwarenessHealth = resolvedHealth

        statusPresenter.renderHealth(binding, resolvedHealth)
    }

    private fun renderValidationStatus(result: GeoZoneValidationResult?) {
        if (_binding == null) return
        statusPresenter.renderValidation(binding, result)
    }

    private fun refreshEventLogCount() {
        if (_binding != null) eventLogPresenter.refresh(binding)
    }

    private fun refreshGeoAwarenessStatus(manual: Boolean) {
        if (::datasetWorkflowController.isInitialized && datasetWorkflowController.isBusy) return
        datasetStateCoordinator.refresh(manual)
    }

    private fun setDatasetBusyState(isBusy: Boolean, message: String = getString(R.string.geo_loading_dataset)) {
        if (_binding == null) return
        statusPresenter.renderBusy(binding, isBusy, message)
        datasetListRenderer.setBusy(isBusy)
    }

    private fun showValidationDetails() {
        val result = validationResult ?: GeoZoneValidationResult.ok()
        showReadableDialog(getString(R.string.geo_dataset_validation), validationPresenter.details(result))
    }

    private fun showValidationDetails(record: GeoZoneDatasetRecord) {
        showReadableDialog(
            title = getString(R.string.geo_dataset_validation_named, record.displayName),
            message = validationPresenter.datasetDetails(record)
        )
    }

    private fun showReadableDialog(title: String, message: String) {
        datasetDialogController.readable(title, message)
    }

    override fun onDestroyView() {
        telemetryObserver.clear()
        if (::liveStatusController.isInitialized) {
            liveStatusController.clear()
        }
        if (::liveStatusPresenter.isInitialized) {
            liveStatusPresenter.clear()
        }
        if (::exportController.isInitialized) {
            exportController.clear()
        }
        if (::logDialogController.isInitialized) {
            logDialogController.dismiss()
        }
        if (::verificationDialogController.isInitialized) {
            verificationDialogController.dismiss()
        }
        if (::verificationController.isInitialized) {
            verificationController.clear()
        }
        datasetPickerController.clear()
        if (::datasetWorkflowController.isInitialized) {
            datasetWorkflowController.clear()
        }
        if (::datasetStateCoordinator.isInitialized) {
            datasetStateCoordinator.clear()
        }
        uiActionController.clear()
        if (::datasetListRenderer.isInitialized) {
            datasetListRenderer.clear()
        }
        super.onDestroyView()
        _binding = null
    }
}
