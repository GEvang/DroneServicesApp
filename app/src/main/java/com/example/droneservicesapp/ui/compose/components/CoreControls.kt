package com.example.droneservicesapp.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.droneservicesapp.ui.compose.theme.DroneServicesSizes
import com.example.droneservicesapp.ui.compose.theme.DroneServicesThemeTokens

enum class AppButtonStyle { PRIMARY, SECONDARY, DANGER, TEXT }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: AppButtonStyle = AppButtonStyle.PRIMARY,
    contentDescription: String = text,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    val resolvedModifier = modifier
        .heightIn(min = DroneServicesSizes.minimumTouchTarget)
        .semantics { this.contentDescription = contentDescription }
    val body: @Composable RowScope.() -> Unit = content ?: { Text(text) }
    when (style) {
        AppButtonStyle.PRIMARY -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = resolvedModifier,
            shape = MaterialTheme.shapes.small,
            content = body,
        )
        AppButtonStyle.SECONDARY -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = resolvedModifier,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = MaterialTheme.shapes.small,
            content = body,
        )
        AppButtonStyle.DANGER -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = resolvedModifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            shape = MaterialTheme.shapes.small,
            content = body,
        )
        AppButtonStyle.TEXT -> TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = resolvedModifier,
            shape = MaterialTheme.shapes.small,
            content = body,
        )
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        isError = errorText != null,
        supportingText = (errorText ?: supportingText)?.let { text -> ({ Text(text) }) },
    )
}

@Composable
fun NumericInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    allowDecimal: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        isError = errorText != null,
        suffix = suffix?.let { unit -> ({ Text(unit) }) },
        supportingText = errorText?.let { text -> ({ Text(text) }) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
    )
}

@Composable
fun <T> AppDropdown(
    label: String,
    selected: T?,
    options: List<T>,
    optionLabel: (T) -> String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "Select",
) {
    Box(modifier = modifier.widthIn(min = 180.dp)) {
        OutlinedButton(
            onClick = { onExpandedChange(!expanded) },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DroneServicesSizes.minimumTouchTarget),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(
                    selected?.let(optionLabel) ?: placeholder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(if (expanded) "▴" else "▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}

@Composable
fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DroneServicesSizes.minimumTouchTarget)
                    .clickable(enabled = enabled) { onExpandedChange(!expanded) }
                    .semantics {
                        role = Role.Button
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                    }
                    .padding(DroneServicesThemeTokens.spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DroneServicesThemeTokens.spacing.small),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleLarge)
            }
            if (expanded) {
                Box(modifier = Modifier.padding(DroneServicesThemeTokens.spacing.medium)) { content() }
            }
        }
    }
}
