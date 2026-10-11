package com.example.droneservicesapp.ui.compose.screens.mavlink

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.example.droneservicesapp.R
import com.example.droneservicesapp.ui.compose.components.AppButton
import com.example.droneservicesapp.ui.compose.components.AppButtonStyle
import com.example.droneservicesapp.ui.compose.screens.common.MoreDivider
import com.example.droneservicesapp.ui.compose.screens.common.MorePanel
import com.example.droneservicesapp.ui.compose.screens.common.MoreScreen
import com.example.droneservicesapp.ui.compose.screens.common.MoreSettingRow
import com.example.droneservicesapp.ui.compose.screens.common.TwoColumnLayout

private enum class MavlinkDialog { INTERFACE, LOCAL_PORT, TARGET_HOST, TARGET_PORT, SYSTEM_ID, QGC_HOST, QGC_PORT }

const val MAVLINK_NETWORK_SCREEN_TAG = "compose-mavlink-network-screen"

private data class MavlinkUiState(
    val interfaceType: String,
    val localPort: String,
    val targetHost: String,
    val targetPort: String,
    val systemId: String,
    val bridgeEnabled: Boolean,
    val qgcHost: String,
    val qgcPort: String,
    val bridgeStatus: String,
) {
    val isTcp: Boolean get() = interfaceType.equals("TCP", ignoreCase = true)
}

