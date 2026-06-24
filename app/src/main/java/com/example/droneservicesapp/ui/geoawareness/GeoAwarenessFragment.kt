package com.example.droneservicesapp.ui.geoawareness

import android.content.res.ColorStateList
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.geoawareness.GeoZoneDatasetValidationException
import com.example.droneservicesapp.data.geoawareness.GeoZoneImportedFileDataSource
import com.example.droneservicesapp.data.geoawareness.GeoZoneRepository
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
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthState
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetStalenessPolicy
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetSourceType
import com.example.droneservicesapp.domain.geoawareness.GeoZoneGeometry
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
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationSeverity
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.home.geoawareness.LiveGeoAwarenessStatusViewBinder
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GeoAwarenessFragment : Fragment() {

    private enum class DatasetPickerMode {
        IMPORT_NEW,
        UPDATE_EXISTING
    }

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
    private var latestRealDroneHeadingDegrees: Double? = null
    private var geoAwarenessHealth: GeoAwarenessHealth? = null
    private var geoAwarenessLoadError: Throwable? = null
    private var validationResult: GeoZoneValidationResult? = null
    private var importedDatasetActive: Boolean = false
    private var datasetRecords: List<GeoZoneDatasetRecord> = emptyList()
    private lateinit var geoEventLogger: GeoAwarenessEventLogger
    private var liveStatusBinder: LiveGeoAwarenessStatusViewBinder? = null
    private var pendingDatasetPickerMode: DatasetPickerMode = DatasetPickerMode.IMPORT_NEW
    private var pendingDatasetFileNameToUpdate: String? = null
    private var lastStaleSignature: String? = null
    private var lastTestRunResult: GeoAwarenessTestRunResult? = null
    private var datasetLoadInProgress: Boolean = false
    private var lastGeoZoneReloadToken: Long? = null
    private var liveStatusJob: Job? = null
    private lateinit var verificationStatusStore: GeoAwarenessVerificationStatusStore
    private val importDatasetLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            handleImportedDatasetUri(uri)
        }
    }

    companion object {
        private const val MAX_IMPORT_BYTES = 5L * 1024L * 1024L
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
        activityViewModel = ViewModelProvider(requireActivity())[MainActivityViewModel::class.java]
        droneViewModel = ViewModelProvider(requireActivity())[DroneViewModel::class.java]
        geoEventLogger = GeoAwarenessEventLogger(requireContext().applicationContext)
        verificationStatusStore = GeoAwarenessVerificationStatusStore(requireContext().applicationContext)

        requireActivity().findViewById<View>(R.id.bottom_nav_view)?.isVisible = false
        liveStatusBinder = LiveGeoAwarenessStatusViewBinder(
            requireContext(),
            binding.geoAwarenessLiveStatusChip
        )
        liveStatusBinder?.bindUnknown(getString(R.string.geo_awareness_live_no_position))
        liveStatusBinder?.setOnClickListener(View.OnClickListener {
            showLiveGeoDetails()
        })

        binding.geoAwarenessFlightLogsSectionTitle.isVisible = false
        binding.geoAwarenessFlightLogsSection.isVisible = false
        binding.geoAwarenessLogsSectionTitle.isVisible = false
        binding.geoAwarenessLogsSection.isVisible = false
        binding.geoAwarenessClearLogsButton.isVisible = false
        binding.geoAwarenessInternalSectionTitle.isVisible = true
        binding.geoAwarenessInternalSection.isVisible = true
        binding.geoAwarenessOverlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (activityViewModel.geoAwarenessLayerVisible.value != isChecked) {
                activityViewModel.geoAwarenessLayerVisible.value = isChecked
            }
        }
        binding.geoAwarenessImportDatasetButton.setOnClickListener {
            launchImportDatasetPicker()
        }
        binding.geoAwarenessResetDatasetButton.setOnClickListener {
            confirmRemoveAllImportedDatasets()
        }
        binding.geoAwarenessRefreshStatusButton.setOnClickListener {
            refreshGeoAwarenessStatus(manual = true)
        }
        binding.geoAwarenessValidationDetailsButton.setOnClickListener {
            showValidationDetails()
        }
        binding.geoAwarenessNoticeSectionTitle.isVisible = false
        binding.geoAwarenessValidationSection.isVisible = false
        binding.geoAwarenessExportLogsButton.setOnClickListener {
            exportGeoAwarenessLogs()
        }
        binding.geoAwarenessViewDetailedLogsButton.setOnClickListener {
            showDetailedLogsPreview()
        }
        binding.geoAwarenessExportEvidenceButton.setOnClickListener {
            exportEvidencePackage()
        }
        binding.geoAwarenessExportEncryptedIncidentsButton.setOnClickListener {
            exportEncryptedIncidentLogs()
        }
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
        loadDatasetIfNeededAsync()
    }

    override fun onResume() {
        super.onResume()
        loadDatasetIfNeededAsync()
        updateLiveStatus()
    }

    private fun observeSharedState() {
        activityViewModel.geoAwarenessLayerVisible.observe(viewLifecycleOwner) { visible ->
            binding.geoAwarenessOverlaySwitch.isChecked = visible ?: true
        }

            activityViewModel.geoZoneDatasetInfo.observe(viewLifecycleOwner) { info ->
            if (info != null) {
                datasetInfo = info
                updateCurrentSourceSummary()
            }
        }

        activityViewModel.geoZoneValidationResult.observe(viewLifecycleOwner) { result ->
            validationResult = result
            renderValidationStatus(result)
        }

        activityViewModel.geoZoneDatasetRecords.observe(viewLifecycleOwner) { records ->
            datasetRecords = records ?: emptyList()
            renderDatasetRecords()
            updateCurrentSourceSummary()
        }

        activityViewModel.geoZoneImportedActive.observe(viewLifecycleOwner) { imported ->
            importedDatasetActive = imported == true
            updateCurrentSourceSummary()
        }

        activityViewModel.geoAwarenessHealth.observe(viewLifecycleOwner) { health ->
            geoAwarenessHealth = health
            renderHealthStatus(health)
        }

        activityViewModel.geoZoneReloadToken.observe(viewLifecycleOwner) { token ->
            if (token == null || token <= 0L || token == lastGeoZoneReloadToken) {
                return@observe
            }
            lastGeoZoneReloadToken = token
            if (!datasetLoadInProgress) {
                geoZones = emptyList()
                datasetInfo = null
                loadDatasetIfNeededAsync(forceReload = true)
            }
        }

    }

    private fun observeDroneLocation() {
        droneViewModel.droneLocationLiveData.observe(viewLifecycleOwner) { location ->
            val usableLocation = location?.takeIf(::isUsableDroneLocation)
            latestRealDronePosition = usableLocation?.let { LatLon(lat = it.latitude, lon = it.longitude) }
            latestRealDroneAltitudeMeters = usableLocation?.altitude
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
        droneViewModel.droneHeading.observe(viewLifecycleOwner) { heading ->
            latestRealDroneHeadingDegrees = heading
            updateLiveStatus()
        }
    }

    private fun loadDatasetIfNeeded() {
        if (geoZones.isNotEmpty() && datasetInfo != null) {
            return
        }

        val sharedInfo = activityViewModel.geoZoneDatasetInfo.value
        val sharedValidation = activityViewModel.geoZoneValidationResult.value
        val sharedRecords = activityViewModel.geoZoneDatasetRecords.value.orEmpty()
        if (sharedInfo != null && geoZones.isNotEmpty()) {
            datasetInfo = sharedInfo
            validationResult = sharedValidation
            datasetRecords = sharedRecords
            return
        }

        loadDatasetIfNeededAsync()
    }

    private fun loadDatasetIfNeededAsync(forceReload: Boolean = false) {
        if (datasetLoadInProgress || _binding == null) return
        if (!forceReload && geoZones.isNotEmpty() && datasetInfo != null) return
        val sharedInfo = activityViewModel.geoZoneDatasetInfo.value
        if (!forceReload && sharedInfo != null && activityViewModel.geoZoneDatasetRecords.value != null) {
            datasetInfo = sharedInfo
            validationResult = activityViewModel.geoZoneValidationResult.value
            datasetRecords = activityViewModel.geoZoneDatasetRecords.value.orEmpty()
            importedDatasetActive = activityViewModel.geoZoneImportedActive.value == true
            updateCurrentSourceSummary()
            renderDatasetRecords()
            renderValidationStatus(validationResult)
            activityViewModel.geoAwarenessHealth.value?.let(::renderHealthStatus)
            return
        }

        datasetLoadInProgress = true
        setDatasetBusyState(true, "Loading geo-zone dataset...")
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            try {
                val (loadResult, importedActive) = withContext(Dispatchers.IO) {
                    val repository = GeoZoneRepository(
                        importedFileDataSource = GeoZoneImportedFileDataSource(appContext)
                    )
                    repository.loadCurrentDataset() to repository.hasImportedDatasets()
                }
                if (_binding != null) {
                    applyLoadedDataset(loadResult, importedActive)
                }
            } catch (error: Exception) {
                if (_binding != null) {
                    applyDatasetLoadError(error)
                }
            } finally {
                datasetLoadInProgress = false
                if (_binding != null) {
                    setDatasetBusyState(false)
                }
            }
        }
    }

    private fun applyDatasetLoadError(error: Throwable) {
        datasetInfo = null
        geoZones = emptyList()
        geoAwarenessLoadError = error
        val health = GeoAwarenessHealthEvaluator.evaluate(
            datasetInfo = null,
            zones = emptyList(),
            datasetRecords = emptyList(),
            loadError = error
        )
        geoAwarenessHealth = health
        activityViewModel.geoAwarenessHealth.value = health
        validationResult = null
        activityViewModel.geoZoneValidationResult.value = null
        datasetRecords = emptyList()
        activityViewModel.geoZoneDatasetRecords.value = emptyList()
        importedDatasetActive = false
        activityViewModel.geoZoneImportedActive.value = false
        updateCurrentSourceSummary()
        renderDatasetRecords()
        renderValidationStatus(null)
        renderHealthStatus(health)
    }

    private fun applyLoadedDataset(
        loadResult: com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult,
        importedActive: Boolean
    ) {
        geoZones = loadResult.zones
        datasetInfo = loadResult.datasetInfo
        validationResult = loadResult.validationResult
        importedDatasetActive = importedActive
        datasetRecords = loadResult.datasetRecords
        geoAwarenessLoadError = null
        val health = GeoAwarenessHealthEvaluator.evaluate(
            datasetInfo = loadResult.datasetInfo,
            zones = loadResult.zones,
            datasetRecords = loadResult.datasetRecords,
            validationResult = loadResult.validationResult
        )
        geoAwarenessHealth = health
        activityViewModel.geoZoneDatasetInfo.value = loadResult.datasetInfo
        activityViewModel.geoZoneValidationResult.value = loadResult.validationResult
        activityViewModel.geoZoneDatasetRecords.value = loadResult.datasetRecords
        activityViewModel.geoAwarenessHealth.value = health
        activityViewModel.geoZoneImportedActive.value = importedActive
        updateCurrentSourceSummary()
        renderDatasetRecords()
        renderValidationStatus(loadResult.validationResult)
        renderHealthStatus(health)
        logStaleDatasetsIfNeeded(loadResult.datasetRecords, health, manualRefresh = false)
        updateLiveStatus()
    }

    private fun updateCurrentSourceSummary() {
        if (_binding == null) return
        val sourceLabel = if (importedDatasetActive) {
            getString(R.string.geo_awareness_current_source_imported)
        } else {
            getString(R.string.geo_awareness_current_source_none)
        }
        binding.geoAwarenessCurrentSource.text =
            getString(R.string.geo_awareness_current_source_label) + " " + sourceLabel
        binding.geoAwarenessLoadedDatasetsSummary.text =
            getString(R.string.geo_awareness_loaded_datasets_label) + " " + datasetRecords.size
        binding.geoAwarenessTotalZonesSummary.text =
            getString(R.string.geo_awareness_total_zones_label) + " " + (datasetInfo?.zoneCount ?: datasetRecords.sumOf { it.zoneCount })
        val validation = validationResult ?: GeoZoneValidationResult.ok()
        binding.geoAwarenessTotalValidationSummary.text =
            getString(R.string.geo_awareness_total_validation_label) + " Errors ${validation.errorCount} | Warnings ${validation.warningCount}"
    }

    private fun renderDatasetRecords() {
        if (_binding == null) return
        val container = binding.geoAwarenessDatasetRecordsContainer
        container.removeAllViews()
        if (datasetRecords.isEmpty()) {
            container.addView(createPanelText(getString(R.string.geo_awareness_dataset_list_empty)).apply {
                setPadding(0, 0, 0, 0)
            })
            return
        }

        datasetRecords.forEachIndexed { index, record ->
            container.addView(createDatasetRecordView(record, topMarginDp = if (index == 0) 0 else 12))
        }
    }

    private fun createDatasetRecordView(record: GeoZoneDatasetRecord, topMarginDp: Int): View {
        val wrapper = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (10 * resources.displayMetrics.density)
                setColor(Color.parseColor("#7010151C"))
                setStroke((1 * resources.displayMetrics.density).toInt(), Color.parseColor("#2EFFFFFF"))
            }
            setPadding(
                (12 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (topMarginDp * resources.displayMetrics.density).toInt()
            }
        }
        val validationLabel = when {
            record.validationResult.hasErrors -> getString(R.string.geo_awareness_validation_errors)
            record.validationResult.hasWarnings -> getString(R.string.geo_awareness_validation_warnings)
            else -> getString(R.string.geo_awareness_validation_ok)
        }
        wrapper.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(createStatusValue(record.displayName).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(createValidationPill(record.validationResult, validationLabel))
        })
        wrapper.addView(createPanelText("Source: " + when (record.sourceType) {
            GeoZoneDatasetSourceType.BUNDLED_ASSET -> getString(R.string.geo_awareness_dataset_source_bundled_row)
            GeoZoneDatasetSourceType.IMPORTED_FILE -> getString(R.string.geo_awareness_dataset_source_imported_row)
        }).apply {
            setPadding(0, (10 * resources.displayMetrics.density).toInt(), 0, 0)
        })
        wrapper.addView(createDatasetMetaGrid(record))
        wrapper.addView(createDatasetValidationDetailsButton(record))
        if (record.sourceType == GeoZoneDatasetSourceType.IMPORTED_FILE) {
            wrapper.addView(createPanelText("${getString(R.string.geo_awareness_updated_label)} ${record.ageDescription ?: GeoZoneDatasetStalenessPolicy.ageDescription(record.updatedAtMillis)}"))
            wrapper.addView(createPanelText(
                text = "${getString(R.string.geo_awareness_stale_label)} ${if (record.isStale) getString(R.string.geo_awareness_yes) else getString(R.string.geo_awareness_no)}",
                textColor = if (record.isStale) Color.parseColor("#FFB74D") else Color.parseColor("#C5D0E6")
            ))
        }
        if (record.sourceType == GeoZoneDatasetSourceType.IMPORTED_FILE && record.storageFileName != null) {
            wrapper.addView(createPanelText("Actions").apply {
                setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
            })
            val actionsRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (8 * resources.displayMetrics.density).toInt()
                }
            }
            actionsRow.addView(createDatasetActionButton(
                text = getString(R.string.geo_awareness_update_dataset),
                backgroundColor = ContextCompat.getColor(requireContext(), R.color.ds_color_shell_selected_surface),
                textColor = ContextCompat.getColor(requireContext(), R.color.ds_color_shell_selected_content),
                onClick = { launchUpdateDatasetPicker(record) }
            ))
            actionsRow.addView(createDatasetActionButton(
                text = getString(R.string.geo_awareness_remove_dataset),
                backgroundColor = ContextCompat.getColor(requireContext(), R.color.ds_color_shell_danger),
                textColor = ContextCompat.getColor(requireContext(), R.color.ds_color_text_primary),
                onClick = { confirmRemoveDataset(record) },
                marginStartDp = 8
            ))
            wrapper.addView(actionsRow)
        }
        return wrapper
    }

    private fun createDatasetActionButton(
        text: String,
        backgroundColor: Int,
        textColor: Int,
        strokeColor: Int? = null,
        marginStartDp: Int = 0,
        onClick: () -> Unit
    ): com.google.android.material.button.MaterialButton {
        return com.google.android.material.button.MaterialButton(requireContext()).apply {
            this.text = text
            minWidth = 0
            insetTop = 0
            insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(backgroundColor)
            setTextColor(textColor)
            cornerRadius = resources.getDimensionPixelSize(R.dimen.ds_radius_round)
            strokeColor?.let {
                this.strokeColor = ColorStateList.valueOf(it)
                strokeWidth = (1 * resources.displayMetrics.density).toInt()
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                0,
                (44 * resources.displayMetrics.density).toInt(),
                1f
            ).apply {
                marginStart = (marginStartDp * resources.displayMetrics.density).toInt()
            }
        }
    }

    private fun createDatasetMetaGrid(record: GeoZoneDatasetRecord): LinearLayout {
        val grid = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (10 * resources.displayMetrics.density).toInt()
            }
        }
        grid.addView(createDatasetMetaRow(
            "Version",
            record.datasetInfo.version ?: "N/A",
            "Zones",
            record.zoneCount.toString()
        ))
        grid.addView(createDatasetMetaRow(
            "Country",
            record.datasetInfo.country ?: "N/A",
            "Type",
            if (record.datasetInfo.isDummy) "Test / dummy" else "Validated JSON"
        ))
        grid.addView(createDatasetMetaRow(
            "Errors",
            record.validationResult.errorCount.toString(),
            "Warnings",
            record.validationResult.warningCount.toString()
        ))
        return grid
    }

    private fun createDatasetMetaRow(
        leftLabel: String,
        leftValue: String,
        rightLabel: String,
        rightValue: String
    ): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (4 * resources.displayMetrics.density).toInt()
            }
            addView(createDatasetMetaCell(leftLabel, leftValue).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(createDatasetMetaCell(rightLabel, rightValue).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (8 * resources.displayMetrics.density).toInt()
                }
            })
        }
    }

    private fun createDatasetMetaCell(label: String, value: String): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (8 * resources.displayMetrics.density)
                setColor(Color.parseColor("#661B2430"))
                setStroke((1 * resources.displayMetrics.density).toInt(), Color.parseColor("#1FFFFFFF"))
            }
            setPadding(
                (10 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt()
            )
            addView(TextView(requireContext()).apply {
                text = label
                setTextColor(Color.parseColor("#8FA0B8"))
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(requireContext()).apply {
                text = value
                setTextColor(Color.parseColor("#EAF1FF"))
                textSize = 13f
                maxLines = 2
            })
        }
    }

    private fun createValidationPill(
        result: GeoZoneValidationResult,
        label: String
    ): TextView {
        val (backgroundColor, textColor) = when {
            result.hasErrors -> Color.parseColor("#B71C1C") to Color.WHITE
            result.hasWarnings -> Color.parseColor("#E65100") to Color.WHITE
            else -> Color.parseColor("#2E7D32") to Color.WHITE
        }
        return TextView(requireContext()).apply {
            text = label
            setTextColor(textColor)
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(
                (10 * resources.displayMetrics.density).toInt(),
                (6 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(),
                (6 * resources.displayMetrics.density).toInt()
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (16 * resources.displayMetrics.density)
                setColor(backgroundColor)
            }
        }
    }

    private fun createDatasetValidationDetailsButton(record: GeoZoneDatasetRecord): com.google.android.material.button.MaterialButton {
        return com.google.android.material.button.MaterialButton(requireContext()).apply {
            text = getString(R.string.geo_awareness_view_validation_details)
            minWidth = 0
            insetTop = 0
            insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.ds_color_shell_selected_surface))
            setTextColor(ContextCompat.getColor(requireContext(), R.color.ds_color_shell_selected_content))
            cornerRadius = resources.getDimensionPixelSize(R.dimen.ds_radius_round)
            setOnClickListener { showValidationDetails(record) }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (40 * resources.displayMetrics.density).toInt()
            ).apply {
                topMargin = (10 * resources.displayMetrics.density).toInt()
            }
        }
    }

    private fun createStatusValue(text: String): TextView = TextView(requireContext()).apply {
        this.text = text
        setTextColor(Color.parseColor("#EAF1FF"))
        textSize = 15f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun createPanelText(
        text: String,
        textColor: Int = Color.parseColor("#C5D0E6")
    ): TextView = TextView(requireContext()).apply {
        this.text = text
        setTextColor(textColor)
        textSize = 13f
        setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, 0)
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

        val (label, backgroundColor, textColor) = when (resolvedHealth.state) {
            GeoAwarenessHealthState.AVAILABLE -> Triple(
                getString(R.string.geo_awareness_health_available),
                Color.parseColor("#2E7D32"),
                Color.WHITE
            )
            GeoAwarenessHealthState.DEGRADED -> Triple(
                getString(R.string.geo_awareness_health_degraded),
                Color.parseColor("#E65100"),
                Color.WHITE
            )
            GeoAwarenessHealthState.STALE -> Triple(
                getString(R.string.geo_awareness_health_stale),
                Color.parseColor("#EF6C00"),
                Color.WHITE
            )
            GeoAwarenessHealthState.UNAVAILABLE -> Triple(
                getString(R.string.geo_awareness_health_unavailable),
                Color.parseColor("#B71C1C"),
                Color.WHITE
            )
        }

        binding.geoAwarenessHealthChip.text = getString(R.string.geo_awareness_health_label) + " " + label
        binding.geoAwarenessHealthChip.setTextColor(textColor)
        binding.geoAwarenessHealthChip.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = (18 * resources.displayMetrics.density)
            setColor(backgroundColor)
            setStroke((1 * resources.displayMetrics.density).toInt(), Color.parseColor("#33FFFFFF"))
        }
        binding.geoAwarenessHealthMessage.text = resolvedHealth.message
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
                .setMessage("Reset all verification checklist statuses to Not Run?")
                .setPositiveButton("Reset") { _, _ ->
                    verificationStatusStore.resetAll()
                    geoEventLogger.logSimple(
                        type = GeoAwarenessEventType.VERIFICATION_CHECKLIST_RESET,
                        severity = "INFO",
                        message = "Geo-awareness verification checklist statuses reset"
                    )
                    refreshEventLogCount()
                    renderChecklist()
                }
                .setNegativeButton("Cancel", null)
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
        wrapper.addView(createPanelText("Current status: ${verificationStatusLabel(status)}", Color.parseColor("#42536F")))
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
            appendLine("Status: ${verificationStatusLabel(status)}")
            appendLine()
            appendLine("Purpose")
            appendLine(verificationCase.purpose)
            appendLine()
            appendLine("Preconditions")
            if (verificationCase.preconditions.isEmpty()) {
                appendLine("- None")
            } else {
                verificationCase.preconditions.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("Steps")
            verificationCase.steps.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Expected result")
            appendLine(verificationCase.expectedResult)
            appendLine()
            appendLine("Evidence to capture")
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
        val resolved = result ?: GeoZoneValidationResult.ok()
        val (label, backgroundColor) = when {
            resolved.hasErrors -> getString(R.string.geo_awareness_validation_errors) to Color.parseColor("#B71C1C")
            resolved.hasWarnings -> getString(R.string.geo_awareness_validation_warnings) to Color.parseColor("#EF6C00")
            else -> getString(R.string.geo_awareness_validation_ok) to Color.parseColor("#2E7D32")
        }
        binding.geoAwarenessValidationChip.text = "Validation: $label"
        binding.geoAwarenessValidationChip.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = (18 * resources.displayMetrics.density)
            setColor(backgroundColor)
            setStroke((1 * resources.displayMetrics.density).toInt(), Color.parseColor("#33FFFFFF"))
        }
        binding.geoAwarenessValidationCounts.text =
            "Errors: ${resolved.errorCount}  Warnings: ${resolved.warningCount}  Info: ${resolved.infoCount}"
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
                repository = buildRepository(),
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

        loadDatasetIfNeeded()
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
                        headingDegrees = heading
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

    private fun launchImportDatasetPicker() {
        pendingDatasetPickerMode = DatasetPickerMode.IMPORT_NEW
        pendingDatasetFileNameToUpdate = null
        importDatasetLauncher.launch(arrayOf("application/json", "text/json", "text/plain", "*/*"))
    }

    private fun launchUpdateDatasetPicker(record: GeoZoneDatasetRecord) {
        pendingDatasetPickerMode = DatasetPickerMode.UPDATE_EXISTING
        pendingDatasetFileNameToUpdate = record.storageFileName
        importDatasetLauncher.launch(arrayOf("application/json", "text/json", "text/plain", "*/*"))
    }

    private fun handleImportedDatasetUri(uri: Uri) {
        val originalFileName = resolveDisplayName(uri)
        when (pendingDatasetPickerMode) {
            DatasetPickerMode.IMPORT_NEW -> handleDatasetImport(uri, originalFileName)
            DatasetPickerMode.UPDATE_EXISTING -> handleDatasetUpdate(uri, originalFileName)
        }
    }

    private fun handleDatasetImport(uri: Uri, originalFileName: String?) {
        if (datasetLoadInProgress) return
        datasetLoadInProgress = true
        setDatasetBusyState(true, "Importing geo-zone dataset...")
        geoEventLogger.logSimple(
            type = GeoAwarenessEventType.DATASET_IMPORT_STARTED,
            severity = "INFO",
            message = "Geo-zone dataset import started",
            details = buildMap {
                put("uri", uri.toString())
                originalFileName?.let { put("originalFileName", it) }
            }
        )
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            try {
                val loadResult = withContext(Dispatchers.IO) {
                    val rawJson = readUtf8FromUri(uri)
                    buildRepository(appContext).importDataset(rawJson, originalFileName)
                }
                if (_binding == null) return@launch
                applyLoadedDataset(loadResult, importedActive = true)
                lastGeoZoneReloadToken = activityViewModel.notifyGeoZoneDatasetReloaded()
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.DATASET_IMPORT_SUCCEEDED,
                    severity = "INFO",
                    message = "Geo-zone dataset import succeeded",
                    datasetTitle = loadResult.datasetInfo.title,
                    datasetVersion = loadResult.datasetInfo.version,
                    healthState = geoAwarenessHealth?.state?.name,
                    details = standardDatasetLogDetails(
                        operation = "manual_import",
                        loadResult = loadResult,
                        requestedUri = uri.toString(),
                        originalFileName = originalFileName
                    ) + mapOf(
                        "addedDatasetTitle" to loadResult.datasetRecords.lastOrNull()?.displayName.orEmpty()
                    )
                )
                refreshEventLogCount()
                val warningSuffix = if (loadResult.validationResult.warningCount > 0) {
                    "\nValidation warnings: ${loadResult.validationResult.warningCount}"
                } else {
                    ""
                }
                showReadableDialog(
                    title = "Import complete",
                    message = "${getString(R.string.geo_awareness_import_success)}\n\nZones loaded: ${loadResult.datasetInfo.zoneCount}$warningSuffix"
                )
            } catch (error: GeoZoneDatasetValidationException) {
                if (_binding != null) {
                    showImportFailure(error, error.validationResult)
                }
            } catch (error: Exception) {
                if (_binding != null) {
                    showImportFailure(error, null)
                }
            } finally {
                datasetLoadInProgress = false
                if (_binding != null) {
                    setDatasetBusyState(false)
                }
            }
        }
    }

    private fun handleDatasetUpdate(uri: Uri, originalFileName: String?) {
        val storageFileName = pendingDatasetFileNameToUpdate
            ?: throw IllegalStateException("No imported dataset selected for update.")
        if (datasetLoadInProgress) return
        datasetLoadInProgress = true
        setDatasetBusyState(true, "Updating geo-zone dataset...")
        geoEventLogger.logSimple(
            type = GeoAwarenessEventType.DATASET_UPDATE_STARTED,
            severity = "INFO",
            message = "Geo-zone dataset update started",
            details = buildMap {
                put("uri", uri.toString())
                put("storageFileName", storageFileName)
                originalFileName?.let { put("originalFileName", it) }
            }
        )
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            try {
                val updateResult = withContext(Dispatchers.IO) {
                    val rawJson = readUtf8FromUri(uri)
                    val repository = buildRepository(appContext)
                    val loadResult = repository.updateImportedDataset(storageFileName, rawJson, originalFileName)
                    loadResult to repository.hasImportedDatasets()
                }
                if (_binding == null) return@launch
                val (loadResult, importedActive) = updateResult
                applyLoadedDataset(loadResult, importedActive = importedActive)
                lastGeoZoneReloadToken = activityViewModel.notifyGeoZoneDatasetReloaded()
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.DATASET_UPDATE_SUCCEEDED,
                    severity = "INFO",
                    message = "Geo-zone dataset update succeeded",
                    datasetTitle = loadResult.datasetInfo.title,
                    datasetVersion = loadResult.datasetInfo.version,
                    healthState = geoAwarenessHealth?.state?.name,
                    details = standardDatasetLogDetails(
                        operation = "manual_update",
                        loadResult = loadResult,
                        requestedUri = uri.toString(),
                        originalFileName = originalFileName,
                        storageFileName = storageFileName
                    ) + mapOf(
                        "storageFileName" to storageFileName
                    )
                )
                refreshEventLogCount()
                Toast.makeText(requireContext(), "Dataset updated successfully", Toast.LENGTH_SHORT).show()
            } catch (error: GeoZoneDatasetValidationException) {
                if (_binding != null) {
                    showDatasetUpdateFailure(storageFileName, error, error.validationResult)
                }
            } catch (error: Exception) {
                if (_binding != null) {
                    showDatasetUpdateFailure(storageFileName, error, null)
                }
            } finally {
                pendingDatasetPickerMode = DatasetPickerMode.IMPORT_NEW
                pendingDatasetFileNameToUpdate = null
                datasetLoadInProgress = false
                if (_binding != null) {
                    setDatasetBusyState(false)
                }
            }
        }
    }

    private fun confirmRemoveAllImportedDatasets() {
        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle("Remove imported datasets?")
            .setMessage("This will remove all imported geo-zone datasets. Geo-awareness will be unavailable until a JSON dataset is imported.")
            .setPositiveButton("Remove all") { _, _ ->
                removeAllImportedDatasets()
            }
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
    }

    private fun removeAllImportedDatasets() {
        try {
            val repository = buildRepository()
            val removedCount = datasetRecords.count { it.sourceType == GeoZoneDatasetSourceType.IMPORTED_FILE }
            val loadResult = repository.removeAllImportedDatasets()
            applyLoadedDataset(loadResult, importedActive = false)
            lastGeoZoneReloadToken = activityViewModel.notifyGeoZoneDatasetReloaded()
            if (removedCount > 0) {
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.ALL_IMPORTED_DATASETS_REMOVED,
                    severity = "INFO",
                    message = "All imported geo-zone datasets removed",
                    details = mapOf("removedCount" to removedCount.toString())
                )
            }
            refreshEventLogCount()
            Toast.makeText(requireContext(), "Imported geo-zone datasets removed", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            showReadableDialog(
                title = "Remove failed",
                message = error.message ?: "Failed to remove imported datasets."
            )
        }
    }

    private fun confirmRemoveDataset(record: GeoZoneDatasetRecord) {
        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle("Remove geo-zone dataset?")
            .setMessage("Remove ${record.displayName} from active geo-awareness datasets?")
            .setPositiveButton("Remove") { _, _ ->
                removeDataset(record)
            }
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
    }

    private fun removeDataset(record: GeoZoneDatasetRecord) {
        val fileName = record.storageFileName ?: return
        try {
            val repository = buildRepository()
            val loadResult = repository.removeImportedDataset(fileName)
            applyLoadedDataset(loadResult, importedActive = repository.hasImportedDatasets())
            lastGeoZoneReloadToken = activityViewModel.notifyGeoZoneDatasetReloaded()
            geoEventLogger.logSimple(
                type = GeoAwarenessEventType.DATASET_REMOVED,
                severity = "INFO",
                message = "Imported geo-zone dataset removed",
                datasetTitle = record.displayName,
                datasetVersion = record.datasetInfo.version,
                healthState = geoAwarenessHealth?.state?.name,
                details = mapOf(
                    "datasetTitle" to record.displayName,
                    "storageFileName" to fileName,
                    "activeDatasetCount" to loadResult.datasetRecords.size.toString(),
                    "totalZones" to loadResult.datasetInfo.zoneCount.toString()
                )
            )
            refreshEventLogCount()
        } catch (error: Exception) {
            showReadableDialog("Remove failed", error.message ?: "Failed to remove imported dataset.")
        }
    }

    private fun refreshGeoAwarenessStatus(manual: Boolean) {
        if (datasetLoadInProgress) return
        datasetLoadInProgress = true
        setDatasetBusyState(true, "Refreshing geo-zone status...")
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            try {
                val (loadResult, importedActive) = withContext(Dispatchers.IO) {
                    val repository = GeoZoneRepository(
                        importedFileDataSource = GeoZoneImportedFileDataSource(appContext)
                    )
                    repository.loadCurrentDataset() to repository.hasImportedDatasets()
                }
                if (_binding == null) return@launch
                applyLoadedDataset(loadResult, importedActive = importedActive)
                geoAwarenessHealth?.let { logStaleDatasetsIfNeeded(loadResult.datasetRecords, it, manualRefresh = true) }
                lastGeoZoneReloadToken = activityViewModel.notifyGeoZoneDatasetReloaded()
                if (manual) {
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
                            "warningCount" to loadResult.validationResult.warningCount.toString()
                        ) + standardDatasetLogDetails(
                            operation = "status_refresh",
                            loadResult = loadResult,
                            requestedUri = "not_applicable",
                            originalFileName = null
                        )
                    )
                    Toast.makeText(requireContext(), "Geo-awareness status refreshed", Toast.LENGTH_SHORT).show()
                }
            } catch (error: Exception) {
                if (_binding != null) {
                    showReadableDialog("Refresh failed", error.message ?: "Failed to refresh dataset status.")
                }
            } finally {
                datasetLoadInProgress = false
                if (_binding != null) {
                    setDatasetBusyState(false)
                }
            }
        }
    }

    private fun readUtf8FromUri(uri: Uri): String {
        val resolver = requireContext().contentResolver
        resolver.openInputStream(uri)?.use { inputStream ->
            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            while (true) {
                val read = inputStream.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                if (output.size().toLong() > MAX_IMPORT_BYTES) {
                    throw IllegalStateException("Selected file exceeds the 5 MB import limit.")
                }
            }
            val rawJson = output.toString(Charsets.UTF_8.name())
            if (rawJson.isBlank()) {
                throw IllegalStateException("Selected geo-zone file is empty.")
            }
            return rawJson
        }
        throw IllegalStateException("Failed to open selected geo-zone file.")
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return requireContext().contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    ?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }

    private fun standardDatasetLogDetails(
        operation: String,
        loadResult: GeoZoneLoadResult,
        requestedUri: String,
        originalFileName: String?,
        storageFileName: String? = null
    ): Map<String, String> {
        val activeRecords = loadResult.datasetRecords
        val countries = activeRecords
            .mapNotNull { it.datasetInfo.country?.takeIf(String::isNotBlank) }
            .distinct()
            .joinToString(", ")
            .ifBlank { "not_specified" }
        val applicability = loadResult.zones.flatMap { it.applicability }
        val timeWindowStart = applicability.mapNotNull { it.startDateTime?.takeIf(String::isNotBlank) }.minOrNull()
        val timeWindowEnd = applicability.mapNotNull { it.endDateTime?.takeIf(String::isNotBlank) }.maxOrNull()

        return buildMap {
            put("standardLogSchema", "prEN4709-003-5.4-transaction-scope-v1")
            put("operation", operation)
            put("retrievalMethod", "manual_file_import")
            put("retrievalRequestUri", requestedUri)
            put("retrievalRequestUtc", isoUtc(System.currentTimeMillis()))
            put("officialRetrievalService", "not_implemented")
            put("subscriptionId", "not_applicable_manual_import")
            put("changePublicationId", "not_applicable_manual_import")
            put("originalFileName", originalFileName ?: "unknown")
            storageFileName?.let { put("storageFileName", it) }
            put("datasetTitle", loadResult.datasetInfo.title)
            put("datasetVersion", loadResult.datasetInfo.version ?: "not_specified")
            put("datasetSource", loadResult.datasetInfo.source ?: "not_specified")
            put("datasetSourceUrl", loadResult.datasetInfo.sourceUrl ?: "not_specified")
            put("datasetCountries", countries)
            put("scopeArea", boundingBox(loadResult.zones) ?: countries)
            put("scopeRegionOfInterest", countries)
            put("scopeTimeWindowStart", timeWindowStart ?: "not_provided_by_dataset")
            put("scopeTimeWindowEnd", timeWindowEnd ?: "not_provided_by_dataset")
            put("permanentApplicabilityCount", applicability.count { it.permanent }.toString())
            put("verticalReferenceSummary", verticalReferenceSummary(loadResult.zones))
            put("altitudeUnitSummary", altitudeUnitSummary(loadResult.zones))
            put("activeDatasetCount", activeRecords.size.toString())
            put("activeDatasetIds", activeRecords.joinToString(",") { it.datasetId })
            put("activeDatasetTitles", activeRecords.joinToString(" | ") { it.displayName })
            put("totalZones", loadResult.datasetInfo.zoneCount.toString())
            put("errorCount", loadResult.validationResult.errorCount.toString())
            put("warningCount", loadResult.validationResult.warningCount.toString())
            put("infoCount", loadResult.validationResult.infoCount.toString())
        }
    }

    private fun boundingBox(zones: List<GeoZone>): String? {
        val points = zones.flatMap { zone ->
            zone.geometries.flatMap { geometry ->
                when (geometry) {
                    is GeoZoneGeometry.Circle -> listOf(
                        LatLon(geometry.center.lat - metersToLatitudeDegrees(geometry.radiusMeters), geometry.center.lon),
                        LatLon(geometry.center.lat + metersToLatitudeDegrees(geometry.radiusMeters), geometry.center.lon),
                        LatLon(geometry.center.lat, geometry.center.lon - metersToLongitudeDegrees(geometry.radiusMeters, geometry.center.lat)),
                        LatLon(geometry.center.lat, geometry.center.lon + metersToLongitudeDegrees(geometry.radiusMeters, geometry.center.lat))
                    )
                    is GeoZoneGeometry.Polygon -> geometry.rings.flatten()
                }
            }
        }
        if (points.isEmpty()) return null
        return "bbox=${points.minOf { it.lon }},${points.minOf { it.lat }},${points.maxOf { it.lon }},${points.maxOf { it.lat }}"
    }

    private fun verticalReferenceSummary(zones: List<GeoZone>): String {
        val references = zones.flatMap { zone ->
            zone.geometries.flatMap { geometry ->
                listOf(geometry.lowerVerticalReference.name, geometry.upperVerticalReference.name)
            }
        }.groupingBy { it }.eachCount()
        return references.entries.joinToString(",") { "${it.key}:${it.value}" }.ifBlank { "none" }
    }

    private fun altitudeUnitSummary(zones: List<GeoZone>): String {
        val units = zones.flatMap { zone ->
            zone.geometries.map { geometry -> geometry.altitudeUnit.name }
        }.groupingBy { it }.eachCount()
        return units.entries.joinToString(",") { "${it.key}:${it.value}" }.ifBlank { "none" }
    }

    private fun metersToLatitudeDegrees(meters: Double): Double = meters / 111_320.0

    private fun metersToLongitudeDegrees(meters: Double, latitude: Double): Double {
        val scale = kotlin.math.cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
        return meters / (111_320.0 * scale)
    }

    private fun isoUtc(timestampMillis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestampMillis))
    }

    private fun showImportFailure(
        error: Throwable,
        result: GeoZoneValidationResult?
    ) {
        geoEventLogger.logSimple(
            type = GeoAwarenessEventType.DATASET_IMPORT_FAILED,
            severity = "ERROR",
            message = "Geo-zone dataset import failed: ${error.message ?: error::class.java.simpleName}",
            healthState = geoAwarenessHealth?.state?.name,
            details = buildMap {
                put("errorMessage", error.message ?: error::class.java.simpleName)
                result?.let {
                    put("errorCount", it.errorCount.toString())
                    put("warningCount", it.warningCount.toString())
                    put("infoCount", it.infoCount.toString())
                }
            }
        )
        refreshEventLogCount()
        val issueLines = result?.issues
            ?.filter { it.severity == GeoZoneValidationSeverity.ERROR }
            ?.take(10)
            ?.joinToString("\n") { "- [${it.code}] ${it.message}" }
            .orEmpty()
        val summary = buildString {
            appendLine(error.message ?: "The selected geo-zone file could not be imported.")
            result?.let {
                appendLine()
                appendLine("Errors: ${it.errorCount}  Warnings: ${it.warningCount}  Info: ${it.infoCount}")
            }
            if (issueLines.isNotBlank()) {
                appendLine()
                append(issueLines)
            }
        }
        showReadableDialog("Import failed", summary.trim())
    }

    private fun showDatasetUpdateFailure(
        storageFileName: String,
        error: Throwable,
        result: GeoZoneValidationResult?
    ) {
        geoEventLogger.logSimple(
            type = GeoAwarenessEventType.DATASET_UPDATE_FAILED,
            severity = "ERROR",
            message = "Geo-zone dataset update failed: ${error.message ?: error::class.java.simpleName}",
            healthState = geoAwarenessHealth?.state?.name,
            details = buildMap {
                put("storageFileName", storageFileName)
                put("errorMessage", error.message ?: error::class.java.simpleName)
                result?.let {
                    put("errorCount", it.errorCount.toString())
                    put("warningCount", it.warningCount.toString())
                    put("infoCount", it.infoCount.toString())
                }
            }
        )
        refreshEventLogCount()
        val issueLines = result?.issues
            ?.filter { it.severity == GeoZoneValidationSeverity.ERROR }
            ?.take(10)
            ?.joinToString("\n") { "- [${it.code}] ${it.message}" }
            .orEmpty()
        val summary = buildString {
            appendLine(error.message ?: "The selected geo-zone file could not replace the existing dataset.")
            result?.let {
                appendLine()
                appendLine("Errors: ${it.errorCount}  Warnings: ${it.warningCount}  Info: ${it.infoCount}")
            }
            if (issueLines.isNotBlank()) {
                appendLine()
                append(issueLines)
            }
        }
        showReadableDialog("Dataset update failed", summary.trim())
    }

    private fun logStaleDatasetsIfNeeded(
        records: List<GeoZoneDatasetRecord>,
        health: GeoAwarenessHealth,
        manualRefresh: Boolean
    ) {
        val staleRecords = records.filter { it.isStale }
        val signature = staleRecords.joinToString("|") { "${it.datasetId}:${it.updatedAtMillis}" }
        if (signature.isBlank()) {
            lastStaleSignature = null
            return
        }
        if (signature == lastStaleSignature && !manualRefresh) {
            return
        }
        lastStaleSignature = signature
        staleRecords.forEach { record ->
            geoEventLogger.logSimple(
                type = GeoAwarenessEventType.DATASET_MARKED_STALE,
                severity = "WARNING",
                message = "Geo-zone dataset marked stale",
                datasetTitle = record.displayName,
                datasetVersion = record.datasetInfo.version,
                healthState = health.state.name,
                details = mapOf(
                    "datasetId" to record.datasetId,
                    "storageFileName" to (record.storageFileName ?: ""),
                    "ageDescription" to (record.ageDescription ?: "Update time unknown"),
                    "stale" to record.isStale.toString()
                )
            )
        }
        refreshEventLogCount()
    }

    private fun buildRepository(appContext: android.content.Context = requireContext().applicationContext): GeoZoneRepository {
        return GeoZoneRepository(
            importedFileDataSource = GeoZoneImportedFileDataSource(appContext)
        )
    }

    private fun setDatasetBusyState(isBusy: Boolean, message: String = "Loading geo-zone dataset...") {
        if (_binding == null) return
        binding.geoAwarenessLoadingOverlay.visibility = if (isBusy) View.VISIBLE else View.GONE
        binding.geoAwarenessLoadingText.text = message
        binding.geoAwarenessImportDatasetButton.isEnabled = !isBusy
        binding.geoAwarenessResetDatasetButton.isEnabled = !isBusy
        binding.geoAwarenessRefreshStatusButton.isEnabled = !isBusy
        binding.geoAwarenessImportDatasetButton.alpha = if (isBusy) 0.6f else 1f
        binding.geoAwarenessResetDatasetButton.alpha = if (isBusy) 0.6f else 1f
        binding.geoAwarenessRefreshStatusButton.alpha = if (isBusy) 0.6f else 1f
    }

    private fun showValidationDetails() {
        val result = validationResult ?: GeoZoneValidationResult.ok()
        showReadableDialog("Geo-zone dataset validation", formatValidationDetails(result))
    }

    private fun showValidationDetails(record: GeoZoneDatasetRecord) {
        showReadableDialog(
            title = "Validation: ${record.displayName}",
            message = formatValidationDetails(record.validationResult)
        )
    }

    private fun formatValidationDetails(result: GeoZoneValidationResult): String {
        val message = if (result.issues.isEmpty()) {
            "Dataset validation passed."
        } else {
            val visibleIssues = result.issues.take(30)
            val remaining = result.issues.size - visibleIssues.size
            buildString {
                GeoZoneValidationSeverity.values().forEach { severity ->
                    val severityIssues = visibleIssues.filter { it.severity == severity }
                    if (severityIssues.isEmpty()) return@forEach
                    appendLine(severity.name)
                    severityIssues.forEach { issue ->
                        append("- [${issue.code}] ${issue.message}")
                        if (!issue.zoneId.isNullOrBlank()) {
                            append(" (zoneId=${issue.zoneId}")
                            if (!issue.field.isNullOrBlank()) {
                                append(", field=${issue.field}")
                            }
                            append(")")
                        } else if (!issue.field.isNullOrBlank()) {
                            append(" (field=${issue.field})")
                        }
                        appendLine()
                    }
                    appendLine()
                }
                if (remaining > 0) {
                    append("...and $remaining more.")
                }
            }
        }
        return message
    }

    private fun showReadableDialog(title: String, message: String) {
        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
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
        liveStatusBinder = null
        super.onDestroyView()
        _binding = null
    }
}
