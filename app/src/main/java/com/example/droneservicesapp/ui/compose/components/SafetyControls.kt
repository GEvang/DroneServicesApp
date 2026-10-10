package com.example.droneservicesapp.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.droneservicesapp.ui.compose.theme.DroneServicesThemeTokens

@Composable
fun SlideToArmControl(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    onProgressFinished: (confirmed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pending: Boolean = false,
    instruction: String = "Slide fully to arm",
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(DroneServicesThemeTokens.spacing.large),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (pending) CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
                Text(
                    if (pending) "Waiting for aircraft confirmation" else instruction,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Slider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = onProgressChange,
                onValueChangeFinished = { onProgressFinished(progress >= 0.95f) },
                enabled = enabled && !pending,
                modifier = Modifier.semantics {
                    contentDescription = instruction
                    if (!enabled || pending) disabled()
                },
            )
        }
    }
}

data class FlightModeOption(
    val id: String,
    val label: String,
    val warning: Boolean = false,
    val destructive: Boolean = false,
    val requiresConfirmation: Boolean = false,
)

@Composable
fun FlightModeSelector(
    currentModeId: String?,
    pendingModeId: String?,
    options: List<FlightModeOption>,
    onModeSelected: (FlightModeOption) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(DroneServicesThemeTokens.spacing.large),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Flight mode", style = MaterialTheme.typography.titleMedium)
            options.forEach { option ->
                val selected = currentModeId == option.id
                val pending = pendingModeId == option.id
                AppButton(
                    text = if (pending) "Applying ${option.label}…" else option.label,
                    onClick = { onModeSelected(option) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled && pendingModeId == null,
                    style = when {
                        option.destructive -> AppButtonStyle.DANGER
                        selected -> AppButtonStyle.PRIMARY
                        else -> AppButtonStyle.SECONDARY
                    },
                    contentDescription = buildString {
                        append(option.label)
                        if (selected) append(", selected")
                        if (option.requiresConfirmation) append(", confirmation required")
                    },
                )
            }
        }
    }
}
