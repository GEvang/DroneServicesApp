package com.example.droneservicesapp.ui.shell.model

import androidx.annotation.DrawableRes
import com.example.droneservicesapp.R

data class ToolbarUiState(
    val isConnected: Boolean = false,
    val batteryText: String = "--%",
    @DrawableRes val batteryIconRes: Int = R.drawable.ic_baseline_battery_full_24,
    val altitudeText: String = "--",
    val sprayerText: String = "--%",
    val armedText: String = "",
    val uploadProgressText: String = "Uploading 0%",
    val showUploadProgress: Boolean = false,
)
