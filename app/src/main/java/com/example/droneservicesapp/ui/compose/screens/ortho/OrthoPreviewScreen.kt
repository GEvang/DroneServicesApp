package com.example.droneservicesapp.ui.compose.screens.ortho

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.livedata.observeAsState
import com.example.droneservicesapp.R
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.compose.components.AppButton
import com.example.droneservicesapp.ui.compose.components.AppButtonStyle
import com.example.droneservicesapp.ui.map.model.MapBounds
import com.example.droneservicesapp.ui.map.model.MapCamera
import com.example.droneservicesapp.ui.map.model.MapCoordinate
import com.example.droneservicesapp.ui.map.model.MapInteractionHandlers
import com.example.droneservicesapp.ui.map.model.MapMarkerModel
import com.example.droneservicesapp.ui.map.model.MapOverlayLayer
import com.example.droneservicesapp.ui.map.model.MapPolygonModel
import com.example.droneservicesapp.ui.map.model.MapPolylineModel
import com.example.droneservicesapp.ui.map.model.MapRasterModel
import com.example.droneservicesapp.ui.map.model.MapScene
import com.example.droneservicesapp.ui.map.osmdroid.OsmdroidMapSurface
import com.example.droneservicesapp.ui.preview.MissionPreviewPathSynchronizer
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

const val ORTHO_PREVIEW_SCREEN_TAG = "ortho-preview-screen"
const val ORTHO_MAP_SURFACE_TAG = "ortho-map-surface"

@Composable
fun OrthoPreviewScreen(
    activityViewModel: MainActivityViewModel,
    droneViewModel: DroneViewModel,
    previewAssetsViewModel: PreviewAssetsViewModel,
    onShowPointCloud: () -> Unit,
    viewModel: OrthoPreviewViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by previewAssetsViewModel.previewSettings.observeAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val area by activityViewModel.missionArea.observeAsState()
    val surveyPath by activityViewModel.surveyPath.observeAsState(emptyList())
    val routeWaypoints by activityViewModel.routeWaypoints.observeAsState(emptyList())
    val retainedPath by previewAssetsViewModel.retainedDroneMissionPath.observeAsState(emptyList())
    val missionItems by droneViewModel.missionItems.observeAsState(emptyList())
    val droneLocation by droneViewModel.droneLocationLiveData.observeAsState()
    val droneHeading by droneViewModel.droneHeading.observeAsState(0.0)
    val aircraftLabel = stringResource(R.string.preview_aircraft_marker)

    LaunchedEffect(Unit) {
        val current = settings
        viewModel.initialize(
            previewAssetsViewModel.orthoAsset,
            current?.orthoOpacity ?: .85f,
            current?.orthoBackgroundEnabled ?: true,
        )
    }
    LaunchedEffect(state.asset) {
        state.asset?.let { asset ->
            previewAssetsViewModel.setOrthoImage(
                asset.bitmap, asset.bitmapFileName, asset.bitmapUri,
                asset.sourceWidth, asset.sourceHeight, notifyChange = asset.bounds == null,
            )
            if (asset.bounds != null && asset.worldFileName != null && asset.worldFileUri != null) {
                previewAssetsViewModel.setOrthoBounds(asset.bounds, asset.worldFileName, asset.worldFileUri)
            }
        }
    }
    LaunchedEffect(state.opacity, state.backgroundEnabled) {
        previewAssetsViewModel.updateSettings {
            copy(orthoOpacity = state.opacity, orthoBackgroundEnabled = state.backgroundEnabled)
        }
    }
    LaunchedEffect(missionItems) { previewAssetsViewModel.retainDroneMission(missionItems) }
    DisposableEffect(lifecycleOwner, activityViewModel, previewAssetsViewModel) {
        val synchronizer = MissionPreviewPathSynchronizer(lifecycleOwner, activityViewModel, previewAssetsViewModel)
        synchronizer.bind()
        onDispose { synchronizer.clear() }
    }

    val showingDownloaded = retainedPath.isNotEmpty()
    val areaPoints = if (showingDownloaded) emptyList() else area?.vertices.orEmpty()
    val surveyPoints = if (showingDownloaded) retainedPath else surveyPath
    val routePoints = if (showingDownloaded) emptyList() else routeWaypoints.map { it.toLatLng() }
    val scene = remember(state.asset, state.opacity, areaPoints, surveyPoints, routePoints, droneLocation, droneHeading) {
        MapScene(
            rasters = state.asset?.bounds?.let { bounds ->
                listOf(MapRasterModel(ORTHO_RASTER_ID, bounds.toMapBounds(), state.opacity))
            }.orEmpty(),
            polygons = areaPoints.takeIf { it.size >= 3 }?.let { points ->
                listOf(
                    MapPolygonModel(
                        "mission-area", points.map { MapCoordinate(it.latitude, it.longitude) },
                        MISSION_AREA_COLOR, MISSION_AREA_FILL_COLOR,
                    )
                )
            }.orEmpty(),
            polylines = buildList {
                if (surveyPoints.size >= 2) add(
                    MapPolylineModel("survey-path", surveyPoints.map { MapCoordinate(it.latitude, it.longitude) }, SURVEY_COLOR, 6f)
                )
                if (routePoints.size >= 2) add(
                    MapPolylineModel("route-path", routePoints.map { MapCoordinate(it.latitude, it.longitude) }, ROUTE_COLOR, 6f)
                )
            },
            markers = buildList {
                surveyPoints.forEachIndexed { index, point ->
                    add(MapMarkerModel("survey-$index", MapCoordinate(point.latitude, point.longitude), "${index + 1}"))
                }
                droneLocation?.takeIf { it.latitude.isFinite() && it.longitude.isFinite() && (it.latitude != 0.0 || it.longitude != 0.0) }
                    ?.let {
                        add(
                            MapMarkerModel(
                                "aircraft", MapCoordinate(it.latitude, it.longitude), aircraftLabel,
                                droneHeading.toFloat(), layer = MapOverlayLayer.AIRCRAFT,
                            )
                        )
                    }
            },
        )
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::loadImage) }
    val worldPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::loadWorldFile) }

    BoxWithConstraints(Modifier.fillMaxSize().testTag(ORTHO_PREVIEW_SCREEN_TAG)) {
        OsmdroidMapSurface(
            scene = scene,
            rasterBitmaps = state.asset?.bitmap?.let { mapOf(ORTHO_RASTER_ID to it) }.orEmpty(),
            initialCamera = state.camera,
            cameraCommand = state.cameraCommand,
            backgroundEnabled = state.backgroundEnabled,
            interactions = MapInteractionHandlers(onCameraChanged = viewModel::rememberCamera),
            modifier = Modifier.fillMaxSize().testTag(ORTHO_MAP_SURFACE_TAG),
        )
        OrthoControlPanel(
            state = state,
            onLoadImage = { imagePicker.launch(arrayOf("image/tiff", "image/*", "application/octet-stream")) },
            onLoadWorld = { worldPicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) },
            onClear = { viewModel.clear(); previewAssetsViewModel.clearOrtho() },
            onZoom = viewModel::zoomToAsset,
            onBackground = viewModel::setBackgroundEnabled,
            onOpacity = viewModel::setOpacity,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .widthIn(max = if (maxWidth < 600.dp) maxWidth - 24.dp else 360.dp),
        )
        AppButton(
            text = stringResource(R.string.preview_mode_next_3d),
            onClick = onShowPointCloud,
            style = AppButtonStyle.SECONDARY,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        )
    }
}

