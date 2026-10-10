package com.example.droneservicesapp.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.droneservicesapp.ui.compose.preview.DroneServicesDevicePreviews
import com.example.droneservicesapp.ui.compose.theme.DroneServicesTheme

@Preview(name = "Core controls", widthDp = 420, heightDp = 620, showBackground = true)
@Composable
private fun CoreControlsPreview() {
    DroneServicesTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton("Primary", {})
                AppButton("Secondary", {}, style = AppButtonStyle.SECONDARY)
                AppButton("Delete", {}, style = AppButtonStyle.DANGER)
            }
            AppTextField("Mission Alpha", {}, "Mission name")
            NumericInputField("50", {}, "Altitude", suffix = "m")
            ExpandableSection("Terrain settings", true, {}) {
                MissionParameterRow("Terrain segment", "2.5 m")
            }
            ProgressPanel("Preparing terrain", progress = 0.42f)
            ErrorPanel("Upload failed", "Aircraft rejected the mission", retryLabel = "Retry", onRetry = {})
        }
    }
}

@Preview(name = "Safety controls", widthDp = 420, heightDp = 620, showBackground = true)
@Composable
private fun SafetyControlsPreview() {
    DroneServicesTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SlideToArmControl(0.35f, {}, {})
            FlightModeSelector(
                currentModeId = "loiter",
                pendingModeId = null,
                options = listOf(
                    FlightModeOption("loiter", "Loiter"),
                    FlightModeOption("auto", "Auto", requiresConfirmation = true),
                    FlightModeOption("rtl", "Return to launch", warning = true, requiresConfirmation = true),
                    FlightModeOption("land", "Land", destructive = true, requiresConfirmation = true),
                ),
                onModeSelected = {},
            )
        }
    }
}

@DroneServicesDevicePreviews
@Composable
private fun MissionPanelsPreview() {
    DroneServicesTheme {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MissionSummaryCard(
                title = "Survey mission",
                status = "Ready",
                statusTone = StatusTone.SUCCESS,
                metrics = listOf("Distance" to "1.2 km", "Altitude" to "50 m", "Time" to "12 min"),
                modifier = Modifier.weight(1f),
            )
            WeatherWindPanel(
                windSpeed = "4.2 m/s",
                windDirection = "NW",
                temperature = "21°C",
                safeToFly = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
