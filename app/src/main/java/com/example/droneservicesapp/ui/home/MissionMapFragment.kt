package com.example.droneservicesapp.ui.home

import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.drawerlayout.widget.DrawerLayout
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.geoawareness.incident.GeoIncidentEncryptedLogStore
import com.example.droneservicesapp.data.geoawareness.incident.GeoIncidentLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.data.geoawareness.logging.OperatorFlightEventLogger
import com.example.droneservicesapp.data.diagnostics.DiagnosticLog
import com.example.droneservicesapp.data.rtk.RtkForwardingState
import com.example.droneservicesapp.data.storage.MissionFileStore
import com.example.droneservicesapp.data.weather.OpenMeteoWindRepository
import com.example.droneservicesapp.databinding.FragmentHomeMapsBinding
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.planning.MissionResourcePlan
import com.example.droneservicesapp.domain.planning.MissionPlanningCoordinator
import com.example.droneservicesapp.domain.planning.PointRouteCoordinator
import com.example.droneservicesapp.domain.planning.MissionServiceStop
import com.example.droneservicesapp.domain.terrain.TerrainWaypoint
import com.example.droneservicesapp.domain.terrain.TerrainPathFailure
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.mavserver.TelemetryMapping
import com.example.droneservicesapp.ui.home.binders.HomeMapChromeBinder
import com.example.droneservicesapp.ui.home.binders.HomeMapModeEffectsBinder
import com.example.droneservicesapp.ui.home.binders.HomeMapPanelsBinder
import com.example.droneservicesapp.ui.home.binders.HomeMapTelemetryBinder
import com.example.droneservicesapp.ui.home.binders.FlightModeUiBinder
import com.example.droneservicesapp.ui.home.binders.ArmUiBinder
import com.example.droneservicesapp.ui.home.binders.MissionLoadController
import com.example.droneservicesapp.ui.home.binders.MissionParamsController
import com.example.droneservicesapp.ui.home.binders.MissionSaveController
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel.ServiceMissionState
import com.example.droneservicesapp.ui.home.components.EsriMapLayers
import com.example.droneservicesapp.ui.home.components.OsmdroidMapController
import com.example.droneservicesapp.ui.home.components.MissionSimulationController
import com.example.droneservicesapp.ui.home.components.MissionMapUiActionController
import com.example.droneservicesapp.ui.home.components.MissionMapObserverCoordinator
import com.example.droneservicesapp.ui.home.components.MissionMapActionHandler
import com.example.droneservicesapp.ui.home.components.MissionPresentationObserver
import com.example.droneservicesapp.ui.home.components.MissionGenerationCoordinator
import com.example.droneservicesapp.ui.home.components.TerrainMissionCoordinator
import com.example.droneservicesapp.ui.home.components.TerrainPreviewCoordinator
import com.example.droneservicesapp.ui.home.components.PreviewMapFocusController
import com.example.droneservicesapp.ui.home.components.PreviewWorkflowController
import com.example.droneservicesapp.ui.home.components.MissionSummaryPresenter
import com.example.droneservicesapp.ui.home.components.MissionMapRenderer
import com.example.droneservicesapp.ui.home.components.MissionEditorCoordinator
import com.example.droneservicesapp.ui.home.components.PlanningWorkflowUiController
import com.example.droneservicesapp.ui.home.components.DroneMapTrackingController
import com.example.droneservicesapp.ui.home.components.PlannedHomePlacementController
import com.example.droneservicesapp.ui.home.components.WindWeatherController
import com.example.droneservicesapp.ui.home.components.HomeMapViewLifecycleController
import com.example.droneservicesapp.ui.home.components.InitialMapViewportController
import com.example.droneservicesapp.ui.home.components.OsmdroidObstacleEditor
import com.example.droneservicesapp.ui.home.components.OsmdroidPolygonEditor
import com.example.droneservicesapp.ui.home.components.OsmdroidRouteWaypointEditor
import com.example.droneservicesapp.ui.home.components.TerrainCoverageOverlayController
import com.example.droneservicesapp.ui.home.geoawareness.GeoZoneOverlayController
import com.example.droneservicesapp.ui.home.geoawareness.GeoAwarenessDatasetController
import com.example.droneservicesapp.ui.home.geoawareness.GeoAwarenessPlanningController
import com.example.droneservicesapp.ui.home.geoawareness.GeoAuthorizationSession
import com.example.droneservicesapp.ui.home.geoawareness.GeoAwarenessEventTracker
import com.example.droneservicesapp.ui.home.geoawareness.GeoAwarenessDialogController
import com.example.droneservicesapp.ui.home.geoawareness.GeoAwarenessUploadController
import com.example.droneservicesapp.ui.home.geoawareness.LiveGeoAwarenessController
import com.example.droneservicesapp.ui.home.model.HomeTelemetryViewModel
import com.example.droneservicesapp.ui.home.model.HomeMapUiState
import com.example.droneservicesapp.ui.home.model.MissionMapViewModel
import com.example.droneservicesapp.ui.preview.OrthoOverlayController
import com.example.droneservicesapp.ui.preview.PreviewMode
import com.example.droneservicesapp.ui.preview.PreviewModeRenderer
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.preview.PreviewAssetStore
import com.example.droneservicesapp.ui.preview.PreviewAssetLoader
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.example.droneservicesapp.ui.common.RtkTonePlayer
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import io.dronefleet.mavlink.common.MavLandedState
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class MissionMapFragment : Fragment() {
    private var _binding: FragmentHomeMapsBinding? = null
    private val binding get() = _binding!!
    private lateinit var mapView: MapView

    private lateinit var droneViewModel: DroneViewModel
    private lateinit var activityViewModel: MainActivityViewModel
    private val previewAssetsViewModel: PreviewAssetsViewModel by activityViewModels()
    private lateinit var homeTelemetryViewModel: HomeTelemetryViewModel
    private lateinit var mapViewModel: MissionMapViewModel

    private lateinit var missionParamsController: MissionParamsController
    private lateinit var missionSaveController: MissionSaveController
    private lateinit var missionLoadController: MissionLoadController
    private val missionPlanningCoordinator = MissionPlanningCoordinator()
    private lateinit var geoEventLogger: GeoAwarenessEventLogger
    private lateinit var operatorEventLogger: OperatorFlightEventLogger
    private lateinit var geoAwarenessEventTracker: GeoAwarenessEventTracker
    private lateinit var geoAwarenessDialogController: GeoAwarenessDialogController
    private lateinit var geoAwarenessDatasetController: GeoAwarenessDatasetController
    private val geoAwarenessPlanningController = GeoAwarenessPlanningController()
    private val geoAuthorizationSession = GeoAuthorizationSession()
    private lateinit var geoAwarenessUploadController: GeoAwarenessUploadController
    private lateinit var liveGeoAwarenessController: LiveGeoAwarenessController
    private lateinit var homeMapChromeBinder: HomeMapChromeBinder
    private lateinit var homeMapPanelsBinder: HomeMapPanelsBinder
    private lateinit var homeMapModeEffectsBinder: HomeMapModeEffectsBinder
    private lateinit var homeMapTelemetryBinder: HomeMapTelemetryBinder
    private lateinit var flightModeUiBinder: FlightModeUiBinder
    private lateinit var armUiBinder: ArmUiBinder
    private lateinit var osmdroidMapController: OsmdroidMapController
    private lateinit var droneMapTrackingController: DroneMapTrackingController
    private lateinit var plannedHomePlacementController: PlannedHomePlacementController
    private lateinit var windWeatherController: WindWeatherController
    private lateinit var viewLifecycleController: HomeMapViewLifecycleController
    private lateinit var initialViewportController: InitialMapViewportController
    private lateinit var esriMapLayers: EsriMapLayers
    private lateinit var osmdroidObstacleEditor: OsmdroidObstacleEditor
    private lateinit var osmdroidPolygonEditor: OsmdroidPolygonEditor
    private lateinit var osmdroidRouteWaypointEditor: OsmdroidRouteWaypointEditor
    private lateinit var missionFileStore: MissionFileStore
    private val windWeatherRepository = OpenMeteoWindRepository()
    private lateinit var geoZoneOverlayController: GeoZoneOverlayController
    private var terrainCoverageOverlayController: TerrainCoverageOverlayController? = null
    private val latestRawDronePosition: LatLon?
        get() = droneMapTrackingController.rawPosition
    private val latestRealDronePosition: LatLon?
        get() = droneMapTrackingController.correctedPosition
    private var latestRealDroneAltitudeMeters: Double? = null
    private var latestRealDroneAltitudeAmslMeters: Double? = null
    private var latestRealDroneHorizontalAccuracyMeters: Float? = null
    private var latestRealDroneVerticalAccuracyMeters: Float? = null
    private var latestRealDroneGroundSpeedMetersPerSecond: Float? = null
    private var latestRealDroneVerticalSpeedMetersPerSecond: Float? = null
    private var latestRealDroneHeadingDegrees: Double? = null
    private var lastRtkStreamingActive: Boolean? = null
    private var isDrawingModeActive = false
    private lateinit var orthoOverlayController: OrthoOverlayController
    private lateinit var previewModeRenderer: PreviewModeRenderer
    private lateinit var previewMapFocusController: PreviewMapFocusController
    private lateinit var terrainPreviewCoordinator: TerrainPreviewCoordinator
    private lateinit var previewWorkflowController: PreviewWorkflowController
    private val selectedSurveyWaypointIndex: Int?
        get() = missionEditorCoordinator.selectedIndex
    private val selectedTerrainWaypointWorkflow: PlanningWorkflow
        get() = missionEditorCoordinator.selectedWorkflow
    private val homePlacementMode: Boolean
        get() = ::plannedHomePlacementController.isInitialized && plannedHomePlacementController.isActive
    private lateinit var missionSimulationController: MissionSimulationController
    private lateinit var missionGenerationCoordinator: MissionGenerationCoordinator
    private lateinit var terrainMissionCoordinator: TerrainMissionCoordinator
    private lateinit var pointRouteCoordinator: PointRouteCoordinator
    private lateinit var missionSummaryPresenter: MissionSummaryPresenter
    private lateinit var missionMapRenderer: MissionMapRenderer
    private lateinit var missionEditorCoordinator: MissionEditorCoordinator
    private lateinit var missionMapUiActionController: MissionMapUiActionController
    private lateinit var planningWorkflowUiController: PlanningWorkflowUiController
    private lateinit var missionMapObserverCoordinator: MissionMapObserverCoordinator
    private lateinit var missionMapActionHandler: MissionMapActionHandler
    private lateinit var missionPresentationObserver: MissionPresentationObserver

    companion object {
        private const val DEFAULT_MAP_ZOOM = 18.0
        private const val DEFAULT_MAP_LAT = 35.3643003
        private const val DEFAULT_MAP_LON = 24.4721854
        private const val OFFLINE_MIN_ZOOM = 14
        private const val OFFLINE_MAX_ZOOM = 18
        private const val MAP_FLIGHT_TRACE_TAG = "MapFlightTrace"
        private const val MIN_VALID_ABS_COORDINATE = 1e-4
        private const val MAX_INITIAL_DRONE_CENTER_ATTEMPTS = 20
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeMapsBinding.inflate(inflater, container, false)

        droneViewModel = ViewModelProvider(requireActivity())[DroneViewModel::class.java]
        activityViewModel = ViewModelProvider(requireActivity())[MainActivityViewModel::class.java]
        homeTelemetryViewModel = ViewModelProvider(requireActivity())[HomeTelemetryViewModel::class.java]
        mapViewModel = ViewModelProvider(this)[MissionMapViewModel::class.java]
        missionFileStore = MissionFileStore(requireContext())
        geoEventLogger = GeoAwarenessEventLogger(requireContext().applicationContext)
        val geoIncidentLogger = GeoIncidentLogger(
            GeoIncidentEncryptedLogStore(requireContext().applicationContext)
        )
        operatorEventLogger = OperatorFlightEventLogger(geoEventLogger)
        geoAwarenessDatasetController = GeoAwarenessDatasetController(requireContext())
        geoAwarenessDialogController = GeoAwarenessDialogController(requireContext(), geoAuthorizationSession)
        geoAwarenessEventTracker = GeoAwarenessEventTracker(
            eventLogger = geoEventLogger,
            incidentLogger = geoIncidentLogger,
            metadata = {
                GeoAwarenessEventTracker.Metadata(
                    datasetTitle = if (::liveGeoAwarenessController.isInitialized) liveGeoAwarenessController.datasetInfo?.title else null,
                    datasetVersion = if (::liveGeoAwarenessController.isInitialized) liveGeoAwarenessController.datasetInfo?.version else null,
                    healthState = if (::liveGeoAwarenessController.isInitialized) liveGeoAwarenessController.health?.state?.name else null,
                )
            },
            telemetry = {
                GeoAwarenessEventTracker.Telemetry(
                    altitudeAglMeters = latestRealDroneAltitudeMeters,
                    altitudeAmslMeters = latestRealDroneAltitudeAmslMeters,
                    horizontalAccuracyMeters = latestRealDroneHorizontalAccuracyMeters,
                    verticalAccuracyMeters = latestRealDroneVerticalAccuracyMeters,
                    groundSpeedMetersPerSecond = latestRealDroneGroundSpeedMetersPerSecond,
                    verticalSpeedMetersPerSecond = latestRealDroneVerticalSpeedMetersPerSecond,
                    headingDegrees = latestRealDroneHeadingDegrees,
                )
            },
        )

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeMapView(view)
        initControllers()
        bindUiButtons()
        previewWorkflowController.bindActions()
        missionMapUiActionController.applyMapInsets()
        observeDroneViewModel()
        observeMapState()
        observeHomeTelemetry()
        observeMissionMapViewModel()
        observeGeoAwarenessSharedState()
        observePreviewSettings()

        mapViewModel.restoreFromMapState(activityViewModel.mapState.value ?: MainActivityViewModel.MapState.Idle)
        previewWorkflowController.selectMode(PreviewMode.MAP)
        binding.root.post {
            if (_binding != null) {
                previewWorkflowController.restorePersistedAssets()
            }
        }
    }

    private fun initializeMapView(view: View) {
        mapView = view.findViewById(R.id.osmMap)
        orthoOverlayController = OrthoOverlayController(
            mapView = mapView,
            assetsViewModel = previewAssetsViewModel,
            focusOverlay = { previewMapFocusController.focusOrtho() },
        )
        previewModeRenderer = PreviewModeRenderer(
            context = requireContext(),
            binding = binding,
            assetsViewModel = previewAssetsViewModel,
            // initializeMapView runs before initControllers; defer the lateinit lookup until render time.
            renderMapPath = { missionMapRenderer.renderCurrentPath() },
            renderOrtho = ::renderHomeOrthoOverlay,
            clearOrtho = ::removeHomeOrthoOverlay,
            renderTerrainStatus = { terrainPreviewCoordinator.renderStatus() },
            renderMissionOverlay = { terrainPreviewCoordinator.renderMissionOverlay() },
            updateDockPlacement = ::updatePreviewDockPlacement,
        )
        mapView.setBuiltInZoomControls(false)
        mapView.setMultiTouchControls(true)
        esriMapLayers = EsriMapLayers.install(requireContext(), mapView)
        mapView.isTilesScaledToDpi = true
        mapView.maxZoomLevel = 20.0
        mapView.controller.setZoom(DEFAULT_MAP_ZOOM)
        mapView.controller.setCenter(GeoPoint(DEFAULT_MAP_LAT, DEFAULT_MAP_LON))

        osmdroidMapController = OsmdroidMapController(requireContext(), mapView)
        osmdroidMapController.initOverlays()
        droneMapTrackingController = DroneMapTrackingController(
            mapController = osmdroidMapController,
            onHomeCaptured = activityViewModel::setPlannedHomePosition,
            onDisarmed = { geoAwarenessUploadController.resetCurrentFlightAuthorizations("disarmed") },
        )
        osmdroidMapController.setSurveyWaypointEditCallbacks(
            onSelected = { index -> missionEditorCoordinator.selectSurvey(index) },
            onMoved = { index, point -> missionEditorCoordinator.moveSurvey(index, point) }
        )

        osmdroidPolygonEditor = OsmdroidPolygonEditor(requireActivity(), activityViewModel, mapView)
        osmdroidPolygonEditor.init()

        osmdroidRouteWaypointEditor = OsmdroidRouteWaypointEditor(requireContext(), activityViewModel, mapView)
        osmdroidRouteWaypointEditor.init()
        osmdroidRouteWaypointEditor.setTerrainWaypointSelectionCallback { index ->
            missionEditorCoordinator.select(PlanningWorkflow.POINTS, index, previewWorkflowController.mode)
        }
        esriMapLayers.bringAttributionToFront()
        binding.homePointCloudGlView.setOnMissionPointClickListener { index, x, y ->
            val workflow = activityViewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA
            val selectedIndex = if (
                selectedTerrainWaypointWorkflow == workflow && selectedSurveyWaypointIndex == index
            ) null else index
            if (workflow == PlanningWorkflow.POINTS) {
                osmdroidRouteWaypointEditor.selectTerrainWaypoint(selectedIndex)
            } else {
                missionEditorCoordinator.select(PlanningWorkflow.AREA, selectedIndex, previewWorkflowController.mode)
            }
            if (selectedIndex != null) {
                missionEditorCoordinator.positionNearViewPoint(binding.homePointCloudGlView, x, y)
            }
        }

        osmdroidObstacleEditor = OsmdroidObstacleEditor(requireContext(), activityViewModel, mapView)
        osmdroidObstacleEditor.init()
        plannedHomePlacementController = PlannedHomePlacementController(
            mapController = osmdroidMapController,
            polygonEditor = osmdroidPolygonEditor,
            routeWaypointEditor = osmdroidRouteWaypointEditor,
            beforePlacement = {
                cancelDroneOffsetAdjustment()
                missionEditorCoordinator.cancelObstaclePlacement()
            },
            onHomePlaced = ::placePlannedHome,
        )
        previewMapFocusController = PreviewMapFocusController(
            mapView = mapView,
            assets = previewAssetsViewModel,
            onFocused = { initialViewportController.markCenteredToDrone() },
        )
        initialViewportController = InitialMapViewportController(
            rootViewProvider = { _binding?.root },
            mapController = osmdroidMapController,
            hasPendingPreviewFocus = previewMapFocusController::hasPendingRequest,
            focusPendingPreview = previewMapFocusController::focusPendingAsset,
            centerOnDrone = osmdroidMapController::centerOnDrone,
            maxDroneAttempts = MAX_INITIAL_DRONE_CENTER_ATTEMPTS,
        )
        viewLifecycleController = HomeMapViewLifecycleController(
            activityProvider = { activity },
            mapView = mapView,
            mapController = osmdroidMapController,
            pointCloudView = binding.homePointCloudGlView,
            refreshMapLabels = esriMapLayers::refreshLabelsEnabled,
            isPointCloudVisible = {
                ::previewWorkflowController.isInitialized && previewWorkflowController.mode == PreviewMode.POINT_CLOUD
            },
        )

        terrainCoverageOverlayController = TerrainCoverageOverlayController(requireContext(), mapView)

        geoZoneOverlayController = GeoZoneOverlayController(requireContext(), mapView)
        liveGeoAwarenessController = LiveGeoAwarenessController(
            context = requireContext(),
            root = binding.root,
            scope = viewLifecycleOwner.lifecycleScope,
            viewModel = activityViewModel,
            datasetController = geoAwarenessDatasetController,
            planningController = geoAwarenessPlanningController,
            overlayController = geoZoneOverlayController,
            eventLogger = geoEventLogger,
            eventTracker = geoAwarenessEventTracker,
            dialogController = geoAwarenessDialogController,
            telemetry = {
                LiveGeoAwarenessController.Telemetry(
                    position = latestRealDronePosition,
                    altitudeAglMeters = latestRealDroneAltitudeMeters,
                    altitudeAmslMeters = latestRealDroneAltitudeAmslMeters,
                    groundSpeedMetersPerSecond = latestRealDroneGroundSpeedMetersPerSecond,
                    verticalSpeedMetersPerSecond = latestRealDroneVerticalSpeedMetersPerSecond,
                    headingDegrees = latestRealDroneHeadingDegrees,
                    connected = droneViewModel.conStateLiveData.value == true,
                    gpsFixQuality = TelemetryMapping.gpsFixQuality(
                        droneViewModel.gpsFixType.value,
                        isConnected = droneViewModel.conStateLiveData.value == true,
                    ),
                )
            },
            isActive = { _binding != null },
        )
        liveGeoAwarenessController.initialize()
    }

    private fun initControllers() {
        missionMapUiActionController = MissionMapUiActionController(requireView(), binding)
        planningWorkflowUiController = PlanningWorkflowUiController(
            context = requireContext(),
            root = requireView(),
            viewModel = activityViewModel,
        )
        missionMapObserverCoordinator = MissionMapObserverCoordinator(viewLifecycleOwner, activityViewModel)
        missionPresentationObserver = MissionPresentationObserver(viewLifecycleOwner)
        pointRouteCoordinator = PointRouteCoordinator(
            scope = viewLifecycleOwner.lifecycleScope,
            viewModel = activityViewModel,
            previewAssets = previewAssetsViewModel,
            planner = missionPlanningCoordinator,
            isActive = { _binding != null },
        )
        terrainMissionCoordinator = TerrainMissionCoordinator(
            context = requireContext(),
            scope = viewLifecycleOwner.lifecycleScope,
            viewModel = activityViewModel,
            previewAssets = previewAssetsViewModel,
            planner = missionPlanningCoordinator,
        )
        terrainPreviewCoordinator = TerrainPreviewCoordinator(
            context = requireContext(),
            scope = viewLifecycleOwner.lifecycleScope,
            binding = binding,
            viewModel = activityViewModel,
            droneViewModel = droneViewModel,
            previewAssets = previewAssetsViewModel,
            currentPointCloud = {
                if (isSurveyMode()) droneViewModel.terrainPreview.value?.pointCloud
                else previewAssetsViewModel.pointCloudAsset?.pointCloud
            },
            currentDroneLocation = ::currentOffsetDroneLocation,
            currentMode = { previewWorkflowController.mode },
            isSurveyMode = ::isSurveyMode,
            selectedWorkflow = { missionEditorCoordinator.selectedWorkflow },
            selectedWaypointIndex = { missionEditorCoordinator.selectedIndex },
            orderPointRoute = ::orderPathForPlannedHome,
            redrawMission = ::redrawAreaMissionIfEditable,
            isActive = { _binding != null },
        )
        previewWorkflowController = PreviewWorkflowController(
            context = requireContext(),
            scope = viewLifecycleOwner.lifecycleScope,
            binding = binding,
            assets = previewAssetsViewModel,
            store = PreviewAssetStore(requireContext()),
            loader = PreviewAssetLoader(requireContext()),
            orthoOverlay = orthoOverlayController,
            modeRenderer = previewModeRenderer,
            terrainPreview = terrainPreviewCoordinator,
            uiActions = missionMapUiActionController,
            isSurveyMode = ::isSurveyMode,
            surveyPointCloud = { droneViewModel.terrainPreview.value?.pointCloud },
            renderMapPath = { missionMapRenderer.renderCurrentPath() },
            redrawMission = ::redrawAreaMissionIfEditable,
            generatePointRoute = ::generatePointRouteTerrainPath,
            launchFilePicker = { intent, requestCode -> startActivityForResult(intent, requestCode) },
            isActive = { _binding != null },
        )
        missionMapRenderer = MissionMapRenderer(
            context = requireContext(),
            obstacleList = requireView().findViewById(R.id.right_panel_obstacle_list),
            viewModel = activityViewModel,
            previewAssets = previewAssetsViewModel,
            planner = missionPlanningCoordinator,
            mapController = osmdroidMapController,
            obstacleEditor = osmdroidObstacleEditor,
            terrainCoordinator = terrainMissionCoordinator,
            coverageOverlay = { terrainCoverageOverlayController },
            heightColorsEnabled = { previewWorkflowController.heightColorsEnabled },
            removeObstacle = activityViewModel::removeMissionObstacle,
        )
        missionEditorCoordinator = MissionEditorCoordinator(
            context = requireContext(),
            binding = binding,
            viewModel = activityViewModel,
            mapController = osmdroidMapController,
            polygonEditor = osmdroidPolygonEditor,
            routeEditor = osmdroidRouteWaypointEditor,
            obstacleEditor = osmdroidObstacleEditor,
            terrainSampler = terrainMissionCoordinator::resampleWaypoint,
            shouldReversePointRoute = pointRouteCoordinator::shouldReverse,
            updateDistance = ::updateFlightDistance,
            renderPath = missionMapRenderer::renderCurrentPath,
            updatePointCloudOverlay = previewWorkflowController::renderMissionOverlay,
            applyObstacleControls = planningWorkflowUiController::renderObstacleControls,
        )
        missionGenerationCoordinator = MissionGenerationCoordinator(
            scope = viewLifecycleOwner.lifecycleScope,
            viewModel = activityViewModel,
            planner = missionPlanningCoordinator,
            isActive = { _binding != null },
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
                    clearRenderedPath = osmdroidMapController::clearSurveyPath,
                )
            },
            publishPath = { path, area -> renderSurveyPath(path, area) },
            cancelPointRoute = pointRouteCoordinator::cancel,
            cancelTerrain = terrainMissionCoordinator::cancel,
        )
        missionSummaryPresenter = MissionSummaryPresenter(
            context = requireContext(),
            root = requireView(),
            scope = viewLifecycleOwner.lifecycleScope,
            viewModel = activityViewModel,
            planner = missionPlanningCoordinator,
            currentPath = ::currentMissionPath,
            isActive = { _binding != null },
            updateGeometryActions = ::updateGeometryActionState,
            updateSimulationButton = ::updateSimulationButton,
            stopSimulation = ::stopMissionSimulation,
            setServiceMarkers = osmdroidMapController::setMissionServiceMarkers,
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
                    altitudeMeters = latestRealDroneAltitudeMeters,
                )
            },
        )
        missionMapActionHandler = MissionMapActionHandler(
            context = requireContext(),
            root = requireView(),
            viewModel = activityViewModel,
            eventLogger = operatorEventLogger,
            cancelPlanning = ::cancelPendingMissionPlanning,
            cancelObstaclePlacement = { missionEditorCoordinator.cancelObstaclePlacement() },
            clearEditors = {
                osmdroidPolygonEditor.clear()
                osmdroidMapController.clearSurveyPath()
            },
        )
        missionSimulationController = MissionSimulationController(
            flightSpeedMetersPerSecond = { activityViewModel.flightSpeed.value ?: 5.0 },
            missionObstacles = { activityViewModel.missionObstacles.value.orEmpty() },
            buildResourcePlan = { path, home -> buildMissionResourcePlan(path, home) },
            setSimulationDrone = osmdroidMapController::setSimulationDrone,
            clearSimulationDrone = osmdroidMapController::clearSimulationDrone,
            onStateChanged = { updateSimulationButton(currentMissionPath().size >= 2) },
            onWaitingForService = { stop ->
                Toast.makeText(
                    requireContext(),
                    getString(R.string.simulation_waiting_for_service_format, serviceDescription(stop)),
                    Toast.LENGTH_LONG,
                ).show()
            },
        )
        windWeatherController = WindWeatherController(
            context = requireContext(),
            rootView = requireView(),
            lifecycleOwner = viewLifecycleOwner,
            repository = windWeatherRepository,
            locationProvider = {
                activityViewModel.plannedHomePosition.value
                    ?: latestRealDronePosition
                    ?: mapView.mapCenter.let { LatLon(it.latitude, it.longitude) }
            },
            internetNetworkProvider = droneViewModel::currentRtkInternetNetwork,
        ).also { it.bind() }
        homeMapChromeBinder = HomeMapChromeBinder(
            binding = binding,
            bottomActionBarViewProvider = { activity?.findViewById(R.id.bottom_nav_view) }
        )
        homeMapPanelsBinder = HomeMapPanelsBinder(
            missionParamsView = requireView().findViewById(R.id.mission_params_side_view),
            planningPanelView = requireView().findViewById(R.id.planning_panel_container),
            saveMissionView = requireView().findViewById(R.id.save_file_layout),
            loadMissionView = requireView().findViewById(R.id.load_file_selector_layout)
        )
        homeMapTelemetryBinder = HomeMapTelemetryBinder(binding.root)
        flightModeUiBinder = FlightModeUiBinder(
            rootView = binding.root,
            droneViewModel = droneViewModel,
            telemetryViewModel = homeTelemetryViewModel
        ).also { it.bind(viewLifecycleOwner) }
        armUiBinder = ArmUiBinder(
            rootView = binding.root,
            droneViewModel = droneViewModel,
        ).also { it.bind(viewLifecycleOwner) }
        requireView().findViewById<View>(R.id.home_obstacle_panel).apply {
            isClickable = true
            isFocusable = true
        }

        missionParamsController = MissionParamsController(
            context = requireContext(),
            rootView = requireView(),
            lifecycleOwner = viewLifecycleOwner,
            activityViewModel = activityViewModel,
            droneViewModel = droneViewModel,
            droneLocationProvider = ::currentOffsetDroneLocation,
            beforeUploadGuard = { onAllowed ->
                geoAwarenessUploadController.handleBeforeUpload {
                    missionSummaryPresenter.showUploadSummary(onAllowed)
                }
            },
            beforeMissionUpload = ::stopMissionSimulation,
        )

        missionSaveController = MissionSaveController(
            activity = requireActivity(),
            rootView = requireView(),
            activityViewModel = activityViewModel
        )

        missionLoadController = MissionLoadController(
            activity = requireActivity(),
            rootView = requireView(),
            activityViewModel = activityViewModel
        )

        homeMapModeEffectsBinder = HomeMapModeEffectsBinder(
            missionParamsController = missionParamsController,
            missionSaveController = missionSaveController,
            missionLoadController = missionLoadController,
            onEnterIdle = {
                missionMapRenderer.renderDownloaded(droneViewModel.missionItems.value.orEmpty(), force = true)
                droneViewModel.downloadMissionNew(force = true)
            }
        )
    }

    private fun bindUiButtons() {
        missionMapUiActionController.bindChrome(
            chromeBinder = homeMapChromeBinder,
            actions = MissionMapUiActionController.Actions(
                downloadOffline = { downloadCurrentViewOffline(OFFLINE_MIN_ZOOM, OFFLINE_MAX_ZOOM) },
                centerOnUser = {
                    if (mapViewModel.homeMapUiState.value?.interactionState?.isDrawingEnabled != true) {
                        osmdroidMapController.centerOnUserIfPermitted()
                    }
                },
                centerOnDrone = ::centerOnDroneOrShowDisconnected,
                toggleObstacles = {
                    cancelDroneOffsetAdjustment()
                    toggleObstaclePanel()
                },
                startDroneOffset = ::startDroneOffsetAdjustment,
                cyclePreviewMode = previewWorkflowController::cycleMode,
                openSettings = {
                    requireActivity().findViewById<DrawerLayout>(R.id.drawer_layout)
                        .openDrawer(GravityCompat.START)
                },
                togglePlanning = {
                    cancelDroneOffsetAdjustment()
                    hideObstaclePanel()
                    mapViewModel.togglePlanningPanelVisible()
                },
                loadMission = ::openSavedMissionList,
                addHome = ::startPlannedHomePlacement,
                simulate = ::toggleMissionSimulation,
                resumeMission = missionParamsController::resumeServiceMission,
                closePanel = mapViewModel::dismissSidePanels,
                acceptGeometry = ::acceptActiveMissionGeometry,
                declineGeometry = {
                    clearActiveMissionGeometry()
                    activityViewModel.mapState.value = MainActivityViewModel.MapState.Idle
                },
                drawGeometry = ::startActiveGeometryDrawing,
                clearGeometry = {
                    clearActiveMissionGeometry()
                    activityViewModel.mapState.value = MainActivityViewModel.MapState.Idle
                },
                addObstacle = {
                    missionEditorCoordinator.toggleObstaclePlacement(previewWorkflowController.mode) {
                        previewWorkflowController.selectMode(PreviewMode.MAP)
                    }
                },
                clearObstacles = missionEditorCoordinator::clearObstacles,
                selectCircleObstacle = {
                    missionEditorCoordinator.selectObstacleMode(OsmdroidObstacleEditor.Mode.CIRCLE)
                },
                selectPolygonObstacle = {
                    missionEditorCoordinator.selectObstacleMode(OsmdroidObstacleEditor.Mode.POLYGON)
                },
                obstacleRadiusChanged = {
                    activityViewModel.updateObstacleRadius(it)
                    missionEditorCoordinator.renderObstacleControls()
                },
                deleteWaypoint = {
                    missionEditorCoordinator.deleteSelected { updateFlightDistance(currentMissionPath()) }
                },
                cancelWaypoint = missionEditorCoordinator::cancelSelection,
                decreaseWaypointHeight = { missionEditorCoordinator.adjustHeight(-1.0) },
                increaseWaypointHeight = { missionEditorCoordinator.adjustHeight(1.0) },
                applyWaypointHeight = missionEditorCoordinator::applyHeightField,
            ),
            initialObstacleRadiusMeters = (activityViewModel.obstacleRadiusMeters.value ?: 5.0).toInt(),
        )
        planningWorkflowUiController.bind(viewLifecycleOwner) { onPlanningWorkflowChanged() }
        renderAddHomeButton()
        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
    }
    private fun centerOnDroneOrShowDisconnected() {
        if (droneViewModel.conStateLiveData.value == true) osmdroidMapController.centerOnDrone()
        else Toast.makeText(context, R.string.no_conn_msg, Toast.LENGTH_LONG).show()
    }

    private fun openSavedMissionList() {
        if (missionFileStore.listMissionFiles().isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_saved_missions_yet, Toast.LENGTH_LONG).show()
            activityViewModel.mapState.value = MainActivityViewModel.MapState.Idle
        } else activityViewModel.mapState.postValue(MainActivityViewModel.MapState.LoadMissionFromFile)
    }

    private fun toggleMissionSimulation() {
        when (missionSimulationController.state) {
            MissionSimulationController.State.FLYING -> stopMissionSimulation()
            MissionSimulationController.State.WAITING_FOR_SERVICE -> missionSimulationController.resume()
            else -> startMissionSimulation()
        }
    }

    private fun acceptActiveMissionGeometry() {
        val workflow = activityViewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA
        val valid = when (workflow) {
            PlanningWorkflow.AREA -> activityViewModel.missionArea.value?.vertices.orEmpty().size >= 3
            PlanningWorkflow.POINTS -> activityViewModel.routeWaypoints.value.orEmpty().size >= 2
        }
        if (valid) activityViewModel.mapState.value = MainActivityViewModel.MapState.SetFlightParams
        else Toast.makeText(
            requireContext(),
            if (workflow == PlanningWorkflow.POINTS) R.string.route_requires_two_points else R.string.wrong_schema_msg,
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun startActiveGeometryDrawing() {
        if (activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS) {
            hideObstaclePanel()
            mapViewModel.setPlanningPanelVisible(false)
            clearPointMissionGeometry()
            activityViewModel.mapState.value = MainActivityViewModel.MapState.Draw
            Toast.makeText(requireContext(), R.string.tap_map_to_add_points, Toast.LENGTH_SHORT).show()
        } else startAreaDrawing()
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
        missionPresentationObserver.bindPreviewAssets(
            viewModel = previewAssetsViewModel,
            onSettings = previewWorkflowController::applySettings,
            onAssetVersion = previewWorkflowController::onAssetsChanged,
        )
        missionPresentationObserver.bind(
            droneViewModel.terrainPreview,
        ) { previewWorkflowController.onTerrainPreviewChanged() }
    }

    private fun toggleObstaclePanel() {
        val panel = requireView().findViewById<View>(R.id.home_obstacle_panel)
        val show = panel.visibility != View.VISIBLE
        if (show) {
            mapViewModel.setPlanningPanelVisible(false)
            missionEditorCoordinator.renderObstacleControls()
        } else {
            missionEditorCoordinator.cancelObstaclePlacement()
        }
        panel.visibility = if (show) View.VISIBLE else View.GONE
        missionMapUiActionController.setDockButtonSelected(R.id.utility_obstacles_button, show)
    }

    private fun hideObstaclePanel() {
        requireView().findViewById<View>(R.id.home_obstacle_panel).visibility = View.GONE
        missionMapUiActionController.setDockButtonSelected(R.id.utility_obstacles_button, false)
        missionEditorCoordinator.cancelObstaclePlacement()
    }

    private fun cancelDroneOffsetAdjustment() {
        osmdroidMapController.cancelDroneOffsetAdjustment()
        missionMapUiActionController.setDockButtonSelected(R.id.utility_offset_button, false)
    }

    private fun startDroneOffsetAdjustment() {
        if (droneViewModel.conStateLiveData.value != true) {
            Toast.makeText(context, getString(R.string.no_conn_msg), Toast.LENGTH_LONG).show()
            return
        }
        hideObstaclePanel()
        mapViewModel.setPlanningPanelVisible(false)
        val started = osmdroidMapController.startDroneOffsetAdjustment { latitudeOffset, longitudeOffset ->
            droneMapTrackingController.updateOffset(latitudeOffset, longitudeOffset)
            syncLatestDroneLocationSnapshot(droneViewModel.droneLocationLiveData.value)
            liveGeoAwarenessController.updateLiveFromActiveSource()
            missionMapUiActionController.setDockButtonSelected(R.id.utility_offset_button, false)
            Toast.makeText(requireContext(), getString(R.string.drone_offset_applied), Toast.LENGTH_SHORT).show()
        }
        if (started) {
            missionMapUiActionController.setDockButtonSelected(R.id.utility_offset_button, true)
            Toast.makeText(requireContext(), getString(R.string.drone_offset_drag_prompt), Toast.LENGTH_LONG).show()
        }
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
        if (::osmdroidPolygonEditor.isInitialized) osmdroidPolygonEditor.clear()
        if (::osmdroidMapController.isInitialized) osmdroidMapController.clearSurveyPath()
    }

    private fun clearPointMissionGeometry() {
        pointRouteCoordinator.cancel()
        activityViewModel.clearRouteWaypoints()
        if (::osmdroidRouteWaypointEditor.isInitialized) osmdroidRouteWaypointEditor.clear()
    }

    private fun renderWorkflowSelection(workflow: PlanningWorkflow) {
        planningWorkflowUiController.render(workflow)
        missionEditorCoordinator.renderObstacleControls()
        updateRouteSummary()
    }

    private fun updateGeometryActionState() {
        planningWorkflowUiController.updateGeometryActionState()
    }
    private fun isWorkflowSelectionActive(): Boolean {
        return when (activityViewModel.mapState.value) {
            MainActivityViewModel.MapState.Draw,
            MainActivityViewModel.MapState.SetFlightParams -> true
            else -> false
        }
    }

    private fun updateRouteSummary() {
        val routeSummary = requireView().findViewById<TextView?>(R.id.right_panel_route_summary) ?: return
        val waypoints = activityViewModel.routeWaypoints.value.orEmpty()
        routeSummary.text = getString(R.string.route_summary_format, waypoints.size)
    }

    private fun currentMissionPath(): List<LatLng> {
        return when (activityViewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA) {
            PlanningWorkflow.AREA -> activityViewModel.surveyPath.value.orEmpty()
            PlanningWorkflow.POINTS -> activityViewModel.terrainRouteWaypoints.value.orEmpty()
                .takeIf { it.size >= 2 }
                ?.map { LatLng(it.latLon.lat, it.latLon.lon) }
                ?: activityViewModel.plannedRoutePath.value.orEmpty().takeIf { it.size >= 2 }
                ?: activityViewModel.routeWaypoints.value.orEmpty().map {
                    LatLng(it.latitude, it.longitude)
                }
        }
    }

    private fun orderPathForPlannedHome(path: List<LatLng>): List<LatLng> {
        return pointRouteCoordinator.orderForHome(path)
    }

    private fun orderPathForHome(path: List<LatLng>, home: LatLon?): List<LatLng> {
        return pointRouteCoordinator.orderForHome(path, home)
    }

    private fun buildMissionResourcePlan(
        path: List<LatLng>,
        home: LatLon? = activityViewModel.plannedHomePosition.value,
    ): MissionResourcePlan {
        return missionPlanningCoordinator.buildResourcePlan(
            path = path.map { LatLon(it.latitude, it.longitude) },
            home = home,
            operationMode = activityViewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY,
            speedMetersPerSecond = (activityViewModel.flightSpeed.value ?: 5.0).coerceAtLeast(0.1),
            sprayRateLitersPerMinute = activityViewModel.sprayFlowLitersPerMinute(),
        )
    }

    private fun updateSimulationButton(hasPath: Boolean) {
        view?.findViewById<com.google.android.material.button.MaterialButton?>(R.id.right_panel_simulate_button)
            ?.apply {
                isEnabled = hasPath
                alpha = if (hasPath) 1.0f else 0.5f
                setText(
                    when (missionSimulationController.state) {
                        MissionSimulationController.State.FLYING -> R.string.stop_mission_simulation
                        MissionSimulationController.State.WAITING_FOR_SERVICE -> R.string.resume_mission
                        else -> R.string.simulate_mission_path
                    }
                )
            }
    }

    private fun startMissionSimulation() {
        val connected = droneViewModel.conStateLiveData.value == true
        if (connected && droneViewModel.armedState.value == true) {
            Toast.makeText(requireContext(), R.string.simulation_requires_landed_disarmed, Toast.LENGTH_SHORT).show()
            return
        }
        if (connected && droneViewModel.droneLandedState.value != MavLandedState.MAV_LANDED_STATE_ON_GROUND) {
            Toast.makeText(
                requireContext(),
                if (droneViewModel.droneLandedState.value == null ||
                    droneViewModel.droneLandedState.value == MavLandedState.MAV_LANDED_STATE_UNDEFINED
                ) R.string.simulation_landed_state_unknown else R.string.simulation_requires_landed_disarmed,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val home = if (connected) latestRealDronePosition else activityViewModel.plannedHomePosition.value
        if (home == null || !isValidDronePosition(home)) {
            Toast.makeText(
                requireContext(),
                if (connected) R.string.drone_gps_not_available_yet else R.string.simulation_requires_home,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val missionPath = orderPathForHome(currentMissionPath(), home)
        if (missionPath.size < 2) {
            Toast.makeText(requireContext(), R.string.simulation_requires_path, Toast.LENGTH_SHORT).show()
            return
        }
        previewWorkflowController.selectMode(PreviewMode.MAP)
        missionSimulationController.start(missionPath, home, useLiveDroneHome = connected)
    }

    private fun serviceDescription(stop: MissionServiceStop): String = when {
        stop.requiresBattery && stop.requiresTankRefill -> {
            getString(R.string.service_change_battery_and_refill_tank)
        }
        stop.requiresBattery -> getString(R.string.service_change_battery)
        else -> getString(R.string.service_refill_tank)
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
        val distance = missionPathTotalDistance(
            routePath
        )
        activityViewModel.flightDistance.postValue(distance.toInt())
    }

    private fun updateCurrentFlightDistance() {
        when (activityViewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA) {
            PlanningWorkflow.AREA -> updateFlightDistance(activityViewModel.surveyPath.value.orEmpty())
            PlanningWorkflow.POINTS -> updateRouteDistance(activityViewModel.routeWaypoints.value.orEmpty())
        }
    }

    private fun renderPlannedHomePosition(position: LatLon?) {
        droneMapTrackingController.renderHome(position)
    }

    private fun observeDroneViewModel() {
        missionMapObserverCoordinator.observe(droneViewModel.droneLocationLiveData) { droneLocation ->
            syncLatestDroneLocationSnapshot(droneLocation)

            val rawPosition = latestRawDronePosition
            val correctedPosition = latestRealDronePosition
            if (rawPosition != null && correctedPosition != null) {
                osmdroidMapController.updateDronePosition(
                    rawPosition.lat,
                    rawPosition.lon
                )
                maybeSetPendingHomeMarker(correctedPosition)
                maybeAppendFlightTrace(correctedPosition)
                initialViewportController.centerIfNeeded()
            } else {
                osmdroidMapController.setDroneVisible(false)
                initialViewportController.centerIfNeeded()
            }

            liveGeoAwarenessController.updateLiveFromActiveSource()
            previewWorkflowController.renderMissionOverlay()
        }

        missionMapObserverCoordinator.observe(droneViewModel.conStateLiveData) {
            if (it != true && missionSimulationController.usesLiveDroneHome) stopMissionSimulationIfActive()
            syncLatestDroneLocationSnapshot(droneViewModel.droneLocationLiveData.value)
            renderAddHomeButton()
            liveGeoAwarenessController.updateLiveFromActiveSource()
            previewWorkflowController.renderMissionOverlay()
        }

        missionMapObserverCoordinator.observe(droneViewModel.gpsFixType) {
            syncLatestDroneLocationSnapshot(droneViewModel.droneLocationLiveData.value)
            liveGeoAwarenessController.updateLiveFromActiveSource()
        }

        missionMapObserverCoordinator.observe(droneViewModel.droneHeading) { droneHeading ->
            droneHeading?.let { heading ->
                latestRealDroneHeadingDegrees = heading
                osmdroidMapController.updateDroneHeadingDegrees(heading.toFloat())
            }
            previewWorkflowController.renderMissionOverlay()
        }

        missionMapObserverCoordinator.observe(droneViewModel.droneGroundSpeedMetersPerSecond) { speed ->
            latestRealDroneGroundSpeedMetersPerSecond = speed
        }

        missionMapObserverCoordinator.observe(droneViewModel.droneVerticalSpeedMetersPerSecond) { speed ->
            latestRealDroneVerticalSpeedMetersPerSecond = speed
        }

        missionMapObserverCoordinator.observe(droneViewModel.droneAltitudeAmslMeters) { altitudeAmslMeters ->
            latestRealDroneAltitudeAmslMeters = altitudeAmslMeters
            liveGeoAwarenessController.updateLiveFromActiveSource()
        }

        missionMapObserverCoordinator.observe(droneViewModel.armedState) { armed ->
            if (armed == true) stopMissionSimulationIfActive()
            handleArmedStateChanged(armed == true)
            activityViewModel.onServiceMissionArmedStateChanged(armed == true)
            renderServiceMissionResumeButton()
        }

        missionMapObserverCoordinator.observe(droneViewModel.droneLandedState) { landedState ->
            if (missionSimulationController.usesLiveDroneHome && landedState != MavLandedState.MAV_LANDED_STATE_ON_GROUND) {
                stopMissionSimulationIfActive()
            }
        }

        missionMapObserverCoordinator.observe(activityViewModel.serviceMissionState) {
            renderServiceMissionResumeButton()
        }

        missionMapObserverCoordinator.observe(activityViewModel.missionArea) { missionArea ->
            stopMissionSimulationIfActive()
            val vertices = missionArea?.vertices ?: emptyList()
            val hasRoute = activityViewModel.routeWaypoints.value.orEmpty().size >= 2
            mapViewModel.setMissionAreaAvailable(vertices.size >= 3 || hasRoute)
            missionEditorCoordinator.syncPolygonVertices(vertices)
            liveGeoAwarenessController.updatePlanningStatus()
            missionSummaryPresenter.renderCard()
            previewWorkflowController.renderMissionOverlay()
        }

        missionMapObserverCoordinator.observe(activityViewModel.plannedHomePosition) { position ->
            renderPlannedHomePosition(position)
            stopMissionSimulation()
            if (
                position != null &&
                activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA &&
                activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams
            ) {
                redrawAreaMissionIfEditable()
            } else if (position != null && activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS) {
                generatePointRouteTerrainPath()
                missionEditorCoordinator.syncRouteWaypoints()
            }
            updateCurrentFlightDistance()
            missionSummaryPresenter.renderCard()
        }

        missionMapObserverCoordinator.observe(activityViewModel.missionObstacles) { obstacles ->
            stopMissionSimulationIfActive()
            missionEditorCoordinator.onObstaclesChanged(obstacles.orEmpty())
            missionMapRenderer.renderObstacleList(obstacles.orEmpty())
            if (
                activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams &&
                activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA &&
                (activityViewModel.missionArea.value?.vertices?.size ?: 0) >= 3
            ) {
                redrawAreaMissionOnMap()
            } else if (activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS) {
                generatePointRouteTerrainPath()
            }
        }

        missionMapObserverCoordinator.observe(droneViewModel.missionItems) { missionItems ->
            previewAssetsViewModel.retainDroneMission(missionItems.orEmpty())
            missionMapRenderer.renderDownloaded(missionItems.orEmpty())
            previewWorkflowController.renderMissionOverlay()
        }

        missionMapObserverCoordinator.observe(droneViewModel.missionDownloadProgressPercent) { progress ->
            val percent = progress ?: -1
            binding.missionDownloadIndicator?.visibility = if (percent >= 0) View.VISIBLE else View.GONE
            if (percent >= 0) {
                binding.missionDownloadProgress?.progress = percent.coerceIn(0, 100)
                binding.missionDownloadText?.text = getString(
                    R.string.mission_downloading_from_drone,
                    percent.coerceIn(0, 100),
                )
            }
        }

        missionMapObserverCoordinator.observe(droneViewModel.rtkForwardingState) { state ->
            val streaming = state is RtkForwardingState.Streaming
            if (lastRtkStreamingActive == streaming) {
                return@observe
            }
            if (lastRtkStreamingActive != null) {
                if (streaming) {
                    RtkTonePlayer.playConnectedTone()
                } else {
                    RtkTonePlayer.playDisconnectedTone()
                }
            }
            lastRtkStreamingActive = streaming
            geoEventLogger.logSimple(
                type = if (streaming) GeoAwarenessEventType.RTK_CONNECTED else GeoAwarenessEventType.RTK_DISCONNECTED,
                severity = if (streaming) "INFO" else "WARNING",
                message = if (streaming) "RTK streaming active" else "RTK streaming inactive",
                category = "RTK",
                connectionState = if (streaming) "CONNECTED" else "DISCONNECTED",
                details = mapOf("state" to (state?.javaClass?.simpleName ?: "Unknown"))
            )
        }
    }

    private fun observeMapState() {
        missionMapObserverCoordinator.bindParameterObservers(
            MissionMapObserverCoordinator.Actions(
                stopSimulation = ::stopMissionSimulationIfActive,
                scheduleAreaRedraw = { scheduleAreaMissionRedraw() },
                flightAltitudeChanged = ::onFlightAltitudeChanged,
                surveyHeightChanged = missionEditorCoordinator::refreshHeightLabel,
                surveyGridChanged = ::onSurveyGridParametersChanged,
                planningModeChanged = ::onPlanningOperationModeChanged,
                updateTerrainOverlay = missionMapRenderer::renderCoverage,
                updateMissionSummary = missionSummaryPresenter::renderCard,
                renderPreview = previewWorkflowController::render,
            )
        )
        missionMapObserverCoordinator.observe(activityViewModel.surveyPath) { surveyPath ->
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

        missionMapObserverCoordinator.observe(activityViewModel.terrainSurveyWaypoints) {
            stopMissionSimulationIfActive()
            previewWorkflowController.renderMissionOverlay()
        }

        missionMapObserverCoordinator.observe(activityViewModel.routeWaypoints) { waypoints ->
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
            generatePointRouteTerrainPath()
            missionMapRenderer.renderCoverage()
        }

        missionMapObserverCoordinator.observe(activityViewModel.terrainRouteWaypoints) {
            stopMissionSimulationIfActive()
            missionEditorCoordinator.syncRouteWaypoints()
            missionSummaryPresenter.renderCard()
            previewWorkflowController.renderMissionOverlay()
            missionMapRenderer.renderCoverage()
        }

        missionMapObserverCoordinator.observe(activityViewModel.plannedRoutePath) {
            stopMissionSimulationIfActive()
            missionEditorCoordinator.syncRouteWaypoints()
            missionSummaryPresenter.renderCard()
            previewWorkflowController.renderMissionOverlay()
            missionMapRenderer.renderCoverage()
        }

        missionMapObserverCoordinator.observe(activityViewModel.mapState) { mapState ->
            mapViewModel.updateFromMapState(mapState)
            renderWorkflowSelection(activityViewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA)
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
                if (hasPolygon) {
                    redrawAreaMissionOnMap()
                }
            }
            missionSummaryPresenter.renderCard()
        }

        missionMapObserverCoordinator.observe(activityViewModel.mapAction) { event ->
            val action = event?.getContentIfNotHandled() ?: return@observe
            missionMapActionHandler.handle(action)
        }
    }
    private fun onFlightAltitudeChanged(altitude: Double) {
        val points = activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS
        if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams && points) {
            savePreference(getString(R.string.survey_altitude_pref), altitude.toInt().toString())
        }
        if (points) {
            liveGeoAwarenessController.updatePlanningStatus()
            missionSummaryPresenter.renderCard()
        }
        if (activityViewModel.planningOperationMode.value == PlanningOperationMode.SPRAY) {
            redrawAreaMissionIfEditable(debounced = true)
        }
    }

    private fun onSurveyGridParametersChanged() {
        if (activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS) {
            generatePointRouteTerrainPath()
        } else redrawAreaMissionIfEditable(debounced = true)
    }

    private fun onPlanningOperationModeChanged() {
        if (
            activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams &&
            activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA &&
            (activityViewModel.missionArea.value?.vertices?.size ?: 0) >= 3
        ) redrawAreaMissionOnMap()
    }

    private fun observeHomeTelemetry() {
        missionPresentationObserver.bindHomeTelemetry(homeTelemetryViewModel, homeMapTelemetryBinder)
    }



    private fun observeMissionMapViewModel() {
        missionPresentationObserver.bindMapUi(mapViewModel, ::renderHomeMapUiState)
    }

    private fun renderHomeMapUiState(state: HomeMapUiState) {
        isDrawingModeActive = state.interactionState.isDrawingEnabled
        liveGeoAwarenessController.setZoneDetailsEnabled(!state.interactionState.isDrawingEnabled)
        missionEditorCoordinator.updatePolygonEnabled(
            state.interactionState.isDrawingEnabled,
            homePlacementMode,
        )
        missionEditorCoordinator.updateRouteEnabled(homePlacementMode)
        homeMapChromeBinder.renderShell(state.shellState)
        homeMapChromeBinder.renderInteraction(state.interactionState)
        homeMapChromeBinder.renderOverlayControls(state.overlayControlsState)
        homeMapPanelsBinder.renderShell(state.shellState)
        homeMapPanelsBinder.renderOverlays(state.panelState)
        homeMapModeEffectsBinder.render(state.screenMode)
        renderAddHomeButton()
        updatePreviewDockPlacement()

    }

    private fun renderAddHomeButton() {
        val addHomeButton = view?.findViewById<View?>(R.id.right_panel_add_home_button) ?: return
        if (droneViewModel.conStateLiveData.value == true) {
            cancelPlannedHomePlacement()
        }
        addHomeButton.visibility = if (droneViewModel.conStateLiveData.value == true) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun renderServiceMissionResumeButton() {
        val button = view?.findViewById<com.google.android.material.button.MaterialButton?>(
            R.id.right_panel_resume_mission_button
        ) ?: return
        val waiting = activityViewModel.serviceMissionState.value == ServiceMissionState.WAITING_FOR_SERVICE
        button.visibility = if (waiting) View.VISIBLE else View.GONE
        if (!waiting) return
        val stop = activityViewModel.currentServiceMissionLeg()?.serviceAfter
        button.text = if (stop != null) {
            getString(R.string.resume_mission_service_format, serviceDescription(stop))
        } else {
            getString(R.string.resume_mission)
        }
    }

    private fun updatePreviewDockPlacement() {
        if (_binding == null) return
        val parent = binding.previewModeBottomDock.parent as? androidx.constraintlayout.widget.ConstraintLayout ?: return
        parent.post {
            if (_binding == null) return@post
            val verticalGap = resources.getDimensionPixelSize(R.dimen.ds_space_lg)
            binding.previewModeBottomDock.layoutParams =
                (binding.previewModeBottomDock.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)
                    .apply {
                        width = resources.getDimensionPixelSize(R.dimen.preview_mode_rail_width)
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
            ConstraintSet().apply {
                clone(parent)
                clear(binding.previewModeBottomDock.id, ConstraintSet.TOP)
                clear(binding.previewModeBottomDock.id, ConstraintSet.BOTTOM)
                clear(binding.previewModeBottomDock.id, ConstraintSet.START)
                clear(binding.previewModeBottomDock.id, ConstraintSet.END)
                connect(
                    binding.previewModeBottomDock.id,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START,
                    0
                )
                connect(
                    binding.previewModeBottomDock.id,
                    ConstraintSet.END,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.END,
                    0
                )
                connect(
                    binding.previewModeBottomDock.id,
                    ConstraintSet.BOTTOM,
                    binding.homeBottomUtilityDock.id,
                    ConstraintSet.TOP,
                    verticalGap
                )
                applyTo(parent)
            }
        }
    }

    private fun savePreference(key: String, value: String) {
        val sharedPref =
            PreferenceManager.getDefaultSharedPreferences(requireActivity().applicationContext)
        with(sharedPref.edit()) {
            putString(key, value)
            apply()
        }
    }


    private fun downloadCurrentViewOffline(minZoom: Int, maxZoom: Int) {
        val bbox = mapView.boundingBox
        val cacheManagers = mutableListOf(CacheManager(mapView))
        if (esriMapLayers.labelsEnabled) {
            val labelsProvider = esriMapLayers.labelsTileProvider
            cacheManagers += CacheManager(
                labelsProvider,
                labelsProvider.tileWriter,
                minZoom,
                maxZoom
            )
        }
        val pendingDownloads = AtomicInteger(cacheManagers.size)
        val totalErrors = AtomicInteger(0)

        Toast.makeText(requireContext(), getString(R.string.offline_download_started), Toast.LENGTH_SHORT).show()

        fun callback() = object : CacheManager.CacheManagerCallback {
            override fun downloadStarted() = Unit
            override fun setPossibleTilesInArea(total: Int) = Unit
            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) = Unit

            override fun onTaskComplete() {
                finishDownload()
            }

            override fun onTaskFailed(errors: Int) {
                totalErrors.addAndGet(errors.coerceAtLeast(1))
                finishDownload()
            }

            private fun finishDownload() {
                if (pendingDownloads.decrementAndGet() != 0 || !isAdded) return
                val errors = totalErrors.get()
                Toast.makeText(
                    requireContext(),
                    if (errors == 0) {
                        getString(R.string.offline_download_complete)
                    } else {
                        getString(R.string.offline_download_failed, errors)
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        cacheManagers.forEach { cacheManager ->
            cacheManager.downloadAreaAsync(requireContext(), bbox, minZoom, maxZoom, callback())
        }
    }

    private fun redrawAreaMissionOnMap() {
        scheduleAreaMissionRedraw(immediate = true)
    }

    private fun scheduleAreaMissionRedraw(immediate: Boolean = false) {
        missionGenerationCoordinator.scheduleAreaRedraw(immediate)
    }

    private fun cancelPendingMissionPlanning() {
        missionGenerationCoordinator.cancel()
        terrainMissionCoordinator.cancel()
    }

    private fun generatePointRouteTerrainPath() {
        pointRouteCoordinator.generate()
    }

    private fun persistMissionEditingPreferences() {
        PreferenceManager.getDefaultSharedPreferences(requireContext().applicationContext)
            .edit()
            .putString(
                getString(R.string.survey_angle_pref),
                (activityViewModel.angleProgress.value?.toInt() ?: 90).toString()
            )
            .putString(
                getString(R.string.survey_line_distance_pref),
                (activityViewModel.lineDistanceProgress.value?.toInt() ?: 5).toString()
            )
            .putString(
                getString(R.string.survey_altitude_pref),
                (activityViewModel.flightAltProgress.value?.toInt() ?: 0).toString()
            )
            .putString(
                getString(R.string.survey_strip_spacing_pref),
                (activityViewModel.surveyStripSpacing.value?.toInt() ?: 70).toString()
            )
            .putString(
                getString(R.string.survey_height_above_terrain_pref),
                (activityViewModel.surveyHeightAboveTerrain.value?.toInt() ?: 50).toString()
            )
            .putString(
                getString(R.string.survey_overlap_pref),
                (activityViewModel.surveyOverlapPercent.value?.toInt() ?: 80).toString()
            )
            .putString(
                getString(R.string.survey_grid_angle_pref),
                (activityViewModel.surveyGridAngle.value?.toInt() ?: 90).toString()
            )
            .putString(
                getString(R.string.survey_terrain_segment_pref),
                (activityViewModel.surveyTerrainSegment.value ?: 2.5).toString()
            )
            .putString(
                getString(R.string.survey_canopy_smoothing_pref),
                (activityViewModel.surveyCanopySmoothing.value?.toInt() ?: 5).toString()
            )
            .apply()
    }

    private fun redrawAreaMissionIfEditable(debounced: Boolean = false) {
        missionGenerationCoordinator.redrawIfEditable(debounced)
    }

    private fun isSurveyMode(): Boolean =
        (activityViewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY) ==
            PlanningOperationMode.SURVEY

    private fun renderHomeOrthoOverlay() {
        orthoOverlayController.render()
    }

    private fun removeHomeOrthoOverlay() {
        if (::orthoOverlayController.isInitialized) orthoOverlayController.clear()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        previewWorkflowController.handleActivityResult(requestCode, resultCode, data)
    }
    private fun renderSurveyPath(
        pathLatLon: List<LatLon>,
        areaVertices: List<LatLng>,
        terrainWaypoints: List<TerrainWaypoint> = emptyList(),
    ) {
        val ordered = missionGenerationCoordinator.orderForPublishing(pathLatLon, terrainWaypoints)
        missionMapRenderer.publishGeneratedPath(ordered.points, areaVertices, ordered.terrainWaypoints)
    }
    private fun updateFlightDistance(path: List<LatLng>) {
        val surveyDistance = missionPathTotalDistance(path)
        activityViewModel.flightDistance.postValue(surveyDistance.toInt())
    }

    private fun missionPathTotalDistance(path: List<LatLng>): Double {
        val homePosition = activityViewModel.plannedHomePosition.value
            ?.takeIf(::isValidDronePosition)
        return missionPlanningCoordinator.totalDistance(
            path = path.map { LatLon(it.latitude, it.longitude) },
            home = homePosition,
        )
    }

    override fun onDestroyView() {
        viewLifecycleController.onDestroyView {
            if (::flightModeUiBinder.isInitialized) flightModeUiBinder.dismiss()
            if (::armUiBinder.isInitialized) armUiBinder.dismiss()
            stopMissionSimulation()
            missionGenerationCoordinator.cancel()
            missionSummaryPresenter.dispose()
            terrainMissionCoordinator.cancel()
            previewWorkflowController.dispose()
            if (::windWeatherController.isInitialized) windWeatherController.dispose()
            cancelDroneOffsetAdjustment()
            liveGeoAwarenessController.dispose()
            terrainCoverageOverlayController?.clear()
            osmdroidObstacleEditor.release()
            terrainCoverageOverlayController = null
            clearFlightTrace()
        }
        super.onDestroyView()
        _binding = null
    }


    override fun onResume() {
        super.onResume()
        viewLifecycleController.onResume {
            previewWorkflowController.refreshAssets()
            missionMapRenderer.renderCurrentPath()
            redrawAreaMissionIfEditable()
            initialViewportController.centerIfNeeded()
        }
    }

    override fun onPause() {
        viewLifecycleController.onPause {
            stopMissionSimulation()
            cancelDroneOffsetAdjustment()
        }
        super.onPause()
    }

    private fun handleArmedStateChanged(isArmed: Boolean) {
        droneMapTrackingController.handleArmedState(isArmed)
    }

    private fun syncLatestDroneLocationSnapshot(droneLocation: android.location.Location?) {
        val usableLocation = droneLocation?.takeIf(::isUsableDroneLocation)
        droneMapTrackingController.syncLocation(usableLocation)
        latestRealDroneAltitudeMeters = usableLocation?.altitude
        latestRealDroneHorizontalAccuracyMeters = usableLocation
            ?.takeIf { it.hasAccuracy() }
            ?.accuracy
        latestRealDroneVerticalAccuracyMeters = usableLocation
            ?.takeIf { it.hasVerticalAccuracy() }
            ?.verticalAccuracyMeters
    }

    private fun currentOffsetDroneLocation(): android.location.Location? {
        val source = droneViewModel.droneLocationLiveData.value?.takeIf(::isUsableDroneLocation) ?: return null
        return droneMapTrackingController.offsetLocation(source)
    }

    private fun maybeSetPendingHomeMarker(position: LatLon) {
        droneMapTrackingController.capturePendingHome(position)
    }

    private fun startPlannedHomePlacement() {
        if (droneViewModel.conStateLiveData.value == true) {
            Toast.makeText(
                requireContext(),
                getString(R.string.planned_home_requires_disconnected),
                Toast.LENGTH_SHORT
            ).show()
            renderAddHomeButton()
            return
        }

        plannedHomePlacementController.start()
        Toast.makeText(requireContext(), getString(R.string.tap_map_to_place_home), Toast.LENGTH_SHORT).show()
    }

    private fun placePlannedHome(plannedHomePosition: LatLon) {
        if (!isValidDronePosition(plannedHomePosition)) return

        activityViewModel.setPlannedHomePosition(plannedHomePosition)
        Toast.makeText(requireContext(), getString(R.string.planned_home_set), Toast.LENGTH_SHORT).show()
        Log.d(
            MAP_FLIGHT_TRACE_TAG,
            "planned home marker set lat=${plannedHomePosition.lat} lon=${plannedHomePosition.lon}"
        )
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

    private fun clearFlightTrace() {
        droneMapTrackingController.clearTraceAndHome()
    }

    private fun isValidDronePosition(position: LatLon): Boolean {
        return DroneMapTrackingController.isValidPosition(position)
    }

    private fun observeGeoAwarenessSharedState() {
        missionPresentationObserver.bindGeoState(
            layerVisible = activityViewModel.geoAwarenessLayerVisible,
            reloadToken = activityViewModel.geoZoneReloadToken,
            renderLayer = liveGeoAwarenessController::renderLayerIfVisible,
            reload = liveGeoAwarenessController::reloadCurrentDataset,
        )
    }

    private fun isUsableDroneLocation(location: android.location.Location): Boolean {
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) {
            return false
        }
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) {
            return false
        }
        return kotlin.math.abs(location.latitude) > MIN_VALID_ABS_COORDINATE ||
            kotlin.math.abs(location.longitude) > MIN_VALID_ABS_COORDINATE
    }

}
