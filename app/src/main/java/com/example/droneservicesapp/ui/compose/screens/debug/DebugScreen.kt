package com.example.droneservicesapp.ui.compose.screens.debug

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.diagnostics.DiagnosticLog
import com.example.droneservicesapp.data.terrain.TerrainDebugOptions
import com.example.droneservicesapp.mavserver.DroneLogDownloadState
import com.example.droneservicesapp.mavserver.DroneLogFile
import com.example.droneservicesapp.mavserver.DroneLogRequestResult
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.mavserver.VehicleParameter
import com.example.droneservicesapp.mavserver.VehicleParameterCatalogState
import com.example.droneservicesapp.ui.compose.components.AppButton
import com.example.droneservicesapp.ui.compose.components.AppButtonStyle
import com.example.droneservicesapp.ui.compose.components.AppDropdown
import com.example.droneservicesapp.ui.compose.screens.common.MorePanel
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_DEBUG_PIN = "123"
private const val DEBUG_PREFS = "debug_access"
private const val KEY_ACCESS_ENABLED = "access_enabled"
private const val KEY_ACCESS_CODE = "access_code"
const val DEBUG_SCREEN_TAG = "compose-debug-screen"

@Composable
fun DebugScreen(droneViewModel: DroneViewModel) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(DEBUG_PREFS, 0) }
    val accessRequired = remember { preferences.getBoolean(KEY_ACCESS_ENABLED, true) }
    var unlocked by rememberSaveable { mutableStateOf(!accessRequired) }
    var pin by rememberSaveable { mutableStateOf("") }
    var pinError by rememberSaveable { mutableStateOf(false) }

    if (!unlocked) {
        DebugUnlock(
            pin = pin,
            pinError = pinError,
            onPinChanged = { pin = it.take(8); pinError = false },
            onUnlock = {
                if (pin == preferences.getString(KEY_ACCESS_CODE, DEFAULT_DEBUG_PIN).orEmpty()) {
                    unlocked = true
                    pin = ""
                    pinError = false
                } else {
                    pinError = true
                }
            },
        )
        return
    }

    DebugContent(droneViewModel)
}