@Composable
fun MavlinkNetworkScreen() {
    val context = LocalContext.current
    val preferences = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var revision by remember { mutableIntStateOf(0) }
    var activeDialogName by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogInput by rememberSaveable { mutableStateOf("") }
    var dialogError by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val state = remember(revision) { readState(preferences, context) }
    val activeDialog = activeDialogName?.let { runCatching { MavlinkDialog.valueOf(it) }.getOrNull() }

    fun openDialog(dialog: MavlinkDialog, value: String = "") {
        activeDialogName = dialog.name
        dialogInput = value
        dialogError = null
    }

    MoreScreen(
        title = stringResource(R.string.menu_mavlink_network),
        subtitle = stringResource(R.string.more_mavlink_subtitle),
        iconRes = R.drawable.ic_menu_mavlink_network,
        modifier = Modifier.testTag(MAVLINK_NETWORK_SCREEN_TAG),
    ) {
        TwoColumnLayout(
            left = {
                MorePanel(
                    title = stringResource(R.string.more_drone_connection),
                    subtitle = stringResource(R.string.more_drone_connection_subtitle),
                ) {
                    MoreSettingRow(
                        title = stringResource(R.string.drone_conn_interface_pref),
                        value = state.interfaceType,
                        iconRes = R.drawable.ic_status_signal_24,
                        onClick = { openDialog(MavlinkDialog.INTERFACE, state.interfaceType) },
                    )
                    MoreDivider()
                    if (!state.isTcp) {
                        MoreSettingRow(
                            title = stringResource(R.string.mavlink_local_port_title),
                            value = state.localPort,
                            iconRes = R.drawable.ic_menu_tune,
                            onClick = { openDialog(MavlinkDialog.LOCAL_PORT, state.localPort) },
                        )
                    }
                    MoreSettingRow(
                        title = stringResource(R.string.mavlink_aircraft_host_title),
                        value = state.targetHost.ifBlank {
                            stringResource(if (state.isTcp) R.string.mavlink_required_value else R.string.mavlink_auto_value)
                        },
                        iconRes = R.drawable.ic_server_24,
                        onClick = { openDialog(MavlinkDialog.TARGET_HOST, state.targetHost) },
                    )
                    MoreSettingRow(
                        title = stringResource(R.string.mavlink_aircraft_port_title),
                        value = state.targetPort,
                        iconRes = R.drawable.ic_menu_tune,
                        onClick = { openDialog(MavlinkDialog.TARGET_PORT, state.targetPort) },
                    )
                    MoreSettingRow(
                        title = stringResource(R.string.mavlink_gcs_system_id_title),
                        value = state.systemId,
                        iconRes = R.drawable.ic_person_24,
                        onClick = { openDialog(MavlinkDialog.SYSTEM_ID, state.systemId) },
                    )
                    Text(stringResource(R.string.more_mavlink_direct_info))
                }
            },
            right = {
                if (!state.isTcp) {
                    MorePanel(
                        title = stringResource(R.string.mavlink_bridge_section_title),
                        subtitle = stringResource(R.string.more_mavlink_bridge_subtitle),
                    ) {
                        MoreSettingRow(
                            title = stringResource(R.string.mavlink_bridge_enabled_title),
                            value = "",
                            iconRes = R.drawable.ic_menu_mavlink_network,
                            supportingText = stringResource(R.string.more_mavlink_distinct_ips),
                            trailing = {
                                Switch(
                                    checked = state.bridgeEnabled,
                                    onCheckedChange = { enabled ->
                                        preferences.edit { putBoolean(context.getString(R.string.mavlink_bridge_enabled_pref), enabled) }
                                    },
                                )
                            },
                        )
                        MoreDivider()
                        MoreSettingRow(
                            title = stringResource(R.string.mavlink_qgc_host_title),
                            value = state.qgcHost.ifBlank { stringResource(R.string.mavlink_auto_value) },
                            iconRes = R.drawable.ic_server_24,
                            enabled = state.bridgeEnabled,
                            onClick = { openDialog(MavlinkDialog.QGC_HOST, state.qgcHost) },
                        )
                        MoreSettingRow(
                            title = stringResource(R.string.mavlink_qgc_port_title),
                            value = state.qgcPort,
                            iconRes = R.drawable.ic_menu_tune,
                            enabled = state.bridgeEnabled,
                            onClick = { openDialog(MavlinkDialog.QGC_PORT, state.qgcPort) },
                        )
                        MoreDivider()
                        Text(state.bridgeStatus)
                    }
                }
            },
        )
    }

    activeDialog?.let { dialog ->
        if (dialog == MavlinkDialog.INTERFACE) {
            InterfaceDialog(
                current = state.interfaceType,
                values = context.resources.getStringArray(R.array.drone_connection_interfaces).toList(),
                onSelected = { value ->
                    preferences.edit { putString(context.getString(R.string.mavlink_interface_pref), value) }
                    activeDialogName = null
                },
                onDismiss = { activeDialogName = null },
            )
        } else {
            val numeric = dialog in setOf(MavlinkDialog.LOCAL_PORT, MavlinkDialog.TARGET_PORT, MavlinkDialog.SYSTEM_ID, MavlinkDialog.QGC_PORT)
            val title = dialogTitle(dialog)
            AlertDialog(
                onDismissRequest = { activeDialogName = null },
                title = { Text(stringResource(title)) },
                text = {
                    OutlinedTextField(
                        value = dialogInput,
                        onValueChange = { dialogInput = it; dialogError = null },
                        singleLine = true,
                        isError = dialogError != null,
                        supportingText = dialogError?.let { error -> ({ Text(error) }) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Decimal,
                        ),
                    )
                },
                confirmButton = {
                    AppButton(stringResource(android.R.string.ok), {
                        val errorRes = validateMavlinkValue(dialog, dialogInput)
                        if (errorRes != null) {
                            dialogError = context.getString(errorRes)
                        } else {
                            preferences.edit { putString(context.getString(dialogKey(dialog)), dialogInput.trim()) }
                            activeDialogName = null
                        }
                    })
                },
                dismissButton = {
                    AppButton(
                        stringResource(android.R.string.cancel),
                        { activeDialogName = null },
                        style = AppButtonStyle.TEXT,
                    )
                },
            )
        }
    }
}

@Composable
private fun InterfaceDialog(current: String, values: List<String>, onSelected: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drone_conn_interface_pref)) },
        text = {
            Column {
                values.forEach { value ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelected(value) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(current == value, onClick = { onSelected(value) })
                        Text(value)
                    }
                }
            }
        },
        confirmButton = {},
    )
}

