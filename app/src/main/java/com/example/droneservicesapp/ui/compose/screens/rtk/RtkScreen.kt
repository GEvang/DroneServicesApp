package com.example.droneservicesapp.ui.compose.screens.rtk

import android.app.Application
import android.net.Network
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.rtk.NtripClient
import com.example.droneservicesapp.data.rtk.NtripResult
import com.example.droneservicesapp.data.rtk.RtkConfig
import com.example.droneservicesapp.data.rtk.RtkForwardingState
import com.example.droneservicesapp.data.rtk.RtkMountpoint
import com.example.droneservicesapp.data.rtk.RtkPreferences
import com.example.droneservicesapp.data.rtk.RtkValidator
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.common.RtkTonePlayer
import com.example.droneservicesapp.ui.compose.components.AppButton
import com.example.droneservicesapp.ui.compose.components.AppButtonStyle
import com.example.droneservicesapp.ui.compose.components.AppTextField
import com.example.droneservicesapp.ui.compose.components.NumericInputField
import com.example.droneservicesapp.ui.compose.screens.common.LabeledValue
import com.example.droneservicesapp.ui.compose.screens.common.MoreDivider
import com.example.droneservicesapp.ui.compose.screens.common.MorePanel
import com.example.droneservicesapp.ui.compose.screens.common.MoreScreen
import com.example.droneservicesapp.ui.compose.screens.common.TwoColumnLayout
import com.example.droneservicesapp.ui.compose.theme.DroneServicesThemeTokens
import java.util.Locale
import javax.net.SocketFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

const val RTK_SCREEN_TAG = "compose-rtk-screen"

data class RtkScreenUiState(
    val ip: String = "",
    val port: String = "2101",
    val username: String = "",
    val password: String = "",
    val mountpoint: String = "",
    val selectedMountpoint: RtkMountpoint? = null,
    val status: String = "",
    val statusSuccess: Boolean = false,
    val busy: Boolean = false,
    val mountpoints: List<RtkMountpoint> = emptyList(),
    val selectingMountpoint: Boolean = false,
)

class RtkScreenViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = RtkPreferences(application)
    private val ntripClient = NtripClient()
    private val effectsChannel = Channel<String>(Channel.BUFFERED)
    val effects = effectsChannel.receiveAsFlow()

    private val initialConfig = preferences.getConfig()
    private val mutableState = MutableStateFlow(
        RtkScreenUiState(
            ip = initialConfig.ip,
            port = initialConfig.port.toString(),
            username = initialConfig.username,
            password = initialConfig.password,
            mountpoint = initialConfig.mountpoint,
            selectedMountpoint = initialConfig.selectedMountpoint,
            status = initialConfig.lastStatusMessage.ifBlank { application.getString(R.string.rtk_connection_not_tested) },
            statusSuccess = initialConfig.lastFetchSucceeded,
        )
    )
    val state = mutableState.asStateFlow()

    fun updateIp(value: String) = updateBaseConfig { copy(ip = value) }.also { preferences.saveIp(value) }
    fun updatePort(value: String) = updateBaseConfig { copy(port = value) }.also {
        value.trim().toIntOrNull()?.let(preferences::savePort)
    }
    fun updateUsername(value: String) = updateBaseConfig { copy(username = value) }.also { preferences.saveUsername(value) }
    fun updatePassword(value: String) = updateBaseConfig { copy(password = value) }.also { preferences.savePassword(value) }

    fun fetchMountpoints(socketFactory: SocketFactory?, network: Network?) {
        val config = buildConfig(requireMountpoint = false) ?: return
        mutableState.value = mutableState.value.copy(busy = true)
        updateStatus(getApplication<Application>().getString(R.string.rtk_status_fetching), false)
        viewModelScope.launch {
            val result = runCatching { ntripClient.fetchSourceTable(config, socketFactory, network) }
                .getOrElse { error -> NtripResult.NetworkFailure(error.message ?: "Network failure") }
            mutableState.value = mutableState.value.copy(busy = false)
            if (result is NtripResult.SourceTableSuccess) {
                when (result.mountpoints.size) {
                    0 -> publishFailure(getApplication<Application>().getString(R.string.rtk_status_no_mountpoints))
                    1 -> selectMountpoint(result.mountpoints.first())
                    else -> mutableState.value = mutableState.value.copy(
                        mountpoints = result.mountpoints,
                        selectingMountpoint = true,
                    )
                }
            } else handleResult(result, notify = false)
        }
    }

    fun testConnection(socketFactory: SocketFactory?, network: Network?) {
        val config = buildConfig(requireMountpoint = true) ?: return
        mutableState.value = mutableState.value.copy(busy = true)
        updateStatus(getApplication<Application>().getString(R.string.rtk_status_testing), false)
        viewModelScope.launch {
            val result = runCatching { ntripClient.testConnection(config, socketFactory, network) }
                .getOrElse { error -> NtripResult.NetworkFailure(error.message ?: "Network failure") }
            mutableState.value = mutableState.value.copy(busy = false)
            handleResult(result, notify = true)
        }
    }

    fun selectMountpoint(mountpoint: RtkMountpoint) {
        preferences.saveMountpoint(mountpoint)
        val message = getApplication<Application>().getString(R.string.rtk_mountpoint_selected, mountpoint.name)
        mutableState.value = mutableState.value.copy(
            mountpoint = mountpoint.name,
            selectedMountpoint = mountpoint,
            mountpoints = emptyList(),
            selectingMountpoint = false,
        )
        updateStatus(message, false)
        viewModelScope.launch { effectsChannel.send(message) }
    }

    fun dismissMountpoints() {
        mutableState.value = mutableState.value.copy(selectingMountpoint = false)
    }

    private fun updateBaseConfig(transform: RtkScreenUiState.() -> RtkScreenUiState) {
        val current = mutableState.value
        if (current.mountpoint.isNotEmpty()) preferences.saveMountpoint("")
        mutableState.value = current.transform().copy(
            mountpoint = "",
            selectedMountpoint = null,
            status = getApplication<Application>().getString(R.string.rtk_connection_not_tested),
            statusSuccess = false,
        )
        saveStatus()
    }

    private fun buildConfig(requireMountpoint: Boolean): RtkConfig? {
        val current = mutableState.value
        val port = current.port.trim().toIntOrNull()
        val error = when {
            !RtkValidator.isValidIp(current.ip.trim()) -> R.string.rtk_ip_required
            port == null || !RtkValidator.isValidPort(port) -> R.string.rtk_port_invalid
            !RtkValidator.isValidUsername(current.username.trim()) -> R.string.rtk_username_required
            !RtkValidator.isValidPassword(current.password) -> R.string.rtk_password_required
            requireMountpoint && !RtkValidator.isValidMountpoint(current.mountpoint.trim()) -> R.string.rtk_mountpoint_required
            else -> null
        }
        if (error != null) {
            publishFailure(
                getApplication<Application>().getString(
                    R.string.rtk_status_invalid_config,
                    getApplication<Application>().getString(error),
                )
            )
            return null
        }
        return RtkConfig(
            ip = current.ip.trim(),
            port = port ?: 0,
            username = current.username.trim(),
            password = current.password,
            mountpoint = current.mountpoint.trim(),
        )
    }

    private fun handleResult(result: NtripResult, notify: Boolean) {
        val app = getApplication<Application>()
        val message = when (result) {
            is NtripResult.ConnectionSuccess -> app.getString(R.string.rtk_status_connected)
            is NtripResult.AuthFailure -> app.getString(R.string.rtk_status_auth_failed)
            is NtripResult.MountpointNotFound -> app.getString(R.string.rtk_status_mountpoint_not_found)
            is NtripResult.NetworkFailure -> app.getString(R.string.rtk_status_network_failed, result.message)
            is NtripResult.InvalidConfig -> app.getString(R.string.rtk_status_invalid_config, result.message)
            is NtripResult.ProtocolFailure -> result.message
            is NtripResult.SourceTableSuccess -> app.getString(R.string.rtk_status_idle)
        }
        updateStatus(message, result is NtripResult.ConnectionSuccess)
        if (notify) viewModelScope.launch { effectsChannel.send(message) }
    }

    private fun publishFailure(message: String) {
        updateStatus(message, false)
        viewModelScope.launch { effectsChannel.send(message) }
    }

    private fun updateStatus(message: String, success: Boolean) {
        mutableState.value = mutableState.value.copy(status = message, statusSuccess = success)
        saveStatus()
    }

    private fun saveStatus() {
        preferences.saveLastStatusMessage(mutableState.value.status)
        preferences.saveLastFetchSucceeded(mutableState.value.statusSuccess)
    }
}

