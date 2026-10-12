package com.example.droneservicesapp.ui.compose.screens.missionmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.droneservicesapp.R
import com.example.droneservicesapp.mavserver.ArmCommandState
import com.example.droneservicesapp.mavserver.FlightModeCommandState
import com.example.droneservicesapp.ui.compose.navigation.MapStyleStatusCard
import com.example.droneservicesapp.ui.compose.navigation.StatusMetric
import com.example.droneservicesapp.ui.compose.navigation.StatusSubvalue
import com.example.droneservicesapp.ui.compose.navigation.StatusValue
import com.example.droneservicesapp.ui.compose.navigation.compactAltitude
import com.example.droneservicesapp.ui.compose.navigation.gpsStatusColor
import com.example.droneservicesapp.ui.home.SafetyControlsState
import com.example.droneservicesapp.ui.home.model.HomeTelemetryUiState
import com.example.droneservicesapp.ui.shell.model.ShellLiveGeoStatus

const val MISSION_MAP_STATUS_STRIP_TAG = "mission-map-status-strip"
const val MISSION_MAP_ARM_CARD_TAG = "mission-map-arm-card"
const val MISSION_MAP_FLIGHT_MODE_CARD_TAG = "mission-map-flight-mode-card"

private enum class StripDialog { GPS, RTK }

/**
 * Map-screen telemetry strip. Unlike the shell's toolbar it also opens slide-to-arm and the
 * flight-mode selector, anchored under their cards.
 */
@Composable
internal fun MapTopStatusStrip(
    telemetry: HomeTelemetryUiState,
    liveGeoStatus: ShellLiveGeoStatus,
    safety: SafetyControlsState,
    flightModeContent: @Composable () -> Unit,
    armContent: @Composable () -> Unit,
    onArmCardClick: () -> Unit,
    onFlightModeCardClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by rememberSaveable { mutableStateOf<StripDialog?>(null) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(MISSION_MAP_STATUS_STRIP_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val connectionColor = colorResource(
            if (telemetry.isConnected) R.color.ds_color_shell_active else R.color.ds_color_shell_danger,
        )
        MapStyleStatusCard(
            modifier = Modifier.weight(1.04f),
            iconRes = R.drawable.ic_status_signal_24,
            iconTint = connectionColor,
        ) {
            StatusValue(telemetry.connectionText.uppercase(), connectionColor, large = true)
        }

        val gpsColor = gpsStatusColor(telemetry)
        MapStyleStatusCard(
            modifier = Modifier.weight(1.0f),
            iconRes = R.drawable.ic_status_gps_pin_24,
            iconTint = gpsColor,
            onClick = { dialog = StripDialog.GPS },
            description = stringResource(R.string.telemetry_gps_details_title),
        ) {
            StatusValue(telemetry.gpsStatusText, gpsColor)
            StatusSubvalue(telemetry.gpsDetailText, gpsColor)
        }

        MapStyleStatusCard(
            modifier = Modifier.weight(1.4f),
            iconRes = R.drawable.ic_status_rtk_24,
            onClick = { dialog = StripDialog.RTK },
            description = stringResource(R.string.telemetry_rtk_details_title),
        ) {
            StatusSubvalue("RTK", colorResource(R.color.ds_color_text_primary))
            StatusSubvalue(telemetry.rtkMountpointText.trim().ifBlank { "--" }, colorResource(R.color.ds_color_text_primary))
        }

        val armPending = telemetry.armCommandState is ArmCommandState.Pending
        val armedColor = colorResource(
            if (telemetry.isArmed) R.color.ds_color_shell_active else R.color.ds_color_shell_warning,
        )
        Box(Modifier.weight(1.25f)) {
            MapStyleStatusCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (telemetry.isConnected) 1f else 0.88f)
                    .testTag(MISSION_MAP_ARM_CARD_TAG),
                iconRes = R.drawable.ic_status_shield_24,
                iconTint = armedColor,
                onClick = onArmCardClick,
                description = when {
                    armPending -> stringResource(R.string.arming)
                    telemetry.isArmed -> stringResource(R.string.armed)
                    telemetry.isConnected -> stringResource(R.string.arm_card_description)
                    else -> stringResource(R.string.arm_unavailable)
                },
            ) {
                StatusValue(
                    if (armPending) stringResource(R.string.arming) else telemetry.armedText.uppercase(),
                    armedColor,
                    large = true,
                )
            }
            if (safety.armOpen) armContent()
        }

        val modeEnabled = telemetry.isFlightModeControlEnabled
        val modeColor = colorResource(
            if (modeEnabled) R.color.ds_color_shell_active else R.color.ds_color_shell_unselected,
        )
        Box(Modifier.weight(1.18f)) {
            MapStyleStatusCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (modeEnabled) 1f else 0.88f)
                    .testTag(MISSION_MAP_FLIGHT_MODE_CARD_TAG),
                iconRes = R.drawable.ic_flight_mode_24,
                iconTint = modeColor,
                onClick = onFlightModeCardClick,
                description = if (modeEnabled) {
                    stringResource(R.string.flight_mode_card_description, telemetry.flightModeText)
                } else {
                    stringResource(R.string.flight_mode_unavailable)
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        StatusSubvalue(stringResource(R.string.flight_mode_short_label).uppercase(), modeColor)
                        StatusValue(telemetry.flightModeText, modeColor)
                    }
                    if (telemetry.flightModeCommandState is FlightModeCommandState.Pending) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = modeColor)
                    }
                    if (modeEnabled) {
                        Image(
                            painterResource(R.drawable.ic_chevron_down_24),
                            null,
                            Modifier.size(16.dp),
                            colorFilter = ColorFilter.tint(modeColor),
                        )
                    }
                }
            }
            if (safety.flightModeOpen) flightModeContent()
        }

        MapStyleStatusCard(modifier = Modifier.weight(0.98f), iconRes = R.drawable.ic_status_speed_24) {
            StatusMetric(telemetry.speedText.removePrefix("SPD:").trim().ifBlank { "-- m/s" })
        }
        MapStyleStatusCard(modifier = Modifier.weight(0.92f), iconRes = R.drawable.ic_status_altitude_24) {
            StatusMetric(compactAltitude(telemetry.altitudeText))
        }
        val batteryColor = colorResource(telemetry.batteryColorRes)
        MapStyleStatusCard(
            modifier = Modifier.weight(1.05f),
            iconRes = telemetry.batteryIconRes,
            iconTint = batteryColor,
        ) {
            StatusValue(telemetry.batteryText, batteryColor)
        }
        val liveGeoColor = Color(liveGeoStatus.color)
        MapStyleStatusCard(
            modifier = Modifier.weight(1.22f),
            iconRes = if (liveGeoStatus.isClear) R.drawable.ic_baseline_check_circle_outline_24 else R.drawable.ic_status_warning_24,
            iconTint = liveGeoColor,
        ) {
            StatusSubvalue(stringResource(R.string.top_status_live_geo).uppercase())
            StatusValue(liveGeoStatus.label.ifBlank { stringResource(R.string.live_geo_unknown_status) }, liveGeoColor)
        }
    }

    dialog?.let { selected ->
        val gps = selected == StripDialog.GPS
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(if (gps) R.string.telemetry_gps_details_title else R.string.telemetry_rtk_details_title)) },
            text = { Text(if (gps) telemetry.gpsDialogText else telemetry.rtkDialogText) },
            confirmButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}
