package com.example.droneservicesapp.ui.compose.screens.missionmap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.droneservicesapp.R
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.compose.navigation.ShellNavigator
import com.example.droneservicesapp.ui.home.CompositionLifecycleOwner
import com.example.droneservicesapp.ui.map.model.MapScreenPoint
import com.example.droneservicesapp.ui.home.MissionMapController
import com.example.droneservicesapp.ui.home.PreviewPanelState
import com.example.droneservicesapp.ui.home.map.MissionMapLayers
import com.example.droneservicesapp.ui.home.map.MissionMapSceneBuilder
import com.example.droneservicesapp.ui.home.model.HomeTelemetryViewModel
import com.example.droneservicesapp.ui.home.model.MissionMapViewModel
import com.example.droneservicesapp.ui.map.model.MapInteractionHandlers
import com.example.droneservicesapp.ui.map.osmdroid.OsmdroidMapSurface
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.ui.pointcloud.PointCloudGlView
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.example.droneservicesapp.ui.shell.model.ShellLiveGeoStatus
import kotlin.math.roundToInt

const val MISSION_MAP_SCREEN_TAG = "mission-map-screen"
const val MISSION_MAP_SURFACE_TAG = "mission-map-surface"
const val MISSION_POINT_CLOUD_SURFACE_TAG = "mission-map-point-cloud-surface"

private const val MISSION_MAP_MAX_ZOOM = 20.0

/**
 * Home destination: the mission map with its telemetry strip, planning panels, preview modes,
 * mission files and the arm / flight-mode safety flows. Replaces `MissionMapFragment`.
 */
