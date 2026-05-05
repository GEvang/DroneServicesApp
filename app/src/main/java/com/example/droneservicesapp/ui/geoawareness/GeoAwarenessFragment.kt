package com.example.droneservicesapp.ui.geoawareness

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.droneservicesapp.BuildConfig
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.geoawareness.GeoZoneAssetDataSource
import com.example.droneservicesapp.data.geoawareness.GeoZoneRepository
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.databinding.FragmentGeoAwarenessBinding
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthEvaluator
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthState
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.LiveGeoAwarenessChecker
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.home.geoawareness.LiveGeoAwarenessStatusViewBinder
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
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
    private lateinit var geoEventLogger: GeoAwarenessEventLogger
    private var liveStatusBinder: LiveGeoAwarenessStatusViewBinder? = null

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
        observeSharedState()
        observeDroneLocation()
        updateGeoTestControls()
        refreshEventLogCount()
        renderHealthStatus(
            activityViewModel.geoAwarenessHealth.value ?: GeoAwarenessHealthEvaluator.evaluate(
                datasetInfo = datasetInfo,
                zones = geoZones,
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
        if (sharedInfo != null && geoZones.isNotEmpty()) {
            datasetInfo = sharedInfo
            return
        }

        try {
            val repository = GeoZoneRepository(
                GeoZoneAssetDataSource(requireContext().applicationContext)
            )
            val loadResult = repository.loadDummyRethymnoDataset()
            geoZones = loadResult.zones
            datasetInfo = loadResult.datasetInfo
            geoAwarenessLoadError = null
            activityViewModel.geoZoneDatasetInfo.value = loadResult.datasetInfo
            val health = GeoAwarenessHealthEvaluator.evaluate(
                datasetInfo = loadResult.datasetInfo,
                zones = loadResult.zones
            )
            geoAwarenessHealth = health
            activityViewModel.geoAwarenessHealth.value = health
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
        }
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

    private fun renderHealthStatus(health: GeoAwarenessHealth?) {
        val resolvedHealth = health ?: GeoAwarenessHealthEvaluator.evaluate(
            datasetInfo = datasetInfo,
            zones = geoZones,
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
