package com.example.droneservicesapp.ui.home

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.geoawareness.incident.GeoIncidentEncryptedLogStore
import com.example.droneservicesapp.data.geoawareness.incident.GeoIncidentLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.data.geoawareness.logging.OperatorFlightEventLogger
import com.example.droneservicesapp.data.rtk.RtkForwardingState
import com.example.droneservicesapp.data.weather.OpenMeteoWindRepository
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.planning.MissionPlanningCoordinator
import com.example.droneservicesapp.domain.planning.MissionResourcePlan
import com.example.droneservicesapp.domain.planning.PointRouteCoordinator
import com.example.droneservicesapp.domain.terrain.PointCloudCoverage
import com.example.droneservicesapp.domain.terrain.TerrainWaypoint
import com.example.droneservicesapp.mavserver.ArduCopterFlightMode
import com.example.droneservicesapp.mavserver.ArmCommandState
import com.example.droneservicesapp.mavserver.ArmRequestResult
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.mavserver.FlightModeCommandState
import com.example.droneservicesapp.mavserver.FlightModeRequestResult
import com.example.droneservicesapp.mavserver.TelemetryMapping
import com.example.droneservicesapp.ui.common.RtkTonePlayer
import com.example.droneservicesapp.ui.home.components.MissionParamsPreferencesBridge
import com.example.droneservicesapp.ui.home.components.DroneMapTrackingController
import com.example.droneservicesapp.ui.home.components.InitialMapViewportController
import com.example.droneservicesapp.ui.home.components.MissionEditorCoordinator
import com.example.droneservicesapp.ui.home.components.MissionFilesCoordinator
import com.example.droneservicesapp.ui.home.components.MissionGenerationCoordinator
import com.example.droneservicesapp.ui.home.components.MissionMapActionHandler
import com.example.droneservicesapp.ui.home.components.MissionMapObserverCoordinator
import com.example.droneservicesapp.ui.home.components.MissionMapRenderer
import com.example.droneservicesapp.ui.home.components.MissionMapSessionState
import com.example.droneservicesapp.ui.home.components.MissionPresentationObserver
import com.example.droneservicesapp.ui.home.components.MissionSimulationController
import com.example.droneservicesapp.ui.home.components.MissionSummaryPresenter
import com.example.droneservicesapp.ui.home.components.MissionUploadCoordinator
import com.example.droneservicesapp.ui.home.components.PlannedHomePlacementController
import com.example.droneservicesapp.ui.home.components.PreviewMapFocusController
import com.example.droneservicesapp.ui.home.components.PreviewWorkflowController
import com.example.droneservicesapp.ui.home.components.TerrainMissionCoordinator
import com.example.droneservicesapp.ui.home.components.TerrainPreviewCoordinator
import com.example.droneservicesapp.ui.home.components.WindWeatherController
import com.example.droneservicesapp.ui.home.geoawareness.GeoAuthorizationSession
import com.example.droneservicesapp.ui.home.geoawareness.GeoAwarenessDatasetController
import com.example.droneservicesapp.ui.home.geoawareness.GeoAwarenessDialogController
import com.example.droneservicesapp.ui.home.geoawareness.GeoAwarenessEventTracker
import com.example.droneservicesapp.ui.home.geoawareness.GeoAwarenessPlanningController
import com.example.droneservicesapp.ui.home.geoawareness.GeoAwarenessUploadController
import com.example.droneservicesapp.ui.home.geoawareness.LiveGeoAwarenessController
import com.example.droneservicesapp.ui.home.map.MissionMapLayers
import com.example.droneservicesapp.ui.home.map.ObstacleEditorState
import com.example.droneservicesapp.ui.home.model.HomeMapScreenMode
import com.example.droneservicesapp.ui.home.model.HomeMapUiState
import com.example.droneservicesapp.ui.home.model.MissionMapViewModel
import com.example.droneservicesapp.ui.map.model.MapCamera
import com.example.droneservicesapp.ui.map.model.MapDownloadHandle
import com.example.droneservicesapp.ui.map.model.MapGeometry
import com.example.droneservicesapp.ui.preview.PreviewAssetLoader
import com.example.droneservicesapp.ui.preview.PreviewAssetStore
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.preview.PreviewMode
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel.ServiceMissionState
import com.google.android.gms.maps.model.LatLng
import io.dronefleet.mavlink.common.MavLandedState
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * Orchestrates the Compose mission-map screen for one on-screen session.
 *
 * This is the former `MissionMapFragment` orchestration moved off Android Views: the same
 * coordinators, observers and decisions, writing into Compose state sections ([layers],
 * [workflowPanel], [preview], ...) instead of widgets. [lifecycleOwner] must be destroyed when
 * the screen leaves composition; see [CompositionLifecycleOwner].
 */
