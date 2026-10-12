package com.example.droneservicesapp.ui.compose.screens.missionmap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.droneservicesapp.R
import com.example.droneservicesapp.ui.home.MissionDialog
import com.example.droneservicesapp.ui.home.MissionDialogQueue
import com.example.droneservicesapp.ui.home.MissionFilesState
import com.example.droneservicesapp.ui.home.MissionMapController

const val MISSION_SAVE_CARD_TAG = "mission-map-save-card"
const val MISSION_SAVE_NAME_FIELD_TAG = "mission-map-save-name"
const val MISSION_SAVE_CONFIRM_TAG = "mission-map-save-confirm"
const val MISSION_LOAD_CARD_TAG = "mission-map-load-card"
const val MISSION_LOAD_CONFIRM_TAG = "mission-map-load-confirm"
const val MISSION_DIALOG_TAG = "mission-map-dialog"

/** Save-mission card: file name (with the fixed `.dsfp` suffix), overwrite checkbox, Cancel/Save. */
@Composable
internal fun SaveMissionCard(state: MissionFilesState, controller: MissionMapController, modifier: Modifier = Modifier) {
    MapOverlayPanel(
        modifier = modifier
            .widthIn(min = 300.dp, max = 460.dp)
            .testTag(MISSION_SAVE_CARD_TAG),
        shape = MapChrome.overlayCardShape,
        contentPadding = 16.dp,
    ) {
        Text(stringResource(R.string.save), color = MapChrome.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.name), color = MapChrome.textSecondary, fontSize = 14.sp)
            OutlinedTextField(
                value = state.saveFileName,
                onValueChange = controller::setSaveFileName,
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag(MISSION_SAVE_NAME_FIELD_TAG),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { controller.confirmSave() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MapChrome.textPrimary,
                    unfocusedTextColor = MapChrome.textPrimary,
                    focusedBorderColor = MapChrome.active,
                    unfocusedBorderColor = MapChrome.overlayStroke,
                    cursorColor = MapChrome.active,
                ),
            )
            Text(stringResource(R.string.DroneServicesFilePageSuffix), color = MapChrome.textSecondary, fontSize = 14.sp)
        }
        Row(
            modifier = Modifier
                .padding(top = 6.dp)
                .clickable(role = Role.Checkbox) { controller.setSaveOverwrite(!state.saveOverwrite) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = state.saveOverwrite,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = MapChrome.active, uncheckedColor = MapChrome.unselected),
            )
            Text(
                stringResource(R.string.override_existing_file),
                color = MapChrome.textPrimary,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            MapPanelButton(stringResource(R.string.cancel), controller::cancelFiles, Modifier.width(120.dp))
            MapPanelButton(
                stringResource(R.string.save),
                controller::confirmSave,
                Modifier
                    .width(120.dp)
                    .testTag(MISSION_SAVE_CONFIRM_TAG),
                highlighted = true,
                contentColor = MapChrome.active,
            )
        }
    }
}

/** Load-mission card: a single-choice list of saved `.dsfp` files with Cancel/OK. */
@Composable
internal fun LoadMissionCard(state: MissionFilesState, controller: MissionMapController, modifier: Modifier = Modifier) {
    MapOverlayPanel(
        modifier = modifier
            .widthIn(min = 300.dp, max = 460.dp)
            .testTag(MISSION_LOAD_CARD_TAG),
        shape = MapChrome.overlayCardShape,
        contentPadding = 16.dp,
    ) {
        Text(stringResource(R.string.load), color = MapChrome.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .padding(top = 10.dp)
                .border(1.dp, MapChrome.overlayStroke, MapChrome.panelShape),
        ) {
            itemsIndexed(state.loadFiles, key = { _, file -> file.file.absolutePath }) { index, file ->
                val selected = index == state.loadSelectedIndex
                Text(
                    file.displayName,
                    color = if (selected) MapChrome.active else MapChrome.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) MapChrome.pillActiveSurface else MapChrome.overlay)
                        .clickable(role = Role.RadioButton) { controller.selectLoadFile(index) }
                        .semantics { this.selected = selected }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            MapPanelButton(stringResource(R.string.cancel), controller::cancelFiles, Modifier.width(120.dp))
            MapPanelButton(
                stringResource(R.string.ok),
                controller::confirmLoad,
                Modifier
                    .width(120.dp)
                    .testTag(MISSION_LOAD_CONFIRM_TAG),
                enabled = state.loadSelectedIndex in state.loadFiles.indices,
                highlighted = true,
                contentColor = MapChrome.active,
            )
        }
    }
}

/** Renders the top-most pending [MissionDialog]. */
@Composable
internal fun MissionDialogHost(queue: MissionDialogQueue) {
    val dialog = queue.current ?: return
    when (dialog) {
        is MissionDialog.Message -> AlertDialog(
            modifier = Modifier.testTag(MISSION_DIALOG_TAG),
            onDismissRequest = { queue.dismiss(dialog) },
            title = { Text(dialog.title) },
            text = { Text(dialog.message) },
            confirmButton = {
                TextButton(onClick = { queue.dismiss(dialog) }) { Text(stringResource(R.string.ok)) }
            },
        )

        is MissionDialog.Confirmation -> AlertDialog(
            modifier = Modifier.testTag(MISSION_DIALOG_TAG),
            onDismissRequest = {
                queue.dismiss(dialog)
                dialog.onDismiss()
            },
            title = { Text(dialog.title) },
            text = { Text(dialog.message) },
            confirmButton = {
                TextButton(onClick = {
                    queue.dismiss(dialog)
                    dialog.onConfirm()
                }) { Text(dialog.confirmText) }
            },
            dismissButton = {
                TextButton(onClick = {
                    queue.dismiss(dialog)
                    dialog.onDismiss()
                }) { Text(dialog.dismissText) }
            },
        )

        is MissionDialog.ZoneList -> AlertDialog(
            modifier = Modifier.testTag(MISSION_DIALOG_TAG),
            onDismissRequest = { queue.dismiss(dialog) },
            title = { Text(dialog.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(dialog.message)
                    Text(dialog.coordinates, fontSize = 12.sp)
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        itemsIndexed(dialog.rows) { index, row ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        queue.dismiss(dialog)
                                        dialog.onSelected(index)
                                    }
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(row.name, fontWeight = FontWeight.SemiBold)
                                Text(row.restriction, fontSize = 13.sp)
                                Text(row.altitude, fontSize = 13.sp)
                                row.message?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { queue.dismiss(dialog) }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}