@Composable
private fun DebugUnlock(pin: String, pinError: Boolean, onPinChanged: (String) -> Unit, onUnlock: () -> Unit) {
    Box(
        Modifier.fillMaxSize().testTag(DEBUG_SCREEN_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 420.dp).padding(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.debug_locked_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.debug_locked_description))
                OutlinedTextField(
                    value = pin,
                    onValueChange = onPinChanged,
                    label = { Text(stringResource(R.string.debug_pin_hint)) },
                    singleLine = true,
                    isError = pinError,
                    supportingText = if (pinError) ({ Text(stringResource(R.string.debug_pin_incorrect)) }) else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onUnlock() }),
                )
                AppButton(stringResource(R.string.debug_unlock), onUnlock, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DebugContent(droneViewModel: DroneViewModel) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(DEBUG_PREFS, 0) }
    val scope = rememberCoroutineScope()
    val parameters by droneViewModel.vehicleParameterCatalog.observeAsState(VehicleParameterCatalogState())
    val logCatalog by droneViewModel.droneLogCatalog.observeAsState()
    val logDownload by droneViewModel.droneLogDownloadState.observeAsState(DroneLogDownloadState.Idle)
    val servoValue by droneViewModel.servo5OutputRaw.observeAsState()
    var accessEnabled by rememberSaveable { mutableStateOf(preferences.getBoolean(KEY_ACCESS_ENABLED, true)) }
    var newCode by rememberSaveable { mutableStateOf("") }
    var terrainSafeguardDisabled by rememberSaveable {
        mutableStateOf(TerrainDebugOptions.isCoverageSafeguardDisabled(context))
    }
    var query by rememberSaveable { mutableStateOf("") }
    var editingParameter by remember { mutableStateOf<VehicleParameter?>(null) }
    var selectedLogId by rememberSaveable { mutableStateOf<Int?>(null) }
    var logExpanded by remember { mutableStateOf(false) }
    var diagnosticsStatus by rememberSaveable { mutableStateOf(context.getString(R.string.debug_diagnostics_ready)) }
    var exporting by remember { mutableStateOf(false) }
    var terminalLogStatus by rememberSaveable { mutableStateOf<String?>(null) }
    val filteredParameters = remember(parameters.parameters, query) {
        if (query.isBlank()) parameters.parameters else parameters.parameters.filter {
            it.name.contains(query.trim(), ignoreCase = true)
        }
    }
    val logs = logCatalog?.logs.orEmpty()
    val selectedLog = logs.firstOrNull { it.id == selectedLogId } ?: logs.firstOrNull()

    LaunchedEffect(logs) {
        if (selectedLogId == null || logs.none { it.id == selectedLogId }) selectedLogId = logs.firstOrNull()?.id
    }
    LaunchedEffect(logDownload) {
        when (val state = logDownload) {
            is DroneLogDownloadState.Succeeded -> {
                terminalLogStatus = context.getString(R.string.debug_dataflash_saved, state.location)
                droneViewModel.clearDroneLogDownloadResult()
            }
            is DroneLogDownloadState.Failed -> {
                terminalLogStatus = context.getString(R.string.debug_dataflash_failed, state.reason)
                droneViewModel.clearDroneLogDownloadResult()
            }
            else -> Unit
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(DEBUG_SCREEN_TAG),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { DebugHeader() }
        item {
            MorePanel(title = stringResource(R.string.debug_title)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.debug_access_enabled), Modifier.weight(1f))
                    Switch(accessEnabled, onCheckedChange = { enabled ->
                        accessEnabled = enabled
                        preferences.edit().putBoolean(KEY_ACCESS_ENABLED, enabled).apply()
                        toast(context, if (enabled) R.string.debug_access_enabled_notice else R.string.debug_access_disabled_notice)
                    })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newCode,
                        onValueChange = { newCode = it.take(8) },
                        label = { Text(stringResource(R.string.debug_new_access_code)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    AppButton(stringResource(R.string.debug_save_access_code), {
                        if (newCode.isBlank()) toast(context, R.string.debug_access_code_required)
                        else {
                            preferences.edit().putString(KEY_ACCESS_CODE, newCode).apply()
                            newCode = ""
                            toast(context, R.string.debug_access_code_saved)
                        }
                    })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.debug_disable_terrain_coverage_safeguard), color = MaterialTheme.colorScheme.error)
                        Text(
                            stringResource(R.string.debug_disable_terrain_coverage_safeguard_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(terrainSafeguardDisabled, onCheckedChange = { disabled ->
                        terrainSafeguardDisabled = disabled
                        TerrainDebugOptions.setCoverageSafeguardDisabled(context, disabled)
                        DiagnosticLog.event(
                            "terrain",
                            "coverage_safeguard_debug_option_changed",
                            if (disabled) "WARN" else "INFO",
                            mapOf("disabled" to disabled),
                        )
                        toast(
                            context,
                            if (disabled) R.string.debug_terrain_coverage_safeguard_disabled_notice
                            else R.string.debug_terrain_coverage_safeguard_enabled_notice,
                        )
                    })
                }
            }
        }
        item {
            MorePanel(
                title = stringResource(R.string.debug_parameters_title),
                subtitle = stringResource(R.string.debug_parameters_description),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.debug_parameters_search)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    AppButton(
                        stringResource(R.string.debug_refresh),
                        {
                            if (!droneViewModel.refreshVehicleParameterCatalog()) toast(context, R.string.debug_requires_connection)
                        },
                        enabled = !parameters.loading,
                    )
                }
                Text(parameterStatus(parameters))
                if (parameters.loading) {
                    if (parameters.expectedCount == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { parameterProgress(parameters) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        items(filteredParameters, key = VehicleParameter::name) { parameter ->
            VehicleParameterRow(parameter) { editingParameter = parameter }
        }
        item {
            MorePanel(
                title = stringResource(R.string.debug_dataflash_title),
                subtitle = stringResource(R.string.debug_dataflash_description),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppDropdown(
                        label = stringResource(R.string.debug_dataflash_title),
                        selected = selectedLog,
                        options = logs,
                        optionLabel = { formatLog(context, it) },
                        expanded = logExpanded,
                        onExpandedChange = { logExpanded = it },
                        onSelected = { selectedLogId = it.id },
                        modifier = Modifier.weight(1f),
                        enabled = logs.isNotEmpty() && logDownload !is DroneLogDownloadState.Downloading,
                    )
                    AppButton(
                        stringResource(R.string.debug_refresh),
                        { presentLogResult(context, droneViewModel.refreshDroneLogs()) },
                        enabled = logCatalog?.loading != true && logDownload !is DroneLogDownloadState.Downloading,
                    )
                    AppButton(
                        stringResource(R.string.debug_dataflash_download),
                        {
                            if (selectedLog == null) toast(context, R.string.debug_dataflash_select_log)
                            else presentLogResult(context, droneViewModel.downloadDroneLog(selectedLog.id))
                        },
                        enabled = selectedLog != null && logDownload !is DroneLogDownloadState.Downloading,
                    )
                }
                if (logDownload is DroneLogDownloadState.Downloading) {
                    val downloading = logDownload as DroneLogDownloadState.Downloading
                    LinearProgressIndicator(
                        progress = { if (downloading.totalBytes > 0) (downloading.receivedBytes.toFloat() / downloading.totalBytes).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(logStatus(context, logCatalog, logDownload, terminalLogStatus))
            }
        }
        item {
            MorePanel(
                title = stringResource(R.string.debug_diagnostics_title),
                subtitle = stringResource(R.string.debug_diagnostics_description),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        stringResource(R.string.debug_diagnostics_export),
                        {
                            exporting = true
                            diagnosticsStatus = context.getString(R.string.debug_diagnostics_exporting)
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { DiagnosticLog.export(context.applicationContext) }
                                diagnosticsStatus = when (result) {
                                    is DiagnosticLog.ExportResult.Success -> context.getString(R.string.debug_diagnostics_exported, result.location)
                                    is DiagnosticLog.ExportResult.Failure -> context.getString(R.string.debug_diagnostics_export_failed, result.reason)
                                }
                                exporting = false
                            }
                        },
                        enabled = !exporting,
                    )
                    AppButton(stringResource(R.string.debug_diagnostics_mark_incident), {
                        droneViewModel.markDiagnosticIncident()
                        diagnosticsStatus = context.getString(R.string.debug_diagnostics_incident_marked)
                    })
                }
                Text(diagnosticsStatus)
            }
        }
        item {
            MorePanel(
                title = stringResource(R.string.debug_sprayer),
                subtitle = stringResource(R.string.debug_sprayer_pwm),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1000, 1500, 2000, 2200).forEach { pwm ->
                        AppButton(
                            text = pwm.toString(),
                            onClick = {
                                val message = if (droneViewModel.sendDebugSprayerServoPwm(pwm)) {
                                    context.getString(R.string.debug_sprayer_command_sent, pwm)
                                } else context.getString(R.string.no_conn_msg)
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.weight(1f),
                            style = AppButtonStyle.SECONDARY,
                        )
                    }
                }
                Text(
                    servoValue?.let { stringResource(R.string.debug_sprayer_drone_value, it) }
                        ?: stringResource(R.string.debug_sprayer_drone_value_unknown)
                )
            }
        }
    }

    editingParameter?.let { parameter ->
        ParameterEditor(
            parameter = parameter,
            onSave = { value ->
                if (!droneViewModel.setVehicleParameter(parameter.name, value)) {
                    toast(context, R.string.debug_requires_connection)
                    false
                } else {
                    toast(context, R.string.debug_parameter_write_sent)
                    editingParameter = null
                    true
                }
            },
            onDismiss = { editingParameter = null },
        )
    }
}