@Composable
fun MissionMapScreen(
    droneViewModel: DroneViewModel,
    activityViewModel: MainActivityViewModel,
    homeTelemetryViewModel: HomeTelemetryViewModel,
    previewAssetsViewModel: PreviewAssetsViewModel,
    navigator: ShellNavigator,
    mapViewModel: MissionMapViewModel = viewModel(),
) {
    val context = LocalContext.current
    val hostLifecycleOwner = LocalLifecycleOwner.current
    val screenLifecycle = remember { CompositionLifecycleOwner() }
    val controller = remember {
        MissionMapController(
            context = context,
            lifecycleOwner = screenLifecycle,
            droneViewModel = droneViewModel,
            activityViewModel = activityViewModel,
            mapViewModel = mapViewModel,
            previewAssetsViewModel = previewAssetsViewModel,
            openNavigation = navigator::openNavigation,
        )
    }

    DisposableEffect(controller, hostLifecycleOwner) {
        // Observers are registered while the screen lifecycle is CREATED and only then does it
        // follow the host up to STARTED/RESUMED, which is the order a Fragment view went through.
        screenLifecycle.follow(Lifecycle.State.CREATED)
        controller.start()
        val hostLifecycle = hostLifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event -> screenLifecycle.follow(event.targetState) }
        hostLifecycle.addObserver(observer)
        onDispose {
            hostLifecycle.removeObserver(observer)
            controller.dispose()
            screenLifecycle.destroy()
        }
    }

    val telemetry by homeTelemetryViewModel.homeTelemetryUiStateFlow.collectAsStateWithLifecycle()
    val liveGeoStatus by activityViewModel.shellLiveGeoStatus.observeAsState(
        ShellLiveGeoStatus(label = stringResource(R.string.live_geo_unknown_status)),
    )
    val homeMapState by controller.homeMapUiState.collectAsStateWithLifecycle()
    val workflow by activityViewModel.activePlanningWorkflow.observeAsState(PlanningWorkflow.AREA)
    val paramsPresentation = rememberMissionParamsPresentation(activityViewModel, droneViewModel)

    val tabletLayout = booleanResource(R.bool.config_tablet_planning_dock)
    val drawBarReplacesDock = booleanResource(R.bool.config_map_draw_bar_replaces_dock)
    val leftPanelWidth = dimensionResource(if (tabletLayout) R.dimen.tablet_left_panel_width else R.dimen.phone_map_panel_left_width)
    val rightPanelWidth = dimensionResource(if (tabletLayout) R.dimen.tablet_right_panel_width else R.dimen.phone_map_panel_right_width)
    val phonePanelMaxHeight = dimensionResource(R.dimen.phone_map_panel_max_height)
    val previewRailWidth = dimensionResource(R.dimen.preview_mode_rail_width)
    val dockBottomMargin = dimensionResource(
        if (tabletLayout) R.dimen.tablet_bottom_dock_margin_bottom else R.dimen.phone_map_bottom_dock_margin_bottom,
    )
    val panelMargin = if (tabletLayout) 12.dp else dimensionResource(R.dimen.phone_map_overlay_card_margin)

    val shell = homeMapState.shellState
    val interaction = homeMapState.interactionState
    val drawBarVisible = interaction.isDrawActionButtonsVisible
    val dockVisible = shell.isBottomUtilityBarVisible &&
        interaction.isBottomActionBarVisible &&
        !(drawBarReplacesDock && drawBarVisible)

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(controller) {
        controller.messages.snackbars.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(MISSION_MAP_SCREEN_TAG),
    ) {
        MissionMapSurface(controller)
        if (controller.preview.showsPointCloud) {
            MissionPointCloudSurface(controller.preview, controller::onPointCloudMissionPointClicked)
        }
        // Light scrim over the map/3D preview; it never consumes touches.
        Box(
            Modifier
                .fillMaxSize()
                .background(MapChrome.scrim),
        )

        Column(Modifier.fillMaxSize()) {
            MapTopStatusStrip(
                telemetry = telemetry,
                liveGeoStatus = liveGeoStatus,
                safety = controller.safety,
                flightModeContent = {
                    FlightModePopup(telemetry, controller.safety.pendingConfirmation, controller)
                },
                armContent = {
                    SlideToArmPopup(telemetry.armCommandState, controller.safety.armSubmitted, controller)
                },
                onArmCardClick = controller::onArmCardClicked,
                onFlightModeCardClick = { controller.onFlightModeCardClicked(telemetry.isFlightModeControlEnabled) },
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (controller.weather.visible) WeatherPanel(controller.weather)
                    controller.missionDownload.percent?.let { MissionDownloadChip(it) }
                }

                if (shell.isLeftPanelVisible) {
                    MissionParamsPanel(
                        activityViewModel = activityViewModel,
                        presentation = paramsPresentation,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = panelMargin, top = panelMargin, bottom = 8.dp)
                            .width(leftPanelWidth)
                            .sidePanelHeight(tabletLayout, phonePanelMaxHeight),
                    )
                }

                if (shell.isRightPanelVisible) {
                    WorkflowPanel(
                        workflow = workflow,
                        state = controller.workflowPanel,
                        weatherVisible = controller.weather.visible,
                        params = paramsPresentation,
                        controller = controller,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = panelMargin, top = panelMargin, bottom = 8.dp)
                            .width(rightPanelWidth)
                            .sidePanelHeight(tabletLayout, phonePanelMaxHeight),
                    )
                }

                if (controller.obstaclePanel.visible) {
                    ObstaclePanel(
                        state = controller.obstaclePanel,
                        obstacles = controller.layers.obstacles.obstacles,
                        controller = controller,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = panelMargin, bottom = 8.dp)
                            .width(rightPanelWidth),
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (dockVisible) 16.dp else dockBottomMargin),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (drawBarVisible) {
                        DrawActionBar(
                            onDecline = controller::declineGeometry,
                            onAccept = controller::acceptGeometry,
                            tabletLayout = tabletLayout,
                        )
                    } else {
                        MissionSummaryCard(controller.summaryCard)
                    }
                    PreviewModeRail(
                        preview = controller.preview,
                        onSelect = controller::selectPreviewMode,
                        modifier = Modifier.width(previewRailWidth),
                    )
                }

                if (controller.waypointDock.visible && controller.waypointDock.anchor == null) {
                    WaypointEditDock(
                        state = controller.waypointDock,
                        controller = controller,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (tabletLayout) 8.dp else 88.dp),
                    )
                }
            }

            if (dockVisible) {
                MapBottomDock(
                    tabletLayout = tabletLayout,
                    overlay = homeMapState.overlayControlsState,
                    dock = controller.dock,
                    planningSelected = shell.isPlanningActive || shell.isRightPanelVisible,
                    controller = controller,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(
                            start = if (tabletLayout) 24.dp else 0.dp,
                            end = if (tabletLayout) 24.dp else 0.dp,
                            bottom = dockBottomMargin,
                        ),
                )
            }
        }

        // A waypoint picked on the map or in 3D gets its dock next to the picked point.
        controller.waypointDock.anchor?.takeIf { controller.waypointDock.visible }?.let { anchor ->
            WaypointEditDock(
                state = controller.waypointDock,
                controller = controller,
                modifier = Modifier.placedNear(anchor, 8.dp),
            )
        }

        if (controller.files.saveVisible) {
            SaveMissionCard(
                state = controller.files,
                controller = controller,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = panelMargin),
            )
        }
        if (controller.files.loadVisible) {
            LoadMissionCard(
                state = controller.files,
                controller = controller,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = panelMargin),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )
    }

    MissionDialogHost(controller.dialogs)
}

