package com.example.droneservicesapp.ui.geoawareness

import android.net.Uri
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.geoawareness.evidence.GeoAwarenessEvidencePackageExporter
import com.example.droneservicesapp.data.geoawareness.incident.GeoIncidentEncryptedLogStore
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEvent
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.verification.GeoAwarenessVerificationStatusStore
import com.example.droneservicesapp.databinding.FragmentGeoAwarenessBinding
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoAltitudeContext
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthEvaluator
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult
import com.example.droneservicesapp.domain.geoawareness.LiveGeoAwarenessChecker
import com.example.droneservicesapp.domain.geoawareness.LiveGeoAwarenessProximityResult
import com.example.droneservicesapp.domain.geoawareness.testing.GeoAwarenessTestRunResult
import com.example.droneservicesapp.domain.geoawareness.testing.GeoAwarenessTestRunner
import com.example.droneservicesapp.domain.geoawareness.testing.GeoAwarenessTestStatus
import com.example.droneservicesapp.domain.geoawareness.verification.GeoAwarenessVerificationCase
import com.example.droneservicesapp.domain.geoawareness.verification.GeoAwarenessVerificationChecklist
import com.example.droneservicesapp.domain.geoawareness.verification.GeoAwarenessVerificationStatus
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.home.geoawareness.LiveGeoAwarenessStatusViewBinder
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GeoAwarenessFragment : Fragment() {

    private var _binding: FragmentGeoAwarenessBinding? = null
    private val binding get() = _binding!!

    private lateinit var activityViewModel: MainActivityViewModel
    private lateinit var droneViewModel: DroneViewModel
    private val liveChecker = LiveGeoAwarenessChecker()
    private var datasetInfo: GeoZoneDatasetInfo? = null
    private var geoZones: List<GeoZone> = emptyList()
    private var latestLiveZones: List<GeoZone> = emptyList()
    private var latestLiveProximity: LiveGeoAwarenessProximityResult? = null
    private var latestRealDronePosition: LatLon? = null
    private var latestRealDroneAltitudeMeters: Double? = null
    private var latestRealDroneAltitudeAmslMeters: Double? = null
    private var latestRealDroneGroundSpeedMetersPerSecond: Float? = null
    private var latestRealDroneVerticalSpeedMetersPerSecond: Float? = null
    private var latestRealDroneHeadingDegrees: Double? = null
    private var geoAwarenessHealth: GeoAwarenessHealth? = null
    private var geoAwarenessLoadError: Throwable? = null
    private var validationResult: GeoZoneValidationResult? = null
    private var importedDatasetActive: Boolean = false
    private var datasetRecords: List<GeoZoneDatasetRecord> = emptyList()
    private lateinit var geoEventLogger: GeoAwarenessEventLogger
    private var liveStatusBinder: LiveGeoAwarenessStatusViewBinder? = null
    private var lastTestRunResult: GeoAwarenessTestRunResult? = null
    private var liveStatusJob: Job? = null
    private lateinit var verificationStatusStore: GeoAwarenessVerificationStatusStore
    private val uiActionController = GeoAwarenessUiActionController()
    private lateinit var datasetListRenderer: GeoAwarenessDatasetListRenderer
    private lateinit var statusPresenter: GeoAwarenessStatusPresenter
    private lateinit var datasetStateCoordinator: GeoAwarenessDatasetStateCoordinator
    private lateinit var datasetWorkflowController: GeoAwarenessDatasetWorkflowController
    private lateinit var datasetDialogController: GeoAwarenessDatasetDialogController
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
        verificationStatusStore = GeoAwarenessVerificationStatusStore(requireContext().applicationContext)
        datasetListRenderer = GeoAwarenessDatasetListRenderer(requireContext())
        statusPresenter = GeoAwarenessStatusPresenter(requireContext())
        datasetDialogController = GeoAwarenessDatasetDialogController(requireContext())
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

        requireActivity().findViewById<View>(R.id.bottom_nav_view)?.isVisible = false
        liveStatusBinder = LiveGeoAwarenessStatusViewBinder(
            requireContext(),
            binding.geoAwarenessLiveStatusChip
        )
        liveStatusBinder?.bindUnknown(getString(R.string.geo_awareness_live_no_position))
        liveStatusBinder?.setOnClickListener(View.OnClickListener {
            showLiveGeoDetails()
        })

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
                exportLogs = ::exportGeoAwarenessLogs,
                showDetailedLogs = ::showDetailedLogsPreview,
                exportEvidence = ::exportEvidencePackage,
                exportEncryptedIncidents = ::exportEncryptedIncidentLogs,
            ),
        )
        updateCurrentSourceSummary()
        renderDatasetRecords()
        renderValidationStatus(activityViewModel.geoZoneValidationResult.value)
        observeSharedState()
        observeDroneLocation()
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
        updateLiveStatus()
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

    private fun observeDroneLocation() {
        droneViewModel.droneLocationLiveData.observe(viewLifecycleOwner) { location ->
            syncLatestDroneLocationSnapshot(location)
            updateLiveStatus()
        }
        droneViewModel.conStateLiveData.observe(viewLifecycleOwner) {
            syncLatestDroneLocationSnapshot(droneViewModel.droneLocationLiveData.value)
            updateLiveStatus()
        }
        droneViewModel.gpsFixType.observe(viewLifecycleOwner) {
            syncLatestDroneLocationSnapshot(droneViewModel.droneLocationLiveData.value)
            updateLiveStatus()
        }
        droneViewModel.droneAltitudeAmslMeters.observe(viewLifecycleOwner) { altitudeAmslMeters ->
            latestRealDroneAltitudeAmslMeters = altitudeAmslMeters
            updateLiveStatus()
        }
        droneViewModel.droneGroundSpeedMetersPerSecond.observe(viewLifecycleOwner) { speed ->
            latestRealDroneGroundSpeedMetersPerSecond = speed
            updateLiveStatus()
        }
        droneViewModel.droneVerticalSpeedMetersPerSecond.observe(viewLifecycleOwner) { speed ->
            latestRealDroneVerticalSpeedMetersPerSecond = speed
            updateLiveStatus()
        }
        droneViewModel.droneHeading.observe(viewLifecycleOwner) { heading ->
            latestRealDroneHeadingDegrees = heading
            updateLiveStatus()
        }
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
        updateLiveStatus()
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

    private fun syncLatestDroneLocationSnapshot(location: Location?) {
        val usableLocation = location?.takeIf(::isUsableDroneLocation)
        latestRealDronePosition = usableLocation?.let { LatLon(lat = it.latitude, lon = it.longitude) }
        latestRealDroneAltitudeMeters = usableLocation?.altitude
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

    private fun createStatusValue(text: String): TextView = datasetListRenderer.statusValue(text)

    private fun createPanelText(
        text: String,
        textColor: Int = Color.parseColor("#C5D0E6")
    ): TextView = datasetListRenderer.panelText(text, textColor)

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

    private fun showVerificationChecklistDialog() {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (20 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(),
                (20 * resources.displayMetrics.density).toInt(),
                0
            )
        }
        val summaryView = TextView(context).apply {
            setTextColor(Color.parseColor("#21304A"))
            textSize = 14f
        }
        val resetButton = com.google.android.material.button.MaterialButton(
            context,
            null,
            R.attr.materialButtonOutlinedStyle
        ).apply {
            text = getString(R.string.geo_awareness_verification_reset)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (44 * resources.displayMetrics.density).toInt()
            ).apply {
                topMargin = (12 * resources.displayMetrics.density).toInt()
                bottomMargin = (12 * resources.displayMetrics.density).toInt()
            }
        }
        val scrollView = ScrollView(context).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (420 * resources.displayMetrics.density).toInt()
            )
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(content)
        root.addView(summaryView)
        root.addView(resetButton)
        root.addView(scrollView)

        fun renderChecklist() {
            val statuses = verificationStatusStore.getAllStatuses()
            val passedCount = statuses.values.count { it == GeoAwarenessVerificationStatus.PASS }
            val failedCount = statuses.values.count { it == GeoAwarenessVerificationStatus.FAIL }
            val blockedCount = statuses.values.count { it == GeoAwarenessVerificationStatus.BLOCKED }
            val notRunCount = statuses.values.count { it == GeoAwarenessVerificationStatus.NOT_RUN }
            summaryView.text = buildString {
                appendLine("${getString(R.string.geo_awareness_verification_total)} ${GeoAwarenessVerificationChecklist.cases.size}")
                appendLine("${getString(R.string.geo_awareness_verification_passed)} $passedCount")
                appendLine("${getString(R.string.geo_awareness_verification_failed)} $failedCount")
                appendLine("${getString(R.string.geo_awareness_verification_blocked)} $blockedCount")
                append("${getString(R.string.geo_awareness_verification_not_run)} $notRunCount")
            }
            content.removeAllViews()
            GeoAwarenessVerificationChecklist.cases
                .groupBy { it.category }
                .forEach { (category, cases) ->
                    content.addView(createStatusValue(category))
                    cases.forEach { verificationCase ->
                        content.addView(
                            createVerificationCaseView(
                                verificationCase = verificationCase,
                                status = statuses[verificationCase.id] ?: GeoAwarenessVerificationStatus.NOT_RUN,
                                onStatusChanged = { newStatus ->
                                    updateVerificationCaseStatus(verificationCase, newStatus)
                                    renderChecklist()
                                }
                            )
                        )
                    }
                }
        }

        resetButton.setOnClickListener {
            val resetDialog = AlertDialog.Builder(context, R.style.Theme_DroneServicesApp_AlertDialog)
                .setTitle(getString(R.string.geo_awareness_verification_checklist))
                .setMessage(R.string.geo_reset_checklist_question)
                .setPositiveButton(R.string.geo_reset) { _, _ ->
                    verificationStatusStore.resetAll()
                    geoEventLogger.logSimple(
                        type = GeoAwarenessEventType.VERIFICATION_CHECKLIST_RESET,
                        severity = "INFO",
                        message = "Geo-awareness verification checklist statuses reset"
                    )
                    refreshEventLogCount()
                    renderChecklist()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            resetDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#212121"))
            resetDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#212121"))
        }

        renderChecklist()
        val dialog = AlertDialog.Builder(context, R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(getString(R.string.geo_awareness_verification_checklist))
            .setView(root)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#212121"))
    }

    private fun createVerificationCaseView(
        verificationCase: GeoAwarenessVerificationCase,
        status: GeoAwarenessVerificationStatus,
        onStatusChanged: (GeoAwarenessVerificationStatus) -> Unit
    ): View {
        val wrapper = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (10 * resources.displayMetrics.density).toInt()
            }
            setPadding(
                (12 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt()
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * resources.displayMetrics.density
                setColor(Color.parseColor("#EEF3FB"))
                setStroke((1 * resources.displayMetrics.density).toInt(), Color.parseColor("#CCD8EA"))
            }
        }
        wrapper.addView(createStatusValue("${verificationCase.id}  ${verificationCase.title}").apply {
            setTextColor(Color.parseColor("#21304A"))
        })
        wrapper.addView(createVerificationStatusChip(status))
        wrapper.addView(createPanelText(getString(R.string.geo_current_status, verificationStatusLabel(status)), Color.parseColor("#42536F")))
        wrapper.addView(com.google.android.material.button.MaterialButton(requireContext(), null, R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.geo_awareness_verification_details)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (40 * resources.displayMetrics.density).toInt()
            ).apply {
                topMargin = (10 * resources.displayMetrics.density).toInt()
            }
            setOnClickListener { showVerificationCaseDetails(verificationCase, status) }
        })
        wrapper.addView(createVerificationStatusButtonRow(
            first = GeoAwarenessVerificationStatus.NOT_RUN,
            second = GeoAwarenessVerificationStatus.PASS,
            current = status,
            onStatusChanged = onStatusChanged
        ))
        wrapper.addView(createVerificationStatusButtonRow(
            first = GeoAwarenessVerificationStatus.FAIL,
            second = GeoAwarenessVerificationStatus.BLOCKED,
            current = status,
            onStatusChanged = onStatusChanged
        ))
        return wrapper
    }

    private fun createVerificationStatusChip(status: GeoAwarenessVerificationStatus): TextView {
        return TextView(requireContext()).apply {
            text = verificationStatusLabel(status)
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * resources.displayMetrics.density
                setColor(verificationStatusColor(status))
            }
            setPadding(
                (12 * resources.displayMetrics.density).toInt(),
                (6 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (6 * resources.displayMetrics.density).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * resources.displayMetrics.density).toInt()
            }
        }
    }

    private fun createVerificationStatusButtonRow(
        first: GeoAwarenessVerificationStatus,
        second: GeoAwarenessVerificationStatus,
        current: GeoAwarenessVerificationStatus,
        onStatusChanged: (GeoAwarenessVerificationStatus) -> Unit
    ): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * resources.displayMetrics.density).toInt()
            }
            addView(createVerificationStatusButton(first, current, onStatusChanged))
            addView(createVerificationStatusButton(second, current, onStatusChanged).apply {
                (layoutParams as LinearLayout.LayoutParams).marginStart = (10 * resources.displayMetrics.density).toInt()
            })
        }
    }

    private fun createVerificationStatusButton(
        status: GeoAwarenessVerificationStatus,
        current: GeoAwarenessVerificationStatus,
        onStatusChanged: (GeoAwarenessVerificationStatus) -> Unit
    ): com.google.android.material.button.MaterialButton {
        return com.google.android.material.button.MaterialButton(
            requireContext(),
            null,
            R.attr.materialButtonOutlinedStyle
        ).apply {
            text = verificationStatusLabel(status)
            isEnabled = status != current
            layoutParams = LinearLayout.LayoutParams(
                0,
                (40 * resources.displayMetrics.density).toInt(),
                1f
            )
            setOnClickListener { onStatusChanged(status) }
        }
    }

    private fun updateVerificationCaseStatus(
        verificationCase: GeoAwarenessVerificationCase,
        newStatus: GeoAwarenessVerificationStatus
    ) {
        val previousStatus = verificationStatusStore.getStatus(verificationCase.id)
        if (previousStatus == newStatus) {
            return
        }
        verificationStatusStore.setStatus(verificationCase.id, newStatus)
        geoEventLogger.logSimple(
            type = GeoAwarenessEventType.VERIFICATION_CASE_STATUS_CHANGED,
            severity = "INFO",
            message = "Geo-awareness verification case status changed",
            details = mapOf(
                "caseId" to verificationCase.id,
                "caseTitle" to verificationCase.title,
                "previousStatus" to previousStatus.name,
                "newStatus" to newStatus.name
            )
        )
        refreshEventLogCount()
    }

    private fun showVerificationCaseDetails(
        verificationCase: GeoAwarenessVerificationCase,
        status: GeoAwarenessVerificationStatus
    ) {
        val message = buildString {
            appendLine(getString(R.string.geo_verification_status, verificationStatusLabel(status)))
            appendLine()
            appendLine(getString(R.string.geo_verification_purpose))
            appendLine(verificationCase.purpose)
            appendLine()
            appendLine(getString(R.string.geo_verification_preconditions))
            if (verificationCase.preconditions.isEmpty()) {
                appendLine("- ${getString(R.string.geo_none)}")
            } else {
                verificationCase.preconditions.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine(getString(R.string.geo_verification_steps))
            verificationCase.steps.forEach { appendLine("- $it") }
            appendLine()
            appendLine(getString(R.string.geo_verification_expected))
            appendLine(verificationCase.expectedResult)
            appendLine()
            appendLine(getString(R.string.geo_verification_evidence))
            verificationCase.evidenceToCapture.forEach { appendLine("- $it") }
        }
        showReadableDialog("${verificationCase.id} ${verificationCase.title}", message.trim())
    }

    private fun verificationStatusLabel(status: GeoAwarenessVerificationStatus): String = when (status) {
        GeoAwarenessVerificationStatus.NOT_RUN -> getString(R.string.geo_awareness_verification_status_not_run)
        GeoAwarenessVerificationStatus.PASS -> getString(R.string.geo_awareness_verification_status_pass)
        GeoAwarenessVerificationStatus.FAIL -> getString(R.string.geo_awareness_verification_status_fail)
        GeoAwarenessVerificationStatus.BLOCKED -> getString(R.string.geo_awareness_verification_status_blocked)
    }

    private fun verificationStatusColor(status: GeoAwarenessVerificationStatus): Int = when (status) {
        GeoAwarenessVerificationStatus.NOT_RUN -> Color.parseColor("#616161")
        GeoAwarenessVerificationStatus.PASS -> Color.parseColor("#2E7D32")
        GeoAwarenessVerificationStatus.FAIL -> Color.parseColor("#B71C1C")
        GeoAwarenessVerificationStatus.BLOCKED -> Color.parseColor("#EF6C00")
    }

    private fun createTestResultView(
        id: String,
        name: String,
        status: GeoAwarenessTestStatus,
        message: String
    ): View {
        val wrapper = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val statusColor = when (status) {
            GeoAwarenessTestStatus.PASS -> Color.parseColor("#2E7D32")
            GeoAwarenessTestStatus.WARNING -> Color.parseColor("#EF6C00")
            GeoAwarenessTestStatus.FAIL -> Color.parseColor("#B71C1C")
            GeoAwarenessTestStatus.SKIPPED -> Color.parseColor("#616161")
        }
        wrapper.addView(createStatusValue("$id  $name"))
        wrapper.addView(TextView(requireContext()).apply {
            text = status.name
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * resources.displayMetrics.density
                setColor(statusColor)
            }
            setPadding(
                (12 * resources.displayMetrics.density).toInt(),
                (6 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (6 * resources.displayMetrics.density).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * resources.displayMetrics.density).toInt()
            }
        })
        wrapper.addView(createPanelText(message))
        return wrapper
    }

    private fun renderValidationStatus(result: GeoZoneValidationResult?) {
        if (_binding == null) return
        statusPresenter.renderValidation(binding, result)
    }

    private fun refreshEventLogCount() {
        if (_binding == null) return
        val events = geoEventLogger.readEvents(maxLines = Int.MAX_VALUE)
        val count = events.size
        binding.geoAwarenessLogCount.text =
            getString(R.string.geo_awareness_event_count) + " " + count
        renderFlightEventLog(events)
    }

    private fun renderFlightEventLog(events: List<GeoAwarenessEvent>) {
        if (_binding == null) return
        val container = binding.geoAwarenessFlightLogContainer
        container.removeAllViews()
        val importantEvents = events
            .filter { it.type in GeoAwarenessEvidencePackageExporter.IMPORTANT_FLIGHT_EVENTS }
            .sortedByDescending { it.timestampMillis }
            .take(50)
        if (importantEvents.isEmpty()) {
            container.addView(createPanelText(getString(R.string.geo_awareness_flight_log_empty)))
            return
        }
        importantEvents.forEachIndexed { index, event ->
            if (index > 0) {
                container.addView(View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (1 * resources.displayMetrics.density).toInt()
                    ).apply {
                        topMargin = (10 * resources.displayMetrics.density).toInt()
                        bottomMargin = (10 * resources.displayMetrics.density).toInt()
                    }
                    setBackgroundColor(Color.parseColor("#1F2A44"))
                })
            }
            container.addView(createFlightEventRow(event))
        }
    }

    private fun createFlightEventRow(event: GeoAwarenessEvent): View {
        val timeText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.timestampMillis))
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(createStatusValue("$timeText  ${friendlyEventLabel(event.type)}"))
            addView(createPanelText(event.message, severityColor(event.severity)))
            val metaLine = buildString {
                event.zoneNames.firstOrNull()?.let { append("Zone: $it") }
                event.restriction?.let {
                    if (isNotEmpty()) append(" | ")
                    append("Restriction: $it")
                }
                if (event.flightMode != null) {
                    if (isNotEmpty()) append(" | ")
                    append("Mode: ${event.flightMode}")
                }
            }
            if (metaLine.isNotBlank()) {
                addView(createPanelText(metaLine))
            }
        }
    }

    private fun showDetailedLogsPreview() {
        val events = geoEventLogger.readEvents(maxLines = 50).sortedByDescending { it.timestampMillis }
        val message = if (events.isEmpty()) {
            "No detailed geo-awareness events recorded yet."
        } else {
            buildString {
                events.forEach { event ->
                    appendLine("${event.timestampIsoUtc} | ${event.type.name} | ${event.message}")
                }
            }.trim()
        }
        showReadableDialog("Detailed geo-awareness logs", message)
    }

    private fun exportEvidencePackage() {
        try {
            val exporter = GeoAwarenessEvidencePackageExporter(
                context = requireContext().applicationContext,
                eventLogger = geoEventLogger,
                repository = datasetWorkflowController.repositoryForEvidenceExport(),
                verificationStatusStore = verificationStatusStore,
                latestDiagnosticsResultProvider = { lastTestRunResult }
            )
            val zipFile = exporter.exportEvidencePackage()
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                zipFile
            )
            geoEventLogger.logSimple(
                type = GeoAwarenessEventType.EVIDENCE_PACKAGE_EXPORTED,
                severity = "INFO",
                message = "Geo-awareness evidence package exported",
                category = "GEO",
                datasetTitle = datasetInfo?.title,
                datasetVersion = datasetInfo?.version,
                healthState = geoAwarenessHealth?.state?.name,
                details = mapOf(
                    "fileName" to zipFile.name,
                    "fileSizeBytes" to zipFile.length().toString()
                )
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Geo-awareness evidence package")
                putExtra(Intent.EXTRA_TEXT, "Geo-awareness evidence package export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share geo-awareness evidence package"))
            refreshEventLogCount()
        } catch (error: Exception) {
            geoEventLogger.logSimple(
                type = GeoAwarenessEventType.EVIDENCE_PACKAGE_EXPORT_FAILED,
                severity = "ERROR",
                message = "Geo-awareness evidence package export failed",
                category = "GEO",
                datasetTitle = datasetInfo?.title,
                datasetVersion = datasetInfo?.version,
                healthState = geoAwarenessHealth?.state?.name,
                details = mapOf("error" to (error.message ?: error::class.java.simpleName))
            )
            refreshEventLogCount()
            showReadableDialog(
                title = "Export evidence package",
                message = "Failed to export evidence package.\n\n${error.message ?: "Unknown error"}"
            )
        }
    }

    private fun exportEncryptedIncidentLogs() {
        try {
            val store = GeoIncidentEncryptedLogStore(requireContext().applicationContext)
            val files = store.getEncryptedLogFiles()
            if (files.isEmpty()) {
                showReadableDialog(
                    title = "Export encrypted geo incident logs",
                    message = getString(R.string.geo_awareness_no_encrypted_incidents)
                )
                return
            }
            val uris = ArrayList<Uri>(files.size)
            files.forEach { file ->
                uris += FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )
            }
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/octet-stream"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, "Encrypted geo incident logs")
                putExtra(Intent.EXTRA_TEXT, "Encrypted geo incident logs export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share encrypted geo incident logs"))
        } catch (error: Exception) {
            showReadableDialog(
                title = "Export encrypted geo incident logs",
                message = "Failed to export encrypted geo incident logs.\n\n${error.message ?: "Unknown error"}"
            )
        }
    }

    private fun exportGeoAwarenessLogs() {
        try {
            val exportFile = geoEventLogger.exportLogsToJson()
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                exportFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Geo-awareness event logs")
                putExtra(Intent.EXTRA_TEXT, "Geo-awareness event log export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share geo-awareness logs"))
            refreshEventLogCount()
        } catch (error: Exception) {
            showReadableDialog(
                title = "Export geo-awareness logs",
                message = "Failed to share geo-awareness logs.\n\n${error.message ?: "Unknown error"}"
            )
        }
    }

    private fun updateLiveStatus() {
        val position = latestRealDronePosition
        val altitude = latestRealDroneAltitudeMeters
        if (position == null) {
            liveStatusJob?.cancel()
            latestLiveZones = emptyList()
            latestLiveProximity = null
            liveStatusBinder?.bindUnknown(getString(R.string.geo_awareness_live_no_position))
            return
        }

        datasetStateCoordinator.loadIfNeeded()
        if (geoZones.isEmpty()) {
            liveStatusJob?.cancel()
            latestLiveZones = emptyList()
            latestLiveProximity = null
            liveStatusBinder?.bindUnknown("Geo-awareness unavailable")
            return
        }
        val altitudeContext = GeoAltitudeContext(
            aglMeters = altitude,
            amslMeters = latestRealDroneAltitudeAmslMeters
        )
        val zoneSnapshot = geoZones
        val groundSpeed = latestRealDroneGroundSpeedMetersPerSecond?.toDouble()
        val verticalSpeed = latestRealDroneVerticalSpeedMetersPerSecond?.toDouble()
        val heading = latestRealDroneHeadingDegrees

        liveStatusJob?.cancel()
        liveStatusJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                val zones = liveChecker.checkDronePosition(
                    dronePosition = position,
                    altitudeContext = altitudeContext,
                    zones = zoneSnapshot
                )
                val proximity = if (zones.isEmpty()) {
                    liveChecker.findNearestZoneWithinThreshold(
                        position = position,
                        zones = zoneSnapshot,
                        thresholdMeters = DEFAULT_NEAR_ZONE_THRESHOLD_METERS,
                        altitudeContext = altitudeContext,
                        groundSpeedMetersPerSecond = groundSpeed,
                        headingDegrees = heading,
                        verticalSpeedMetersPerSecond = verticalSpeed
                    )
                } else {
                    null
                }
                zones to proximity
            }
            if (_binding == null) return@launch
            val zones = result.first
            val proximity = result.second
            latestLiveZones = zones
            latestLiveProximity = proximity
            when {
                zones.isNotEmpty() -> liveStatusBinder?.bindInsideMultiple(zones)
                proximity != null && proximity.warningMode.startsWith("VERTICAL") -> {
                    liveStatusBinder?.bindVerticalNear(
                        zone = proximity.nearestZone,
                        verticalDistanceMeters = proximity.verticalDistanceMeters
                    )
                }
                proximity != null -> liveStatusBinder?.bindNear(
                    zone = proximity.nearestZone,
                    distanceMeters = proximity.distanceMeters
                )
                else -> liveStatusBinder?.bindClear()
            }
        }
    }

    private fun showLiveGeoDetails() {
        val title: String
        val message: String
        val activePosition = latestRealDronePosition
        when {
            activePosition == null -> {
                title = getString(R.string.geo_awareness_title)
                message = getString(R.string.geo_awareness_live_no_position)
            }
            geoZones.isEmpty() -> {
                title = getString(R.string.geo_awareness_title)
                message = getString(R.string.geo_awareness_no_dataset_message)
            }
            else -> {
                when {
                    latestLiveZones.isNotEmpty() -> {
                        title = "Live geo-awareness warning"
                        val visibleZones = latestLiveZones.take(5)
                        val remaining = latestLiveZones.size - visibleZones.size
                        message = buildString {
                            appendLine("Drone is inside loaded geo-zone(s):")
                            appendLine()
                            visibleZones.forEach { zone ->
                                appendLine("- ${zone.name}")
                                appendLine("  Restriction: ${zone.restriction}")
                                appendLine("  Message: ${zone.message ?: "No message"}")
                            }
                            if (remaining > 0) {
                                appendLine("...and $remaining more.")
                            }
                            append("Verify restrictions with the responsible authority before flight.")
                        }
                    }
                    latestLiveProximity != null -> {
                        val proximity = latestLiveProximity!!
                        title = "Nearby geo-zone"
                        message = buildString {
                            appendLine("Nearest zone: ${proximity.nearestZone.name}")
                            appendLine("Restriction: ${proximity.restriction}")
                            appendLine("Distance: ${proximity.distanceMeters.toInt().coerceAtLeast(0)} m")
                            appendLine("Configured threshold: ${proximity.configuredThresholdMeters.toInt()} m")
                            appendLine("Effective threshold: ${proximity.effectiveThresholdMeters.toInt()} m")
                            appendLine("Required warning time: ${proximity.requiredWarningSeconds} s")
                            proximity.groundSpeedMetersPerSecond?.let { speed ->
                                appendLine("Ground speed: ${"%.2f".format(Locale.US, speed)} m/s")
                            }
                            proximity.closingSpeedMetersPerSecond?.let { speed ->
                                appendLine("Closing speed: ${"%.2f".format(Locale.US, speed)} m/s")
                            }
                            proximity.timeToBoundarySeconds?.let { seconds ->
                                appendLine("Time to boundary: ${"%.2f".format(Locale.US, seconds)} s")
                            }
                            appendLine("Warning mode: ${proximity.warningMode}")
                            if (!datasetInfo?.title.isNullOrBlank()) {
                                appendLine("Dataset: ${datasetInfo?.title} (${datasetInfo?.version ?: "N/A"})")
                            }
                            if (!proximity.nearestZone.message.isNullOrBlank()) {
                                appendLine("Message: ${proximity.nearestZone.message}")
                            }
                            appendLine()
                            append("The drone is outside this zone but within the near-zone warning threshold.")
                        }
                    }
                    else -> {
                        title = getString(R.string.geo_awareness_title)
                        message = getString(R.string.geo_awareness_live_clear)
                    }
                }
            }
        }
        showReadableDialog(title, message)
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
        showReadableDialog(getString(R.string.geo_dataset_validation), formatValidationDetails(result))
    }

    private fun showValidationDetails(record: GeoZoneDatasetRecord) {
        showReadableDialog(
            title = getString(R.string.geo_dataset_validation_named, record.displayName),
            message = formatValidationDetails(record.validationResult)
        )
    }

    private fun formatValidationDetails(result: GeoZoneValidationResult): String =
        datasetDialogController.formatValidationDetails(result)

    private fun showReadableDialog(title: String, message: String) {
        datasetDialogController.readable(title, message)
    }

    private fun isUsableDroneLocation(location: Location): Boolean {
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) {
            return false
        }
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) {
            return false
        }
        return kotlin.math.abs(location.latitude) > 1e-4 ||
            kotlin.math.abs(location.longitude) > 1e-4
    }

    private fun friendlyEventLabel(type: GeoAwarenessEventType): String {
        return type.name.lowercase()
            .split('_')
            .joinToString(" ") { token -> token.replaceFirstChar { it.titlecase(Locale.getDefault()) } }
    }

    private fun severityColor(severity: String): Int {
        return when (severity.uppercase(Locale.getDefault())) {
            "ERROR", "BLOCKED" -> Color.parseColor("#FF8A80")
            "WARNING" -> Color.parseColor("#FFB74D")
            else -> Color.parseColor("#C5D0E6")
        }
    }

    override fun onDestroyView() {
        liveStatusJob?.cancel()
        liveStatusJob = null
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
        liveStatusBinder = null
        super.onDestroyView()
        _binding = null
    }
}
