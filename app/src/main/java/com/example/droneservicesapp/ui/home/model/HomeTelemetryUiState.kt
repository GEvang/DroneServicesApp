package com.example.droneservicesapp.ui.home.model

import androidx.annotation.DrawableRes
import androidx.annotation.ColorRes
import com.example.droneservicesapp.R
import com.example.droneservicesapp.mavserver.GpsFixQuality
import com.example.droneservicesapp.mavserver.FlightModeCommandState
import com.example.droneservicesapp.mavserver.ArmCommandState

data class HomeTelemetryUiState(
    val isConnected: Boolean = false,
    val connectionText: String = "",
    val gpsStatusText: String = "",
    val gpsDetailText: String = "",
    val gpsDialogText: String = "",
    val gpsFixQuality: GpsFixQuality = GpsFixQuality.DISCONNECTED,
    val rtkMountpointText: String = "",
    val rtkDialogText: String = "",
    val batteryText: String = "--.-V --%",
    @DrawableRes val batteryIconRes: Int = R.drawable.ic_baseline_battery_full_24,
    @ColorRes val batteryColorRes: Int = R.color.ds_color_shell_unselected,
    val altitudeText: String = "--",
    val speedText: String = "0.0 m/s",
    val sprayerText: String = "--.-L",
    val armedText: String = "",
    val isArmed: Boolean = false,
    val armCommandState: ArmCommandState = ArmCommandState.Idle,
    val flightModeText: String = "",
    val flightModeCustomMode: Int? = null,
    val flightModeCommandState: FlightModeCommandState = FlightModeCommandState.Idle,
    val isFlightModeControlEnabled: Boolean = false,
    val uploadProgressText: String = "",
    val showUploadProgress: Boolean = false,
    val frontDistanceMeters: Int? = null,
    val backDistanceMeters: Int? = null,
)
