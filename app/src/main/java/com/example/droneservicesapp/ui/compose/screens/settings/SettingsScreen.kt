package com.example.droneservicesapp.ui.compose.screens.settings

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.example.droneservicesapp.R
import com.example.droneservicesapp.core.util.LocaleUtils
import com.example.droneservicesapp.data.geoawareness.GeoZoneImportedFileDataSource
import com.example.droneservicesapp.data.geoawareness.GeoZoneRepository
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetStalenessPolicy
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.ui.compose.components.AppButton
import com.example.droneservicesapp.ui.compose.components.AppButtonStyle
import com.example.droneservicesapp.ui.compose.screens.common.LabeledValue
import com.example.droneservicesapp.ui.compose.screens.common.MoreDivider
import com.example.droneservicesapp.ui.compose.screens.common.MorePanel
import com.example.droneservicesapp.ui.compose.screens.common.MoreScreen
import com.example.droneservicesapp.ui.compose.screens.common.MoreSettingRow
import com.example.droneservicesapp.ui.compose.screens.common.TwoColumnLayout
import com.example.droneservicesapp.ui.home.components.MapDisplayPreferences
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration

private data class GeoDatasetSummary(
    val source: String,
    val updated: String,
    val status: String,
    val scope: String,
)

private enum class SettingsDialog { LANGUAGE }

const val SETTINGS_SCREEN_TAG = "compose-settings-screen"

