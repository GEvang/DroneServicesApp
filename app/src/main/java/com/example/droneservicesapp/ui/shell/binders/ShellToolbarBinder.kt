package com.example.droneservicesapp.ui.shell.binders

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.R
import com.example.droneservicesapp.databinding.ActivityMainBinding
import com.example.droneservicesapp.ui.home.model.HomeTelemetryUiState
import com.example.droneservicesapp.ui.home.model.HomeTelemetryViewModel

class ShellToolbarBinder(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val homeTelemetryViewModel: HomeTelemetryViewModel,
) {
    private val toolbar by lazy { binding.appBarMain.customToolbar }

    fun bind(lifecycleOwner: LifecycleOwner) {
        activity.setSupportActionBar(toolbar)
        val initial = homeTelemetryViewModel.homeTelemetryUiState.value ?: HomeTelemetryUiState(
            connectionText = activity.getString(R.string.shell_status_disconnected),
            armedText = activity.getString(R.string.disarmed)
        )
        render(initial)
        homeTelemetryViewModel.homeTelemetryUiState.observe(lifecycleOwner, ::render)
    }

    private fun render(state: HomeTelemetryUiState) {
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
                    if (state.isConnected) R.color.ds_color_shell_active else R.color.ds_color_shell_unselected
                )
            )
        }
        toolbar.findViewById<TextView>(R.id.drone_arm_text).setTextColor(
            ContextCompat.getColor(
                activity,
                if (state.isArmed) {
                    R.color.ds_color_shell_active
                } else {
                    R.color.ds_color_shell_warning
                }
            )
        )

        val uploadTxt = toolbar.findViewById<TextView>(R.id.mission_upload_progress_text)
        uploadTxt.text = state.uploadProgressText
        uploadTxt.visibility = if (state.showUploadProgress) View.VISIBLE else View.GONE
    }
}
