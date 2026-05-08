package com.example.droneservicesapp.ui.home.binders

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.R
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.mavserver.TelemetryMapping
import com.example.droneservicesapp.ui.home.model.HomeTelemetryUiState
import com.example.droneservicesapp.ui.home.model.HomeTelemetryViewModel
import java.util.Locale

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

            val iconRes = when {
                batteryPercentage < 0.0F -> R.drawable.ic_baseline_battery_alert_24
                batteryPercentage >= 1.0F -> R.drawable.ic_baseline_battery_full_24
                batteryPercentage >= 0.7F -> R.drawable.ic_baseline_battery_6_bar_24
                batteryPercentage >= 0.4F -> R.drawable.ic_baseline_battery_4_bar_24
                batteryPercentage >= 0.25F -> R.drawable.ic_baseline_battery_3_bar_24
                else -> R.drawable.ic_baseline_battery_2_bar_24
            }
            update { state ->
                state.copy(
                    batteryText = formatBatteryText(batteryPercentage),
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
                    gpsStatusText = formatGpsStatus(isConnected = true)
                )
            }
        }

        droneViewModel.droneGroundSpeedMetersPerSecond.observe(lifecycleOwner) { speed ->
            if (droneViewModel.conStateLiveData.value != true) return@observe
            update { state ->
                state.copy(speedText = formatSpeedText(speed))
            }
        }

        droneViewModel.gpsFixType.observe(lifecycleOwner) {
            update { state ->
                state.copy(gpsStatusText = formatGpsStatus(isConnected = state.isConnected))
            }
        }

        droneViewModel.liquidLevel.observe(lifecycleOwner) { liquidLevel ->
            update { state ->
                state.copy(sprayerText = formatSprayerText(liquidLevel))
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

        droneViewModel.rtkForwardingState.observe(lifecycleOwner) {
            update { state ->
                state.copy(gpsStatusText = formatGpsStatus(isConnected = state.isConnected))
            }
        }
    }

    private fun renderCurrent() {
        val isConnected = droneViewModel.conStateLiveData.value == true
        val armed = droneViewModel.armedState.value == true
        val batteryPercentage = droneViewModel.droneBatteryPercentage.value
        val batteryIconRes = when {
            batteryPercentage == null || batteryPercentage < 0.0F -> R.drawable.ic_baseline_battery_alert_24
            batteryPercentage >= 1.0F -> R.drawable.ic_baseline_battery_full_24
            batteryPercentage >= 0.7F -> R.drawable.ic_baseline_battery_6_bar_24
            batteryPercentage >= 0.4F -> R.drawable.ic_baseline_battery_4_bar_24
            batteryPercentage >= 0.25F -> R.drawable.ic_baseline_battery_3_bar_24
            else -> R.drawable.ic_baseline_battery_2_bar_24
        }
        val batteryText = formatBatteryText(batteryPercentage)
        val uploadProgress = droneViewModel.uploadProgressPercent.value ?: 0
        val batteryColorRes = resolveBatteryColorRes(batteryPercentage)
        val currentLocation = droneViewModel.droneLocationLiveData.value

        homeTelemetryViewModel.homeTelemetryUiState.value = HomeTelemetryUiState(
            isConnected = isConnected,
            connectionText = activity.getString(
                if (isConnected) R.string.shell_status_connected else R.string.shell_status_disconnected
            ),
            gpsStatusText = formatGpsStatus(isConnected = isConnected),
            batteryText = batteryText,
            batteryIconRes = batteryIconRes,
            batteryColorRes = batteryColorRes,
            altitudeText = currentLocation?.altitude?.toInt()?.let { "${it}m" } ?: "--",
            speedText = formatSpeedText(droneViewModel.droneGroundSpeedMetersPerSecond.value),
            sprayerText = formatSprayerText(droneViewModel.liquidLevel.value),
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
            gpsStatusText = "No GPS",
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

    private fun formatGpsStatus(isConnected: Boolean): String {
        if (!isConnected) return "No GPS"
        return TelemetryMapping.gpsFixLabel(droneViewModel.gpsFixType.value)
    }

    private fun formatBatteryText(batteryPercentage: Float?): String {
        return TelemetryMapping.displayPercentFromFraction(batteryPercentage)?.let { "$it%" } ?: "--%"
    }

    private fun formatSprayerText(sprayerPercentage: Float?): String {
        return TelemetryMapping.displayPercentFromRaw(sprayerPercentage)?.let { "$it%" } ?: "--%"
    }

    private fun formatSpeedText(speedMetersPerSecond: Float?): String {
        val speed = speedMetersPerSecond
            ?.takeIf { TelemetryMapping.isValidGroundSpeedMetersPerSecond(it) }
            ?: 0f
        return "SPD: ${String.format(Locale.US, "%.1f", speed)}"
    }
}