class MissionMapController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val droneViewModel: DroneViewModel,
    private val activityViewModel: MainActivityViewModel,
    private val mapViewModel: MissionMapViewModel,
    private val previewAssetsViewModel: PreviewAssetsViewModel,
    private val openNavigation: () -> Unit,
) {
    // Screen state sections

    val messages = MissionMessages(context)
    val dialogs = MissionDialogQueue()
    val layers = MissionMapLayers(context, activityViewModel, dialogs) { messages.toast(it) }
    val dock = MapDockState()
    val obstaclePanel = ObstaclePanelState()
    val waypointDock = WaypointEditDockState()
    val summaryCard = MissionSummaryCardState()
    val workflowPanel = WorkflowPanelState()
    val weather = WeatherPanelState()
    val missionDownload = MissionDownloadState()
    val offlineDownload = OfflineDownloadState()
    val files = MissionFilesState()
    val preview = PreviewPanelState()
    val safety = SafetyControlsState()

    val homeMapUiState = mapViewModel.homeMapUiStateFlow

    // Collaborators

    private val scope = lifecycleOwner.lifecycleScope
    private val sessionState = MissionMapSessionState()
    private val missionPlanningCoordinator = MissionPlanningCoordinator()
    private val geoAuthorizationSession = GeoAuthorizationSession()
    private val geoAwarenessPlanningController = GeoAwarenessPlanningController()
    private val windWeatherRepository = OpenMeteoWindRepository()
    private val preferencesBridge = MissionParamsPreferencesBridge(context, activityViewModel)
    private val geoEventLogger = GeoAwarenessEventLogger(context.applicationContext)
    private val operatorEventLogger = OperatorFlightEventLogger(geoEventLogger)
    private val geoAwarenessDialogController = GeoAwarenessDialogController(context, geoAuthorizationSession, dialogs)
    private val geoAwarenessDatasetController = GeoAwarenessDatasetController(context)
    private val geoAwarenessEventTracker = GeoAwarenessEventTracker(
        geoEventLogger,
        GeoIncidentLogger(GeoIncidentEncryptedLogStore(context.applicationContext)),
        metadata = {
            val live = if (::liveGeoAwarenessController.isInitialized) liveGeoAwarenessController else null
            GeoAwarenessEventTracker.Metadata(
                datasetTitle = live?.datasetInfo?.title,
                datasetVersion = live?.datasetInfo?.version,
                healthState = live?.health?.state?.name,
            )
        },
        telemetry = {
            GeoAwarenessEventTracker.Telemetry(
                altitudeAglMeters = sessionState.droneAltitudeAglMeters,
                altitudeAmslMeters = sessionState.droneAltitudeAmslMeters,
                horizontalAccuracyMeters = sessionState.horizontalAccuracyMeters,
                verticalAccuracyMeters = sessionState.verticalAccuracyMeters,
                groundSpeedMetersPerSecond = sessionState.groundSpeedMetersPerSecond,
                verticalSpeedMetersPerSecond = sessionState.verticalSpeedMetersPerSecond,
                headingDegrees = sessionState.headingDegrees,
            )
        },
    )

    private var active = false
    private var offlineDownloadHandle: MapDownloadHandle? = null

    /** Incremented per offline download so late callbacks from a cancelled one are ignored. */
    private var offlineDownloadGeneration = 0
    private var paramsBound = false
    private var lastParamsLayoutSignature: Triple<Boolean, Boolean, Boolean>? = null
    private var lastScreenMode: HomeMapScreenMode? = null
    private var lastPresentedFlightModeResult: FlightModeCommandState? = null
    private var lastPresentedArmState: ArmCommandState? = null

    private val missionObserverCoordinator = MissionMapObserverCoordinator(lifecycleOwner, activityViewModel)
    private val presentationObserver = MissionPresentationObserver(lifecycleOwner)
    private val pointRouteCoordinator = PointRouteCoordinator(
        scope, activityViewModel, previewAssetsViewModel, missionPlanningCoordinator, ::isActive,
    )
    private val terrainMissionCoordinator = TerrainMissionCoordinator(
        context, scope, activityViewModel, previewAssetsViewModel, missionPlanningCoordinator,
    )

    private lateinit var droneMapTrackingController: DroneMapTrackingController
    private lateinit var plannedHomePlacementController: PlannedHomePlacementController
    private lateinit var previewMapFocusController: PreviewMapFocusController
    private lateinit var initialViewportController: InitialMapViewportController
    private lateinit var liveGeoAwarenessController: LiveGeoAwarenessController
    private lateinit var terrainPreviewCoordinator: TerrainPreviewCoordinator
    private lateinit var previewWorkflowController: PreviewWorkflowController
    private lateinit var missionMapRenderer: MissionMapRenderer
    private lateinit var missionEditorCoordinator: MissionEditorCoordinator
    private lateinit var missionGenerationCoordinator: MissionGenerationCoordinator
    private lateinit var missionSummaryPresenter: MissionSummaryPresenter
    private lateinit var geoAwarenessUploadController: GeoAwarenessUploadController
    private lateinit var missionMapActionHandler: MissionMapActionHandler
    private lateinit var missionSimulationController: MissionSimulationController
    private lateinit var windWeatherController: WindWeatherController
    private lateinit var missionUploadCoordinator: MissionUploadCoordinator
    private lateinit var missionFilesCoordinator: MissionFilesCoordinator

    private val latestRawDronePosition: LatLon?
        get() = droneMapTrackingController.rawPosition
    private val latestRealDronePosition: LatLon?
        get() = droneMapTrackingController.correctedPosition
    private val homePlacementMode: Boolean
        get() = ::plannedHomePlacementController.isInitialized && plannedHomePlacementController.isActive

    /** Camera the map should open with: the last one used, or the default operating area. */
    val initialCamera: MapCamera
        get() = mapViewModel.camera

    fun rememberCamera(camera: MapCamera) {
        // Layout passes can re-report the camera the map was opened with; that is not a choice
        // the operator made, so it must not suppress the first automatic focus.
        if (camera == mapViewModel.camera) return
        mapViewModel.camera = camera
    }

    // Lifecycle

    fun start() {
        active = true
        initializeMap()
        initControllers()
        bindWorkflowObserver()
        renderAddHomeButton()
        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
        observeDroneViewModel()
        observeMapState()
        observeMissionMapViewModel()
        observeGeoAwarenessSharedState()
        observePreviewSettings()
        observeSafetyCommandResults()

        mapViewModel.restoreFromMapState(activityViewModel.mapState.value ?: MainActivityViewModel.MapState.Idle)
        previewWorkflowController.selectMode(PreviewMode.MAP)
        if (mapViewModel.hasRememberedCamera) initialViewportController.markInitialViewportRestored()
        scope.launch {
            yield()
            if (isActive()) previewWorkflowController.restorePersistedAssets()
        }
        lifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> onResume()
                Lifecycle.Event.ON_PAUSE -> onPause()
                else -> Unit
            }
        })
    }

    fun dispose() {
        if (!active) return
        // The download runs against this screen's map view, which is about to be released.
        offlineDownloadGeneration++
        offlineDownloadHandle?.cancel()
        offlineDownloadHandle = null
        offlineDownload.percent = null
        activityViewModel.setShellBottomActionBarVisible(false)
        safety.flightModeOpen = false
        safety.armOpen = false
        stopMissionSimulation()
        missionGenerationCoordinator.cancel()
        missionSummaryPresenter.dispose()
        terrainMissionCoordinator.cancel()
        previewWorkflowController.dispose()
        windWeatherController.dispose()
        cancelDroneOffsetAdjustment()
        liveGeoAwarenessController.dispose()
        layers.terrainCoverage.clear()
        layers.obstacles.release()
        droneMapTrackingController.clearTraceAndHome()
        sessionState.clear()
        dialogs.clear()
        active = false
    }

    private fun onResume() {
        if (!isActive()) return
        scope.launch {
            yield()
            if (!isActive()) return@launch
            previewWorkflowController.refreshAssets()
            missionMapRenderer.renderCurrentPath()
            missionGenerationCoordinator.redrawIfEditable()
            initialViewportController.centerIfNeeded()
        }
    }

    private fun onPause() {
        if (!isActive()) return
        stopMissionSimulation()
        cancelDroneOffsetAdjustment()
    }

    private fun isActive(): Boolean = active

    // Setup

    private fun initializeMap() {
        droneMapTrackingController = DroneMapTrackingController(
            aircraftLayer = layers.aircraft,
            onHomeCaptured = activityViewModel::setPlannedHomePosition,
            onDisarmed = { geoAwarenessUploadController.resetCurrentFlightAuthorizations("disarmed") },
        )
        layers.survey.setEditCallbacks(
            onSelected = { missionEditorCoordinator.selectSurvey(it) },
            onMoved = { index, point -> missionEditorCoordinator.moveSurvey(index, point) },
        )
        layers.route.setTerrainWaypointSelectionCallback {
            missionEditorCoordinator.select(PlanningWorkflow.POINTS, it, previewWorkflowController.mode)
        }
        plannedHomePlacementController = PlannedHomePlacementController(
            layers = layers,
            beforePlacement = {
                cancelDroneOffsetAdjustment()
                missionEditorCoordinator.cancelObstaclePlacement()
            },
            onHomePlaced = ::placePlannedHome,
        )
        previewMapFocusController = PreviewMapFocusController(
            layers = layers,
            assets = previewAssetsViewModel,
            onFocused = { initialViewportController.markCenteredToDrone() },
        )
        initialViewportController = InitialMapViewportController(
            scope = scope,
            isActive = ::isActive,
            hasDronePosition = layers.aircraft::hasDronePosition,
            hasPendingPreviewFocus = previewMapFocusController::hasPendingRequest,
            focusPendingPreview = previewMapFocusController::focusPendingAsset,
            centerOnDrone = layers::centerOnDrone,
            centerOnUser = { layers.centerOnUserIfPermitted(showErrors = false) },
            maxDroneAttempts = MAX_INITIAL_DRONE_CENTER_ATTEMPTS,
        )
        liveGeoAwarenessController = LiveGeoAwarenessController(
            context = context,
            scope = scope,
            viewModel = activityViewModel,
            datasetController = geoAwarenessDatasetController,
            planningController = geoAwarenessPlanningController,
            overlayController = layers.geoZones,
            eventLogger = geoEventLogger,
            eventTracker = geoAwarenessEventTracker,
            dialogController = geoAwarenessDialogController,
            telemetry = {
                LiveGeoAwarenessController.Telemetry(
                    position = latestRealDronePosition,
                    altitudeAglMeters = sessionState.droneAltitudeAglMeters,
                    altitudeAmslMeters = sessionState.droneAltitudeAmslMeters,
                    groundSpeedMetersPerSecond = sessionState.groundSpeedMetersPerSecond,
                    verticalSpeedMetersPerSecond = sessionState.verticalSpeedMetersPerSecond,
                    headingDegrees = sessionState.headingDegrees,
                    connected = droneViewModel.conStateLiveData.value == true,
                    gpsFixQuality = TelemetryMapping.gpsFixQuality(
                        droneViewModel.gpsFixType.value,
                        isConnected = droneViewModel.conStateLiveData.value == true,
                    ),
                )
            },
            isActive = ::isActive,
        )
        liveGeoAwarenessController.initialize()
    }

    private fun initControllers() {
        terrainPreviewCoordinator = TerrainPreviewCoordinator(
            context = context,
            scope = scope,
            preview = preview,
            viewModel = activityViewModel,
            droneViewModel = droneViewModel,
            previewAssets = previewAssetsViewModel,
            currentPointCloud = {
                if (isSurveyMode()) droneViewModel.terrainPreview.value?.pointCloud
                else previewAssetsViewModel.pointCloudAsset?.pointCloud
            },
            currentDroneLocation = ::currentOffsetDroneLocation,
            isSurveyMode = ::isSurveyMode,
            selectedWorkflow = { missionEditorCoordinator.selectedWorkflow },
            selectedWaypointIndex = { missionEditorCoordinator.selectedIndex },
            orderPointRoute = { pointRouteCoordinator.orderForHome(it) },
            redrawMission = { missionGenerationCoordinator.redrawIfEditable() },
            isActive = ::isActive,
            showMessage = { messages.toast(it, long = true) },
        )
        previewWorkflowController = PreviewWorkflowController(
            context = context,
            scope = scope,
            preview = preview,
            layers = layers,
            assets = previewAssetsViewModel,
            store = PreviewAssetStore(context),
            loader = PreviewAssetLoader(context),
            terrainPreview = terrainPreviewCoordinator,
            isSurveyMode = ::isSurveyMode,
            surveyPointCloud = { droneViewModel.terrainPreview.value?.pointCloud },
            renderMapPath = { missionMapRenderer.renderCurrentPath() },
            focusOrtho = { previewMapFocusController.focusOrtho() },
            redrawMission = { missionGenerationCoordinator.redrawIfEditable() },
            generatePointRoute = pointRouteCoordinator::generate,
            isActive = ::isActive,
            showMessage = { messages.toast(it) },
        )
        missionMapRenderer = MissionMapRenderer(
            viewModel = activityViewModel,
            previewAssets = previewAssetsViewModel,
            planner = missionPlanningCoordinator,
            layers = layers,
            terrainCoordinator = terrainMissionCoordinator,
            heightColorsEnabled = { previewWorkflowController.heightColorsEnabled },
        )
        missionEditorCoordinator = MissionEditorCoordinator(
            context = context,
            viewModel = activityViewModel,
            layers = layers,
            dock = waypointDock,
            obstaclePanel = obstaclePanel,
            terrainSampler = terrainMissionCoordinator::resampleWaypoint,
            shouldReversePointRoute = pointRouteCoordinator::shouldReverse,
            updateDistance = ::updateFlightDistance,
            renderPath = missionMapRenderer::renderCurrentPath,
            updatePointCloudOverlay = previewWorkflowController::renderMissionOverlay,
            showMessage = { messages.toast(it) },
            showFormattedMessage = { messages.toast(it) },
        )
        missionGenerationCoordinator = MissionGenerationCoordinator(
            scope = scope,
            viewModel = activityViewModel,
            planner = missionPlanningCoordinator,
            isActive = ::isActive,
            persistPreferences = ::persistMissionEditingPreferences,
            generateSpray = { distance, angle, generation ->
                terrainMissionCoordinator.generateSpray(
                    distance = distance,
                    angle = angle,
                    generation = generation,
                    plannedHome = activityViewModel.plannedHomePosition.value
                        ?: currentOffsetDroneLocation()?.let { LatLon(it.latitude, it.longitude) },
                    droneAltitudeAmsl = droneViewModel.droneAltitudeAmslMeters.value,
                    isCurrent = missionGenerationCoordinator::isCurrent,
                    publishPath = ::renderSurveyPath,
                    clearRenderedPath = layers::clearSurveyPath,
                )
            },
            publishPath = { path, area -> renderSurveyPath(path, area) },
            cancelPointRoute = pointRouteCoordinator::cancel,
            cancelTerrain = terrainMissionCoordinator::cancel,
        )
        missionSummaryPresenter = MissionSummaryPresenter(
            context = context,
            card = summaryCard,
            dialogs = dialogs,
            scope = scope,
            viewModel = activityViewModel,
            planner = missionPlanningCoordinator,
            currentPath = ::currentMissionPath,
            isActive = ::isActive,
            updateGeometryActions = ::updateGeometryActionState,
            updateSimulationButton = ::updateSimulationButton,
            stopSimulation = ::stopMissionSimulation,
            setServiceMarkers = layers.survey::setServiceMarkers,
        )
        geoAwarenessUploadController = GeoAwarenessUploadController(
            eventLogger = geoEventLogger,
            dialogController = geoAwarenessDialogController,
            authorizationSession = geoAuthorizationSession,
            evaluatePlanningResult = liveGeoAwarenessController::evaluatePlanningResult,
            ensureHealth = liveGeoAwarenessController::ensureHealth,
            datasetInfo = { liveGeoAwarenessController.datasetInfo },
            telemetry = {
                GeoAwarenessUploadController.Telemetry(
                    position = latestRealDronePosition,
                    altitudeMeters = sessionState.droneAltitudeAglMeters,
                )
            },
        )
        missionMapActionHandler = MissionMapActionHandler(
            context = context,
            viewModel = activityViewModel,
            eventLogger = operatorEventLogger,
            cancelPlanning = ::cancelPendingMissionPlanning,
            cancelObstaclePlacement = { missionEditorCoordinator.cancelObstaclePlacement() },
            clearEditors = {
                layers.polygon.clear()
                layers.clearSurveyPath()
            },
            showToast = { messages.toast(it, long = true) },
            showSnackbar = messages::snackbar,
        )
        missionSimulationController = MissionSimulationController(
            flightSpeedMetersPerSecond = { activityViewModel.flightSpeed.value ?: 5.0 },
            missionObstacles = { activityViewModel.missionObstacles.value.orEmpty() },
            buildResourcePlan = { path, home -> buildMissionResourcePlan(path, home) },
            setSimulationDrone = layers.aircraft::setSimulationDrone,
            clearSimulationDrone = layers.aircraft::clearSimulationDrone,
            onStateChanged = { updateSimulationButton(currentMissionPath().size >= 2) },
            onWaitingForService = { stop ->
                messages.toast(
                    context.getString(
                        R.string.simulation_waiting_for_service_format,
                        context.getString(MissionSimulationController.serviceDescriptionResource(stop)),
                    ),
                    long = true,
                )
            },
        )
        windWeatherController = WindWeatherController(
            state = weather,
            scope = scope,
            repository = windWeatherRepository,
            locationProvider = {
                activityViewModel.plannedHomePosition.value
                    ?: latestRealDronePosition
                    ?: (layers.surface?.currentCamera() ?: mapViewModel.camera).center
                        .let { LatLon(it.latitude, it.longitude) }
            },
            internetNetworkProvider = droneViewModel::currentRtkInternetNetwork,
        )
        missionUploadCoordinator = MissionUploadCoordinator(
            context = context,
            activityViewModel = activityViewModel,
            droneViewModel = droneViewModel,
            droneLocationProvider = ::currentOffsetDroneLocation,
            preferencesBridge = preferencesBridge,
            beforeUploadGuard = { onAllowed ->
                geoAwarenessUploadController.handleBeforeUpload {
                    missionSummaryPresenter.showUploadSummary(onAllowed)
                }
            },
            beforeMissionUpload = ::stopMissionSimulation,
            showMessage = { messages.toast(it, long = true) },
        )
        missionFilesCoordinator = MissionFilesCoordinator(
            context = context,
            activityViewModel = activityViewModel,
            showMessage = { messages.toast(it, long = true) },
        )
    }

    private fun bindWorkflowObserver() {
        missionObserverCoordinator.observe(activityViewModel.activePlanningWorkflow) {
            updateGeometryActionState()
            onPlanningWorkflowChanged()
            if (paramsBound) enforceSprayAltitudeReference()
        }
    }

    // Operator actions: bottom dock

    fun downloadCurrentViewOffline() {
        val surface = layers.surface ?: return
        // An oversized area would take very long and could push earlier offline areas out of the
        // size-limited tile cache, so it is refused before anything is downloaded.
        val tiles = surface.visibleBounds()?.let { MapGeometry.tileCount(it, OFFLINE_MIN_ZOOM, OFFLINE_MAX_ZOOM) }
        if (tiles != null && tiles > OFFLINE_MAX_TILES) {
            messages.toast(context.getString(R.string.offline_download_area_too_large, tiles, OFFLINE_MAX_TILES), long = true)
            return
        }
        if (offlineDownloadHandle != null) return // one download at a time; progress is on screen
        messages.toast(R.string.offline_download_started)
        offlineDownload.percent = 0
        val generation = ++offlineDownloadGeneration
        offlineDownloadHandle = surface.downloadVisibleRegion(
            minZoom = OFFLINE_MIN_ZOOM,
            maxZoom = OFFLINE_MAX_ZOOM,
            onProgress = { percent ->
                if (generation == offlineDownloadGeneration && offlineDownload.percent != null) {
                    offlineDownload.percent = percent
                }
            },
            onComplete = { errors ->
                if (generation != offlineDownloadGeneration) return@downloadVisibleRegion
                offlineDownloadHandle = null
                offlineDownload.percent = null
                if (!isActive()) return@downloadVisibleRegion
                messages.toast(
                    if (errors == 0) context.getString(R.string.offline_download_complete)
                    else context.getString(R.string.offline_download_failed, errors),
                    long = true,
                )
            },
        )
    }

    fun cancelOfflineDownload() {
        val handle = offlineDownloadHandle ?: return
        offlineDownloadGeneration++
        offlineDownloadHandle = null
        offlineDownload.percent = null
        handle.cancel()
        messages.toast(R.string.offline_download_cancelled)
    }

    fun centerOnUser() {
        if (mapViewModel.homeMapUiState.value?.interactionState?.isDrawingEnabled != true) {
            layers.centerOnUserIfPermitted()
        }
    }

    fun centerOnDroneOrShowDisconnected() {
        if (droneViewModel.conStateLiveData.value == true) layers.centerOnDrone()
        else messages.toast(R.string.no_conn_msg, long = true)
    }

    fun toggleObstacles() {
        cancelDroneOffsetAdjustment()
        toggleObstaclePanel()
    }

    fun startDroneOffsetAdjustment() {
        if (droneViewModel.conStateLiveData.value != true) {
            messages.toast(R.string.no_conn_msg, long = true)
            return
        }
        hideObstaclePanel()
        mapViewModel.setPlanningPanelVisible(false)
        val started = layers.aircraft.startDroneOffsetAdjustment { latitudeOffset, longitudeOffset ->
            droneMapTrackingController.updateOffset(latitudeOffset, longitudeOffset)
            syncLatestDroneLocationSnapshot(droneViewModel.droneLocationLiveData.value)
            liveGeoAwarenessController.updateLiveFromActiveSource()
            dock.offsetSelected = false
            messages.toast(R.string.drone_offset_applied)
        }
        if (started) {
            layers.centerOnDrone()
            dock.offsetSelected = true
            messages.toast(R.string.drone_offset_drag_prompt, long = true)
        } else {
            messages.toast(R.string.drone_location_not_available_yet)
        }
    }

    fun cyclePreviewMode() = previewWorkflowController.cycleMode()

    fun selectPreviewMode(mode: PreviewMode) = previewWorkflowController.selectMode(mode)

    fun openSettings() = openNavigation()

    fun togglePlanning() {
        cancelDroneOffsetAdjustment()
        hideObstaclePanel()
        mapViewModel.togglePlanningPanelVisible()
    }

    // Operator actions: workflow panel

    fun selectWorkflow(workflow: PlanningWorkflow) {
        activityViewModel.setPlanningWorkflow(workflow)
        activityViewModel.mapState.value = MainActivityViewModel.MapState.Idle
    }

    fun loadMission() {
        if (!missionFilesCoordinator.hasSavedMissions()) {
            messages.toast(R.string.no_saved_missions_yet, long = true)
            activityViewModel.mapState.value = MainActivityViewModel.MapState.Idle
        } else {
            activityViewModel.mapState.postValue(MainActivityViewModel.MapState.LoadMissionFromFile)
        }
    }

    fun saveMission() = missionUploadCoordinator.saveMission()

    fun uploadMission() = missionUploadCoordinator.uploadMission()

    fun downloadTerrain() = missionUploadCoordinator.downloadTerrainForCurrentMission()

    fun resumeServiceMission() = missionUploadCoordinator.resumeServiceMission()

    fun toggleWeather() = windWeatherController.toggle()

    fun addPlannedHome() = startPlannedHomePlacement()

    fun toggleMissionSimulation() {
        when (missionSimulationController.state) {
            MissionSimulationController.State.FLYING -> stopMissionSimulation()
            MissionSimulationController.State.WAITING_FOR_SERVICE -> missionSimulationController.resume()
            else -> startMissionSimulation()
        }
    }

    fun closePanels() = mapViewModel.dismissSidePanels()

    fun drawGeometry() = startActiveGeometryDrawing()

    fun clearGeometry() {
        clearActiveMissionGeometry()
        activityViewModel.mapState.value = MainActivityViewModel.MapState.Idle
    }

    // Operator actions: drawing bar

    fun acceptGeometry() {
        val workflow = activityViewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA
        val valid = when (workflow) {
            PlanningWorkflow.AREA -> activityViewModel.missionArea.value?.vertices.orEmpty().size >= 3
            PlanningWorkflow.POINTS -> activityViewModel.routeWaypoints.value.orEmpty().size >= 2
        }
        if (valid) {
            activityViewModel.mapState.value = MainActivityViewModel.MapState.SetFlightParams
        } else {
            messages.toast(
                if (workflow == PlanningWorkflow.POINTS) R.string.route_requires_two_points else R.string.wrong_schema_msg,
                long = true,
            )
        }
    }

    fun declineGeometry() {
        clearActiveMissionGeometry()
        activityViewModel.mapState.value = MainActivityViewModel.MapState.Idle
    }

    // Operator actions: obstacles

    fun addObstacle() {
        missionEditorCoordinator.toggleObstaclePlacement(previewWorkflowController.mode) {
            previewWorkflowController.selectMode(PreviewMode.MAP)
        }
    }

    fun clearObstacles() = missionEditorCoordinator.clearObstacles()

    fun selectObstacleShape(mode: ObstacleEditorState.Mode) = missionEditorCoordinator.selectObstacleMode(mode)

    fun setObstacleRadius(meters: Int) {
        activityViewModel.updateObstacleRadius(meters)
        missionEditorCoordinator.renderObstacleControls()
    }

    fun removeObstacle(id: String) = activityViewModel.removeMissionObstacle(id)

    // Operator actions: waypoint edit dock

    fun deleteSelectedWaypoint() {
        missionEditorCoordinator.deleteSelected { updateFlightDistance(currentMissionPath()) }
    }

    fun cancelSelectedWaypoint() = missionEditorCoordinator.cancelSelection()

    fun changeSelectedWaypointHeight(deltaMeters: Double) = missionEditorCoordinator.adjustHeight(deltaMeters)

    fun setSelectedWaypointHeightText(text: String) {
        waypointDock.heightText = text
    }

    fun applySelectedWaypointHeight() = missionEditorCoordinator.applyHeightField()

    // Operator actions: point-cloud preview

    fun onPointCloudMissionPointClicked(index: Int, x: Float, y: Float) {
        val workflow = activityViewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA
        val selectedIndex = if (
            missionEditorCoordinator.selectedWorkflow == workflow && missionEditorCoordinator.selectedIndex == index
        ) null else index
        if (workflow == PlanningWorkflow.POINTS) {
            layers.route.selectTerrainWaypoint(selectedIndex)
        } else {
            missionEditorCoordinator.select(PlanningWorkflow.AREA, selectedIndex, previewWorkflowController.mode)
        }
        if (selectedIndex != null) missionEditorCoordinator.positionNearScreenPoint(x, y)
    }

    fun resetPointCloudCamera() = previewWorkflowController.resetPointCloudCamera()

    // Operator actions: save / load forms

    fun setSaveFileName(name: String) {
        files.saveFileName = name
    }

    fun setSaveOverwrite(overwrite: Boolean) {
        files.saveOverwrite = overwrite
    }

    fun confirmSave() {
        missionFilesCoordinator.save(files.saveFileName, files.saveOverwrite)
    }

    fun cancelFiles() = missionFilesCoordinator.cancel()

    fun selectLoadFile(index: Int) {
        if (index in files.loadFiles.indices) files.loadSelectedIndex = index
    }

    fun confirmLoad() {
        missionFilesCoordinator.load(files.loadFiles.getOrNull(files.loadSelectedIndex))
    }

    // Operator actions: flight mode and arming

    fun onFlightModeCardClicked(controlEnabled: Boolean) {
        if (!controlEnabled) {
            messages.toast(R.string.flight_mode_unavailable)
            return
        }
        safety.armOpen = false
        safety.pendingConfirmation = null
        safety.flightModeOpen = !safety.flightModeOpen
    }

    fun selectFlightMode(mode: ArduCopterFlightMode, currentCustomMode: Int?) {
        safety.pendingConfirmation = null
        if (currentCustomMode == mode.customMode) return
        if (mode.requiresUploadedMission && droneViewModel.missionItems.value.isNullOrEmpty()) {
            messages.toast(R.string.flight_mode_auto_requires_mission)
            return
        }
        if (mode == ArduCopterFlightMode.AUTO || mode.holdDurationMs > 0L) {
            safety.pendingConfirmation = mode
            return
        }
        submitFlightMode(mode)
    }

    fun submitFlightMode(mode: ArduCopterFlightMode) {
        safety.pendingConfirmation = null
        when (droneViewModel.requestFlightMode(mode)) {
            FlightModeRequestResult.Sent -> dismissFlightMode()
            FlightModeRequestResult.Disconnected -> messages.toast(R.string.flight_mode_unavailable)
            FlightModeRequestResult.TargetUnavailable -> messages.toast(R.string.flight_mode_target_unavailable)
            FlightModeRequestResult.AlreadyPending -> messages.toast(R.string.flight_mode_request_pending)
            FlightModeRequestResult.TerrainNotReady -> messages.toast(R.string.terrain_auto_not_ready)
        }
    }

    fun dismissFlightMode() {
        safety.flightModeOpen = false
        safety.pendingConfirmation = null
    }

    fun onArmCardClicked() {
        when {
            droneViewModel.conStateLiveData.value != true -> messages.toast(R.string.arm_unavailable)
            droneViewModel.armedState.value == true -> messages.toast(R.string.arm_already_armed)
            else -> {
                safety.flightModeOpen = false
                safety.armOpen = !safety.armOpen
            }
        }
    }

    fun submitArm() {
        when (droneViewModel.requestArm()) {
            ArmRequestResult.SENT -> safety.armSubmitted = true
            ArmRequestResult.DISCONNECTED -> messages.toast(R.string.arm_unavailable)
            ArmRequestResult.TARGET_UNAVAILABLE -> messages.toast(R.string.arm_target_unavailable)
            ArmRequestResult.ALREADY_ARMED -> messages.toast(R.string.arm_already_armed)
            ArmRequestResult.ALREADY_PENDING -> messages.toast(R.string.arm_request_pending)
        }
    }

    fun dismissArm() {
        safety.armOpen = false
        safety.armSubmitted = false
    }

    // Map interactions (from the map surface)

    fun onMapTap(coordinate: com.example.droneservicesapp.ui.map.model.MapCoordinate) = layers.onMapTap(coordinate)

    // Internal behaviour, ported from the Fragment

    private fun startActiveGeometryDrawing() {
        if (activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS) {
            hideObstaclePanel()
            mapViewModel.setPlanningPanelVisible(false)
            clearPointMissionGeometry()
            activityViewModel.mapState.value = MainActivityViewModel.MapState.Draw
            messages.toast(R.string.tap_map_to_add_points)
        } else {
            startAreaDrawing()
        }
    }

    private fun onPlanningWorkflowChanged() {
        missionEditorCoordinator.cancelObstaclePlacement(render = false)
        stopMissionSimulation()
        missionEditorCoordinator.renderObstacleControls()
        updateRouteSummary()
        missionEditorCoordinator.updateRouteEnabled(homePlacementMode)
        missionEditorCoordinator.updateSurveyEnabled()
        liveGeoAwarenessController.updatePlanningStatus()
        missionSummaryPresenter.renderCard()
        missionMapRenderer.renderCoverage()
    }

    private fun observePreviewSettings() {
        presentationObserver.bindPreviewAssets(
            viewModel = previewAssetsViewModel,
            onSettings = previewWorkflowController::applySettings,
            onAssetVersion = previewWorkflowController::onAssetsChanged,
        )
        presentationObserver.bind(droneViewModel.terrainPreview) { previewWorkflowController.onTerrainPreviewChanged() }
    }

    private fun toggleObstaclePanel() {
        val show = !obstaclePanel.visible
        if (show) {
            mapViewModel.setPlanningPanelVisible(false)
            missionEditorCoordinator.renderObstacleControls()
        } else {
            missionEditorCoordinator.cancelObstaclePlacement()
        }
        obstaclePanel.visible = show
        dock.obstaclesSelected = show
    }

    private fun hideObstaclePanel() {
        obstaclePanel.visible = false
        dock.obstaclesSelected = false
        missionEditorCoordinator.cancelObstaclePlacement()
    }

    private fun cancelDroneOffsetAdjustment() {
        layers.aircraft.cancelDroneOffsetAdjustment()
        dock.offsetSelected = false
    }

    private fun startAreaDrawing() {
        hideObstaclePanel()
        mapViewModel.setPlanningPanelVisible(false)
        activityViewModel.setPlanningWorkflow(PlanningWorkflow.AREA)
        clearAreaMissionGeometry()
        activityViewModel.mapState.value = MainActivityViewModel.MapState.Draw
    }

    private fun clearActiveMissionGeometry() {
        when (activityViewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA) {
            PlanningWorkflow.AREA -> clearAreaMissionGeometry()
            PlanningWorkflow.POINTS -> clearPointMissionGeometry()
        }
    }

    private fun clearAreaMissionGeometry() {
        cancelPendingMissionPlanning()
        activityViewModel.clearPolygonVertices()
        activityViewModel.surveyPath.value = emptyList()
        activityViewModel.clearPointCloudMissionProfile()
        layers.polygon.clear()
        layers.clearSurveyPath()
    }

    private fun clearPointMissionGeometry() {
        pointRouteCoordinator.cancel()
        activityViewModel.clearRouteWaypoints()
        layers.route.clear()
    }

    private fun updateGeometryActionState() {
        val workflow = activityViewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA
        workflowPanel.hasGeometry = when (workflow) {
            PlanningWorkflow.AREA -> activityViewModel.missionArea.value?.vertices.orEmpty().isNotEmpty() ||
                activityViewModel.surveyPath.value.orEmpty().isNotEmpty()
            PlanningWorkflow.POINTS -> activityViewModel.routeWaypoints.value.orEmpty().isNotEmpty() ||
                activityViewModel.plannedRoutePath.value.orEmpty().isNotEmpty() ||
                activityViewModel.terrainRouteWaypoints.value.orEmpty().isNotEmpty()
        }
    }

    private fun updateRouteSummary() {
        workflowPanel.routeWaypointCount = activityViewModel.routeWaypoints.value.orEmpty().size
    }

    private fun currentMissionPath(): List<LatLng> = when (activityViewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA) {
        PlanningWorkflow.AREA -> activityViewModel.surveyPath.value.orEmpty()
        PlanningWorkflow.POINTS -> activityViewModel.terrainRouteWaypoints.value.orEmpty()
            .takeIf { it.size >= 2 }
            ?.map { LatLng(it.latLon.lat, it.latLon.lon) }
            ?: activityViewModel.plannedRoutePath.value.orEmpty().takeIf { it.size >= 2 }
            ?: activityViewModel.routeWaypoints.value.orEmpty().map { LatLng(it.latitude, it.longitude) }
    }

    private fun buildMissionResourcePlan(
        path: List<LatLng>,
        home: LatLon? = activityViewModel.plannedHomePosition.value,
    ): MissionResourcePlan = missionPlanningCoordinator.buildResourcePlan(
        path = path.map { LatLon(it.latitude, it.longitude) },
        home = home,
        operationMode = activityViewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY,
        speedMetersPerSecond = (activityViewModel.flightSpeed.value ?: 5.0).coerceAtLeast(0.1),
        sprayRateLitersPerMinute = activityViewModel.sprayFlowLitersPerMinute(),
    )

    private fun updateSimulationButton(hasPath: Boolean) {
        workflowPanel.simulationEnabled = hasPath
        workflowPanel.simulationState = if (::missionSimulationController.isInitialized) {
            missionSimulationController.state
        } else {
            MissionSimulationController.State.IDLE
        }
    }

    private fun startMissionSimulation() {
        val connected = droneViewModel.conStateLiveData.value == true
        if (connected && droneViewModel.armedState.value == true) {
            messages.toast(R.string.simulation_requires_landed_disarmed)
            return
        }
        if (connected && droneViewModel.droneLandedState.value != MavLandedState.MAV_LANDED_STATE_ON_GROUND) {
            val landedState = droneViewModel.droneLandedState.value
            messages.toast(
                if (landedState == null || landedState == MavLandedState.MAV_LANDED_STATE_UNDEFINED) {
                    R.string.simulation_landed_state_unknown
                } else {
                    R.string.simulation_requires_landed_disarmed
                },
            )
            return
        }
        val home = if (connected) latestRealDronePosition else activityViewModel.plannedHomePosition.value
        if (home == null || !DroneMapTrackingController.isValidPosition(home)) {
            messages.toast(if (connected) R.string.drone_gps_not_available_yet else R.string.simulation_requires_home)
            return
        }
        val missionPath = pointRouteCoordinator.orderForHome(currentMissionPath(), home)
        if (missionPath.size < 2) {
            messages.toast(R.string.simulation_requires_path)
            return
        }
        previewWorkflowController.selectMode(PreviewMode.MAP)
        missionSimulationController.start(missionPath, home, useLiveDroneHome = connected)
    }

    private fun stopMissionSimulation() {
        if (::missionSimulationController.isInitialized) missionSimulationController.stop()
        updateSimulationButton(currentMissionPath().size >= 2)
    }

    private fun stopMissionSimulationIfActive() {
        if (::missionSimulationController.isInitialized) missionSimulationController.stopIfActive()
    }

    private fun updateRouteDistance(waypoints: List<com.example.droneservicesapp.domain.model.RouteWaypoint>) {
        if (waypoints.size < 2) {
            activityViewModel.flightDistance.postValue(0)
            return
        }
        val routePath = activityViewModel.plannedRoutePath.value.orEmpty().takeIf { it.size >= 2 }
            ?: waypoints.map { LatLng(it.latitude, it.longitude) }
        activityViewModel.flightDistance.postValue(missionPathTotalDistance(routePath).toInt())
    }

    private fun updateCurrentFlightDistance() {
        when (activityViewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA) {
            PlanningWorkflow.AREA -> updateFlightDistance(activityViewModel.surveyPath.value.orEmpty())
            PlanningWorkflow.POINTS -> updateRouteDistance(activityViewModel.routeWaypoints.value.orEmpty())
        }
    }

    private fun observeDroneViewModel() {
        missionObserverCoordinator.observe(droneViewModel.droneLocationLiveData) { droneLocation ->
            syncLatestDroneLocationSnapshot(droneLocation)
            val rawPosition = latestRawDronePosition
            val correctedPosition = latestRealDronePosition
            if (rawPosition != null && correctedPosition != null) {
                layers.aircraft.updateDronePosition(rawPosition.lat, rawPosition.lon)
                droneMapTrackingController.capturePendingHome(correctedPosition)
                maybeAppendFlightTrace(correctedPosition)
                initialViewportController.centerIfNeeded()
            } else {
                layers.aircraft.setDroneVisible(false)
                initialViewportController.centerIfNeeded()
            }
            liveGeoAwarenessController.updateLiveFromActiveSource()
            previewWorkflowController.renderMissionOverlay()
        }

        missionObserverCoordinator.observe(droneViewModel.conStateLiveData) {
            if (it != true && missionSimulationController.usesLiveDroneHome) stopMissionSimulationIfActive()
            syncLatestDroneLocationSnapshot(droneViewModel.droneLocationLiveData.value)
            renderAddHomeButton()
            liveGeoAwarenessController.updateLiveFromActiveSource()
            previewWorkflowController.renderMissionOverlay()
        }

        missionObserverCoordinator.observe(droneViewModel.gpsFixType) {
            syncLatestDroneLocationSnapshot(droneViewModel.droneLocationLiveData.value)
            liveGeoAwarenessController.updateLiveFromActiveSource()
        }

        missionObserverCoordinator.observe(droneViewModel.droneHeading) { droneHeading ->
            droneHeading?.let { heading ->
                sessionState.headingDegrees = heading
                layers.aircraft.updateHeadingDegrees(heading.toFloat())
            }
            previewWorkflowController.renderMissionOverlay()
        }

        missionObserverCoordinator.observe(droneViewModel.droneGroundSpeedMetersPerSecond) { speed ->
            sessionState.groundSpeedMetersPerSecond = speed
        }

        missionObserverCoordinator.observe(droneViewModel.droneVerticalSpeedMetersPerSecond) { speed ->
            sessionState.verticalSpeedMetersPerSecond = speed
        }

        missionObserverCoordinator.observe(droneViewModel.droneAltitudeAmslMeters) { altitudeAmslMeters ->
            sessionState.droneAltitudeAmslMeters = altitudeAmslMeters
            liveGeoAwarenessController.updateLiveFromActiveSource()
        }

        missionObserverCoordinator.observe(droneViewModel.armedState) { armed ->
            if (armed == true) stopMissionSimulationIfActive()
            droneMapTrackingController.handleArmedState(armed == true)
            activityViewModel.onServiceMissionArmedStateChanged(armed == true)
            renderServiceMissionResumeButton()
        }

        missionObserverCoordinator.observe(droneViewModel.droneLandedState) { landedState ->
            if (missionSimulationController.usesLiveDroneHome && landedState != MavLandedState.MAV_LANDED_STATE_ON_GROUND) {
                stopMissionSimulationIfActive()
            }
        }

        missionObserverCoordinator.observe(activityViewModel.serviceMissionState) {
            renderServiceMissionResumeButton()
        }

        missionObserverCoordinator.observe(activityViewModel.missionArea) { missionArea ->
            stopMissionSimulationIfActive()
            val vertices = missionArea?.vertices?.toList().orEmpty()
            val hasRoute = activityViewModel.routeWaypoints.value.orEmpty().size >= 2
            mapViewModel.setMissionAreaAvailable(vertices.size >= 3 || hasRoute)
            missionEditorCoordinator.syncPolygonVertices(vertices)
            liveGeoAwarenessController.updatePlanningStatus()
            missionSummaryPresenter.renderCard()
            previewWorkflowController.renderMissionOverlay()
        }

        missionObserverCoordinator.observe(activityViewModel.plannedHomePosition) { position ->
            droneMapTrackingController.renderHome(position)
            stopMissionSimulation()
            if (
                position != null &&
                activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA &&
                activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams
            ) {
                missionGenerationCoordinator.redrawIfEditable()
            } else if (position != null && activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS) {
                pointRouteCoordinator.generate()
                missionEditorCoordinator.syncRouteWaypoints()
            }
            updateCurrentFlightDistance()
            missionSummaryPresenter.renderCard()
        }

        missionObserverCoordinator.observe(activityViewModel.missionObstacles) { obstacles ->
            stopMissionSimulationIfActive()
            missionEditorCoordinator.onObstaclesChanged(obstacles.orEmpty())
            if (
                activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams &&
                activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA &&
                (activityViewModel.missionArea.value?.vertices?.size ?: 0) >= 3
            ) {
                missionGenerationCoordinator.scheduleAreaRedraw(immediate = true)
            } else if (activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS) {
                pointRouteCoordinator.generate()
            }
        }

        missionObserverCoordinator.observe(droneViewModel.missionItems) { missionItems ->
            previewAssetsViewModel.retainDroneMission(missionItems.orEmpty())
            missionMapRenderer.renderDownloaded(missionItems.orEmpty())
            previewWorkflowController.renderMissionOverlay()
        }

        missionObserverCoordinator.observe(droneViewModel.missionDownloadProgressPercent) { progress ->
            val percent = progress ?: -1
            missionDownload.percent = if (percent >= 0) percent.coerceIn(0, 100) else null
        }

        missionObserverCoordinator.observe(droneViewModel.rtkForwardingState) { state ->
            val streaming = state is RtkForwardingState.Streaming
            if (sessionState.lastRtkStreamingActive == streaming) return@observe
            if (sessionState.lastRtkStreamingActive != null) {
                if (streaming) RtkTonePlayer.playConnectedTone() else RtkTonePlayer.playDisconnectedTone()
            }
            sessionState.lastRtkStreamingActive = streaming
            geoEventLogger.logSimple(
                type = if (streaming) GeoAwarenessEventType.RTK_CONNECTED else GeoAwarenessEventType.RTK_DISCONNECTED,
                severity = if (streaming) "INFO" else "WARNING",
                message = if (streaming) "RTK streaming active" else "RTK streaming inactive",
                category = "RTK",
                connectionState = if (streaming) "CONNECTED" else "DISCONNECTED",
                details = mapOf("state" to (state?.javaClass?.simpleName ?: "Unknown")),
            )
        }
    }

    private fun observeMapState() {
        missionObserverCoordinator.bindParameterObservers(
            MissionMapObserverCoordinator.Actions(
                stopSimulation = ::stopMissionSimulationIfActive,
                scheduleAreaRedraw = { missionGenerationCoordinator.scheduleAreaRedraw() },
                flightAltitudeChanged = ::onFlightAltitudeChanged,
                surveyHeightChanged = missionEditorCoordinator::refreshHeightLabel,
                surveyGridChanged = ::onSurveyGridParametersChanged,
                planningModeChanged = ::onPlanningOperationModeChanged,
                updateTerrainOverlay = missionMapRenderer::renderCoverage,
                updateMissionSummary = missionSummaryPresenter::renderCard,
                renderPreview = previewWorkflowController::render,
            ),
        )
        missionObserverCoordinator.observe(activityViewModel.surveyPath) { surveyPath ->
            stopMissionSimulationIfActive()
            val hasPolygon = (activityViewModel.missionArea.value?.vertices?.size ?: 0) >= 3
            val hasRoute = activityViewModel.routeWaypoints.value.orEmpty().size >= 2
            mapViewModel.setMissionAreaAvailable(hasPolygon || hasRoute || !surveyPath.isNullOrEmpty())
            missionEditorCoordinator.updateSurveyEnabled()
            liveGeoAwarenessController.schedulePlanningStatusUpdate()
            missionSummaryPresenter.renderCard()
            previewWorkflowController.renderMissionOverlay()
            missionMapRenderer.renderCoverage()
        }

        missionObserverCoordinator.observe(activityViewModel.terrainSurveyWaypoints) {
            stopMissionSimulationIfActive()
            previewWorkflowController.renderMissionOverlay()
        }

        missionObserverCoordinator.observe(activityViewModel.routeWaypoints) { waypoints ->
            stopMissionSimulationIfActive()
            missionEditorCoordinator.syncRouteWaypoints()
            val hasPolygon = (activityViewModel.missionArea.value?.vertices?.size ?: 0) >= 3
            val hasSurveyPath = !activityViewModel.surveyPath.value.isNullOrEmpty()
            mapViewModel.setMissionAreaAvailable(hasPolygon || hasSurveyPath || waypoints.orEmpty().size >= 2)
            if (activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS) {
                updateRouteDistance(waypoints.orEmpty())
            }
            updateRouteSummary()
            missionSummaryPresenter.renderCard()
            liveGeoAwarenessController.updatePlanningStatus()
            previewWorkflowController.renderMissionOverlay()
            missionEditorCoordinator.updateSurveyEnabled()
            pointRouteCoordinator.generate()
            missionMapRenderer.renderCoverage()
        }

        missionObserverCoordinator.observe(activityViewModel.terrainRouteWaypoints) {
            stopMissionSimulationIfActive()
            missionEditorCoordinator.syncRouteWaypoints()
            missionSummaryPresenter.renderCard()
            previewWorkflowController.renderMissionOverlay()
            missionMapRenderer.renderCoverage()
        }

        missionObserverCoordinator.observe(activityViewModel.plannedRoutePath) {
            stopMissionSimulationIfActive()
            missionEditorCoordinator.syncRouteWaypoints()
            missionSummaryPresenter.renderCard()
            previewWorkflowController.renderMissionOverlay()
            missionMapRenderer.renderCoverage()
        }

        missionObserverCoordinator.observe(activityViewModel.mapState) { mapState ->
            mapViewModel.updateFromMapState(mapState)
            missionEditorCoordinator.renderObstacleControls()
            updateRouteSummary()
            updateGeometryActionState()
            missionEditorCoordinator.updateRouteEnabled(homePlacementMode)
            missionEditorCoordinator.updateSurveyEnabled()
            if (mapState == MainActivityViewModel.MapState.SetFlightParams) {
                hideObstaclePanel()
            }
            if (
                mapState == MainActivityViewModel.MapState.SetFlightParams &&
                activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA
            ) {
                val hasPolygon = (activityViewModel.missionArea.value?.vertices?.size ?: 0) >= 3
                if (hasPolygon) missionGenerationCoordinator.scheduleAreaRedraw(immediate = true)
            }
            missionSummaryPresenter.renderCard()
        }

        missionObserverCoordinator.observe(activityViewModel.mapAction) { event ->
            val action = event?.getContentIfNotHandled() ?: return@observe
            missionMapActionHandler.handle(action)
        }

        missionObserverCoordinator.observe(activityViewModel.pointCloudCoverage) {
            if (paramsBound) enforceSprayAltitudeReference()
        }
    }

    private fun onFlightAltitudeChanged(altitude: Double) {
        val points = activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS
        if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams && points) {
            savePreference(context.getString(R.string.survey_altitude_pref), altitude.toInt().toString())
        }
        if (points) {
            liveGeoAwarenessController.updatePlanningStatus()
            missionSummaryPresenter.renderCard()
        }
        if (activityViewModel.planningOperationMode.value == PlanningOperationMode.SPRAY) {
            missionGenerationCoordinator.redrawIfEditable(debounced = true)
        }
    }

    private fun onSurveyGridParametersChanged() {
        if (activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS) {
            pointRouteCoordinator.generate()
        } else {
            missionGenerationCoordinator.redrawIfEditable(debounced = true)
        }
    }

    private fun onPlanningOperationModeChanged() {
        if (paramsBound) enforceSprayAltitudeReference()
        if (
            activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams &&
            activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA &&
            (activityViewModel.missionArea.value?.vertices?.size ?: 0) >= 3
        ) missionGenerationCoordinator.scheduleAreaRedraw(immediate = true)
    }

    private fun observeMissionMapViewModel() {
        presentationObserver.bindMapUi(mapViewModel, ::renderHomeMapUiState)
    }

    private fun renderHomeMapUiState(state: HomeMapUiState) {
        liveGeoAwarenessController.setZoneDetailsEnabled(!state.interactionState.isDrawingEnabled)
        missionEditorCoordinator.updatePolygonEnabled(state.interactionState.isDrawingEnabled, homePlacementMode)
        missionEditorCoordinator.updateRouteEnabled(homePlacementMode)
        activityViewModel.setShellBottomActionBarVisible(state.interactionState.isLegacyBottomActionBarVisible)
        renderModeEffects(state.screenMode)
        renderAddHomeButton()
    }

    /** One-shot reactions to entering a screen mode (formerly `HomeMapModeEffectsBinder`). */
    private fun renderModeEffects(screenMode: HomeMapScreenMode) {
        if (lastScreenMode == screenMode) return
        lastScreenMode = screenMode
        files.saveVisible = screenMode == HomeMapScreenMode.SavingMission
        files.loadVisible = screenMode == HomeMapScreenMode.LoadingMission
        when (screenMode) {
            HomeMapScreenMode.Idle -> {
                missionMapRenderer.renderDownloaded(droneViewModel.missionItems.value.orEmpty(), force = true)
                if (droneViewModel.conStateLiveData.value == true) droneViewModel.downloadMissionNew()
            }
            HomeMapScreenMode.Drawing -> Unit
            HomeMapScreenMode.EditingParams -> bindMissionParams()
            HomeMapScreenMode.SavingMission -> {
                files.saveFileName = ""
                files.saveOverwrite = false
            }
            HomeMapScreenMode.LoadingMission -> {
                val available = missionFilesCoordinator.listMissionFiles()
                if (available.isEmpty()) {
                    files.loadVisible = false
                    messages.toast(R.string.no_saved_missions_yet, long = true)
                    activityViewModel.mapState.value = MainActivityViewModel.MapState.Idle
                } else {
                    files.loadFiles = available
                    files.loadSelectedIndex = -1
                }
            }
        }
    }

    /** First time the flight-parameter panel opens in this session, load saved defaults. */
    private fun bindMissionParams() {
        if (paramsBound) return
        preferencesBridge.loadIntoViewModel()
        paramsBound = true
        lastParamsLayoutSignature = null
        enforceSprayAltitudeReference()
    }

    /**
     * Spray missions fly relative to a validated point-cloud profile when one covers the area, and
     * terrain-following otherwise. The operator cannot change this for spray, so the panel layout
     * change applies it, exactly as the former parameter renderer did.
     */
    private fun enforceSprayAltitudeReference() {
        val survey = (activityViewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY) ==
            PlanningOperationMode.SURVEY
        val pointRoute = activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS
        val threeDimensionalSpray = !survey &&
            (activityViewModel.pointCloudCoverage.value ?: PointCloudCoverage.NONE) == PointCloudCoverage.COMPLETE
        val signature = Triple(survey, pointRoute, threeDimensionalSpray)
        if (signature == lastParamsLayoutSignature) return
        lastParamsLayoutSignature = signature
        if (!survey) {
            val required = if (threeDimensionalSpray) AltitudeReferenceMode.RELATIVE else AltitudeReferenceMode.TERRAIN
            if (activityViewModel.altitudeReferenceMode.value != required) {
                activityViewModel.setAltitudeReferenceMode(required)
            }
        }
    }

    private fun renderAddHomeButton() {
        val connected = droneViewModel.conStateLiveData.value == true
        if (connected) cancelPlannedHomePlacement()
        workflowPanel.addHomeVisible = !connected
    }

    private fun renderServiceMissionResumeButton() {
        val waiting = activityViewModel.serviceMissionState.value == ServiceMissionState.WAITING_FOR_SERVICE
        workflowPanel.resumeVisible = waiting
        if (!waiting) {
            workflowPanel.resumeLabel = null
            return
        }
        val stop = activityViewModel.currentServiceMissionLeg()?.serviceAfter
        workflowPanel.resumeLabel = if (stop != null) {
            context.getString(
                R.string.resume_mission_service_format,
                context.getString(MissionSimulationController.serviceDescriptionResource(stop)),
            )
        } else {
            context.getString(R.string.resume_mission)
        }
    }

    private fun observeSafetyCommandResults() {
        missionObserverCoordinator.observe(droneViewModel.flightModeCommandState) { state ->
            if (state === lastPresentedFlightModeResult) return@observe
            lastPresentedFlightModeResult = state
            when (state) {
                is FlightModeCommandState.Succeeded -> {
                    messages.toast(context.getString(R.string.flight_mode_applied, flightModeLabel(state.mode)))
                    droneViewModel.clearFlightModeCommandResult()
                }
                is FlightModeCommandState.Failed -> {
                    messages.toast(
                        context.getString(R.string.flight_mode_failed, flightModeLabel(state.requestedMode), state.reason),
                        long = true,
                    )
                    droneViewModel.clearFlightModeCommandResult()
                }
                else -> Unit
            }
        }
        missionObserverCoordinator.observe(droneViewModel.armCommandState) { state ->
            if (state == lastPresentedArmState) return@observe
            lastPresentedArmState = state
            when (state) {
                ArmCommandState.Succeeded -> {
                    messages.toast(R.string.arm_succeeded)
                    dismissArm()
                    droneViewModel.clearArmCommandResult()
                }
                is ArmCommandState.Failed -> {
                    messages.toast(context.getString(R.string.arm_failed, state.reason), long = true)
                    safety.armSubmitted = false
                    droneViewModel.clearArmCommandResult()
                }
                else -> Unit
            }
        }
    }

    private fun flightModeLabel(mode: ArduCopterFlightMode): String = context.getString(flightModeLabelRes(mode))

    private fun cancelPendingMissionPlanning() {
        missionGenerationCoordinator.cancel()
        terrainMissionCoordinator.cancel()
    }

    private fun isSurveyMode(): Boolean =
        (activityViewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY) == PlanningOperationMode.SURVEY

    private fun renderSurveyPath(
        pathLatLon: List<LatLon>,
        areaVertices: List<LatLng>,
        terrainWaypoints: List<TerrainWaypoint> = emptyList(),
    ) {
        val ordered = missionGenerationCoordinator.orderForPublishing(pathLatLon, terrainWaypoints)
        missionMapRenderer.publishGeneratedPath(ordered.points, areaVertices, ordered.terrainWaypoints)
    }

    private fun updateFlightDistance(path: List<LatLng>) {
        activityViewModel.flightDistance.postValue(missionPathTotalDistance(path).toInt())
    }

    private fun missionPathTotalDistance(path: List<LatLng>): Double {
        val homePosition = activityViewModel.plannedHomePosition.value
            ?.takeIf(DroneMapTrackingController::isValidPosition)
        return missionPlanningCoordinator.totalDistance(
            path = path.map { LatLon(it.latitude, it.longitude) },
            home = homePosition,
        )
    }

    private fun syncLatestDroneLocationSnapshot(droneLocation: Location?) {
        val usableLocation = droneLocation?.takeIf(::isUsableDroneLocation)
        droneMapTrackingController.syncLocation(usableLocation)
        sessionState.updateLocation(usableLocation)
    }

    private fun currentOffsetDroneLocation(): Location? {
        val source = droneViewModel.droneLocationLiveData.value?.takeIf(::isUsableDroneLocation) ?: return null
        return droneMapTrackingController.offsetLocation(source)
    }

    private fun startPlannedHomePlacement() {
        if (droneViewModel.conStateLiveData.value == true) {
            messages.toast(R.string.planned_home_requires_disconnected)
            renderAddHomeButton()
            return
        }
        plannedHomePlacementController.start()
        messages.toast(R.string.tap_map_to_place_home)
    }

    private fun placePlannedHome(plannedHomePosition: LatLon) {
        if (!DroneMapTrackingController.isValidPosition(plannedHomePosition)) return
        activityViewModel.setPlannedHomePosition(plannedHomePosition)
        messages.toast(R.string.planned_home_set)
        Log.d(MAP_FLIGHT_TRACE_TAG, "planned home marker set lat=${plannedHomePosition.lat} lon=${plannedHomePosition.lon}")
        mapViewModel.homeMapUiState.value?.let(::renderHomeMapUiState)
    }

    private fun cancelPlannedHomePlacement() {
        if (::plannedHomePlacementController.isInitialized) plannedHomePlacementController.cancel()
    }

    private fun maybeAppendFlightTrace(position: LatLon) {
        droneMapTrackingController.appendTraceIfFlying(
            position = position,
            connected = droneViewModel.conStateLiveData.value == true,
            armed = droneViewModel.armedState.value == true,
        )
    }

    private fun observeGeoAwarenessSharedState() {
        presentationObserver.bindGeoState(
            layerVisible = activityViewModel.geoAwarenessLayerVisible,
            reloadToken = activityViewModel.geoZoneReloadToken,
            renderLayer = liveGeoAwarenessController::renderLayerIfVisible,
            reload = liveGeoAwarenessController::reloadCurrentDataset,
        )
    }

    private fun isUsableDroneLocation(location: Location): Boolean {
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return false
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return false
        return kotlin.math.abs(location.latitude) > MIN_VALID_ABS_COORDINATE ||
            kotlin.math.abs(location.longitude) > MIN_VALID_ABS_COORDINATE
    }

    private fun savePreference(key: String, value: String) {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .putString(key, value)
            .apply()
    }

    private fun persistMissionEditingPreferences() {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .putString(context.getString(R.string.survey_angle_pref), (activityViewModel.angleProgress.value?.toInt() ?: 90).toString())
            .putString(context.getString(R.string.survey_line_distance_pref), (activityViewModel.lineDistanceProgress.value?.toInt() ?: 5).toString())
            .putString(context.getString(R.string.survey_altitude_pref), (activityViewModel.flightAltProgress.value?.toInt() ?: 0).toString())
            .putString(context.getString(R.string.survey_strip_spacing_pref), (activityViewModel.surveyStripSpacing.value?.toInt() ?: 70).toString())
            .putString(context.getString(R.string.survey_height_above_terrain_pref), (activityViewModel.surveyHeightAboveTerrain.value?.toInt() ?: 50).toString())
            .putString(context.getString(R.string.survey_overlap_pref), (activityViewModel.surveyOverlapPercent.value?.toInt() ?: 80).toString())
            .putString(context.getString(R.string.survey_grid_angle_pref), (activityViewModel.surveyGridAngle.value?.toInt() ?: 90).toString())
            .putString(context.getString(R.string.survey_terrain_segment_pref), (activityViewModel.surveyTerrainSegment.value ?: 2.5).toString())
            .putString(context.getString(R.string.survey_canopy_smoothing_pref), (activityViewModel.surveyCanopySmoothing.value?.toInt() ?: 5).toString())
            .apply()
    }

    companion object {
        private const val OFFLINE_MIN_ZOOM = 14
        private const val OFFLINE_MAX_ZOOM = 18

        /**
         * Largest offline download, in imagery tiles across zoom 14–18. At roughly 20 KB per
         * satellite tile this stays well inside the 800 MB the tile cache trims back to.
         */
        internal const val OFFLINE_MAX_TILES = 20_000L
        private const val MAP_FLIGHT_TRACE_TAG = "MapFlightTrace"
        private const val MIN_VALID_ABS_COORDINATE = 1e-4
        private const val MAX_INITIAL_DRONE_CENTER_ATTEMPTS = 20

        fun flightModeLabelRes(mode: ArduCopterFlightMode): Int = when (mode) {
            ArduCopterFlightMode.AUTO -> R.string.flight_mode_auto
            ArduCopterFlightMode.GUIDED -> R.string.flight_mode_guided
            ArduCopterFlightMode.LOITER -> R.string.flight_mode_loiter
            ArduCopterFlightMode.RTL -> R.string.flight_mode_rtl
            ArduCopterFlightMode.LAND -> R.string.flight_mode_land
            ArduCopterFlightMode.BRAKE -> R.string.flight_mode_brake
        }
    }
}