@Composable
fun RtkScreen(droneViewModel: DroneViewModel, screenViewModel: RtkScreenViewModel = viewModel()) {
    val context = LocalContext.current
    val state by screenViewModel.state.collectAsStateWithLifecycle()
    val forwarding by droneViewModel.rtkForwardingState.observeAsState(RtkForwardingState.Idle)
    val gpsDebug by droneViewModel.rtkGpsDebugStatus.observeAsState("")
    val selectedMountpoint by droneViewModel.selectedRtkMountpoint.observeAsState(state.selectedMountpoint)
    var lastStreaming by remember { mutableStateOf<Boolean?>(null) }
    var lastMountpoint by remember { mutableStateOf(state.mountpoint) }

    LaunchedEffect(Unit) {
        droneViewModel.onRtkConfigurationChanged()
        screenViewModel.effects.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(state.mountpoint) {
        if (lastMountpoint != state.mountpoint) {
            droneViewModel.onRtkConfigurationChanged(forceStart = true)
            lastMountpoint = state.mountpoint
        }
    }
    val streaming = forwarding is RtkForwardingState.Streaming
    LaunchedEffect(streaming) {
        if (lastStreaming != null && lastStreaming != streaming) {
            if (streaming) RtkTonePlayer.playConnectedTone() else RtkTonePlayer.playDisconnectedTone()
        }
        lastStreaming = streaming
    }

    MoreScreen(
        title = stringResource(R.string.rtk_title),
        subtitle = stringResource(R.string.rtk_subtitle),
        iconRes = R.drawable.ic_status_rtk_24,
        modifier = Modifier.testTag(RTK_SCREEN_TAG),
    ) {
        TwoColumnLayout(
            left = {
                MorePanel(
                    title = stringResource(R.string.rtk_caster_access),
                    subtitle = stringResource(R.string.rtk_caster_access_subtitle),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            state.ip,
                            { value ->
                                screenViewModel.updateIp(value)
                                droneViewModel.onRtkConfigurationChanged(forceStart = true)
                            },
                            stringResource(R.string.rtk_ip),
                            Modifier.weight(1f),
                        )
                        NumericInputField(
                            state.port,
                            { value ->
                                screenViewModel.updatePort(value)
                                droneViewModel.onRtkConfigurationChanged(forceStart = true)
                            },
                            stringResource(R.string.rtk_port),
                            Modifier.weight(1f),
                            allowDecimal = false,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            state.username,
                            { value ->
                                screenViewModel.updateUsername(value)
                                droneViewModel.onRtkConfigurationChanged(forceStart = true)
                            },
                            stringResource(R.string.rtk_username),
                            Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = { value ->
                                screenViewModel.updatePassword(value)
                                droneViewModel.onRtkConfigurationChanged(forceStart = true)
                            },
                            label = { Text(stringResource(R.string.rtk_password)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                    OutlinedTextField(
                        value = state.mountpoint,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.rtk_mountpoint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppButton(
                        stringResource(R.string.rtk_fetch_mountpoints),
                        { screenViewModel.fetchMountpoints(droneViewModel.currentRtkSocketFactory(), droneViewModel.currentRtkInternetNetwork()) },
                        Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                        style = AppButtonStyle.SECONDARY,
                    )
                    AppButton(
                        stringResource(R.string.rtk_test_connection),
                        { screenViewModel.testConnection(droneViewModel.currentRtkSocketFactory(), droneViewModel.currentRtkInternetNetwork()) },
                        Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                    )
                }
            },
            right = {
                MorePanel(title = stringResource(R.string.rtk_connection_status)) {
                    LabeledValue(
                        stringResource(R.string.rtk_status),
                        state.status,
                        valueColor = if (state.statusSuccess) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    LabeledValue(
                        stringResource(R.string.rtk_forwarding_status),
                        forwardingDisplayString(forwarding),
                        valueColor = forwardingStateColor(forwarding),
                    )
                    MoreDivider()
                    LabeledValue(stringResource(R.string.rtk_gps_diagnostics), gpsDebug)
                    MoreDivider()
                    LabeledValue(
                        stringResource(R.string.rtk_base_coordinates),
                        baseCoordinates(selectedMountpoint),
                    )
                }
            },
        )
    }

    if (state.selectingMountpoint) {
        AlertDialog(
            onDismissRequest = screenViewModel::dismissMountpoints,
            title = { Text(stringResource(R.string.rtk_select_mountpoint)) },
            text = {
                Column {
                    state.mountpoints.forEach { mountpoint ->
                        Row(
                            Modifier.fillMaxWidth().clickable { screenViewModel.selectMountpoint(mountpoint) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                state.mountpoint == mountpoint.name,
                                onClick = { screenViewModel.selectMountpoint(mountpoint) },
                            )
                            Text(mountpoint.name)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun forwardingStateColor(state: RtkForwardingState) = when (state) {
    is RtkForwardingState.Streaming -> MaterialTheme.colorScheme.secondary
    is RtkForwardingState.ConnectingToCaster,
    is RtkForwardingState.Reconnecting,
    is RtkForwardingState.WaitingForInternet,
    is RtkForwardingState.WaitingForDrone,
    is RtkForwardingState.WaitingForGps,
    is RtkForwardingState.WaitingForMountpoint -> DroneServicesThemeTokens.warning
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun forwardingDisplayString(state: RtkForwardingState): String = when (state) {
    is RtkForwardingState.Idle -> stringResource(R.string.rtk_forwarding_idle)
    is RtkForwardingState.WaitingForMountpoint -> stringResource(R.string.rtk_forwarding_waiting_for_mountpoint)
    is RtkForwardingState.WaitingForInternet -> stringResource(R.string.rtk_forwarding_waiting_for_internet)
    is RtkForwardingState.WaitingForDrone -> stringResource(R.string.rtk_forwarding_waiting_for_drone)
    is RtkForwardingState.WaitingForGps -> stringResource(R.string.rtk_forwarding_waiting_for_drone_gps)
    is RtkForwardingState.ConnectingToCaster -> stringResource(R.string.rtk_forwarding_connecting)
    is RtkForwardingState.Streaming -> stringResource(R.string.rtk_forwarding_streaming)
    is RtkForwardingState.Reconnecting -> stringResource(R.string.rtk_forwarding_reconnecting, state.message)
    is RtkForwardingState.InvalidConfig -> stringResource(R.string.rtk_forwarding_invalid_config, state.message)
    is RtkForwardingState.AuthFailed -> stringResource(R.string.rtk_forwarding_auth_failed)
    is RtkForwardingState.MountpointInvalid -> stringResource(R.string.rtk_forwarding_mountpoint_invalid, state.message)
    is RtkForwardingState.NetworkError -> stringResource(R.string.rtk_forwarding_network_error, state.message)
    is RtkForwardingState.ProtocolError -> stringResource(R.string.rtk_forwarding_protocol_error, state.message)
    is RtkForwardingState.Stopped -> stringResource(R.string.rtk_forwarding_stopped)
}

@Composable
private fun baseCoordinates(mountpoint: RtkMountpoint?): String = when {
    mountpoint?.hasCoordinates == true -> stringResource(
        R.string.rtk_base_coordinates_format,
        String.format(Locale.US, "%.6f", mountpoint.latitude),
        String.format(Locale.US, "%.6f", mountpoint.longitude),
    )
    else -> stringResource(R.string.rtk_base_coordinates_unknown)
}
