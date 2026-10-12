package com.example.droneservicesapp.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A dialog request raised by mission-map logic and rendered by Compose. */
sealed interface MissionDialog {
    data class Message(
        val title: String,
        val message: String,
    ) : MissionDialog

    data class Confirmation(
        val title: String,
        val message: String,
        val confirmText: String,
        val dismissText: String,
        val onConfirm: () -> Unit,
        val onDismiss: () -> Unit = {},
    ) : MissionDialog

    /** Several overlapping geo-zones at one map location; selecting a row opens its details. */
    data class ZoneList(
        val title: String,
        val message: String,
        val coordinates: String,
        val rows: List<Row>,
        val onSelected: (Int) -> Unit,
    ) : MissionDialog {
        data class Row(
            val name: String,
            val restriction: String,
            val altitude: String,
            val message: String?,
        )
    }
}

/**
 * Stack of pending dialogs. Like stacked Android dialogs, the most recent request is shown on
 * top; dismissing it reveals the previous one.
 */
class MissionDialogQueue {
    var dialogs by mutableStateOf<List<MissionDialog>>(emptyList())
        private set

    val current: MissionDialog?
        get() = dialogs.lastOrNull()

    fun show(dialog: MissionDialog) {
        dialogs = dialogs + dialog
    }

    fun dismiss(dialog: MissionDialog) {
        val index = dialogs.indexOfLast { it === dialog }
        if (index >= 0) dialogs = dialogs.filterIndexed { i, _ -> i != index }
    }

    fun clear() {
        dialogs = emptyList()
    }
}
