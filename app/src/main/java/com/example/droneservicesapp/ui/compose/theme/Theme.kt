package com.example.droneservicesapp.ui.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val DroneServicesColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentDim,
    secondary = Success,
    background = Background,
    onBackground = TextPrimary,
    surface = SurfacePrimary,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceSecondary,
    onSurfaceVariant = TextSecondary,
    surfaceContainerHigh = SurfaceElevated,
    outline = Stroke,
    error = Danger,
    onError = Background,
    errorContainer = Danger,
    onErrorContainer = Background,
)

@Composable
fun DroneServicesTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDroneServicesSpacing provides DroneServicesSpacing()) {
        MaterialTheme(
            colorScheme = DroneServicesColorScheme,
            typography = DroneServicesTypography,
            shapes = DroneServicesShapes,
            content = content,
        )
    }
}

object DroneServicesThemeTokens {
    val spacing: DroneServicesSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalDroneServicesSpacing.current

    val warning = Warning
}
