package com.example.droneservicesapp.ui.compose.screens.geoawareness

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthState
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetSourceType
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetStalenessPolicy
import com.example.droneservicesapp.domain.geoawareness.testing.GeoAwarenessTestStatus
import com.example.droneservicesapp.domain.geoawareness.verification.GeoAwarenessVerificationCase
import com.example.droneservicesapp.domain.geoawareness.verification.GeoAwarenessVerificationStatus
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.compose.components.AppButton
import com.example.droneservicesapp.ui.compose.components.AppButtonStyle
import com.example.droneservicesapp.ui.compose.screens.common.MoreDivider
import com.example.droneservicesapp.ui.compose.screens.common.MorePanel
import com.example.droneservicesapp.ui.compose.screens.common.MoreScreen
import com.example.droneservicesapp.ui.compose.screens.common.MoreSettingRow
import com.example.droneservicesapp.ui.compose.screens.common.TwoColumnLayout
import com.example.droneservicesapp.ui.geoawareness.GeoAwarenessAction
import com.example.droneservicesapp.ui.geoawareness.GeoAwarenessDialog
import com.example.droneservicesapp.ui.geoawareness.GeoAwarenessEffect
import com.example.droneservicesapp.ui.geoawareness.GeoAwarenessEventLogPresentationMapper
import com.example.droneservicesapp.ui.geoawareness.GeoAwarenessEventTone
import com.example.droneservicesapp.ui.geoawareness.GeoAwarenessStatusPresentationMapper
import com.example.droneservicesapp.ui.geoawareness.GeoAwarenessStatusTone
import com.example.droneservicesapp.ui.geoawareness.GeoAwarenessUiState
import com.example.droneservicesapp.ui.geoawareness.GeoAwarenessViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

const val GEO_AWARENESS_SCREEN_TAG = "compose-geo-awareness-screen"
const val GEO_AWARENESS_VERIFICATION_BUTTON_TAG = "geo-awareness-verification-button"
const val GEO_AWARENESS_VERIFICATION_DIALOG_TAG = "geo-awareness-verification-dialog"

