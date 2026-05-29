package com.example.droneservicesapp.ui.shell.binders

import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.R
import com.example.droneservicesapp.databinding.ActivityMainBinding
import com.example.droneservicesapp.mavserver.GpsFixQuality
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
        val connectedColor = ContextCompat.getColor(
            activity,
            if (state.isConnected) R.color.ds_color_shell_active else R.color.ds_color_shell_danger
        )
        val armedColor = ContextCompat.getColor(
            activity,
            if (state.isArmed) R.color.ds_color_shell_active else R.color.ds_color_shell_warning
        )
        val tankColor = ContextCompat.getColor(activity, resolveTankColorRes(state.sprayerText))
        val batteryColor = ContextCompat.getColor(activity, state.batteryColorRes)
        val gpsColor = ContextCompat.getColor(activity, resolveGpsColorRes(state.gpsFixQuality))
        val secondaryText = ContextCompat.getColor(activity, R.color.text_secondary)

        toolbar.findViewById<TextView>(R.id.drone_connection_text).apply {
            text = state.connectionText.uppercase()
            setTextColor(connectedColor)
        }
        toolbar.findViewById<ImageView>(R.id.ivConnected).setColorFilter(connectedColor)

        toolbar.findViewById<TextView>(R.id.gps_status_text).apply {
            text = "GPS: ${state.gpsStatusText}"
            setTextColor(gpsColor)
        }
        toolbar.findViewById<ImageView>(R.id.ivGps).setColorFilter(gpsColor)

        toolbar.findViewById<TextView>(R.id.sprayer_flow_text).apply {
            text = state.sprayerText
            setTextColor(tankColor)
        }
        toolbar.findViewById<ImageView>(R.id.ivTank).setColorFilter(tankColor)

        toolbar.findViewById<TextView>(R.id.drone_arm_text).apply {
            text = state.armedText.uppercase()
            setTextColor(armedColor)
        }

        toolbar.findViewById<TextView>(R.id.speed_status_text).text = state.speedText
        toolbar.findViewById<TextView>(R.id.drone_alt_txt).text = formatAltitudeText(state.altitudeText)
        toolbar.findViewById<TextView>(R.id.drone_battery_percentage_text).text = state.batteryText
        toolbar.findViewById<ImageView>(R.id.drone_battery_image).setImageResource(state.batteryIconRes)
        toolbar.findViewById<TextView>(R.id.drone_battery_percentage_text).setTextColor(batteryColor)
        toolbar.findViewById<ImageView>(R.id.drone_battery_image).setColorFilter(batteryColor)

        toolbar.findViewById<TextView?>(R.id.rtk_mountpoint_text)?.apply {
            text = state.rtkMountpointText
            setTextColor(secondaryText)
        }

        val uploadTxt = toolbar.findViewById<TextView>(R.id.mission_upload_progress_text)
        uploadTxt.text = state.uploadProgressText
        uploadTxt.setTextColor(armedColor)
        uploadTxt.visibility = if (state.showUploadProgress) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun formatAltitudeText(altitudeText: String): String {
        return if (altitudeText.startsWith("ALT:")) altitudeText else "ALT: $altitudeText"
    }

    private fun resolveTankColorRes(sprayerText: String): Int {
        val percent = sprayerText.filter { it.isDigit() }.toIntOrNull() ?: return android.R.color.white
        return when {
            percent <= 20 -> R.color.ds_color_shell_danger
            percent <= 45 -> R.color.ds_color_shell_warning
            else -> android.R.color.white
        }
    }

    private fun resolveGpsColorRes(quality: GpsFixQuality): Int {
        return when (quality) {
            GpsFixQuality.DISCONNECTED,
            GpsFixQuality.NO_GPS -> R.color.gps_no_fix_red
            GpsFixQuality.FIX_2D -> R.color.gps_2d_orange
            GpsFixQuality.FIX_3D -> R.color.gps_3d_neutral
            GpsFixQuality.DGPS -> R.color.gps_dgps_blue
            GpsFixQuality.RTK_FLOAT -> R.color.gps_rtk_float_amber
            GpsFixQuality.RTK_FIXED -> R.color.gps_rtk_fixed_green
            GpsFixQuality.UNKNOWN -> R.color.gps_unknown_gray
        }
    }
}
