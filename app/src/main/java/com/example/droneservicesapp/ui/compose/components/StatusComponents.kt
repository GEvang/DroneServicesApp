package com.example.droneservicesapp.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.droneservicesapp.ui.compose.theme.DroneServicesSizes
import com.example.droneservicesapp.ui.compose.theme.DroneServicesThemeTokens

enum class StatusTone { NEUTRAL, INFO, SUCCESS, WARNING, ERROR }

@Composable
private fun statusColor(tone: StatusTone): Color = when (tone) {
    StatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    StatusTone.INFO -> MaterialTheme.colorScheme.primary
    StatusTone.SUCCESS -> MaterialTheme.colorScheme.secondary
    StatusTone.WARNING -> DroneServicesThemeTokens.warning
    StatusTone.ERROR -> MaterialTheme.colorScheme.error
}

@Composable
fun StatusIndicator(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val color = statusColor(tone)
    Row(
        modifier = modifier.semantics {
            contentDescription = listOfNotNull(label, detail).joinToString(", ")
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Column {
            Text(label, color = color, style = MaterialTheme.typography.labelLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun ConnectionStateBadge(
    connected: Boolean,
    modifier: Modifier = Modifier,
    connectedLabel: String = "Connected",
    disconnectedLabel: String = "Disconnected",
) {
    val tone = if (connected) StatusTone.SUCCESS else StatusTone.ERROR
    val color = statusColor(tone)
    Surface(
        modifier = modifier.semantics {
            contentDescription = if (connected) connectedLabel else disconnectedLabel
        },
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color),
    ) {
        Text(
            text = if (connected) connectedLabel else disconnectedLabel,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun TelemetryValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.NEUTRAL,
) {
    Column(
        modifier = modifier.semantics { contentDescription = "$label, $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = statusColor(tone),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ToolbarAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    AppButton(
        text = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = if (selected) AppButtonStyle.PRIMARY else AppButtonStyle.TEXT,
        contentDescription = label,
    )
}

@Composable
fun NavigationItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    supportingText: String? = null,
) {
    val background = if (selected) Color(0x2929E6DA) else Color.Transparent
    val contentColor = if (selected) Color(0xFF29E6DA) else Color(0xFFC9D0D6)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DroneServicesSizes.minimumTouchTarget),
        color = background,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            iconRes?.let {
                Image(painterResource(it), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(contentColor))
            }
            Column {
                Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge)
                supportingText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
fun ProgressPanel(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    progress: Float? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = listOfNotNull(title, message).joinToString(", ")
            },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(DroneServicesThemeTokens.spacing.large),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (progress == null) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            progress?.let { LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
fun ErrorPanel(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Row(
            modifier = Modifier.padding(DroneServicesThemeTokens.spacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            if (retryLabel != null && onRetry != null) {
                AppButton(retryLabel, onRetry, style = AppButtonStyle.SECONDARY)
            }
        }
    }
}