@Composable
fun GeoAwarenessScreen(
    activityViewModel: MainActivityViewModel,
    droneViewModel: DroneViewModel,
    screenViewModel: GeoAwarenessViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by screenViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        screenViewModel.handlePickerResult(it)
    }

    LaunchedEffect(screenViewModel, activityViewModel, droneViewModel) {
        screenViewModel.initialize(activityViewModel, droneViewModel)
    }
    LaunchedEffect(screenViewModel) {
        screenViewModel.effects.collect { effect ->
            when (effect) {
                is GeoAwarenessEffect.LaunchFilePicker -> picker.launch(effect.mimeTypes)
                is GeoAwarenessEffect.LaunchIntent -> runCatching { context.startActivity(effect.intent) }
                    .exceptionOrNull()
                    ?.let { snackbarHostState.showSnackbar(it.message ?: "Unable to open share target") }
                is GeoAwarenessEffect.Message -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Box(Modifier.fillMaxSize().testTag(GEO_AWARENESS_SCREEN_TAG)) {
        MoreScreen(
            title = stringResource(R.string.geo_awareness_title),
            subtitle = stringResource(R.string.geo_awareness_subtitle),
            iconRes = R.drawable.ic_status_warning_24,
        ) {
            TwoColumnLayout(
                left = {
                    SystemStatusPanel(state, screenViewModel)
                    DatasetSourcePanel(state, screenViewModel)
                    InternalToolsPanel(state, screenViewModel)
                },
                right = {
                    LoadedDatasetsPanel(state, screenViewModel)
                    EventLogPanel(state, screenViewModel)
                },
            )
        }
        if (state.busy) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.70f),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(
                        state.busyMessage ?: stringResource(R.string.geo_loading_dataset),
                        Modifier.padding(top = 14.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp),
        )
    }
    GeoAwarenessDialogHost(state, screenViewModel)
}

@Composable
private fun SystemStatusPanel(state: GeoAwarenessUiState, viewModel: GeoAwarenessViewModel) {
    val health = state.health
    val healthTone = health?.let(GeoAwarenessStatusPresentationMapper::health)?.tone ?: GeoAwarenessStatusTone.ERROR
    val healthLabel = when (health?.state) {
        GeoAwarenessHealthState.AVAILABLE -> stringResource(R.string.geo_awareness_health_available)
        GeoAwarenessHealthState.DEGRADED -> stringResource(R.string.geo_awareness_health_degraded)
        GeoAwarenessHealthState.STALE -> stringResource(R.string.geo_awareness_health_stale)
        null, GeoAwarenessHealthState.UNAVAILABLE -> stringResource(R.string.geo_awareness_health_unavailable)
    }
    MorePanel(title = stringResource(R.string.more_system_status)) {
        StatusPill(
            text = "${stringResource(R.string.geo_awareness_health_label)} $healthLabel",
            tone = healthTone,
        )
        Text(
            health?.message ?: stringResource(R.string.geo_awareness_no_dataset_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        state.errorMessage?.takeIf(String::isNotBlank)?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        MoreSettingRow(
            title = stringResource(R.string.geo_awareness_live_status),
            value = viewModel.liveStatusLabel(),
            iconRes = R.drawable.ic_status_gps_pin_24,
            onClick = { viewModel.dispatch(GeoAwarenessAction.ShowLiveStatus) },
        )
        MoreDivider()
        MoreSettingRow(
            title = stringResource(R.string.geo_awareness_overlay_section),
            value = "",
            iconRes = R.drawable.ic_layers_24,
            supportingText = stringResource(R.string.geo_awareness_show_zones),
            trailing = {
                Switch(
                    checked = state.overlayVisible,
                    onCheckedChange = { viewModel.dispatch(GeoAwarenessAction.SetOverlayVisibility(it)) },
                )
            },
        )
    }
}

@Composable
private fun DatasetSourcePanel(state: GeoAwarenessUiState, viewModel: GeoAwarenessViewModel) {
    val source = if (state.importedDatasetActive) {
        stringResource(R.string.geo_awareness_current_source_imported)
    } else {
        stringResource(R.string.geo_awareness_current_source_none)
    }
    val validation = state.validation
    MorePanel(title = stringResource(R.string.geo_awareness_dataset_source_section)) {
        MoreSettingRow(
            title = stringResource(R.string.geo_awareness_current_source_label),
            value = source,
            iconRes = R.drawable.ic_database_24,
        )
        MoreDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryValue(
                stringResource(R.string.geo_awareness_loaded_datasets_label),
                state.datasetRecords.size.toString(),
                Modifier.weight(1f),
            )
            SummaryValue(
                stringResource(R.string.geo_awareness_total_zones_label),
                (state.datasetInfo?.zoneCount ?: state.datasetRecords.sumOf { it.zoneCount }).toString(),
                Modifier.weight(1f),
            )
            SummaryValue(
                stringResource(R.string.geo_awareness_total_validation_label),
                "Errors ${validation.errorCount} | Warnings ${validation.warningCount}",
                Modifier.weight(1.3f),
            )
        }
        AppButton(
            stringResource(R.string.geo_awareness_import_dataset),
            { viewModel.dispatch(GeoAwarenessAction.ImportDataset) },
            Modifier.fillMaxWidth(),
            enabled = !state.busy,
            style = AppButtonStyle.SECONDARY,
        )
        AppButton(
            stringResource(R.string.geo_awareness_reset_dataset),
            { viewModel.dispatch(GeoAwarenessAction.RemoveAllDatasets) },
            Modifier.fillMaxWidth(),
            enabled = !state.busy && state.datasetRecords.any { it.sourceType == GeoZoneDatasetSourceType.IMPORTED_FILE },
            style = AppButtonStyle.DANGER,
        )
        AppButton(
            stringResource(R.string.geo_awareness_refresh_status),
            { viewModel.dispatch(GeoAwarenessAction.RefreshStatus) },
            Modifier.fillMaxWidth(),
            enabled = !state.busy,
            style = AppButtonStyle.SECONDARY,
        )
    }
}

@Composable
private fun InternalToolsPanel(state: GeoAwarenessUiState, viewModel: GeoAwarenessViewModel) {
    MorePanel(title = stringResource(R.string.more_internal_tools)) {
        MoreSettingRow(
            title = stringResource(R.string.geo_awareness_open_verification_checklist),
            value = "",
            modifier = Modifier.testTag(GEO_AWARENESS_VERIFICATION_BUTTON_TAG),
            iconRes = R.drawable.ic_shield_lock_24,
            supportingText = stringResource(R.string.geo_awareness_internal_logs_notice),
            onClick = { viewModel.dispatch(GeoAwarenessAction.ShowVerification) },
        )
        AppButton(
            stringResource(R.string.geo_awareness_view_detailed_logs),
            { viewModel.dispatch(GeoAwarenessAction.ShowDetailedLogs) },
            Modifier.fillMaxWidth(),
            style = AppButtonStyle.SECONDARY,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(
                stringResource(R.string.geo_awareness_export_logs),
                { viewModel.dispatch(GeoAwarenessAction.ExportLogs) },
                Modifier.weight(1f),
                enabled = !state.busy,
                style = AppButtonStyle.SECONDARY,
            )
            AppButton(
                stringResource(R.string.geo_awareness_export_evidence_package),
                { viewModel.dispatch(GeoAwarenessAction.ExportEvidence) },
                Modifier.weight(1f),
                enabled = !state.busy,
                style = AppButtonStyle.SECONDARY,
            )
        }
        AppButton(
            stringResource(R.string.geo_awareness_export_encrypted_incidents),
            { viewModel.dispatch(GeoAwarenessAction.ExportEncryptedIncidents) },
            Modifier.fillMaxWidth(),
            enabled = !state.busy,
            style = AppButtonStyle.SECONDARY,
        )
    }
}

@Composable
private fun LoadedDatasetsPanel(state: GeoAwarenessUiState, viewModel: GeoAwarenessViewModel) {
    MorePanel(title = stringResource(R.string.geo_awareness_loaded_datasets_section)) {
        if (state.datasetRecords.isEmpty()) {
            Text(
                stringResource(R.string.geo_awareness_dataset_list_empty),
                Modifier.padding(vertical = 48.dp).align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.datasetRecords.forEach { record -> DatasetCard(record, state.busy, viewModel) }
        }
    }
}

@Composable
private fun EventLogPanel(state: GeoAwarenessUiState, viewModel: GeoAwarenessViewModel) {
    val rows = GeoAwarenessEventLogPresentationMapper.rows(state.events)
    MorePanel(title = stringResource(R.string.geo_awareness_flight_logs_section)) {
        if (rows.isEmpty()) {
            Text(
                stringResource(R.string.geo_awareness_flight_log_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        } else {
            rows.take(5).forEachIndexed { index, row ->
                if (index > 0) MoreDivider()
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        row.title,
                        color = when (row.tone) {
                            GeoAwarenessEventTone.NORMAL -> MaterialTheme.colorScheme.onSurface
                            GeoAwarenessEventTone.WARNING -> toneColor(GeoAwarenessStatusTone.WARNING)
                            GeoAwarenessEventTone.ERROR -> toneColor(GeoAwarenessStatusTone.ERROR)
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                    Text(row.message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    row.metadata?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                }
            }
        }
        AppButton(
            stringResource(R.string.geo_awareness_view_detailed_logs),
            { viewModel.dispatch(GeoAwarenessAction.ShowDetailedLogs) },
            Modifier.fillMaxWidth(),
            style = AppButtonStyle.SECONDARY,
        )
    }
}

@Composable
private fun DatasetCard(record: GeoZoneDatasetRecord, busy: Boolean, viewModel: GeoAwarenessViewModel) {
    val validationLabel = when {
        record.validationResult.hasErrors -> stringResource(R.string.geo_awareness_validation_errors)
        record.validationResult.hasWarnings -> stringResource(R.string.geo_awareness_validation_warnings)
        else -> stringResource(R.string.geo_awareness_validation_ok)
    }
    val validationTone = GeoAwarenessStatusPresentationMapper.validation(record.validationResult).tone
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        color = Color(0x99111B20),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    record.displayName,
                    Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusPill(validationLabel, validationTone)
            }
            Text(
                stringResource(
                    R.string.geo_source,
                    stringResource(
                        if (record.sourceType == GeoZoneDatasetSourceType.BUNDLED_ASSET) {
                            R.string.geo_awareness_dataset_source_bundled_row
                        } else {
                            R.string.geo_awareness_dataset_source_imported_row
                        }
                    ),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MetaRow("Version", record.datasetInfo.version ?: "N/A", "Zones", record.zoneCount.toString())
            MetaRow(
                "Country", record.datasetInfo.country ?: "N/A",
                "Type", if (record.datasetInfo.isDummy) "Test / dummy" else "Validated JSON",
            )
            MetaRow(
                "Errors", record.validationResult.errorCount.toString(),
                "Warnings", record.validationResult.warningCount.toString(),
            )
            AppButton(
                stringResource(R.string.geo_awareness_view_validation_details),
                { viewModel.dispatch(GeoAwarenessAction.ShowDatasetValidation(record)) },
                style = AppButtonStyle.SECONDARY,
            )
            if (record.sourceType == GeoZoneDatasetSourceType.IMPORTED_FILE) {
                Text(
                    "${stringResource(R.string.geo_awareness_updated_label)} ${record.ageDescription ?: GeoZoneDatasetStalenessPolicy.ageDescription(record.updatedAtMillis)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Text(
                    "${stringResource(R.string.geo_awareness_stale_label)} ${stringResource(if (record.isStale) R.string.geo_awareness_yes else R.string.geo_awareness_no)}",
                    color = if (record.isStale) toneColor(GeoAwarenessStatusTone.WARNING) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            if (record.sourceType == GeoZoneDatasetSourceType.IMPORTED_FILE && record.storageFileName != null) {
                Text(stringResource(R.string.geo_actions), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        stringResource(R.string.geo_awareness_update_dataset),
                        { viewModel.dispatch(GeoAwarenessAction.UpdateDataset(record)) },
                        Modifier.weight(1f),
                        enabled = !busy,
                        style = AppButtonStyle.SECONDARY,
                    )
                    AppButton(
                        stringResource(R.string.geo_awareness_remove_dataset),
                        { viewModel.dispatch(GeoAwarenessAction.RemoveDataset(record)) },
                        Modifier.weight(1f),
                        enabled = !busy,
                        style = AppButtonStyle.DANGER,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label1: String, value1: String, label2: String, value2: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetaCell(label1, value1, Modifier.weight(1f))
        MetaCell(label2, value2, Modifier.weight(1f))
    }
}

@Composable
private fun MetaCell(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = Color(0x991B2430), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2)
    }
}

@Composable
private fun StatusPill(text: String, tone: GeoAwarenessStatusTone) {
    Surface(color = toneColor(tone), shape = RoundedCornerShape(18.dp)) {
        Text(
            text,
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun toneColor(tone: GeoAwarenessStatusTone): Color = when (tone) {
    GeoAwarenessStatusTone.SUCCESS -> Color(0xFF2E7D32)
    GeoAwarenessStatusTone.WARNING -> Color(0xFFE65100)
    GeoAwarenessStatusTone.ERROR -> Color(0xFFB71C1C)
}

@Composable
private fun GeoAwarenessDialogHost(state: GeoAwarenessUiState, viewModel: GeoAwarenessViewModel) {
    when (val dialog = state.dialog) {
        is GeoAwarenessDialog.Message -> AlertDialog(
            onDismissRequest = { viewModel.dispatch(GeoAwarenessAction.DismissDialog) },
            title = { Text(dialog.title) },
            text = { Text(dialog.message) },
            confirmButton = {
                AppButton(stringResource(android.R.string.ok), { viewModel.dispatch(GeoAwarenessAction.DismissDialog) })
            },
        )
        GeoAwarenessDialog.ConfirmRemoveAll -> ConfirmationDialog(
            title = stringResource(R.string.geo_remove_all_title),
            message = stringResource(R.string.geo_remove_all_message),
            confirm = stringResource(R.string.geo_remove_all),
            viewModel = viewModel,
        )
        is GeoAwarenessDialog.ConfirmRemove -> ConfirmationDialog(
            title = stringResource(R.string.geo_remove_one_title),
            message = stringResource(R.string.geo_remove_one_message, dialog.record.displayName),
            confirm = stringResource(R.string.geo_awareness_remove_dataset),
            viewModel = viewModel,
        )
        GeoAwarenessDialog.ConfirmVerificationReset -> ConfirmationDialog(
            title = stringResource(R.string.geo_awareness_verification_reset),
            message = stringResource(R.string.geo_reset_checklist_question),
            confirm = stringResource(R.string.geo_reset),
            viewModel = viewModel,
        )
        GeoAwarenessDialog.Verification -> VerificationDialog(state, viewModel)
        is GeoAwarenessDialog.VerificationCaseDetails -> VerificationCaseDialog(dialog.case, viewModel)
        null -> Unit
    }
}

@Composable
private fun ConfirmationDialog(title: String, message: String, confirm: String, viewModel: GeoAwarenessViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.dispatch(GeoAwarenessAction.DismissDialog) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { AppButton(confirm, viewModel::confirmDialog, style = AppButtonStyle.DANGER) },
        dismissButton = {
            AppButton(
                stringResource(R.string.cancel),
                { viewModel.dispatch(GeoAwarenessAction.DismissDialog) },
                style = AppButtonStyle.TEXT,
            )
        },
    )
}

@Composable
private fun VerificationDialog(state: GeoAwarenessUiState, viewModel: GeoAwarenessViewModel) {
    val verification = state.verification
    Dialog(onDismissRequest = { viewModel.dispatch(GeoAwarenessAction.DismissDialog) }) {
        Surface(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 1000.dp)
                .heightIn(max = 900.dp)
                .testTag(GEO_AWARENESS_VERIFICATION_DIALOG_TAG),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.geo_awareness_verification_checklist), style = MaterialTheme.typography.headlineSmall)
                val cases = verification?.cases.orEmpty()
                val statuses = cases.map { it.status }
                Text(
                    "${stringResource(R.string.geo_awareness_verification_total)} ${cases.size}  " +
                        "${stringResource(R.string.geo_awareness_verification_passed)} ${statuses.count { it == GeoAwarenessVerificationStatus.PASS }}  " +
                        "${stringResource(R.string.geo_awareness_verification_failed)} ${statuses.count { it == GeoAwarenessVerificationStatus.FAIL }}  " +
                        "${stringResource(R.string.geo_awareness_verification_blocked)} ${statuses.count { it == GeoAwarenessVerificationStatus.BLOCKED }}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                verification?.testRun?.let { run ->
                    StatusPill("${stringResource(R.string.geo_awareness_tests_overall)} ${run.overallStatus}", testTone(run.overallStatus))
                    Text(
                        "${stringResource(R.string.geo_awareness_tests_passed)} ${run.passCount}  " +
                            "${stringResource(R.string.geo_awareness_tests_warnings)} ${run.warningCount}  " +
                            "${stringResource(R.string.geo_awareness_tests_failed)} ${run.failCount}  " +
                            "${stringResource(R.string.geo_awareness_tests_skipped)} ${run.skippedCount}",
                    )
                } ?: Text(stringResource(R.string.geo_awareness_tests_idle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        if (verification?.testRunning == true) stringResource(R.string.geo_awareness_tests_running) else stringResource(R.string.geo_awareness_run_tests),
                        { viewModel.dispatch(GeoAwarenessAction.RunVerificationTests) },
                        Modifier.weight(1f),
                        enabled = verification?.testRunning != true,
                    )
                    AppButton(
                        stringResource(R.string.geo_awareness_verification_reset),
                        { viewModel.dispatch(GeoAwarenessAction.ResetVerification) },
                        Modifier.weight(1f),
                        style = AppButtonStyle.SECONDARY,
                    )
                }
                cases.forEach { item ->
                    Surface(
                        Modifier.fillMaxWidth(),
                        color = Color(0x99111B20),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.definition.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                StatusPill(statusLabel(item.status), verificationTone(item.status))
                            }
                            Text(item.definition.purpose, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                GeoAwarenessVerificationStatus.entries.forEach { status ->
                                    AppButton(
                                        statusLabel(status),
                                        { viewModel.updateVerification(item.definition, status) },
                                        Modifier.weight(1f),
                                        style = if (status == item.status) AppButtonStyle.PRIMARY else AppButtonStyle.SECONDARY,
                                    )
                                }
                            }
                            AppButton(
                                stringResource(R.string.geo_awareness_verification_details),
                                { viewModel.showVerificationCase(item.definition) },
                                style = AppButtonStyle.TEXT,
                            )
                        }
                    }
                }
                AppButton(
                    stringResource(android.R.string.ok),
                    { viewModel.dispatch(GeoAwarenessAction.DismissDialog) },
                    Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun VerificationCaseDialog(case: GeoAwarenessVerificationCase, viewModel: GeoAwarenessViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::returnToVerification,
        title = { Text(case.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DetailSection(stringResource(R.string.geo_verification_purpose), case.purpose)
                DetailSection(stringResource(R.string.geo_verification_preconditions), case.preconditions.joinToString("\n") { "• $it" })
                DetailSection(stringResource(R.string.geo_verification_steps), case.steps.mapIndexed { index, step -> "${index + 1}. $step" }.joinToString("\n"))
                DetailSection(stringResource(R.string.geo_verification_expected), case.expectedResult)
                DetailSection(stringResource(R.string.geo_verification_evidence), case.evidenceToCapture.joinToString("\n") { "• $it" })
            }
        },
        confirmButton = { AppButton(stringResource(android.R.string.ok), viewModel::returnToVerification) },
    )
}

@Composable
private fun DetailSection(title: String, body: String) {
    Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
    Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun statusLabel(status: GeoAwarenessVerificationStatus): String = stringResource(
    when (status) {
        GeoAwarenessVerificationStatus.NOT_RUN -> R.string.geo_awareness_verification_status_not_run
        GeoAwarenessVerificationStatus.PASS -> R.string.geo_awareness_verification_status_pass
        GeoAwarenessVerificationStatus.FAIL -> R.string.geo_awareness_verification_status_fail
        GeoAwarenessVerificationStatus.BLOCKED -> R.string.geo_awareness_verification_status_blocked
    }
)

private fun verificationTone(status: GeoAwarenessVerificationStatus) = when (status) {
    GeoAwarenessVerificationStatus.PASS -> GeoAwarenessStatusTone.SUCCESS
    GeoAwarenessVerificationStatus.FAIL -> GeoAwarenessStatusTone.ERROR
    GeoAwarenessVerificationStatus.NOT_RUN,
    GeoAwarenessVerificationStatus.BLOCKED -> GeoAwarenessStatusTone.WARNING
}

private fun testTone(status: GeoAwarenessTestStatus) = when (status) {
    GeoAwarenessTestStatus.PASS -> GeoAwarenessStatusTone.SUCCESS
    GeoAwarenessTestStatus.FAIL -> GeoAwarenessStatusTone.ERROR
    GeoAwarenessTestStatus.WARNING,
    GeoAwarenessTestStatus.SKIPPED -> GeoAwarenessStatusTone.WARNING
}