@Composable
private fun OrthoControlPanel(
    state: OrthoPreviewUiState,
    onLoadImage: () -> Unit,
    onLoadWorld: () -> Unit,
    onClear: () -> Unit,
    onZoom: () -> Unit,
    onBackground: (Boolean) -> Unit,
    onOpacity: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val asset = state.asset
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ortho_title), style = MaterialTheme.typography.titleMedium)
            Text(
                state.statusOverride ?: when {
                    asset == null -> stringResource(R.string.ortho_empty_state)
                    asset.bounds == null -> stringResource(R.string.ortho_image_loaded_no_world, asset.bitmapFileName, asset.sourceWidth, asset.sourceHeight)
                    else -> stringResource(R.string.ortho_loaded, asset.bitmapFileName, asset.worldFileName.orEmpty())
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                if (asset?.bounds != null) stringResource(
                    R.string.ortho_stats, asset.sourceWidth, asset.sourceHeight,
                    asset.bounds.minLon, asset.bounds.minLat, asset.bounds.maxLon, asset.bounds.maxLat,
                ) else stringResource(R.string.ortho_stats_empty),
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.loading) CircularProgressIndicator()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(stringResource(R.string.ortho_load_image), onLoadImage, Modifier.weight(1f), !state.loading)
                AppButton(stringResource(R.string.ortho_load_world), onLoadWorld, Modifier.weight(1f), !state.loading && asset != null, AppButtonStyle.SECONDARY)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(stringResource(R.string.ortho_zoom_to), onZoom, Modifier.weight(1f), asset?.bounds != null, AppButtonStyle.SECONDARY)
                AppButton(stringResource(R.string.ortho_clear), onClear, Modifier.weight(1f), asset != null, AppButtonStyle.DANGER)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ortho_background), Modifier.weight(1f))
                Switch(state.backgroundEnabled, onBackground)
            }
            Text("${stringResource(R.string.ortho_opacity)}: ${(state.opacity * 100).toInt()}%")
            Slider(state.opacity, onOpacity, valueRange = 0f..1f, steps = 19)
        }
    }
}

private fun com.example.droneservicesapp.data.ortho.OrthoBounds.toMapBounds() = MapBounds(
    MapCoordinate(minLat, minLon), MapCoordinate(maxLat, maxLon)
)
private fun com.example.droneservicesapp.domain.model.RouteWaypoint.toLatLng() =
    com.google.android.gms.maps.model.LatLng(latitude, longitude)

private const val ORTHO_RASTER_ID = "ortho"
private const val MISSION_AREA_COLOR = 0xFF50C8FF.toInt()
private const val MISSION_AREA_FILL_COLOR = 0x3C50C8FF
private const val SURVEY_COLOR = 0xFF29E6DA.toInt()
private const val ROUTE_COLOR = 0xFF4DFF59.toInt()