@Composable
fun SettingsScreen(activityViewModel: MainActivityViewModel) {
    val context = LocalContext.current
    val preferences = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var revision by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var cacheSize by remember { mutableStateOf("0 B") }
    var geoSummary by remember {
        mutableStateOf(
            GeoDatasetSummary(
                context.getString(R.string.settings_no_dataset_loaded),
                context.getString(R.string.settings_no_update_recorded),
                context.getString(R.string.settings_unavailable),
                context.getString(R.string.settings_import_dataset_prompt),
            )
        )
    }
    val scope = rememberCoroutineScope()

    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val operationMode = remember(revision) { operationMode(preferences, context) }
    val labelsEnabled = remember(revision) {
        preferences.getBoolean(MapDisplayPreferences.LABELS_ENABLED_KEY, MapDisplayPreferences.LABELS_ENABLED_DEFAULT)
    }
    val language = remember(revision) {
        preferences.getString(LocaleUtils.PREFERENCE_KEY, LocaleUtils.ENGLISH) ?: LocaleUtils.ENGLISH
    }

    LaunchedEffect(operationMode) { activityViewModel.setPlanningOperationMode(operationMode) }
    LaunchedEffect(revision) {
        geoSummary = withContext(Dispatchers.IO) { loadGeoSummary(context) }
        cacheSize = withContext(Dispatchers.IO) { formatBytes(tileCacheDir()?.let(::dirSizeBytes) ?: 0L) }
    }

    MoreScreen(
        title = stringResource(R.string.menu_settings),
        subtitle = stringResource(R.string.more_settings_subtitle),
        iconRes = R.drawable.ic_menu_settings,
        modifier = Modifier.testTag(SETTINGS_SCREEN_TAG),
    ) {
        TwoColumnLayout(
            left = {
                MorePanel(
                    title = stringResource(R.string.more_mission_defaults),
                    subtitle = stringResource(R.string.more_mission_defaults_subtitle),
                ) {
                    Text(stringResource(R.string.mission_operation_mode_title))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OperationModeButton(
                            label = stringResource(R.string.mission_operation_mode_spray),
                            selected = operationMode == PlanningOperationMode.SPRAY,
                            modifier = Modifier.weight(1f),
                        ) {
                            preferences.edit { putString(context.getString(R.string.mission_operation_mode_pref), PlanningOperationMode.SPRAY.name) }
                        }
                        OperationModeButton(
                            label = stringResource(R.string.mission_operation_mode_survey),
                            selected = operationMode == PlanningOperationMode.SURVEY,
                            modifier = Modifier.weight(1f),
                        ) {
                            preferences.edit { putString(context.getString(R.string.mission_operation_mode_pref), PlanningOperationMode.SURVEY.name) }
                        }
                    }
                }
                MorePanel(
                    title = stringResource(R.string.locales_categ_pref),
                    subtitle = stringResource(R.string.more_localization_subtitle),
                ) {
                    MoreSettingRow(
                        title = stringResource(R.string.language),
                        value = if (language == LocaleUtils.ENGLISH) "English" else "Ελληνικά",
                        iconRes = R.drawable.ic_language_24,
                        onClick = { dialog = SettingsDialog.LANGUAGE },
                    )
                }
                MorePanel(
                    title = stringResource(R.string.settings_offline_maps),
                    subtitle = stringResource(R.string.more_offline_maps_subtitle),
                ) {
                    MoreSettingRow(
                        title = stringResource(R.string.settings_cached_offline_maps),
                        value = cacheSize,
                        iconRes = R.drawable.ic_database_24,
                    )
                    MoreDivider()
                    AppButton(
                        text = stringResource(R.string.settings_clear_offline_cache),
                        onClick = {
                            scope.launch {
                                val cache = tileCacheDir()
                                if (cache == null) {
                                    cacheSize = context.getString(R.string.cache_directory_not_found)
                                    return@launch
                                }
                                val ok = withContext(Dispatchers.IO) { deleteRecursively(cache) }
                                cacheSize = if (ok) "0 B" else withContext(Dispatchers.IO) {
                                    formatBytes(dirSizeBytes(cache))
                                }
                                Toast.makeText(
                                    context,
                                    if (ok) R.string.offline_map_cache_cleared else R.string.failed_to_clear_cache,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        style = AppButtonStyle.DANGER,
                    )
                }
            },
            right = {
                MorePanel(
                    title = stringResource(R.string.settings_map_display),
                    subtitle = stringResource(R.string.more_map_display_subtitle),
                ) {
                    MoreSettingRow(
                        title = stringResource(R.string.settings_map_labels),
                        value = "",
                        iconRes = R.drawable.ic_menu_map,
                        supportingText = stringResource(R.string.settings_map_labels_description),
                        trailing = {
                            Switch(
                                checked = labelsEnabled,
                                onCheckedChange = { enabled ->
                                    preferences.edit { putBoolean(MapDisplayPreferences.LABELS_ENABLED_KEY, enabled) }
                                },
                            )
                        },
                    )
                }
                MorePanel(
                    title = stringResource(R.string.settings_geo_data),
                    subtitle = stringResource(R.string.more_geo_data_subtitle),
                ) {
                    LabeledValue(stringResource(R.string.settings_dataset_source), geoSummary.source)
                    MoreDivider()
                    LabeledValue(stringResource(R.string.settings_last_data_update), geoSummary.updated)
                    LabeledValue(stringResource(R.string.settings_update_status), geoSummary.status)
                    LabeledValue(stringResource(R.string.settings_active_scope), geoSummary.scope)
                }
            },
        )
    }

    if (dialog == SettingsDialog.LANGUAGE) {
        ChoiceDialog(
            title = stringResource(R.string.language),
            options = listOf("English" to LocaleUtils.ENGLISH, "Ελληνικά" to LocaleUtils.GREEK),
            selected = language,
            onSelected = { selected ->
                preferences.edit { putString(LocaleUtils.PREFERENCE_KEY, selected) }
                LocaleUtils.setSelectedLanguageId(selected)
                LocaleUtils.setLocale(context, selected)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun OperationModeButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    AppButton(
        text = label,
        onClick = onClick,
        modifier = modifier,
        style = if (selected) AppButtonStyle.PRIMARY else AppButtonStyle.SECONDARY,
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelected(value) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected == value, onClick = { onSelected(value) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
    )
}

private fun operationMode(preferences: SharedPreferences, context: Context): PlanningOperationMode = runCatching {
    PlanningOperationMode.valueOf(
        preferences.getString(context.getString(R.string.mission_operation_mode_pref), PlanningOperationMode.SURVEY.name).orEmpty()
    )
}.getOrDefault(PlanningOperationMode.SURVEY)

private fun loadGeoSummary(context: Context): GeoDatasetSummary {
    val result = runCatching {
        GeoZoneRepository(GeoZoneImportedFileDataSource(context.applicationContext)).loadCurrentDataset()
    }.getOrNull()
    if (result == null || result.datasetRecords.isEmpty()) {
        return GeoDatasetSummary(
            context.getString(R.string.settings_no_dataset_loaded),
            context.getString(R.string.settings_no_update_recorded),
            context.getString(R.string.settings_unavailable),
            context.getString(R.string.settings_import_dataset_prompt),
        )
    }
    val records = result.datasetRecords
    val newest = records.mapNotNull { it.updatedAtMillis }.maxOrNull()
    val stale = records.count { it.isStale }
    return GeoDatasetSummary(
        source = "${result.datasetInfo.source ?: context.getString(R.string.settings_unknown_source)} | ${records.size}${context.resources.getQuantityString(R.plurals.settings_dataset_count, records.size)}",
        updated = newest?.let(::formatDateTime) ?: context.getString(R.string.settings_update_time_unknown),
        status = when {
            stale > 0 -> context.getString(R.string.settings_stale_datasets, stale, GeoZoneDatasetStalenessPolicy.DEFAULT_STALE_AFTER_MILLIS / DAY_MILLIS)
            result.validationResult.hasErrors -> context.getString(R.string.settings_validation_errors)
            result.validationResult.warningCount > 0 -> context.getString(R.string.settings_valid_with_warnings, result.validationResult.warningCount)
            else -> context.getString(R.string.settings_valid_current)
        },
        scope = formatGeoScope(context, records),
    )
}

private fun formatGeoScope(context: Context, records: List<GeoZoneDatasetRecord>): String {
    val countries = records.mapNotNull { it.datasetInfo.country?.takeIf(String::isNotBlank) }.distinct()
    val zones = records.sumOf { it.zoneCount }
    val names = records.take(3).joinToString(", ") { it.displayName }
    val suffix = if (records.size > 3) " +${records.size - 3}" else ""
    return context.getString(
        R.string.settings_scope_format,
        countries.takeIf(List<String>::isNotEmpty)?.joinToString(", ") ?: context.getString(R.string.settings_not_specified),
        zones,
        names,
        suffix,
    )
}

private fun formatDateTime(timestamp: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).apply {
    timeZone = TimeZone.getDefault()
}.format(Date(timestamp))

private fun tileCacheDir(): File? = runCatching { Configuration.getInstance().osmdroidTileCache }.getOrNull()

private fun dirSizeBytes(file: File): Long = when {
    !file.exists() -> 0L
    file.isFile -> file.length()
    else -> file.listFiles()?.sumOf(::dirSizeBytes) ?: 0L
}

private fun deleteRecursively(file: File): Boolean {
    if (!file.exists()) return true
    if (file.isDirectory && file.listFiles()?.all(::deleteRecursively) == false) return false
    return file.delete()
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${DecimalFormat("#.##").format(kb)} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return "${DecimalFormat("#.##").format(mb)} MB"
    return "${DecimalFormat("#.##").format(mb / 1024.0)} GB"
}

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
