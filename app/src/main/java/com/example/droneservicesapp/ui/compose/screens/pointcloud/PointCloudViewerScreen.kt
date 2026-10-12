package com.example.droneservicesapp.ui.compose.screens.pointcloud

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.compose.components.AppButton
import com.example.droneservicesapp.ui.compose.components.AppButtonStyle
import com.example.droneservicesapp.ui.pointcloud.PointCloudGlView
import com.example.droneservicesapp.ui.pointcloud.PointCloudMissionOverlay
import com.example.droneservicesapp.ui.preview.MissionPreviewPathSynchronizer
import com.example.droneservicesapp.ui.preview.PointCloudMissionOverlayBuilder
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.preview.PreviewMapFocus
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import java.util.Locale

const val POINT_CLOUD_SCREEN_TAG = "point-cloud-screen"
const val POINT_CLOUD_SURFACE_TAG = "point-cloud-render-surface"

@Composable
fun PointCloudViewerScreen(
    activityViewModel: MainActivityViewModel,
    droneViewModel: DroneViewModel,
    previewAssetsViewModel: PreviewAssetsViewModel,
    onReturnToMap: () -> Unit,
    viewModel: PointCloudViewerViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by previewAssetsViewModel.previewSettings.observeAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val area by activityViewModel.missionArea.observeAsState()
    val surveyPath by activityViewModel.surveyPath.observeAsState(emptyList())
    val terrainSurvey by activityViewModel.terrainSurveyWaypoints.observeAsState(emptyList())
    val routeWaypoints by activityViewModel.routeWaypoints.observeAsState(emptyList())
    val retainedPath by previewAssetsViewModel.retainedDroneMissionPath.observeAsState(emptyList())
    val missionItems by droneViewModel.missionItems.observeAsState(emptyList())
    val droneLocation by droneViewModel.droneLocationLiveData.observeAsState()
    val droneHeading by droneViewModel.droneHeading.observeAsState(0.0)
    val workflow by activityViewModel.activePlanningWorkflow.observeAsState(PlanningWorkflow.AREA)

    LaunchedEffect(Unit) {
        val current = settings
        viewModel.initialize(
            previewAssetsViewModel.pointCloudAsset,
            current?.pointCloudPointSize ?: 2.5f,
            current?.orthoOpacity ?: .85f,
            current?.heightColorModeEnabled ?: true,
        )
    }
    LaunchedEffect(state.asset) {
        state.asset?.let { previewAssetsViewModel.setPointCloud(it.pointCloud, it.fileName, it.uri) }
    }
    LaunchedEffect(state.pointSize, state.opacity, state.heightColors) {
        previewAssetsViewModel.updateSettings {
            copy(
                pointCloudPointSize = state.pointSize,
                orthoOpacity = state.opacity,
                heightColorModeEnabled = state.heightColors,
            )
        }
    }
    LaunchedEffect(missionItems) { previewAssetsViewModel.retainDroneMission(missionItems) }
    DisposableEffect(lifecycleOwner, activityViewModel, previewAssetsViewModel) {
        val synchronizer = MissionPreviewPathSynchronizer(lifecycleOwner, activityViewModel, previewAssetsViewModel)
        synchronizer.bind()
        onDispose { synchronizer.clear() }
    }

    val showingDownloaded = retainedPath.isNotEmpty()
    val survey = if (showingDownloaded) retainedPath else surveyPath
    val areaPoints = if (showingDownloaded) emptyList() else area?.vertices.orEmpty()
    val route = if (showingDownloaded) emptyList() else routeWaypoints.map { LatLng(it.latitude, it.longitude) }
    val validDrone = droneLocation?.takeIf {
        it.latitude.isFinite() && it.longitude.isFinite() &&
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 &&
            (it.latitude != 0.0 || it.longitude != 0.0)
    }?.let { LatLng(it.latitude, it.longitude) }
    val overlay = remember(state.asset, areaPoints, survey, terrainSurvey, route, validDrone, droneHeading, workflow) {
        PointCloudMissionOverlayBuilder().build(
            PointCloudMissionOverlayBuilder.Input(
                pointCloud = state.asset?.pointCloud,
                areaPoints = areaPoints,
                areaZValues = null,
                surveyPoints = survey,
                surveyZValues = terrainSurvey
                    .takeIf { !showingDownloaded && it.size == survey.size && it.isNotEmpty() }
                    ?.map { it.displayAltitudeMeters.toFloat() },
                routePoints = route,
                routeZValues = null,
                droneLocation = validDrone,
                droneHeadingDegrees = droneHeading,
                selectedWorkflow = workflow,
                selectedWaypointIndex = null,
            )
        )
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::load) }
    var resetToken by remember { mutableStateOf(0) }

    BoxWithConstraints(Modifier.fillMaxSize().testTag(POINT_CLOUD_SCREEN_TAG)) {
        PointCloudRenderSurface(
            asset = state.asset,
            missionOverlay = overlay,
            pointSize = state.pointSize,
            opacity = state.opacity,
            heightColors = state.heightColors,
            resetToken = resetToken,
            modifier = Modifier.fillMaxSize().testTag(POINT_CLOUD_SURFACE_TAG),
        )
        PointCloudControlPanel(
            state = state,
            overlay = overlay,
            areaPoints = areaPoints,
            onLoad = { picker.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) },
            onReset = { resetToken++ },
            onPointSize = viewModel::setPointSize,
            onOpacity = viewModel::setOpacity,
            onHeightColors = viewModel::setHeightColors,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .widthIn(max = if (maxWidth < 600.dp) maxWidth - 24.dp else 360.dp),
        )
        AppButton(
            text = stringResource(R.string.preview_mode_next_map),
            onClick = {
                previewAssetsViewModel.requestMapFocus(PreviewMapFocus.POINT_CLOUD)
                onReturnToMap()
            },
            style = AppButtonStyle.SECONDARY,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        )
    }
}

