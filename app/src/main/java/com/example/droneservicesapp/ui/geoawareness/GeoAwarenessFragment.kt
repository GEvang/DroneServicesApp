package com.example.droneservicesapp.ui.geoawareness

import android.net.Uri
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.droneservicesapp.BuildConfig
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.geoawareness.GeoZoneAssetDataSource
import com.example.droneservicesapp.data.geoawareness.GeoZoneDatasetValidationException
import com.example.droneservicesapp.data.geoawareness.GeoZoneImportedFileDataSource
import com.example.droneservicesapp.data.geoawareness.GeoZoneRepository
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.databinding.FragmentGeoAwarenessBinding
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthEvaluator
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthState
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetSourceType
import com.example.droneservicesapp.domain.geoawareness.LiveGeoAwarenessChecker
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

class GeoAwarenessFragment : Fragment() {

    private var _binding: FragmentGeoAwarenessBinding? = null
    private val binding get() = _binding!!

    private lateinit var activityViewModel: MainActivityViewModel
    private lateinit var droneViewModel: DroneViewModel
    private val liveChecker = LiveGeoAwarenessChecker()
    private var datasetInfo: GeoZoneDatasetInfo? = null
    private var geoZones: List<GeoZone> = emptyList()
    private var latestLiveZones: List<GeoZone> = emptyList()
    private var latestRealDronePosition: LatLon? = null
    private var latestRealDroneAltitudeMeters: Double? = null
    private var geoAwarenessHealth: GeoAwarenessHealth? = null
    private var geoAwarenessLoadError: Throwable? = null
    private var validationResult: GeoZoneValidationResult? = null
    private var importedDatasetActive: Boolean = false
    private var datasetRecords: List<GeoZoneDatasetRecord> = emptyList()
    private lateinit var geoEventLogger: GeoAwarenessEventLogger
    private var liveStatusBinder: LiveGeoAwarenessStatusViewBinder? = null
    private val importDatasetLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            handleImportedDatasetUri(uri)
        }
    }

    companion object {
        private const val MAX_IMPORT_BYTES = 5L * 1024L * 1024L
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

        requireActivity().findViewById<View>(R.id.bottom_nav_view)?.isVisible = false
        liveStatusBinder = LiveGeoAwarenessStatusViewBinder(
            requireContext(),
            binding.geoAwarenessLiveStatusChip
        )
        liveStatusBinder?.bindUnknown(getString(R.string.geo_awareness_live_no_position))
        liveStatusBinder?.setOnClickListener(View.OnClickListener {
            showLiveGeoDetails()
        })

        binding.geoAwarenessDebugNote.isVisible = BuildConfig.DEBUG
        binding.geoAwarenessDebugSectionTitle.isVisible = BuildConfig.DEBUG
        binding.geoAwarenessDebugControlsContainer.isVisible = BuildConfig.DEBUG
        binding.geoAwarenessOverlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (activityViewModel.geoAwarenessLayerVisible.value != isChecked) {
                activityViewModel.geoAwarenessLayerVisible.value = isChecked
            }
        }
        binding.geoAwarenessDatasetDetailsButton.setOnClickListener {
            showDatasetDetails()
        }
        binding.geoAwarenessImportDatasetButton.setOnClickListener {
            launchImportDatasetPicker()
        }
        binding.geoAwarenessResetDatasetButton.setOnClickListener {
            confirmResetToBundledDataset()
        }
        binding.geoAwarenessValidationDetailsButton.setOnClickListener {
            showValidationDetails()
        }
        binding.geoAwarenessExportLogsButton.setOnClickListener {
            exportGeoAwarenessLogs()
        }
        binding.geoAwarenessClearLogsButton.setOnClickListener {
            confirmClearGeoAwarenessLogs()
        }
        binding.geoAwarenessGeoTestModeButton.setOnClickListener {
            val enabled = !(activityViewModel.geoTestModeEnabled.value == true)
            activityViewModel.geoTestModeEnabled.value = enabled
            if (!enabled) {
                activityViewModel.virtualGeoTestPosition.value = null
            }
        }
        binding.geoAwarenessClearGeoTestButton.setOnClickListener {
            activityViewModel.virtualGeoTestPosition.value = null
        }

        loadDatasetIfNeeded()
        bindDatasetSummary()
        updateCurrentSourceSummary()
        renderDatasetRecords()
        renderValidationStatus(activityViewModel.geoZoneValidationResult.value)
        observeSharedState()
        observeDroneLocation()
        updateGeoTestControls()
        refreshEventLogCount()
        renderHealthStatus(
            activityViewModel.geoAwarenessHealth.value ?: GeoAwarenessHealthEvaluator.evaluate(
                datasetInfo = datasetInfo,
                zones = geoZones,
                validationResult = validationResult,
                loadError = geoAwarenessLoadError
            )
        )
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            refreshEventLogCount()
        }
    }

    private fun observeSharedState() {
        activityViewModel.geoAwarenessLayerVisible.observe(viewLifecycleOwner) { visible ->
            binding.geoAwarenessOverlaySwitch.isChecked = visible ?: true
        }

            activityViewModel.geoZoneDatasetInfo.observe(viewLifecycleOwner) { info ->
            if (info != null) {
                datasetInfo = info
                bindDatasetSummary()
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

        activityViewModel.geoTestModeEnabled.observe(viewLifecycleOwner) {
            updateGeoTestControls()
            updateLiveStatus()
        }

        activityViewModel.virtualGeoTestPosition.observe(viewLifecycleOwner) {
            updateGeoTestControls()
            updateLiveStatus()
        }
    }

    private fun observeDroneLocation() {
        droneViewModel.droneLocationLiveData.observe(viewLifecycleOwner) { location ->
            val usableLocation = location?.takeIf(::isUsableDroneLocation)
            latestRealDronePosition = usableLocation?.let { LatLon(lat = it.latitude, lon = it.longitude) }
            latestRealDroneAltitudeMeters = usableLocation?.altitude
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

        try {
            val repository = buildRepository()
            val loadResult = repository.loadCurrentDataset()
            applyLoadedDataset(loadResult, repository.hasImportedDatasets())
        } catch (error: Exception) {
            datasetInfo = null
            geoZones = emptyList()
            geoAwarenessLoadError = error
            val health = GeoAwarenessHealthEvaluator.evaluate(
                datasetInfo = null,
                zones = emptyList(),
                loadError = error
            )
            geoAwarenessHealth = health
            activityViewModel.geoAwarenessHealth.value = health
            validationResult = null
            activityViewModel.geoZoneValidationResult.value = null
            importedDatasetActive = false
            activityViewModel.geoZoneImportedActive.value = false
        }
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
            validationResult = loadResult.validationResult
        )
        geoAwarenessHealth = health
        activityViewModel.geoZoneDatasetInfo.value = loadResult.datasetInfo
        activityViewModel.geoZoneValidationResult.value = loadResult.validationResult
        activityViewModel.geoZoneDatasetRecords.value = loadResult.datasetRecords
        activityViewModel.geoAwarenessHealth.value = health
        activityViewModel.geoZoneImportedActive.value = importedActive
        bindDatasetSummary()
        updateCurrentSourceSummary()
        renderDatasetRecords()
        renderValidationStatus(loadResult.validationResult)
        renderHealthStatus(health)
        updateLiveStatus()
    }

    private fun bindDatasetSummary() {
        val info = datasetInfo
        binding.geoAwarenessDatasetTitle.text = getString(
            R.string.geo_awareness_dataset_label
        ) + " " + (info?.title ?: "Unknown geo-zone dataset")
        binding.geoAwarenessDatasetVersion.text = getString(
            R.string.geo_awareness_version_label
        ) + " " + (info?.version ?: "N/A")
        binding.geoAwarenessDatasetOfficial.text = getString(
            R.string.geo_awareness_official_label
        ) + " " + if (info?.isOfficial == true) "Yes" else "No"
        binding.geoAwarenessDatasetZones.text = getString(
            R.string.geo_awareness_zones_label
        ) + " " + (info?.zoneCount ?: 0)
    }

    private fun updateCurrentSourceSummary() {
        if (_binding == null) return
        val sourceLabel = if (importedDatasetActive) {
            getString(R.string.geo_awareness_current_source_imported)
        } else {
            getString(R.string.geo_awareness_current_source_bundled)
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
            container.addView(createPanelText(getString(R.string.geo_awareness_dataset_list_empty)))
            return
        }

        datasetRecords.forEachIndexed { index, record ->
            if (index > 0) {
                container.addView(View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (1 * resources.displayMetrics.density).toInt()
                    ).apply {
                        topMargin = (12 * resources.displayMetrics.density).toInt()
                        bottomMargin = (12 * resources.displayMetrics.density).toInt()
                    }
                    setBackgroundColor(Color.parseColor("#1F2A44"))
                })
            }
            container.addView(createDatasetRecordView(record))
        }
    }

    private fun createDatasetRecordView(record: GeoZoneDatasetRecord): View {
        val wrapper = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val validationLabel = when {
            record.validationResult.hasErrors -> getString(R.string.geo_awareness_validation_errors)
            record.validationResult.hasWarnings -> getString(R.string.geo_awareness_validation_warnings)
            else -> getString(R.string.geo_awareness_validation_ok)
        }
        wrapper.addView(createStatusValue(record.displayName))
        wrapper.addView(createPanelText("Source: " + when (record.sourceType) {
            GeoZoneDatasetSourceType.BUNDLED_ASSET -> getString(R.string.geo_awareness_dataset_source_bundled_row)
            GeoZoneDatasetSourceType.IMPORTED_FILE -> getString(R.string.geo_awareness_dataset_source_imported_row)
        }))
        wrapper.addView(createPanelText("Version: ${record.datasetInfo.version ?: "N/A"}"))
        wrapper.addView(createPanelText("Country: ${record.datasetInfo.country ?: "N/A"}"))
        wrapper.addView(createPanelText("Zones: ${record.zoneCount}"))
        wrapper.addView(createPanelText("Validation: $validationLabel"))
        wrapper.addView(createPanelText("Official: ${if (record.datasetInfo.isOfficial) "Yes" else "No"}"))
        wrapper.addView(createPanelText("Dummy: ${if (record.datasetInfo.isDummy) "Yes" else "No"}"))
        if (record.sourceType == GeoZoneDatasetSourceType.IMPORTED_FILE && record.storageFileName != null) {
            wrapper.addView(com.google.android.material.button.MaterialButton(requireContext(), null, R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.geo_awareness_remove_dataset)
                setOnClickListener { confirmRemoveDataset(record) }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    (44 * resources.displayMetrics.density).toInt()
                ).apply {
                    topMargin = (12 * resources.displayMetrics.density).toInt()
                }
            })
        }
        return wrapper
    }

    private fun createStatusValue(text: String): TextView = TextView(requireContext()).apply {
        this.text = text
        setTextColor(Color.parseColor("#EAF1FF"))
        textSize = 15f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun createPanelText(text: String): TextView = TextView(requireContext()).apply {
        this.text = text
        setTextColor(Color.parseColor("#C5D0E6"))
        textSize = 13f
        setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    private fun renderHealthStatus(health: GeoAwarenessHealth?) {
        val resolvedHealth = health ?: GeoAwarenessHealthEvaluator.evaluate(
            datasetInfo = datasetInfo,
            zones = geoZones,
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
            GeoAwarenessHealthState.DUMMY_DATA -> Triple(
                getString(R.string.geo_awareness_health_dummy),
                Color.parseColor("#8E24AA"),
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
        val count = geoEventLogger.readEvents(maxLines = Int.MAX_VALUE).size
        binding.geoAwarenessLogCount.text =
            getString(R.string.geo_awareness_event_count) + " " + count
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

    private fun confirmClearGeoAwarenessLogs() {
        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle("Clear geo-awareness logs?")
            .setMessage("This will delete stored geo-awareness event logs from this device.")
            .setPositiveButton("Clear") { _, _ ->
                geoEventLogger.clearLogs()
                refreshEventLogCount()
                Toast.makeText(requireContext(), "Geo-awareness logs cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
    }

    private fun updateLiveStatus() {
        val useVirtualPosition = activityViewModel.geoTestModeEnabled.value == true &&
            activityViewModel.virtualGeoTestPosition.value != null
        val position = if (useVirtualPosition) {
            activityViewModel.virtualGeoTestPosition.value
        } else {
            latestRealDronePosition
        }
        val altitude = if (useVirtualPosition) null else latestRealDroneAltitudeMeters
        if (position == null) {
            latestLiveZones = emptyList()
            liveStatusBinder?.bindUnknown(getString(R.string.geo_awareness_live_no_position))
            return
        }

        loadDatasetIfNeeded()
        val zones = liveChecker.checkDronePosition(
            dronePosition = position,
            droneAltitudeMeters = altitude,
            zones = geoZones
        )
        latestLiveZones = zones
        if (zones.isEmpty()) {
            liveStatusBinder?.bindClear()
        } else {
            liveStatusBinder?.bindInsideMultiple(zones)
        }
    }

    private fun showLiveGeoDetails() {
        val title: String
        val message: String
        val useVirtualPosition = activityViewModel.geoTestModeEnabled.value == true &&
            activityViewModel.virtualGeoTestPosition.value != null
        val activePosition = if (useVirtualPosition) {
            activityViewModel.virtualGeoTestPosition.value
        } else {
            latestRealDronePosition
        }
        when {
            activePosition == null -> {
                title = getString(R.string.geo_awareness_title)
                message = getString(R.string.geo_awareness_live_no_position)
            }
            latestLiveZones.isEmpty() -> {
                title = getString(R.string.geo_awareness_title)
                message = getString(R.string.geo_awareness_live_clear)
            }
            else -> {
                title = "Live geo-awareness warning"
                val visibleZones = latestLiveZones.take(5)
                val remaining = latestLiveZones.size - visibleZones.size
                message = buildString {
                    appendLine("Drone is inside loaded dummy geo-zone(s):")
                    appendLine()
                    visibleZones.forEach { zone ->
                        appendLine("- ${zone.name}")
                        appendLine("  Restriction: ${zone.restriction}")
                        appendLine("  Message: ${zone.message ?: "No message"}")
                    }
                    if (remaining > 0) {
                        appendLine("...and $remaining more.")
                    }
                    append(getString(R.string.geo_awareness_dummy_notice))
                }
            }
        }
        showReadableDialog(title, message)
    }

    private fun updateGeoTestControls() {
        if (!BuildConfig.DEBUG || _binding == null) {
            return
        }

        binding.geoAwarenessGeoTestModeButton.text = if (activityViewModel.geoTestModeEnabled.value == true) {
            getString(R.string.geo_awareness_debug_test_on)
        } else {
            getString(R.string.geo_awareness_debug_test_off)
        }
    }

    private fun showDatasetDetails() {
        val info = datasetInfo
        if (info == null) {
            showReadableDialog(
                title = "Geo-awareness dataset",
                message = "Geo-awareness dataset information is unavailable."
            )
            return
        }

        val loadedAtText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(info.loadedAtMillis))
        val notice = when {
            info.isDummy -> "This is development-only dummy data. It is not official DAGR/HCAA data. Verify official restrictions in DAGR before flight."
            !info.isOfficial -> "This dataset is not marked official. Verify restrictions with the responsible authority before flight."
            else -> "Verify dataset validity and operational restrictions before flight."
        }
        val message = buildString {
            appendLine("Dataset: ${info.title}")
            appendLine("Description: ${info.description ?: "N/A"}")
            appendLine("Version: ${info.version ?: "N/A"}")
            appendLine("Source: ${info.source ?: "N/A"}")
            appendLine("Source URL: ${info.sourceUrl ?: "N/A"}")
            appendLine("Country: ${info.country ?: "N/A"}")
            appendLine("Official: ${if (info.isOfficial) "Yes" else "No"}")
            appendLine("Dummy/test data: ${if (info.isDummy) "Yes" else "No"}")
            appendLine("Zones loaded: ${info.zoneCount}")
            appendLine("Circle geometries: ${info.circleGeometryCount}")
            appendLine("Polygon geometries: ${info.polygonGeometryCount}")
            appendLine("Loaded at: $loadedAtText")
            appendLine()
            append(notice)
        }
        showReadableDialog("Geo-awareness dataset", message)
    }

    private fun launchImportDatasetPicker() {
        importDatasetLauncher.launch(arrayOf("application/json", "text/json", "text/plain", "*/*"))
    }

    private fun handleImportedDatasetUri(uri: Uri) {
        geoEventLogger.logSimple(
            type = GeoAwarenessEventType.DATASET_IMPORT_STARTED,
            severity = "INFO",
            message = "Geo-zone dataset import started",
            details = mapOf("uri" to uri.toString())
        )

        try {
            val rawJson = readUtf8FromUri(uri)
            val repository = buildRepository()
            val loadResult = repository.importDataset(rawJson)
            applyLoadedDataset(loadResult, importedActive = true)
            activityViewModel.notifyGeoZoneDatasetReloaded()
            geoEventLogger.logSimple(
                type = GeoAwarenessEventType.DATASET_IMPORT_SUCCEEDED,
                severity = "INFO",
                message = "Geo-zone dataset import succeeded",
                datasetTitle = loadResult.datasetInfo.title,
                datasetVersion = loadResult.datasetInfo.version,
                healthState = geoAwarenessHealth?.state?.name,
                details = mapOf(
                    "title" to loadResult.datasetInfo.title,
                    "version" to (loadResult.datasetInfo.version ?: "N/A"),
                    "zoneCount" to loadResult.datasetInfo.zoneCount.toString(),
                    "addedDatasetTitle" to loadResult.datasetRecords.lastOrNull()?.displayName.orEmpty(),
                    "activeDatasetCount" to loadResult.datasetRecords.size.toString(),
                    "totalZones" to loadResult.datasetInfo.zoneCount.toString(),
                    "errorCount" to loadResult.validationResult.errorCount.toString(),
                    "warningCount" to loadResult.validationResult.warningCount.toString(),
                    "infoCount" to loadResult.validationResult.infoCount.toString()
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
            showImportFailure(error, error.validationResult)
        } catch (error: Exception) {
            showImportFailure(error, null)
        }
    }

    private fun confirmResetToBundledDataset() {
        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle("Reset geo-zone dataset?")
            .setMessage("This will remove the imported dataset and restore the bundled Rethymno dummy dataset.")
            .setPositiveButton("Reset") { _, _ ->
                resetToBundledDataset()
            }
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
    }

    private fun resetToBundledDataset() {
        try {
            val repository = buildRepository()
            val removedCount = datasetRecords.count { it.sourceType == GeoZoneDatasetSourceType.IMPORTED_FILE }
            val loadResult = repository.resetToBundledDataset()
            applyLoadedDataset(loadResult, importedActive = false)
            activityViewModel.notifyGeoZoneDatasetReloaded()
            if (removedCount > 0) {
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.ALL_IMPORTED_DATASETS_REMOVED,
                    severity = "INFO",
                    message = "All imported geo-zone datasets removed",
                    details = mapOf("removedCount" to removedCount.toString())
                )
            }
            geoEventLogger.logSimple(
                type = GeoAwarenessEventType.DATASET_RESET_TO_BUNDLED,
                severity = "INFO",
                message = "Geo-zone dataset reset to bundled dummy dataset",
                datasetTitle = loadResult.datasetInfo.title,
                datasetVersion = loadResult.datasetInfo.version,
                healthState = geoAwarenessHealth?.state?.name
            )
            refreshEventLogCount()
            Toast.makeText(requireContext(), "Bundled dummy dataset restored", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            showReadableDialog(
                title = "Reset failed",
                message = error.message ?: "Failed to restore bundled dummy dataset."
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
            activityViewModel.notifyGeoZoneDatasetReloaded()
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

    private fun buildRepository(): GeoZoneRepository {
        val appContext = requireContext().applicationContext
        return GeoZoneRepository(
            assetDataSource = GeoZoneAssetDataSource(appContext),
            importedDataSource = GeoZoneImportedFileDataSource(appContext)
        )
    }

    private fun showValidationDetails() {
        val result = validationResult ?: GeoZoneValidationResult.ok()
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
        showReadableDialog("Geo-zone dataset validation", message)
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

    override fun onDestroyView() {
        liveStatusBinder = null
        super.onDestroyView()
        _binding = null
    }
}
