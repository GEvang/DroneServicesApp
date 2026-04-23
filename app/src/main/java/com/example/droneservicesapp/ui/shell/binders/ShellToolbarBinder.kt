package com.example.droneservicesapp.ui.shell.binders

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.R
import com.example.droneservicesapp.databinding.ActivityMainBinding
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.shell.model.ToolbarUiState
import java.math.RoundingMode
import java.text.DecimalFormat

class ShellToolbarBinder(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val droneViewModel: DroneViewModel,
) {
    private val toolbar by lazy { binding.appBarMain.customToolbar }
    private var toolbarUiState = ToolbarUiState(
        armedText = activity.getString(R.string.disarmed),
        connectionText = activity.getString(R.string.shell_status_disconnected)
    )

    fun bind(lifecycleOwner: LifecycleOwner) {
        activity.setSupportActionBar(toolbar)
        render(toolbarUiState)

        droneViewModel.conStateLiveData.observe(lifecycleOwner) { connState ->
            toolbarUiState = toolbarUiState.copy(
                isConnected = connState,
                connectionText = activity.getString(
                    if (connState) R.string.shell_status_connected else R.string.shell_status_disconnected
                )
            )
            render(toolbarUiState)
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
            toolbarUiState = toolbarUiState.copy(
                batteryText = "${df.format(batteryPercentage * 100.0F)}%",
                batteryIconRes = iconRes
            )
            render(toolbarUiState)
        }

        droneViewModel.droneLocationLiveData.observe(lifecycleOwner) { location ->
            if (droneViewModel.conStateLiveData.value != true) return@observe
            toolbarUiState = toolbarUiState.copy(altitudeText = "${location.altitude.toInt()}m")
            render(toolbarUiState)
        }

        droneViewModel.liquidLevel.observe(lifecycleOwner) { liquidLevel ->
            toolbarUiState = toolbarUiState.copy(sprayerText = "$liquidLevel%")
            render(toolbarUiState)
        }

        droneViewModel.armedState.observe(lifecycleOwner) { armedState ->
            val resId = if (armedState) R.string.armed else R.string.disarmed
            toolbarUiState = toolbarUiState.copy(armedText = activity.getString(resId))
            render(toolbarUiState)
        }

        droneViewModel.uploadProgressPercent.observe(lifecycleOwner) { percent ->
            toolbarUiState = when {
                percent in 1..99 -> {
                    toolbarUiState.copy(
                        showUploadProgress = true,
                        uploadProgressText = "Uploading ${percent}%"
                    )
                }
                percent >= 100 -> {
                    toolbarUiState.copy(showUploadProgress = false)
                }
                else -> {
                    toolbarUiState.copy(showUploadProgress = false)
                }
            }
            render(toolbarUiState)
        }
    }

    private fun render(state: ToolbarUiState) {
        toolbar.findViewById<TextView>(R.id.drone_battery_percentage_text).text = state.batteryText
        toolbar.findViewById<ImageView>(R.id.drone_battery_image).setImageResource(state.batteryIconRes)
        toolbar.findViewById<TextView>(R.id.drone_alt_txt).text = state.altitudeText
        toolbar.findViewById<TextView>(R.id.sprayer_flow_text).text = state.sprayerText
        toolbar.findViewById<TextView>(R.id.drone_arm_text).text = state.armedText
        toolbar.findViewById<TextView>(R.id.drone_connection_text).apply {
            text = state.connectionText
            setTextColor(
                ContextCompat.getColor(
                    activity,
                    if (state.isConnected) R.color.ds_color_accent else R.color.ds_color_text_secondary
                )
            )
        }

        val uploadTxt = toolbar.findViewById<TextView>(R.id.mission_upload_progress_text)
        uploadTxt.text = state.uploadProgressText
        uploadTxt.visibility = if (state.showUploadProgress) View.VISIBLE else View.GONE
    }
}
