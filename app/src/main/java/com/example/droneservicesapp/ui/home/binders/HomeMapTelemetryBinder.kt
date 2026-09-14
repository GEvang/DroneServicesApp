package com.example.droneservicesapp.ui.home.binders

import android.view.View
import androidx.core.content.ContextCompat
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.droneservicesapp.R
import com.example.droneservicesapp.mavserver.GpsFixQuality
import com.example.droneservicesapp.ui.home.model.HomeTelemetryUiState
import com.example.droneservicesapp.mavserver.FlightModeCommandState
import com.example.droneservicesapp.mavserver.ArmCommandState

class HomeMapTelemetryBinder(
    private val rootView: View,
) {
    fun render(state: HomeTelemetryUiState) {
        renderTopStatusStrip(state)
    }

    private fun renderTopStatusStrip(state: HomeTelemetryUiState) {
        val context = rootView.context
        val connectionColor = ContextCompat.getColor(
            context,
            if (state.isConnected) R.color.ds_color_shell_active else R.color.ds_color_shell_danger
        )
        rootView.findViewById<TextView?>(R.id.top_connection_text)?.apply {
            text = state.connectionText.uppercase()
            setTextColor(connectionColor)
        }
        rootView.findViewById<ImageView?>(R.id.top_connection_icon)?.setColorFilter(connectionColor)
        val gpsColor = ContextCompat.getColor(context, gpsStatusColor(state.gpsFixQuality))
        rootView.findViewById<TextView?>(R.id.top_gps_text)?.apply {
            text = state.gpsStatusText
            setTextColor(gpsColor)
        }
        rootView.findViewById<TextView?>(R.id.top_gps_detail_text)?.apply {
            text = state.gpsDetailText
            setTextColor(gpsColor)
        }
        rootView.findViewById<ImageView?>(R.id.top_gps_icon)?.setColorFilter(gpsColor)
        rootView.findViewById<View?>(R.id.top_gps_card)?.setOnClickListener {
            showDetailsDialog(R.string.telemetry_gps_details_title, state.gpsDialogText)
        }
        rootView.findViewById<TextView?>(R.id.top_rtk_text)?.text = compactRtkText(state.rtkMountpointText)
        rootView.findViewById<View?>(R.id.top_rtk_card)?.setOnClickListener {
            showDetailsDialog(R.string.telemetry_rtk_details_title, state.rtkDialogText)
        }
        val armedColor = ContextCompat.getColor(
            context,
            if (state.isArmed) R.color.ds_color_shell_active else R.color.ds_color_shell_warning
        )
        rootView.findViewById<ImageView?>(R.id.top_armed_icon)?.setColorFilter(armedColor)
        rootView.findViewById<View?>(R.id.top_armed_card)?.apply {
            alpha = if (state.isConnected) 1f else 0.88f
            contentDescription = when {
                state.armCommandState is ArmCommandState.Pending -> context.getString(R.string.arming)
                state.isArmed -> context.getString(R.string.armed)
                state.isConnected -> context.getString(R.string.arm_card_description)
                else -> context.getString(R.string.arm_unavailable)
            }
        }
        rootView.findViewById<TextView?>(R.id.top_armed_text)?.apply {
            text = if (state.armCommandState is ArmCommandState.Pending) {
                context.getString(R.string.arming)
            } else state.armedText.uppercase()
            setTextColor(armedColor)
        }
        val modeColor = ContextCompat.getColor(
            context,
            if (state.isFlightModeControlEnabled) R.color.ds_color_shell_active
            else R.color.ds_color_shell_unselected
        )
        rootView.findViewById<View?>(R.id.top_flight_mode_card)?.apply {
            isEnabled = state.isFlightModeControlEnabled
            alpha = if (state.isFlightModeControlEnabled) 1f else 0.88f
            contentDescription = if (state.isFlightModeControlEnabled) {
                context.getString(R.string.flight_mode_card_description, state.flightModeText)
            } else {
                context.getString(R.string.flight_mode_unavailable)
            }
        }
        rootView.findViewById<ImageView?>(R.id.top_flight_mode_icon)?.setColorFilter(modeColor)
        rootView.findViewById<ImageView?>(R.id.top_flight_mode_chevron)?.apply {
            visibility = if (state.isFlightModeControlEnabled) View.VISIBLE else View.GONE
            setColorFilter(modeColor)
        }
        rootView.findViewById<TextView?>(R.id.top_flight_mode_value)?.apply {
            text = state.flightModeText
            setTextColor(modeColor)
        }
        rootView.findViewById<View?>(R.id.top_flight_mode_progress)?.visibility =
            if (state.flightModeCommandState is FlightModeCommandState.Pending) View.VISIBLE else View.GONE
        rootView.findViewById<TextView?>(R.id.top_speed_text)?.text =
            state.speedText.removePrefix("SPD:").trim().ifBlank { "-- m/s" }
        rootView.findViewById<TextView?>(R.id.top_altitude_text)?.text =
            state.altitudeText
                .removePrefix("ALT:")
                .trim()
                .replace(Regex("(-?\\d+(?:\\.\\d+)?)m$"), "$1 m")
                .let { value -> if (value == "--") "-- m" else value }
        rootView.findViewById<ImageView?>(R.id.top_battery_icon)?.apply {
            setImageResource(state.batteryIconRes)
            setColorFilter(ContextCompat.getColor(context, state.batteryColorRes))
        }
        rootView.findViewById<TextView?>(R.id.top_battery_text)?.apply {
            text = state.batteryText
            setTextColor(ContextCompat.getColor(context, state.batteryColorRes))
        }
    }

    private fun compactRtkText(text: String): String {
        val value = text.trim()
        return if (value.isBlank()) "RTK" else "RTK\n$value"
    }

    private fun showDetailsDialog(titleRes: Int, message: String) {
        val dialog = AlertDialog.Builder(rootView.context, R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(titleRes)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            ContextCompat.getColor(rootView.context, R.color.ds_color_text_primary)
        )
    }

    private fun gpsStatusColor(quality: GpsFixQuality): Int {
        return when (quality) {
            GpsFixQuality.DISCONNECTED,
            GpsFixQuality.NO_GPS -> R.color.ds_color_shell_danger
            GpsFixQuality.RTK_FLOAT,
            GpsFixQuality.RTK_FIXED -> R.color.ds_color_shell_active
            GpsFixQuality.FIX_2D -> R.color.gps_2d_orange
            GpsFixQuality.UNKNOWN -> R.color.gps_unknown_gray
            else -> R.color.ds_color_shell_warning
        }
    }

}
