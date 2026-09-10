package com.example.droneservicesapp.ui.home.binders

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.content.ContextCompat
import android.widget.ImageView
import android.widget.TextView
import com.example.droneservicesapp.R
import com.example.droneservicesapp.mavserver.GpsFixQuality
import com.example.droneservicesapp.ui.home.model.HomeTelemetryUiState
import com.example.droneservicesapp.mavserver.FlightModeCommandState

class HomeMapTelemetryBinder(
    private val rootView: View,
) {
    companion object {
        private const val MIN_DISTANCE_VALUE = 5
        private const val MAX_DISTANCE_VALUE = 15
    }

    fun render(state: HomeTelemetryUiState) {
        renderTopStatusStrip(state)
        renderDistance(
            distance = state.frontDistanceMeters,
            textViewId = R.id.front_dist,
            colorIndex = 0
        )
        renderDistance(
            distance = state.backDistanceMeters,
            textViewId = R.id.back_dist,
            colorIndex = 2
        )
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
        rootView.findViewById<ImageView?>(R.id.top_gps_icon)?.setColorFilter(gpsColor)
        rootView.findViewById<TextView?>(R.id.top_rtk_text)?.text = compactRtkText(state.rtkMountpointText)
        val armedColor = ContextCompat.getColor(
            context,
            if (state.isArmed) R.color.ds_color_shell_active else R.color.ds_color_shell_warning
        )
        rootView.findViewById<ImageView?>(R.id.top_armed_icon)?.setColorFilter(armedColor)
        rootView.findViewById<TextView?>(R.id.top_armed_text)?.apply {
            text = state.armedText.uppercase()
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
        val value = text.removePrefix("RTK Mountpoint:").trim()
        return if (value.isBlank()) "RTK" else "RTK\n$value"
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

    private fun renderDistance(
        distance: Int?,
        textViewId: Int,
        colorIndex: Int,
    ) {
        if (distance == null) {
            rootView.findViewById<TextView>(textViewId)?.text =
                rootView.context.getString(R.string.home_avoidance_unknown)
            resetCompassSegment(colorIndex)
            return
        }

        val color = getColor(distance)
        rootView.findViewById<TextView>(textViewId)?.text = "$distance m"

        val compassImageView = rootView.findViewById<ImageView>(R.id.avoidance_compass)
        val drawable = compassImageView?.drawable as? GradientDrawable
        drawable?.colors?.let { colors ->
            val newColors = colors.copyOf()
            newColors[colorIndex] = color
            drawable.colors = newColors
        }
    }

    private fun resetCompassSegment(colorIndex: Int) {
        val compassImageView = rootView.findViewById<ImageView>(R.id.avoidance_compass)
        val drawable = compassImageView?.drawable as? GradientDrawable
        val neutralColor = ContextCompat.getColor(rootView.context, R.color.ds_color_shell_stroke)
        drawable?.colors?.let { colors ->
            val newColors = colors.copyOf()
            newColors[colorIndex] = neutralColor
            drawable.colors = newColors
        }
    }

    private fun getColor(inValue: Int): Int {
        var value = when {
            inValue < MIN_DISTANCE_VALUE -> MIN_DISTANCE_VALUE
            inValue > MAX_DISTANCE_VALUE -> MAX_DISTANCE_VALUE
            else -> inValue
        }
        value = MAX_DISTANCE_VALUE + MIN_DISTANCE_VALUE - value

        val hue =
            ((120 * (MAX_DISTANCE_VALUE - value)) / (MAX_DISTANCE_VALUE - MIN_DISTANCE_VALUE)).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
    }
}
