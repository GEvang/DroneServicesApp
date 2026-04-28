package com.example.droneservicesapp.ui.home.model

import androidx.annotation.DrawableRes
import androidx.annotation.ColorRes
import com.example.droneservicesapp.R

data class HomeTelemetryUiState(
    val isConnected: Boolean = false,
    val connectionText: String = "",
    val gpsStatusText: String = "NO GPS",
    val batteryText: String = "--%",
    @DrawableRes val batteryIconRes: Int = R.drawable.ic_baseline_battery_full_24,
    @ColorRes val batteryColorRes: Int = R.color.ds_color_shell_unselected,
    val altitudeText: String = "--",
    val speedText: String = "SPD: 0.0",
    val sprayerText: String = "--%",
    val armedText: String = "",
    val isArmed: Boolean = false,
    val uploadProgressText: String = "Uploading 0%",
    val showUploadProgress: Boolean = false,
    val frontDistanceMeters: Int? = null,
    val backDistanceMeters: Int? = null,
)
