package com.example.droneservicesapp.ui.compose.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class DroneServicesSpacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val extraLarge: Dp = 24.dp,
)

internal val LocalDroneServicesSpacing = staticCompositionLocalOf { DroneServicesSpacing() }

object DroneServicesSizes {
    val minimumTouchTarget = 48.dp
    val compactButtonHeight = 40.dp
    val toolbarHeight = 56.dp
    val drawerWidth = 304.dp
    val panelMinWidth = 320.dp
    val panelMaxWidth = 420.dp
}
