package com.example.droneservicesapp.ui.compose.screens.datasets

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.pointcloud.PointCloudDetailLevel
import com.example.droneservicesapp.data.preview.PreviewDatasetRecord
import com.example.droneservicesapp.ui.compose.components.AppButton
import com.example.droneservicesapp.ui.compose.components.AppButtonStyle
import com.example.droneservicesapp.ui.compose.screens.common.MoreCircleIcon
import com.example.droneservicesapp.ui.compose.screens.common.MoreDivider
import com.example.droneservicesapp.ui.compose.screens.common.MorePanel
import com.example.droneservicesapp.ui.compose.screens.common.MoreScreen
import com.example.droneservicesapp.ui.compose.screens.common.MoreSettingRow
import com.example.droneservicesapp.ui.compose.screens.common.TwoColumnLayout
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel

const val DATASETS_SCREEN_TAG = "compose-datasets-screen"

@Composable
fun DatasetsScreen(
    previewAssetsViewModel: PreviewAssetsViewModel,
    screenViewModel: DatasetsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by screenViewModel.state.collectAsStateWithLifecycle()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { screenViewModel.attachOrthoImage(it, previewAssetsViewModel) }
    }
    val worldPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { screenViewModel.attachWorldFile(it, previewAssetsViewModel) }
    }
    val cloudPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { screenViewModel.attachPointCloud(it, previewAssetsViewModel) }
    }

    LaunchedEffect(screenViewModel) {
        screenViewModel.effects.collect { effect ->
            when (effect) {
                is DatasetEffect.Message -> Toast.makeText(context, effect.value, Toast.LENGTH_SHORT).show()
            }
        }
    }

    MoreScreen(
        title = stringResource(R.string.datasets_title),
        subtitle = stringResource(R.string.datasets_subtitle),
        iconRes = R.drawable.ic_folder_24,
        modifier = Modifier.testTag(DATASETS_SCREEN_TAG),
    ) {
        TwoColumnLayout(
            left = {
                DatasetManagerPanel(state, screenViewModel, previewAssetsViewModel)
                state.activeDataset?.let { record ->
                    DatasetAssetsPanel(
                        record = record,
                        state = state,
                        onLoadImage = { imagePicker.launch(arrayOf("image/tiff", "image/*", "application/octet-stream")) },
                        onLoadWorld = { worldPicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) },
                        onLoadCloud = { cloudPicker.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) },
                        onCancel = screenViewModel::cancelLoad,
                    )
                }
            },
            right = {
                state.activeDataset?.let { record ->
                    DisplayOptionsPanel(record, screenViewModel, previewAssetsViewModel)
                }
            },
        )
    }

    DatasetDialogHost(state, screenViewModel, previewAssetsViewModel)
}

