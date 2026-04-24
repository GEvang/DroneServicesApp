package com.example.droneservicesapp.ui.home.binders

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.R
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
                    batteryIconRes = iconRes
                )
            }
        }

        droneViewModel.droneLocationLiveData.observe(lifecycleOwner) { location ->
            if (droneViewModel.conStateLiveData.value != true) return@observe
            update { state ->
                state.copy(altitudeText = "${location.altitude.toInt()}m")
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

        homeTelemetryViewModel.homeTelemetryUiState.value = HomeTelemetryUiState(
            isConnected = isConnected,
            connectionText = activity.getString(
                if (isConnected) R.string.shell_status_connected else R.string.shell_status_disconnected
            ),
            batteryText = batteryText,
            batteryIconRes = batteryIconRes,
            altitudeText = droneViewModel.droneLocationLiveData.value?.altitude?.toInt()?.let { "${it}m" } ?: "--",
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
            batteryText = "--%",
            batteryIconRes = R.drawable.ic_baseline_battery_alert_24,
            altitudeText = "--",
            sprayerText = "--%",
            armedText = activity.getString(R.string.disarmed),
            isArmed = false,
            showUploadProgress = false,
            frontDistanceMeters = null,
            backDistanceMeters = null
        )
    }
}
