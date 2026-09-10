package com.example.droneservicesapp.ui.home.model

import androidx.annotation.DrawableRes
import androidx.annotation.ColorRes
import com.example.droneservicesapp.R
import com.example.droneservicesapp.mavserver.GpsFixQuality
import com.example.droneservicesapp.mavserver.FlightModeCommandState

data class HomeTelemetryUiState(
    val isConnected: Boolean = false,
    val connectionText: String = "",
    val gpsStatusText: String = "No GPS",
    val gpsFixQuality: GpsFixQuality = GpsFixQuality.DISCONNECTED,
    val rtkMountpointText: String = "RTK Mountpoint: Not connected",
    val batteryText: String = "--.-V --%",
    @DrawableRes val batteryIconRes: Int = R.drawable.ic_baseline_battery_full_24,
    @ColorRes val batteryColorRes: Int = R.color.ds_color_shell_unselected,
    val altitudeText: String = "--",
    val speedText: String = "SPD: 0.0 m/s",
    val sprayerText: String = "--.-L",
    val armedText: String = "",
    val isArmed: Boolean = false,
    val flightModeText: String = "NO LINK",
    val flightModeCustomMode: Int? = null,
    val flightModeCommandState: FlightModeCommandState = FlightModeCommandState.Idle,
    val isFlightModeControlEnabled: Boolean = false,
    val uploadProgressText: String = "Uploading 0%",
    val showUploadProgress: Boolean = false,
    val frontDistanceMeters: Int? = null,
    val backDistanceMeters: Int? = null,
)
