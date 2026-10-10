package com.example.droneservicesapp.ui.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.droneservicesapp.ui.compose.theme.DroneServicesSizes

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            AppButton(
                text = confirmLabel,
                onClick = onConfirm,
                style = if (destructive) AppButtonStyle.DANGER else AppButtonStyle.PRIMARY,
            )
        },
        dismissButton = { AppButton(dismissLabel, onDismiss, style = AppButtonStyle.TEXT) },
    )
}

data class FileSelectionItem(
    val id: String,
    val name: String,
    val detail: String? = null,
)

@Composable
fun FileSelectionDialog(
    title: String,
    items: List<FileSelectionItem>,
    selectedId: String?,
    onSelected: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No files available",
    confirmLabel: String = "Open",
    dismissLabel: String = "Cancel",
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (items.isEmpty()) {
                Text(emptyMessage)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(items, key = FileSelectionItem::id) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = DroneServicesSizes.minimumTouchTarget)
                                .clickable { onSelected(item.id) }
                                .semantics { role = Role.RadioButton }
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedId == item.id, onClick = { onSelected(item.id) })
                            Column {
                                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                                item.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppButton(confirmLabel, onConfirm, enabled = selectedId != null && items.isNotEmpty())
        },
        dismissButton = { AppButton(dismissLabel, onDismiss, style = AppButtonStyle.TEXT) },
    )
}
