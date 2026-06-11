package com.example.droneservicesapp.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.example.droneservicesapp.Application
import com.example.droneservicesapp.R
import com.example.droneservicesapp.core.util.LocaleUtils
import com.example.droneservicesapp.data.geoawareness.GeoZoneImportedFileDataSource
import com.example.droneservicesapp.data.geoawareness.GeoZoneRepository
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetStalenessPolicy
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.jakewharton.processphoenix.ProcessPhoenix
import org.osmdroid.config.Configuration
import java.io.File
import java.text.SimpleDateFormat
import java.text.DecimalFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SettingsFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var interfaceSummary: TextView
    private lateinit var portSummary: TextView
    private lateinit var targetIpSummary: TextView
    private lateinit var targetPortSummary: TextView
    private lateinit var operationModeSummary: TextView
    private lateinit var languageSummary: TextView
    private lateinit var geoDatasetSourceSummary: TextView
    private lateinit var geoDatasetUpdatedSummary: TextView
    private lateinit var geoDatasetStatusSummary: TextView
    private lateinit var geoDatasetScopeSummary: TextView
    private lateinit var cacheSizeSummary: TextView
    private lateinit var activityViewModel: MainActivityViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        activityViewModel = ViewModelProvider(requireActivity())[MainActivityViewModel::class.java]
        requireActivity().findViewById<View>(R.id.bottom_nav_view)?.isVisible = false

        val context = requireContext()
        val contentPadding = resources.getDimensionPixelSize(R.dimen.ds_space_lg)

        val scrollView = ScrollView(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.ds_color_background))
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(contentPadding, contentPadding, contentPadding, resources.getDimensionPixelSize(R.dimen.ds_space_xl))
        }
        scrollView.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        content.addView(createMissionOperationPanel())
        content.addView(createDroneConnectionPanel())
        content.addView(createLocalizationPanel())
        content.addView(createGeoAwarenessPanel())
        content.addView(createOfflineMapsPanel())

        refreshSummaries()
        return scrollView
    }

    private fun createMissionOperationPanel(): View {
        val panel = createPanel(getString(R.string.mission_operation_settings_title))
        panel.addView(createSettingRow(
            title = getString(R.string.mission_operation_mode_title),
            onClick = {
                showChoiceDialog(
                    title = getString(R.string.mission_operation_mode_title),
                    entries = arrayOf(
                        getString(R.string.mission_operation_mode_survey),
                        getString(R.string.mission_operation_mode_spray)
                    ),
                    values = arrayOf(
                        PlanningOperationMode.SURVEY.name,
                        PlanningOperationMode.SPRAY.name
                    ),
                    key = getString(R.string.mission_operation_mode_pref),
                    defaultValue = PlanningOperationMode.SURVEY.name
                )
            }
        ).also { operationModeSummary = it.findViewWithTag(SUMMARY_TAG) })
        return panel
    }

    private fun createDroneConnectionPanel(): View {
        val panel = createPanel(getString(R.string.drone_con_props_title_pref))
        panel.addView(createSettingRow(
            title = getString(R.string.drone_conn_interface_pref),
            onClick = { showChoiceDialog(
                title = getString(R.string.drone_conn_interface_pref),
                entries = resources.getStringArray(R.array.drone_connection_interfaces),
                values = resources.getStringArray(R.array.drone_connection_interfaces),
                key = getString(R.string.mavlink_interface_pref),
                defaultValue = "UDP"
            ) }
        ).also { interfaceSummary = it.findViewWithTag(SUMMARY_TAG) })

        panel.addView(createSettingRow(
            title = getString(R.string.drone_tcpudp_port_pref),
            onClick = { showChoiceDialog(
                title = getString(R.string.drone_tcpudp_port_pref),
                entries = resources.getStringArray(R.array.drone_tcp_udp_ports),
                values = resources.getStringArray(R.array.drone_tcp_udp_ports),
                key = getString(R.string.mavlink_lan_port_pref),
                defaultValue = "14550"
            ) }
        ).also { portSummary = it.findViewWithTag(SUMMARY_TAG) })

        panel.addView(createSettingRow(
            title = getString(R.string.mavlink_target_host_title),
            onClick = { showMavTargetHostDialog() }
        ).also { targetIpSummary = it.findViewWithTag(SUMMARY_TAG) })

        panel.addView(createSettingRow(
            title = getString(R.string.mavlink_target_port_title),
            onClick = { showChoiceDialog(
                title = getString(R.string.mavlink_target_port_title),
                entries = resources.getStringArray(R.array.drone_tcp_udp_ports),
                values = resources.getStringArray(R.array.drone_tcp_udp_ports),
                key = getString(R.string.mavlink_target_port_pref),
                defaultValue = "14550"
            ) }
        ).also { targetPortSummary = it.findViewWithTag(SUMMARY_TAG) })

        return panel
    }

    private fun createLocalizationPanel(): View {
        val panel = createPanel(getString(R.string.locales_categ_pref))
        panel.addView(createSettingRow(
            title = getString(R.string.language),
            onClick = {
                showChoiceDialog(
                    title = getString(R.string.language),
                    entries = arrayOf("English", "Ελληνικά"),
                    values = arrayOf(LocaleUtils.ENGLISH, LocaleUtils.GREEK),
                    key = getString(R.string.language_pref),
                    defaultValue = LocaleUtils.ENGLISH,
                    restartOnChange = true
                )
            }
        ).also { languageSummary = it.findViewWithTag(SUMMARY_TAG) })
        return panel
    }

    private fun createGeoAwarenessPanel(): View {
        val panel = createPanel("Geo-awareness data")
        panel.addView(createSettingRow(
            title = "Dataset source",
            onClick = null
        ).also { geoDatasetSourceSummary = it.findViewWithTag(SUMMARY_TAG) })
        panel.addView(createSettingRow(
            title = "Last data update",
            onClick = null
        ).also { geoDatasetUpdatedSummary = it.findViewWithTag(SUMMARY_TAG) })
        panel.addView(createSettingRow(
            title = "Update status",
            onClick = null
        ).also { geoDatasetStatusSummary = it.findViewWithTag(SUMMARY_TAG) })
        panel.addView(createSettingRow(
            title = "Active scope",
            onClick = null
        ).also { geoDatasetScopeSummary = it.findViewWithTag(SUMMARY_TAG) })
        return panel
    }

    private fun createOfflineMapsPanel(): View {
        val panel = createPanel("Offline Maps")
        panel.addView(createSettingRow(
            title = "Cached offline maps",
            onClick = null
        ).also { cacheSizeSummary = it.findViewWithTag(SUMMARY_TAG) })

        panel.addView(createSettingRow(
            title = "Clear offline map cache",
            summary = "Deletes downloaded tiles from this device",
            onClick = { clearOfflineMapCache() }
        ))
        return panel
    }

    private fun createPanel(title: String): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_ds_overlay_card)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.ds_space_lg),
                resources.getDimensionPixelSize(R.dimen.ds_space_lg),
                resources.getDimensionPixelSize(R.dimen.ds_space_lg),
                resources.getDimensionPixelSize(R.dimen.ds_space_lg)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.ds_space_lg)
            }

            addView(TextView(requireContext()).apply {
                text = title
                setTextAppearance(R.style.TextAppearance_DroneServices_MapPanelTitle)
            })
        }
    }

    private fun createSettingRow(
        title: String,
        summary: String = "",
        onClick: (() -> Unit)?
    ): LinearLayout {
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.ds_space_md)
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = onClick != null
            isFocusable = onClick != null
            setPadding(
                0,
                verticalPadding,
                0,
                verticalPadding
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.ds_space_sm)
            }
            onClick?.let { action -> setOnClickListener { action() } }

            addView(TextView(requireContext()).apply {
                text = title
                setTextAppearance(R.style.TextAppearance_DroneServices_StatusLabel)
            })
            addView(TextView(requireContext()).apply {
                tag = SUMMARY_TAG
                text = summary
                setTextAppearance(R.style.TextAppearance_DroneServices_StatusValue)
                setPadding(0, resources.getDimensionPixelSize(R.dimen.ds_space_xs), 0, 0)
            })
        }
    }

    private fun showChoiceDialog(
        title: String,
        entries: Array<String>,
        values: Array<String>,
        key: String,
        defaultValue: String,
        restartOnChange: Boolean = false
    ) {
        val currentValue = sharedPreferences.getString(key, defaultValue) ?: defaultValue
        val checkedIndex = values.indexOf(currentValue).coerceAtLeast(0)
        AlertDialog.Builder(requireContext(), R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(title)
            .setSingleChoiceItems(entries, checkedIndex) { dialog, which ->
                val newValue = values[which]
                sharedPreferences.edit().putString(key, newValue).apply()
                dialog.dismiss()
                refreshSummaries()
                if (restartOnChange) {
                    LocaleUtils.setSelectedLanguageId(newValue)
                    ProcessPhoenix.triggerRebirth(Application.getInstance().applicationContext)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showMavTargetHostDialog() {
        val key = getString(R.string.mavlink_target_host_pref)
        val current = sharedPreferences.getString(key, "") ?: ""

        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
            hint = "blank = auto"
            setText(current)
            selectAll()
        }

        AlertDialog.Builder(requireContext(), R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(getString(R.string.mavlink_target_host_title))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                sharedPreferences.edit()
                    .putString(key, input.text.toString().trim())
                    .apply()
                refreshSummaries()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun clearOfflineMapCache() {
        val cacheDir = getOsmdroidTileCacheDir()
        if (cacheDir == null) {
            cacheSizeSummary.text = getString(R.string.cache_directory_not_found)
            return
        }

        val ok = deleteRecursively(cacheDir)
        if (ok) {
            cacheSizeSummary.text = "0 B"
            Toast.makeText(requireContext(), getString(R.string.offline_map_cache_cleared), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), getString(R.string.failed_to_clear_cache), Toast.LENGTH_LONG).show()
            updateCacheSizeSummary()
        }
    }

    private fun refreshSummaries() {
        if (::operationModeSummary.isInitialized) {
            val operationMode = operationModeFromPreferences()
            operationModeSummary.text = operationModeLabel(operationMode)
            activityViewModel.setPlanningOperationMode(operationMode)
        }
        interfaceSummary.text = sharedPreferences.getString(getString(R.string.mavlink_interface_pref), "UDP") ?: "UDP"
        portSummary.text = sharedPreferences.getString(getString(R.string.mavlink_lan_port_pref), "14550") ?: "14550"
        targetIpSummary.text = sharedPreferences
            .getString(getString(R.string.mavlink_target_host_pref), "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "Auto"
        targetPortSummary.text = sharedPreferences.getString(getString(R.string.mavlink_target_port_pref), "14550") ?: "14550"
        languageSummary.text = languageLabel(
            sharedPreferences.getString(getString(R.string.language_pref), LocaleUtils.ENGLISH) ?: LocaleUtils.ENGLISH
        )
        updateGeoAwarenessDataSummary()
        updateCacheSizeSummary()
    }

    private fun updateGeoAwarenessDataSummary() {
        if (!::geoDatasetSourceSummary.isInitialized) return
        val result = runCatching {
            GeoZoneRepository(
                importedFileDataSource = GeoZoneImportedFileDataSource(requireContext().applicationContext)
            ).loadCurrentDataset()
        }.getOrNull()

        if (result == null || result.datasetRecords.isEmpty()) {
            geoDatasetSourceSummary.text = "No dataset loaded"
            geoDatasetUpdatedSummary.text = "No update recorded"
            geoDatasetStatusSummary.text = "Unavailable"
            geoDatasetScopeSummary.text = "Import or update a geo-zone dataset"
            return
        }

        val records = result.datasetRecords
        val newestUpdate = records.mapNotNull { it.updatedAtMillis }.maxOrNull()
        val staleCount = records.count { it.isStale }
        geoDatasetSourceSummary.text = buildString {
            append(result.datasetInfo.source ?: "Unknown source")
            append(" | ")
            append(records.size)
            append(if (records.size == 1) " dataset" else " datasets")
        }
        geoDatasetUpdatedSummary.text = newestUpdate?.let(::formatDateTime) ?: "Update time unknown"
        geoDatasetStatusSummary.text = when {
            staleCount > 0 -> "Stale: $staleCount dataset(s) older than ${GeoZoneDatasetStalenessPolicy.DEFAULT_STALE_AFTER_MILLIS / DAY_MILLIS} days"
            result.validationResult.hasErrors -> "Validation errors"
            result.validationResult.warningCount > 0 -> "Valid with ${result.validationResult.warningCount} warning(s)"
            else -> "Valid and current"
        }
        geoDatasetScopeSummary.text = formatGeoScope(records)
    }

    private fun formatGeoScope(records: List<GeoZoneDatasetRecord>): String {
        val countries = records.mapNotNull { it.datasetInfo.country?.takeIf(String::isNotBlank) }.distinct()
        val zones = records.sumOf { it.zoneCount }
        val names = records.take(3).joinToString(", ") { it.displayName }
        val suffix = if (records.size > 3) " +" + (records.size - 3) else ""
        return buildString {
            append("Countries: ")
            append(countries.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "not specified")
            append(" | Zones: ")
            append(zones)
            append(" | ")
            append(names)
            append(suffix)
        }
    }

    private fun formatDateTime(timestampMillis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date(timestampMillis))
    }

    private fun languageLabel(value: String): String {
        return if (value == LocaleUtils.ENGLISH) "English" else "Ελληνικά"
    }

    private fun operationModeFromPreferences(): PlanningOperationMode {
        val value = sharedPreferences.getString(
            getString(R.string.mission_operation_mode_pref),
            PlanningOperationMode.SURVEY.name
        )
        return runCatching {
            PlanningOperationMode.valueOf(value.orEmpty())
        }.getOrDefault(PlanningOperationMode.SURVEY)
    }

    private fun operationModeLabel(mode: PlanningOperationMode): String {
        return getString(
            when (mode) {
                PlanningOperationMode.SURVEY -> R.string.mission_operation_mode_survey
                PlanningOperationMode.SPRAY -> R.string.mission_operation_mode_spray
            }
        )
    }

    private fun updateCacheSizeSummary() {
        if (!::cacheSizeSummary.isInitialized) return
        val cacheDir = getOsmdroidTileCacheDir()
        if (cacheDir == null || !cacheDir.exists()) {
            cacheSizeSummary.text = "0 B"
            return
        }
        cacheSizeSummary.text = formatBytes(dirSizeBytes(cacheDir))
    }

    private fun getOsmdroidTileCacheDir(): File? {
        return try {
            Configuration.getInstance().osmdroidTileCache
        } catch (_: Exception) {
            null
        }
    }

    private fun dirSizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()

        var total = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            total += dirSizeBytes(file)
        }
        return total
    }

    private fun deleteRecursively(target: File): Boolean {
        if (!target.exists()) return true
        if (target.isDirectory) {
            val files = target.listFiles()
            if (files != null) {
                for (file in files) {
                    if (!deleteRecursively(file)) return false
                }
            }
        }
        return target.delete()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "${DecimalFormat("#.##").format(kb)} KB"
        val mb = kb / 1024.0
        if (mb < 1024) return "${DecimalFormat("#.##").format(mb)} MB"
        val gb = mb / 1024.0
        return "${DecimalFormat("#.##").format(gb)} GB"
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (!isAdded) return
        refreshSummaries()
    }

    override fun onStart() {
        super.onStart()
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        updateCacheSizeSummary()
    }

    companion object {
        private const val SUMMARY_TAG = "summary"
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
