package com.example.droneservicesapp.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.example.droneservicesapp.R
import com.example.droneservicesapp.core.util.LocaleUtils
import com.example.droneservicesapp.data.geoawareness.GeoZoneImportedFileDataSource
import com.example.droneservicesapp.data.geoawareness.GeoZoneRepository
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetStalenessPolicy
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.example.droneservicesapp.ui.home.components.MapDisplayPreferences
import com.google.android.material.switchmaterial.SwitchMaterial
import org.osmdroid.config.Configuration
import java.io.File
import java.text.SimpleDateFormat
import java.text.DecimalFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SettingsFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var sharedPreferences: SharedPreferences
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
        content.addView(createLocalizationPanel())
        content.addView(createMapDisplayPanel())
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

    private fun createLocalizationPanel(): View {
        val panel = createPanel(getString(R.string.locales_categ_pref))
        panel.addView(createSettingRow(
            title = getString(R.string.language),
            onClick = {
                showChoiceDialog(
                    title = getString(R.string.language),
                    entries = arrayOf("English", "Ελληνικά"),
                    values = arrayOf(LocaleUtils.ENGLISH, LocaleUtils.GREEK),
                    key = LocaleUtils.PREFERENCE_KEY,
                    defaultValue = LocaleUtils.ENGLISH,
                    restartOnChange = true
                )
            }
        ).also { languageSummary = it.findViewWithTag(SUMMARY_TAG) })
        return panel
    }

    private fun createGeoAwarenessPanel(): View {
        val panel = createPanel(getString(R.string.settings_geo_data))
        panel.addView(createSettingRow(
            title = getString(R.string.settings_dataset_source),
            onClick = null
        ).also { geoDatasetSourceSummary = it.findViewWithTag(SUMMARY_TAG) })
        panel.addView(createSettingRow(
            title = getString(R.string.settings_last_data_update),
            onClick = null
        ).also { geoDatasetUpdatedSummary = it.findViewWithTag(SUMMARY_TAG) })
        panel.addView(createSettingRow(
            title = getString(R.string.settings_update_status),
            onClick = null
        ).also { geoDatasetStatusSummary = it.findViewWithTag(SUMMARY_TAG) })
        panel.addView(createSettingRow(
            title = getString(R.string.settings_active_scope),
            onClick = null
        ).also { geoDatasetScopeSummary = it.findViewWithTag(SUMMARY_TAG) })
        return panel
    }

    private fun createMapDisplayPanel(): View {
        val panel = createPanel(getString(R.string.settings_map_display))
        panel.addView(createToggleSettingRow(
            title = getString(R.string.settings_map_labels),
            summary = getString(R.string.settings_map_labels_description),
            checked = sharedPreferences.getBoolean(
                MapDisplayPreferences.LABELS_ENABLED_KEY,
                MapDisplayPreferences.LABELS_ENABLED_DEFAULT
            ),
            onCheckedChanged = { enabled ->
                sharedPreferences.edit {
                    putBoolean(MapDisplayPreferences.LABELS_ENABLED_KEY, enabled)
                }
            }
        ))
        return panel
    }

    private fun createOfflineMapsPanel(): View {
        val panel = createPanel(getString(R.string.settings_offline_maps))
        panel.addView(createSettingRow(
            title = getString(R.string.settings_cached_offline_maps),
            onClick = null
        ).also { cacheSizeSummary = it.findViewWithTag(SUMMARY_TAG) })

        panel.addView(createSettingRow(
            title = getString(R.string.settings_clear_offline_cache),
            summary = getString(R.string.settings_clear_offline_cache_description),
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

    private fun createToggleSettingRow(
        title: String,
        summary: String,
        checked: Boolean,
        onCheckedChanged: (Boolean) -> Unit
    ): LinearLayout {
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.ds_space_md)
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, verticalPadding, 0, verticalPadding)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.ds_space_sm)
            }
        }

        row.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(requireContext()).apply {
                text = title
                setTextAppearance(R.style.TextAppearance_DroneServices_StatusLabel)
            })
            addView(TextView(requireContext()).apply {
                text = summary
                setTextAppearance(R.style.TextAppearance_DroneServices_StatusValue)
                setPadding(0, resources.getDimensionPixelSize(R.dimen.ds_space_xs), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val toggle = SwitchMaterial(requireContext()).apply {
            isChecked = checked
            contentDescription = title
            setOnCheckedChangeListener { _, enabled -> onCheckedChanged(enabled) }
        }
        row.addView(toggle)
        row.isClickable = true
        row.isFocusable = true
        row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
        return row
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
                sharedPreferences.edit {
                    putString(key, newValue)
                }
                dialog.dismiss()
                refreshSummaries()
                if (restartOnChange) {
                    LocaleUtils.setSelectedLanguageId(newValue)
                    LocaleUtils.setLocale(requireContext(), newValue)
                }
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
        languageSummary.text = languageLabel(
            sharedPreferences.getString(LocaleUtils.PREFERENCE_KEY, LocaleUtils.ENGLISH) ?: LocaleUtils.ENGLISH
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
            geoDatasetSourceSummary.setText(R.string.settings_no_dataset_loaded)
            geoDatasetUpdatedSummary.setText(R.string.settings_no_update_recorded)
            geoDatasetStatusSummary.setText(R.string.settings_unavailable)
            geoDatasetScopeSummary.setText(R.string.settings_import_dataset_prompt)
            return
        }

        val records = result.datasetRecords
        val newestUpdate = records.mapNotNull { it.updatedAtMillis }.maxOrNull()
        val staleCount = records.count { it.isStale }
        geoDatasetSourceSummary.text = buildString {
            append(result.datasetInfo.source ?: getString(R.string.settings_unknown_source))
            append(" | ")
            append(records.size)
            append(resources.getQuantityString(R.plurals.settings_dataset_count, records.size))
        }
        geoDatasetUpdatedSummary.text = newestUpdate?.let(::formatDateTime) ?: getString(R.string.settings_update_time_unknown)
        geoDatasetStatusSummary.text = when {
            staleCount > 0 -> getString(R.string.settings_stale_datasets, staleCount, GeoZoneDatasetStalenessPolicy.DEFAULT_STALE_AFTER_MILLIS / DAY_MILLIS)
            result.validationResult.hasErrors -> getString(R.string.settings_validation_errors)
            result.validationResult.warningCount > 0 -> getString(R.string.settings_valid_with_warnings, result.validationResult.warningCount)
            else -> getString(R.string.settings_valid_current)
        }
        geoDatasetScopeSummary.text = formatGeoScope(records)
    }

    private fun formatGeoScope(records: List<GeoZoneDatasetRecord>): String {
        val countries = records.mapNotNull { it.datasetInfo.country?.takeIf(String::isNotBlank) }.distinct()
        val zones = records.sumOf { it.zoneCount }
        val names = records.take(3).joinToString(", ") { it.displayName }
        val suffix = if (records.size > 3) " +" + (records.size - 3) else ""
        return buildString {
            append(getString(R.string.settings_scope_format,
                countries.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: getString(R.string.settings_not_specified),
                zones,
                names,
                suffix
            ))
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