/** Tablet side panels run down to the dock; phone panels are capped at the configured height. */
private fun Modifier.sidePanelHeight(tabletLayout: Boolean, phoneMaxHeight: Dp): Modifier =
    if (tabletLayout) this else heightIn(max = phoneMaxHeight)

/**
 * Places the element to the right of [anchor] (pixels in the screen box), vertically centred on
 * it and clamped inside the box, as the View-based dock positioning did.
 */
private fun Modifier.placedNear(anchor: MapScreenPoint, margin: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val marginPx = margin.toPx()
    val x = (anchor.x + marginPx).coerceIn(marginPx, (width - placeable.width).toFloat().coerceAtLeast(marginPx))
    val y = (anchor.y - placeable.height / 2f).coerceIn(marginPx, (height - placeable.height).toFloat().coerceAtLeast(marginPx))
    layout(width, height) {
        placeable.place(x.roundToInt(), y.roundToInt())
    }
}

@Composable
private fun MissionMapSurface(controller: MissionMapController) {
    val layers: MissionMapLayers = controller.layers
    val geoZones by remember(layers) {
        derivedStateOf { MissionMapSceneBuilder.geoZones(layers.geoZones.zones) }
    }
    val terrainCoverage by remember(layers) {
        derivedStateOf { MissionMapSceneBuilder.terrainCoverage(layers.terrainCoverage.coverage) }
    }
    val missionArea by remember(layers) {
        derivedStateOf { MissionMapSceneBuilder.missionArea(layers.polygon.vertices, layers.polygon.enabled) }
    }
    val surveyPath by remember(layers) {
        derivedStateOf {
            val survey = layers.survey
            MissionMapSceneBuilder.surveyPath(
                path = survey.path,
                areaVertices = survey.areaVertices,
                segmentColors = survey.segmentColors,
                editingEnabled = survey.editingEnabled,
                selectedIndex = survey.selectedIndex,
                batteryReturnPoints = survey.batteryReturnPoints,
                tankRefillPoints = survey.tankRefillPoints,
            )
        }
    }
    val route by remember(layers) {
        derivedStateOf {
            val route = layers.route
            MissionMapSceneBuilder.route(
                waypoints = route.waypoints,
                displayPath = route.displayPath,
                terrainPath = route.terrainPath,
                enabled = route.enabled,
                selectedControlIndex = route.selectedControlIndex,
                selectedTerrainIndex = route.selectedTerrainIndex,
                dragPreview = route.dragPreview,
            )
        }
    }
    val obstacles by remember(layers) {
        derivedStateOf { MissionMapSceneBuilder.obstacles(layers.obstacles.obstacles, layers.obstacles.draftPoints) }
    }
    val aircraft by remember(layers) {
        derivedStateOf {
            val aircraft = layers.aircraft
            MissionMapSceneBuilder.aircraft(
                displayedPosition = aircraft.displayedPosition,
                headingDegrees = aircraft.headingDegrees,
                offsetAdjustmentActive = aircraft.offsetAdjustmentActive,
                homePosition = aircraft.homePosition,
                flightTrace = aircraft.flightTrace,
                simulation = aircraft.simulation,
            )
        }
    }
    val scene by remember(layers) {
        derivedStateOf {
            MissionMapSceneBuilder.compose(
                rasters = MissionMapSceneBuilder.orthoRaster(layers.ortho),
                layers = listOf(geoZones, terrainCoverage, missionArea, surveyPath, route, obstacles, aircraft),
            )
        }
    }
    val rasterBitmaps by remember(layers) {
        derivedStateOf {
            layers.ortho?.let { mapOf(MissionMapSceneBuilder.ORTHO_RASTER_ID to it.bitmap) } ?: emptyMap()
        }
    }
    val interactions = remember(controller) {
        MapInteractionHandlers(
            onTap = controller::onMapTap,
            onMarkerMoved = layers::onMarkerMoved,
            onCameraChanged = controller::rememberCamera,
            onMarkerClick = layers::onMarkerClick,
            onMarkerDoubleTap = layers::onMarkerDoubleTap,
            onMarkerDrag = layers::onMarkerDrag,
        )
    }
    val initialCamera = remember(controller) { controller.initialCamera }

    OsmdroidMapSurface(
        scene = scene,
        rasterBitmaps = rasterBitmaps,
        initialCamera = initialCamera,
        cameraCommand = layers.cameraCommand,
        backgroundEnabled = layers.tilesEnabled,
        interactions = interactions,
        modifier = Modifier
            .fillMaxSize()
            .testTag(MISSION_MAP_SURFACE_TAG),
        maxZoom = MISSION_MAP_MAX_ZOOM,
        showUserLocation = true,
        onSurfaceReady = { layers.surface = it },
    )
}