@Composable
private fun PointCloudRenderSurface(
    asset: com.example.droneservicesapp.ui.preview.PointCloudPreviewAsset?,
    missionOverlay: PointCloudMissionOverlay?,
    pointSize: Float,
    opacity: Float,
    heightColors: Boolean,
    resetToken: Int,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var glView by remember { mutableStateOf<PointCloudGlView?>(null) }
    var appliedResetToken by remember { mutableStateOf(resetToken) }
    var appliedAsset by remember { mutableStateOf<com.example.droneservicesapp.ui.preview.PointCloudPreviewAsset?>(null) }
    var appliedOverlay by remember { mutableStateOf<PointCloudMissionOverlay?>(null) }
    DisposableEffect(lifecycleOwner, glView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView?.onResume()
                Lifecycle.Event.ON_PAUSE -> glView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) glView?.onResume()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    AndroidView(
        modifier = modifier,
        factory = { context -> PointCloudGlView(context).also { glView = it } },
        update = { view ->
            if (asset !== appliedAsset) {
                asset?.pointCloud?.let(view::setPointCloud) ?: view.clearPointCloud()
                appliedAsset = asset
            }
            if (missionOverlay !== appliedOverlay) {
                view.setMissionOverlay(missionOverlay)
                appliedOverlay = missionOverlay
            }
            view.setPointSize(pointSize)
            view.setPointCloudOpacity(opacity)
            view.setHeightColorModeEnabled(heightColors)
            if (resetToken != appliedResetToken) {
                view.resetCamera()
                appliedResetToken = resetToken
            }
        },
        onRelease = { view -> glView = null; view.release() },
    )
}

@Composable
private fun PointCloudControlPanel(
    state: PointCloudViewerUiState,
    overlay: PointCloudMissionOverlay?,
    areaPoints: List<LatLng>,
    onLoad: () -> Unit,
    onReset: () -> Unit,
    onPointSize: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onHeightColors: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cloud = state.asset?.pointCloud
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.point_cloud_title), style = MaterialTheme.typography.titleMedium)
            Text(
                state.error ?: state.asset?.let { stringResource(R.string.point_cloud_loaded, it.fileName) }
                ?: stringResource(R.string.point_cloud_empty_state),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                if (cloud == null) stringResource(R.string.point_cloud_stats_empty) else stringResource(
                    R.string.point_cloud_stats,
                    cloud.displayedPointCount, cloud.totalPointCount,
                    cloud.bounds.spanX, cloud.bounds.spanY, cloud.bounds.spanZ,
                    stringResource(if (cloud.hasRgb) R.string.point_cloud_color_rgb else R.string.point_cloud_color_height),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (overlay != null) {
                Text(
                    stringResource(
                        R.string.point_cloud_mission_overlay_stats,
                        overlay.lineVertexCount / 2,
                        overlay.pointVertexCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                if (areaPoints.size >= 3) stringResource(
                    R.string.point_cloud_area_status,
                    formatArea(SphericalUtil.computeArea(areaPoints)),
                ) else stringResource(R.string.point_cloud_area_empty),
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.loading) CircularProgressIndicator()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(stringResource(R.string.point_cloud_load), onLoad, Modifier.weight(1f), !state.loading)
                AppButton(stringResource(R.string.point_cloud_reset), onReset, Modifier.weight(1f), cloud != null || overlay != null, AppButtonStyle.SECONDARY)
            }
            Text("${stringResource(R.string.point_cloud_point_size)}: ${"%.1f".format(Locale.US, state.pointSize)}")
            Slider(state.pointSize, onPointSize, valueRange = 1f..8f, steps = 13)
            Text("${stringResource(R.string.ortho_opacity)}: ${(state.opacity * 100).toInt()}%")
            Slider(state.opacity, onOpacity, valueRange = .1f..1f, steps = 17)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.point_cloud_height_colors), Modifier.weight(1f))
                Switch(state.heightColors, onHeightColors)
            }
        }
    }
}

private fun formatArea(squareMeters: Double): String = if (squareMeters >= 10_000.0) {
    String.format(Locale.US, "%.2f ha", squareMeters / 10_000.0)
} else {
    "${squareMeters.toInt().coerceAtLeast(0)} m²"
}
