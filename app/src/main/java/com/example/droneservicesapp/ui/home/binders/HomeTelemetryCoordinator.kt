package com.example.droneservicesapp.ui.home.binders

import androidx.appcompat.app.AppCompatActivity
import android.location.Location
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.rtk.RtkForwardingState
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.home.model.HomeTelemetryUiState
import com.example.droneservicesapp.ui.home.model.HomeTelemetryViewModel
import java.math.RoundingMode
import java.text.DecimalFormat

class HomeTelemetryCoordinator(
    private val activity: AppCompatActivity,
    private val droneViewModel: DroneViewModel,
    private val homeTelemetryViewModel: HomeTelemetryViewModel,
) {
    fun bind(lifecycleOwner: LifecycleOwner) {
        renderCurrent()

        droneViewModel.conStateLiveData.observe(lifecycleOwner) { connState ->
            update { state ->
                if (connState) {
                    state.copy(
                        isConnected = true,
                        connectionText = activity.getString(R.string.shell_status_connected)
                    )
                } else {
                    disconnectedState(state)
                }
            }
        }

        droneViewModel.droneBatteryPercentage.observe(lifecycleOwner) { batteryPercentage ->
            if (droneViewModel.conStateLiveData.value != true) return@observe

            val df = DecimalFormat("#.##").apply { roundingMode = RoundingMode.DOWN }
            val iconRes = when {
                batteryPercentage == -1.0F -> R.drawable.ic_baseline_battery_alert_24
                batteryPercentage >= 1.0F -> R.drawable.ic_baseline_battery_full_24
                batteryPercentage >= 0.7F -> R.drawable.ic_baseline_battery_6_bar_24
                batteryPercentage >= 0.4F -> R.drawable.ic_baseline_battery_4_bar_24
                batteryPercentage >= 0.25F -> R.drawable.ic_baseline_battery_3_bar_24
                else -> R.drawable.ic_baseline_battery_2_bar_24
            }
            update { state ->
                state.copy(
                    batteryText = "${df.format(batteryPercentage * 100.0F)}%",
                    batteryIconRes = iconRes,
                    batteryColorRes = resolveBatteryColorRes(batteryPercentage)
                )
            }
        }

        droneViewModel.droneLocationLiveData.observe(lifecycleOwner) { location ->
            if (droneViewModel.conStateLiveData.value != true) return@observe
            update { state ->
                state.copy(
                    altitudeText = "${location.altitude.toInt()}m",
                    speedText = formatSpeedText(location),
                    gpsStatusText = formatGpsStatus(
                        isConnected = true,
                        hasLocation = true,
                        rtkState = droneViewModel.rtkForwardingState.value
                    )
                )
            }
        }

        droneViewModel.liquidLevel.observe(lifecycleOwner) { liquidLevel ->
            update { state ->
                state.copy(sprayerText = "$liquidLevel%")
            }
        }

        droneViewModel.armedState.observe(lifecycleOwner) { armedState ->
            update { state ->
                state.copy(
                    isArmed = armedState,
                    armedText = activity.getString(if (armedState) R.string.armed else R.string.disarmed)
                )
            }
        }

        droneViewModel.uploadProgressPercent.observe(lifecycleOwner) { percent ->
            update { state ->
                when {
                    percent in 1..99 -> state.copy(
                        showUploadProgress = true,
                        uploadProgressText = "Uploading ${percent}%"
                    )
                    else -> state.copy(showUploadProgress = false)
                }
            }
        }

        droneViewModel.droneFrontDistance.observe(lifecycleOwner) { frontDistance ->
            update { state ->
                state.copy(frontDistanceMeters = frontDistance)
            }
        }

        droneViewModel.droneBackDistance.observe(lifecycleOwner) { backDistance ->
            update { state ->
                state.copy(backDistanceMeters = backDistance)
            }
        }

        droneViewModel.rtkForwardingState.observe(lifecycleOwner) { rtkState ->
            update { state ->
                state.copy(
                    gpsStatusText = formatGpsStatus(
                        isConnected = state.isConnected,
                        hasLocation = droneViewModel.droneLocationLiveData.value != null,
                        rtkState = rtkState
                    )
                )
            }
        }
    }

    private fun renderCurrent() {
        val isConnected = droneViewModel.conStateLiveData.value == true
        val armed = droneViewModel.armedState.value == true
        val batteryPercentage = droneViewModel.droneBatteryPercentage.value
        val batteryIconRes = when {
            batteryPercentage == null || batteryPercentage == -1.0F -> R.drawable.ic_baseline_battery_alert_24
            batteryPercentage >= 1.0F -> R.drawable.ic_baseline_battery_full_24
            batteryPercentage >= 0.7F -> R.drawable.ic_baseline_battery_6_bar_24
            batteryPercentage >= 0.4F -> R.drawable.ic_baseline_battery_4_bar_24
            batteryPercentage >= 0.25F -> R.drawable.ic_baseline_battery_3_bar_24
            else -> R.drawable.ic_baseline_battery_2_bar_24
        }
        val batteryText = if (batteryPercentage == null) {
            "--%"
        } else {
            val df = DecimalFormat("#.##").apply { roundingMode = RoundingMode.DOWN }
            "${df.format(batteryPercentage * 100.0F)}%"
        }
        val uploadProgress = droneViewModel.uploadProgressPercent.value ?: 0
        val batteryColorRes = resolveBatteryColorRes(batteryPercentage)
        val currentLocation = droneViewModel.droneLocationLiveData.value

        homeTelemetryViewModel.homeTelemetryUiState.value = HomeTelemetryUiState(
            isConnected = isConnected,
            connectionText = activity.getString(
                if (isConnected) R.string.shell_status_connected else R.string.shell_status_disconnected
            ),
            gpsStatusText = formatGpsStatus(
                isConnected = isConnected,
                hasLocation = currentLocation != null,
                rtkState = droneViewModel.rtkForwardingState.value
            ),
            batteryText = batteryText,
            batteryIconRes = batteryIconRes,
            batteryColorRes = batteryColorRes,
            altitudeText = currentLocation?.altitude?.toInt()?.let { "${it}m" } ?: "--",
            speedText = formatSpeedText(currentLocation),
            sprayerText = "${droneViewModel.liquidLevel.value ?: 0}%",
            armedText = activity.getString(if (armed) R.string.armed else R.string.disarmed),
            isArmed = armed,
            uploadProgressText = "Uploading ${uploadProgress}%",
            showUploadProgress = uploadProgress in 1..99,
            frontDistanceMeters = droneViewModel.droneFrontDistance.value,
            backDistanceMeters = droneViewModel.droneBackDistance.value
        )
    }

    private fun update(transform: (HomeTelemetryUiState) -> HomeTelemetryUiState) {
        val current = homeTelemetryViewModel.homeTelemetryUiState.value
            ?: HomeTelemetryUiState(
                connectionText = activity.getString(R.string.shell_status_disconnected),
                armedText = activity.getString(R.string.disarmed)
            )
        homeTelemetryViewModel.homeTelemetryUiState.value = transform(current)
    }

    private fun disconnectedState(current: HomeTelemetryUiState): HomeTelemetryUiState {
        return current.copy(
            isConnected = false,
            connectionText = activity.getString(R.string.shell_status_disconnected),
            gpsStatusText = "NO GPS",
            batteryText = "--%",
            batteryIconRes = R.drawable.ic_baseline_battery_alert_24,
            batteryColorRes = R.color.ds_color_shell_unselected,
            altitudeText = "--",
            speedText = "SPD: 0.0",
            sprayerText = "--%",
            armedText = activity.getString(R.string.disarmed),
            isArmed = false,
            showUploadProgress = false,
            frontDistanceMeters = null,
            backDistanceMeters = null
        )
    }

    private fun resolveBatteryColorRes(batteryPercentage: Float?): Int {
        if (batteryPercentage == null || batteryPercentage < 0f) {
            return R.color.ds_color_shell_unselected
        }
        return when {
            batteryPercentage <= 0.2f -> R.color.ds_color_shell_danger
            batteryPercentage <= 0.5f -> R.color.ds_color_shell_warning
            else -> R.color.ds_color_shell_active
        }
    }

    private fun formatGpsStatus(
        isConnected: Boolean,
        hasLocation: Boolean,
        rtkState: RtkForwardingState?,
    ): String {
        if (!isConnected) return "NO GPS"
        return when (rtkState) {
            is RtkForwardingState.Streaming -> "RTK FIX"
            is RtkForwardingState.ConnectingToCaster,
            is RtkForwardingState.Reconnecting -> "RTK FLOAT"
            is RtkForwardingState.WaitingForGps -> "NO GPS"
            else -> if (hasLocation) "3D LOCK" else "NO GPS"
        }
    }

    private fun formatSpeedText(location: Location?): String {
        val speedMetersPerSecond = location?.takeIf { it.hasSpeed() }?.speed ?: 0f
        return "SPD: ${"%.1f".format(speedMetersPerSecond)}"
    }
}