/** The embedded 3D terrain/point-cloud preview, shown over the map in 3D mode. */
@Composable
private fun MissionPointCloudSurface(
    preview: PreviewPanelState,
    onMissionPointClicked: (Int, Float, Float) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var glView by remember { mutableStateOf<PointCloudGlView?>(null) }
    var appliedPointCloud by remember { mutableStateOf<Any?>(Unit) }
    var appliedOverlay by remember { mutableStateOf<Any?>(Unit) }
    var appliedResetToken by remember { mutableStateOf(preview.cameraResetToken) }

    DisposableEffect(lifecycleOwner, glView) {
        val view = glView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view?.onResume()
                Lifecycle.Event.ON_PAUSE -> view?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .testTag(MISSION_POINT_CLOUD_SURFACE_TAG),
        factory = { context ->
            PointCloudGlView(context).also { view ->
                view.setOnMissionPointClickListener { index, x, y -> onMissionPointClicked(index, x, y) }
                glView = view
            }
        },
        update = { view ->
            val pointCloud = preview.pointCloud
            if (pointCloud !== appliedPointCloud) {
                if (pointCloud != null) view.setPointCloud(pointCloud) else view.clearPointCloud()
                appliedPointCloud = pointCloud
            }
            val overlay = preview.missionOverlay
            if (overlay !== appliedOverlay) {
                view.setMissionOverlay(overlay)
                appliedOverlay = overlay
            }
            view.setPointSize(preview.pointSize)
            view.setPointCloudOpacity(preview.pointOpacity)
            view.setHeightColorModeEnabled(preview.heightColors)
            if (preview.cameraResetToken != appliedResetToken) {
                view.resetCamera()
                appliedResetToken = preview.cameraResetToken
            }
        },
        onRelease = { view ->
            glView = null
            view.setOnMissionPointClickListener(null)
            view.release()
        },
    )
}
