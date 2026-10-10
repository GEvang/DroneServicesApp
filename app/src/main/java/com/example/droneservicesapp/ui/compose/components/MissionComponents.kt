package com.example.droneservicesapp.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.droneservicesapp.ui.compose.theme.DroneServicesSizes
import com.example.droneservicesapp.ui.compose.theme.DroneServicesThemeTokens

@Composable
fun MissionParameterRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    control: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DroneServicesSizes.minimumTouchTarget)
            .semantics { contentDescription = "$label, $value" }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            supportingText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        control?.invoke()
    }
}

@Composable
fun MissionSummaryCard(
    title: String,
    metrics: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    status: String? = null,
    statusTone: StatusTone = StatusTone.NEUTRAL,
    primaryAction: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(DroneServicesThemeTokens.spacing.large),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            status?.let { StatusIndicator(it, statusTone) }
            metrics.forEach { (label, value) -> MissionParameterRow(label, value) }
            if (primaryAction != null && onPrimaryAction != null) {
                AppButton(primaryAction, onPrimaryAction, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

enum class ObstacleShapeOption { CIRCLE, POLYGON }

@Composable
fun ObstacleControls(
    selectedShape: ObstacleShapeOption,
    radiusMeters: Float,
    onShapeSelected: (ObstacleShapeOption) -> Unit,
    onRadiusChanged: (Float) -> Unit,
    onAddObstacle: () -> Unit,
    onClearObstacles: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(
            modifier = Modifier.padding(DroneServicesThemeTokens.spacing.large),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Obstacle editor", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ObstacleShapeOption.entries.forEach { shape ->
                    AppButton(
                        text = shape.name.lowercase().replaceFirstChar(Char::titlecase),
                        onClick = { onShapeSelected(shape) },
                        enabled = enabled,
                        style = if (shape == selectedShape) AppButtonStyle.PRIMARY else AppButtonStyle.SECONDARY,
                    )
                }
            }
            Text("Radius: ${radiusMeters.toInt()} m")
            Slider(
                value = radiusMeters,
                onValueChange = onRadiusChanged,
                valueRange = 2f..100f,
                enabled = enabled && selectedShape == ObstacleShapeOption.CIRCLE,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton("Add obstacle", onAddObstacle, enabled = enabled)
                AppButton("Clear", onClearObstacles, enabled = enabled, style = AppButtonStyle.DANGER)
            }
        }
    }
}

@Composable
fun WeatherWindPanel(
    windSpeed: String,
    windDirection: String,
    temperature: String,
    safeToFly: Boolean,
    modifier: Modifier = Modifier,
    stale: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(DroneServicesThemeTokens.spacing.large),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Weather and wind", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                StatusIndicator(
                    label = if (safeToFly) "Within limits" else "Outside limits",
                    tone = if (safeToFly) StatusTone.SUCCESS else StatusTone.WARNING,
                    detail = "Wind $windSpeed at $windDirection",
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                TelemetryValue("Wind", windSpeed)
                TelemetryValue("Direction", windDirection)
                TelemetryValue("Temperature", temperature)
            }
            if (stale) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Weather data is stale", modifier = Modifier.weight(1f), color = DroneServicesThemeTokens.warning)
                    if (onRefresh != null) AppButton("Refresh", onRefresh, style = AppButtonStyle.TEXT)
                }
            }
        }
    }
}

@Composable
fun GeoAwarenessStatusPanel(
    overlayVisible: Boolean,
    onOverlayVisibilityChanged: (Boolean) -> Unit,
    healthLabel: String,
    healthTone: StatusTone,
    datasetSummary: String,
    validationSummary: String,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    onImport: () -> Unit,
    onValidationDetails: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(DroneServicesThemeTokens.spacing.large),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Geo-awareness", style = MaterialTheme.typography.titleMedium)
                    StatusIndicator(healthLabel, healthTone)
                }
                Text("Map overlay", style = MaterialTheme.typography.labelMedium)
                Switch(
                    checked = overlayVisible,
                    onCheckedChange = onOverlayVisibilityChanged,
                    enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = "Geo-awareness map overlay" },
                )
            }
            Text(datasetSummary, style = MaterialTheme.typography.bodyMedium)
            Text(validationSummary, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton("Import dataset", onImport, enabled = !busy)
                AppButton(
                    "Validation details",
                    onValidationDetails,
                    enabled = !busy,
                    style = AppButtonStyle.SECONDARY,
                )
            }
        }
    }
}