@Composable
private fun DebugHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        ) {
            Image(
                painterResource(R.drawable.ic_menu_bug),
                null,
                Modifier.padding(12.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            )
        }
        Column {
            Text(stringResource(R.string.debug_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.debug_subtitle), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun VehicleParameterRow(parameter: VehicleParameter, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(parameter.name, Modifier.weight(1.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatParameterValue(parameter.value), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(parameter.type, Modifier.weight(0.8f), maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ParameterEditor(parameter: VehicleParameter, onSave: (Float) -> Boolean, onDismiss: () -> Unit) {
    var value by rememberSaveable(parameter.name) { mutableStateOf(parameter.value.toString()) }
    var error by rememberSaveable(parameter.name) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.debug_parameter_edit_title, parameter.name)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it; error = false },
                singleLine = true,
                isError = error,
                supportingText = if (error) ({ Text(stringResource(R.string.debug_parameter_invalid_value)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            AppButton(stringResource(R.string.debug_parameter_save), {
                val parsed = value.toFloatOrNull()
                if (parsed == null || !parsed.isFinite()) error = true else onSave(parsed)
            })
        },
        dismissButton = { AppButton(stringResource(android.R.string.cancel), onDismiss, style = AppButtonStyle.TEXT) },
    )
}

@Composable
private fun parameterStatus(state: VehicleParameterCatalogState): String = when {
    state.error != null -> state.error
    state.loading && state.expectedCount != null -> stringResource(R.string.debug_parameters_loading_count, state.receivedCount, state.expectedCount)
    state.loading -> stringResource(R.string.debug_parameters_loading)
    state.partial -> stringResource(R.string.debug_parameters_partial, state.receivedCount, state.expectedCount ?: state.receivedCount)
    state.parameters.isNotEmpty() -> stringResource(R.string.debug_parameters_loaded, state.parameters.size)
    else -> stringResource(R.string.debug_parameters_not_loaded)
}

private fun parameterProgress(state: VehicleParameterCatalogState): Float {
    val expected = state.expectedCount ?: return 0f
    return if (expected > 0) (state.receivedCount.toFloat() / expected).coerceIn(0f, 1f) else 0f
}

private fun formatParameterValue(value: Float): String {
    val integral = value.toLong()
    return if (value.isFinite() && value == integral.toFloat()) integral.toString()
    else String.format(Locale.US, "%.6g", value)
}

private fun formatLog(context: Context, log: DroneLogFile): String {
    val date = if (log.timeUtcSeconds > 0) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(log.timeUtcSeconds * 1000L))
    } else context.getString(R.string.debug_dataflash_unknown_date)
    return context.getString(R.string.debug_dataflash_log_item, log.id, formatBytes(log.sizeBytes), date)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun logStatus(
    context: Context,
    catalog: com.example.droneservicesapp.mavserver.DroneLogCatalogState?,
    download: DroneLogDownloadState,
    terminalStatus: String?,
): String = when {
    download is DroneLogDownloadState.Downloading -> stringResource(
        R.string.debug_dataflash_downloading,
        formatBytes(download.receivedBytes),
        formatBytes(download.totalBytes),
    )
    terminalStatus != null -> terminalStatus
    catalog?.loading == true -> stringResource(R.string.debug_dataflash_loading)
    catalog?.error != null -> catalog.error
    catalog?.logs?.isNotEmpty() == true -> stringResource(R.string.debug_dataflash_loaded, catalog.logs.size)
    else -> context.getString(R.string.debug_dataflash_not_loaded)
}

private fun presentLogResult(context: Context, result: DroneLogRequestResult) {
    val message = when (result) {
        DroneLogRequestResult.SENT -> null
        DroneLogRequestResult.DISCONNECTED -> R.string.debug_requires_connection
        DroneLogRequestResult.ARMED -> R.string.debug_dataflash_disarm_first
        DroneLogRequestResult.BUSY -> R.string.debug_dataflash_busy
        DroneLogRequestResult.INVALID_LOG -> R.string.debug_dataflash_select_log
    }
    message?.let { toast(context, it) }
}

private fun toast(context: Context, message: Int) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