@Composable
private fun DatasetManagerPanel(
    state: DatasetsUiState,
    viewModel: DatasetsViewModel,
    preview: PreviewAssetsViewModel,
) {
    val active = state.activeDataset
    MorePanel(
        title = stringResource(R.string.more_dataset_manager),
        subtitle = stringResource(R.string.more_dataset_manager_subtitle),
    ) {
        Text(
            active?.let { stringResource(R.string.dataset_active_name, it.name) }
                ?: stringResource(R.string.dataset_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (active == null) {
            Text(stringResource(R.string.dataset_empty_state), style = MaterialTheme.typography.bodySmall)
        } else {
            DatasetSelector(state, enabled = !state.loading) { viewModel.selectDataset(it, preview) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DatasetActionButton(
                stringResource(R.string.dataset_create), R.drawable.ic_add_24,
                { viewModel.createDataset(preview) }, Modifier.weight(1f), !state.loading,
            )
            if (active != null) {
                DatasetActionButton(
                    stringResource(R.string.dataset_load), R.drawable.ic_folder_24,
                    { viewModel.loadActiveDataset(preview) }, Modifier.weight(1f), !state.loading,
                )
                DatasetActionButton(
                    stringResource(R.string.dataset_unload), R.drawable.ic_upload_24,
                    { viewModel.unload(preview) }, Modifier.weight(1f), !state.loading,
                )
            }
        }
        if (active != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DatasetActionButton(
                    stringResource(R.string.dataset_rename), R.drawable.ic_menu_tune,
                    viewModel::showRename, Modifier.weight(1f), !state.loading,
                )
                DatasetActionButton(
                    stringResource(R.string.dataset_delete), R.drawable.ic_delete_24,
                    viewModel::showDeleteActive, Modifier.weight(1f), !state.loading, danger = true,
                )
                DatasetActionButton(
                    stringResource(R.string.dataset_delete_all), R.drawable.ic_delete_24,
                    viewModel::showDeleteAll, Modifier.weight(1f), !state.loading, danger = true,
                )
            }
        }
    }
}

@Composable
private fun DatasetSelector(state: DatasetsUiState, enabled: Boolean, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().height(56.dp).clickable(enabled) { expanded = true },
        color = Color(0x99111B20),
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, Color(0x8AC9D0D6)),
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.activeDataset?.name.orEmpty(), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("▾")
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.datasets.forEach { record ->
                    DropdownMenuItem(
                        text = { Text(record.name) },
                        onClick = { expanded = false; onSelected(record.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DatasetAssetsPanel(
    record: PreviewDatasetRecord,
    state: DatasetsUiState,
    onLoadImage: () -> Unit,
    onLoadWorld: () -> Unit,
    onLoadCloud: () -> Unit,
    onCancel: () -> Unit,
) {
    MorePanel(
        title = stringResource(R.string.dataset_assets),
        subtitle = stringResource(R.string.more_dataset_assets_subtitle),
    ) {
        AssetRow(
            R.drawable.ic_ortho_24,
            stringResource(R.string.dataset_ortho_image_status, record.orthoImageName ?: stringResource(R.string.dataset_none)),
            stringResource(R.string.ortho_load_image),
            !state.loading,
            onLoadImage,
        )
        AssetRow(
            R.drawable.ic_folder_24,
            stringResource(R.string.dataset_ortho_world_status, record.orthoWorldName ?: stringResource(R.string.dataset_none)),
            stringResource(R.string.ortho_load_world),
            !state.loading,
            onLoadWorld,
        )
        AssetRow(
            R.drawable.ic_point_cloud_24,
            stringResource(R.string.dataset_point_cloud_status, record.pointCloudName ?: stringResource(R.string.dataset_none)),
            stringResource(R.string.point_cloud_load),
            !state.loading,
            onLoadCloud,
        )
        if (state.loading) {
            Text(state.loadingMessage, style = MaterialTheme.typography.bodySmall)
            if (state.loadingPercent == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { state.loadingPercent.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AppButton(
                stringResource(android.R.string.cancel), onCancel,
                Modifier.fillMaxWidth(), style = AppButtonStyle.SECONDARY,
            )
        }
    }
}

@Composable
private fun AssetRow(
    @DrawableRes icon: Int,
    status: String,
    button: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(70.dp),
        color = Color(0x99111B20),
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, Color(0x8AC9D0D6)),
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(icon), null, Modifier.size(28.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
            Text(
                status,
                Modifier.padding(start = 12.dp).weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            AppButton(button, onClick, Modifier.width(150.dp), enabled, AppButtonStyle.SECONDARY)
        }
    }
}

@Composable
private fun DisplayOptionsPanel(
    record: PreviewDatasetRecord,
    viewModel: DatasetsViewModel,
    preview: PreviewAssetsViewModel,
) {
    var detailExpanded by remember { mutableStateOf(false) }
    MorePanel(
        title = stringResource(R.string.dataset_display_options),
        subtitle = stringResource(R.string.more_display_options_subtitle),
    ) {
        MoreSettingRow(
            title = stringResource(R.string.ortho_background),
            value = "",
            iconRes = R.drawable.ic_ortho_24,
            supportingText = stringResource(R.string.more_background_subtitle),
            trailing = {
                Switch(record.orthoBackgroundEnabled, { viewModel.updateOrthoBackground(it, preview) })
            },
        )
        MoreDivider()
        SliderOption(
            R.drawable.ic_opacity_24,
            stringResource(R.string.dataset_ortho_opacity_value, (record.orthoOpacity * 100).toInt()),
            record.orthoOpacity,
            0f..1f,
            19,
        ) { viewModel.updateOrthoOpacity(it, preview) }
        MoreDivider()
        SliderOption(
            R.drawable.ic_menu_tune,
            stringResource(R.string.dataset_point_size_value, record.pointCloudPointSize),
            record.pointCloudPointSize,
            1f..10f,
            17,
        ) { viewModel.updatePointSize(it, preview) }
        MoreDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.ic_layers_24), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
            Text(stringResource(R.string.point_cloud_detail), fontWeight = FontWeight.SemiBold)
        }
        Surface(
            modifier = Modifier.fillMaxWidth().height(56.dp).clickable { detailExpanded = true },
            color = Color(0x99111B20),
            shape = RoundedCornerShape(9.dp),
            border = BorderStroke(1.dp, Color(0x8AC9D0D6)),
        ) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(pointCloudDetailLabel(PointCloudDetailLevel.fromStored(record.pointCloudDetailLevel)), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text("▾")
                DropdownMenu(detailExpanded, { detailExpanded = false }) {
                    datasetDetailLevels.forEach { detail ->
                        DropdownMenuItem(
                            text = { Text(pointCloudDetailLabel(detail)) },
                            onClick = { detailExpanded = false; viewModel.updateDetail(detail, preview) },
                        )
                    }
                }
            }
        }
        MoreDivider()
        MoreSettingRow(
            title = stringResource(R.string.preview_height_color_mode),
            value = "",
            iconRes = R.drawable.ic_layers_24,
            supportingText = stringResource(R.string.more_height_colors_subtitle),
            trailing = {
                Switch(record.heightColorModeEnabled, { viewModel.updateHeightColors(it, preview) })
            },
        )
    }
}

@Composable
private fun SliderOption(
    @DrawableRes icon: Int,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(icon), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
    Slider(value, onChange, valueRange = range, steps = steps)
}

@Composable
private fun DatasetActionButton(
    text: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    danger: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
    ) {
        val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        Image(painterResource(icon), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(color))
        Text(text, Modifier.padding(start = 8.dp), color = color, maxLines = 1)
    }
}

@Composable
private fun DatasetDialogHost(
    state: DatasetsUiState,
    viewModel: DatasetsViewModel,
    preview: PreviewAssetsViewModel,
) {
    when (state.dialog) {
        DatasetDialog.Rename -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.dataset_rename_title)) },
            text = {
                OutlinedTextField(
                    value = state.renameValue,
                    onValueChange = viewModel::updateRename,
                    label = { Text(stringResource(R.string.dataset_name_hint)) },
                    isError = state.renameError,
                    supportingText = if (state.renameError) ({ Text(stringResource(R.string.dataset_name_required)) }) else null,
                    singleLine = true,
                )
            },
            confirmButton = { AppButton(stringResource(R.string.dataset_rename), viewModel::confirmRename) },
            dismissButton = { AppButton(stringResource(android.R.string.cancel), viewModel::dismissDialog, style = AppButtonStyle.TEXT) },
        )
        DatasetDialog.DeleteActive -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.dataset_delete)) },
            text = { Text(stringResource(R.string.dataset_delete_confirmation, state.activeDataset?.name.orEmpty())) },
            confirmButton = { AppButton(stringResource(R.string.dataset_delete), { viewModel.deleteActive(preview) }, style = AppButtonStyle.DANGER) },
            dismissButton = { AppButton(stringResource(android.R.string.cancel), viewModel::dismissDialog, style = AppButtonStyle.TEXT) },
        )
        DatasetDialog.DeleteAll -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.dataset_delete_all)) },
            text = { Text(stringResource(R.string.dataset_delete_all_confirmation)) },
            confirmButton = { AppButton(stringResource(R.string.dataset_delete_all), { viewModel.deleteAll(preview) }, style = AppButtonStyle.DANGER) },
            dismissButton = { AppButton(stringResource(android.R.string.cancel), viewModel::dismissDialog, style = AppButtonStyle.TEXT) },
        )
        null -> Unit
    }
}

@Composable
private fun pointCloudDetailLabel(detail: PointCloudDetailLevel): String = stringResource(
    when (detail) {
        PointCloudDetailLevel.FAST -> R.string.point_cloud_detail_fast
        PointCloudDetailLevel.BALANCED -> R.string.point_cloud_detail_balanced
        PointCloudDetailLevel.HIGH -> R.string.point_cloud_detail_high
    }
)
