package com.example.droneservicesapp.ui.compose.components

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.droneservicesapp.ui.compose.theme.DroneServicesTheme

const val COMPONENT_GALLERY_ROOT_TAG = "component-gallery-root"
const val COMPONENT_GALLERY_PRIMARY_BUTTON_TAG = "component-gallery-primary-button"
const val COMPONENT_GALLERY_EXPANDABLE_TAG = "component-gallery-expandable"
const val COMPONENT_GALLERY_DIALOG_BUTTON_TAG = "component-gallery-dialog-button"
const val COMPONENT_GALLERY_SLIDER_TAG = "component-gallery-arm-slider"

class ComponentGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DroneServicesTheme { ComponentGallery() } }
    }
}

@Composable
private fun ComponentGallery() {
    var expanded by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf("Survey") }
    var armProgress by remember { mutableFloatStateOf(0f) }
    var showDialog by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready") }
    var overlayVisible by remember { mutableStateOf(true) }
    var obstacleShape by remember { mutableStateOf(ObstacleShapeOption.CIRCLE) }
    var radius by remember { mutableFloatStateOf(10f) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(COMPONENT_GALLERY_ROOT_TAG)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Compose component gallery", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Status: $status") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(
                    "Primary",
                    { status = "Primary clicked" },
                    modifier = Modifier.testTag(COMPONENT_GALLERY_PRIMARY_BUTTON_TAG),
                )
                AppButton("Secondary", {}, style = AppButtonStyle.SECONDARY)
                AppButton(
                    "Confirm action",
                    { showDialog = true },
                    modifier = Modifier.testTag(COMPONENT_GALLERY_DIALOG_BUTTON_TAG),
                    style = AppButtonStyle.DANGER,
                )
            }
        }
        item { ConnectionStateBadge(connected = true) }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                TelemetryValue("Altitude", "50 m")
                TelemetryValue("Speed", "5.0 m/s")
                TelemetryValue("Battery", "82%", tone = StatusTone.SUCCESS)
            }
        }
        item { AppTextField("Mission Alpha", {}, "Mission name") }
        item { NumericInputField("50", {}, "Altitude", suffix = "m") }
        item {
            AppDropdown(
                label = "Operation mode",
                selected = selectedMode,
                options = listOf("Survey", "Spray"),
                optionLabel = { it },
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                onSelected = { selectedMode = it },
            )
        }
        item {
            ExpandableSection(
                title = "Mission parameters",
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.testTag(COMPONENT_GALLERY_EXPANDABLE_TAG),
            ) {
                MissionParameterRow("Grid angle", "90°")
            }
        }
        item {
            ProgressPanel(
                title = "Preparing terrain",
                message = "Downloading elevation tiles",
                progress = 0.35f,
            )
        }
        item {
            ErrorPanel(
                title = "Example error",
                message = "A recoverable problem occurred",
                retryLabel = "Retry",
                onRetry = {},
            )
        }
        item {
            SlideToArmControl(
                progress = armProgress,
                onProgressChange = { armProgress = it },
                onProgressFinished = { confirmed ->
                    status = if (confirmed) "Arm requested" else "Arm cancelled"
                    if (!confirmed) armProgress = 0f
                },
                modifier = Modifier.testTag(COMPONENT_GALLERY_SLIDER_TAG),
            )
        }
        item {
            FlightModeSelector(
                currentModeId = "loiter",
                pendingModeId = null,
                options = listOf(
                    FlightModeOption("loiter", "Loiter"),
                    FlightModeOption("auto", "Auto", requiresConfirmation = true),
                    FlightModeOption("land", "Land", destructive = true, requiresConfirmation = true),
                ),
                onModeSelected = { status = "Selected ${it.label}" },
            )
        }
        item {
            MissionSummaryCard(
                title = "Survey mission",
                status = "Ready",
                statusTone = StatusTone.SUCCESS,
                metrics = listOf("Distance" to "1.2 km", "Estimated time" to "12 min"),
                primaryAction = "Upload mission",
                onPrimaryAction = { status = "Upload requested" },
            )
        }
        item {
            ObstacleControls(
                selectedShape = obstacleShape,
                radiusMeters = radius,
                onShapeSelected = { obstacleShape = it },
                onRadiusChanged = { radius = it },
                onAddObstacle = { status = "Obstacle placement started" },
                onClearObstacles = { status = "Obstacles cleared" },
            )
        }
        item {
            WeatherWindPanel("4.2 m/s", "NW", "21°C", safeToFly = true)
        }
        item {
            GeoAwarenessStatusPanel(
                overlayVisible = overlayVisible,
                onOverlayVisibilityChanged = { overlayVisible = it },
                healthLabel = "Available",
                healthTone = StatusTone.SUCCESS,
                datasetSummary = "2 datasets · 48 zones",
                validationSummary = "0 errors · 1 warning",
                onImport = { status = "Import requested" },
                onValidationDetails = { status = "Validation requested" },
            )
        }
    }

    if (showDialog) {
        ConfirmationDialog(
            title = "Confirm example",
            message = "This demonstrates the shared confirmation dialog.",
            confirmLabel = "Confirm",
            dismissLabel = "Cancel",
            onConfirm = {
                status = "Confirmed"
                showDialog = false
            },
            onDismiss = { showDialog = false },
            destructive = true,
        )
    }
}