private fun readState(preferences: SharedPreferences, context: android.content.Context): MavlinkUiState {
    fun string(key: Int, default: String = "") = preferences.getString(context.getString(key), default).orEmpty().trim()
    val interfaceType = string(R.string.mavlink_interface_pref, "UDP").ifBlank { "UDP" }
    val target = string(R.string.mavlink_target_host_pref)
    val qgc = string(R.string.mavlink_bridge_host_pref)
    val enabled = preferences.getBoolean(context.getString(R.string.mavlink_bridge_enabled_pref), false)
    val bridgeStatus = when {
        !enabled -> context.getString(R.string.mavlink_bridge_status_off)
        target.isEmpty() || qgc.isEmpty() -> context.getString(R.string.mavlink_bridge_status_incomplete)
        target == qgc -> context.getString(R.string.mavlink_bridge_status_same_host)
        else -> context.getString(R.string.mavlink_bridge_status_ready, target, qgc)
    }
    return MavlinkUiState(
        interfaceType = interfaceType,
        localPort = string(R.string.mavlink_lan_port_pref, "14550"),
        targetHost = target,
        targetPort = string(R.string.mavlink_target_port_pref, "14550"),
        systemId = string(R.string.mavlink_gcs_system_id_pref, "254"),
        bridgeEnabled = enabled,
        qgcHost = qgc,
        qgcPort = string(R.string.mavlink_bridge_port_pref, "14550"),
        bridgeStatus = bridgeStatus,
    )
}

private fun dialogTitle(dialog: MavlinkDialog): Int = when (dialog) {
    MavlinkDialog.LOCAL_PORT -> R.string.mavlink_local_port_title
    MavlinkDialog.TARGET_HOST -> R.string.mavlink_aircraft_host_title
    MavlinkDialog.TARGET_PORT -> R.string.mavlink_aircraft_port_title
    MavlinkDialog.SYSTEM_ID -> R.string.mavlink_gcs_system_id_title
    MavlinkDialog.QGC_HOST -> R.string.mavlink_qgc_host_title
    MavlinkDialog.QGC_PORT -> R.string.mavlink_qgc_port_title
    MavlinkDialog.INTERFACE -> R.string.drone_conn_interface_pref
}

private fun dialogKey(dialog: MavlinkDialog): Int = when (dialog) {
    MavlinkDialog.LOCAL_PORT -> R.string.mavlink_lan_port_pref
    MavlinkDialog.TARGET_HOST -> R.string.mavlink_target_host_pref
    MavlinkDialog.TARGET_PORT -> R.string.mavlink_target_port_pref
    MavlinkDialog.SYSTEM_ID -> R.string.mavlink_gcs_system_id_pref
    MavlinkDialog.QGC_HOST -> R.string.mavlink_bridge_host_pref
    MavlinkDialog.QGC_PORT -> R.string.mavlink_bridge_port_pref
    MavlinkDialog.INTERFACE -> R.string.mavlink_interface_pref
}

private fun validateMavlinkValue(dialog: MavlinkDialog, rawValue: String): Int? {
    val value = rawValue.trim()
    return when (dialog) {
        MavlinkDialog.SYSTEM_ID -> if (value.toIntOrNull() !in 1..255) R.string.mavlink_invalid_system_id else null
        MavlinkDialog.LOCAL_PORT, MavlinkDialog.TARGET_PORT, MavlinkDialog.QGC_PORT ->
            if (value.toIntOrNull() !in 1..65535) R.string.mavlink_invalid_port else null
        MavlinkDialog.TARGET_HOST, MavlinkDialog.QGC_HOST ->
            if (value.isNotEmpty() && !isValidIpv4(value)) R.string.mavlink_invalid_ip else null
        MavlinkDialog.INTERFACE -> null
    }
}

internal fun isValidIpv4(value: String): Boolean {
    val parts = value.split('.')
    return parts.size == 4 && parts.all { part ->
        part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) && part.toInt() in 0..255
    }
}
