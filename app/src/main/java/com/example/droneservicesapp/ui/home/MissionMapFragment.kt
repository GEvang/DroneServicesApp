package com.example.droneservicesapp.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.example.droneservicesapp.data.pointcloud.PointCloudData
import com.example.droneservicesapp.data.rtk.RtkForwardingState
import com.example.droneservicesapp.data.storage.MissionFileStore
import com.example.droneservicesapp.data.weather.OpenMeteoWindRepository
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessResult
import com.example.droneservicesapp.domain.geoawareness.GeoAltitudeContext
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction
import com.example.droneservicesapp.domain.geoawareness.LiveGeoAwarenessChecker
import com.example.droneservicesapp.domain.geoawareness.LiveGeoAwarenessProximityResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import com.example.droneservicesapp.databinding.FragmentHomeMapsBinding
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.MissionObstacle
import com.example.droneservicesapp.domain.model.MissionObstacleShape
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.planning.MissionResourcePlan
import com.example.droneservicesapp.domain.planning.MissionPlanningCoordinator
import com.example.droneservicesapp.domain.planning.PointRouteCoordinator
import com.example.droneservicesapp.domain.planning.MissionServiceStop
import com.example.droneservicesapp.domain.terrain.TerrainWaypoint
import com.example.droneservicesapp.domain.terrain.TerrainPathFailure
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.mavserver.GpsFixQuality
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
import com.example.droneservicesapp.ui.home.geoawareness.GeoUploadGuardPolicy
import com.example.droneservicesapp.ui.home.geoawareness.LiveGeoAwarenessPanelBinder
import com.example.droneservicesapp.ui.home.geoawareness.LiveGeoThreatPresenter
import com.example.droneservicesapp.ui.home.geoawareness.GeoAwarenessEventTracker
import com.example.droneservicesapp.ui.home.geoawareness.GeoAwarenessDialogController
import com.example.droneservicesapp.ui.home.model.HomeTelemetryViewModel
import com.example.droneservicesapp.ui.home.model.HomeMapUiState
import com.example.droneservicesapp.ui.home.model.MissionMapViewModel
import com.example.droneservicesapp.ui.preview.OrthoOverlayController
import com.example.droneservicesapp.ui.preview.PreviewMode
import com.example.droneservicesapp.ui.preview.PreviewModeRenderer
import com.example.droneservicesapp.ui.preview.PointCloudMissionOverlayBuilder
import com.example.droneservicesapp.ui.preview.PointCloudTerrainStyleMapper
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.preview.PreviewAssetStore
import com.example.droneservicesapp.ui.preview.PreviewAssetLoader
import com.example.droneservicesapp.ui.preview.PreviewMapFocus
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.example.droneservicesapp.ui.common.RtkTonePlayer
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import io.dronefleet.mavlink.common.MavLandedState
import io.dronefleet.mavlink.common.MissionItemInt
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

class MissionMapFragment : Fragment() {
    private var _binding: FragmentHomeMapsBinding? = null
    private val binding get() = _binding!!
    private lateinit var mapView: MapView

    private lateinit var droneViewModel: DroneViewModel
    private lateinit var activityViewModel: MainActivityViewModel
    private val previewAssetsViewModel: PreviewAssetsViewModel by activityViewModels()
    private lateinit var previewAssetStore: PreviewAssetStore
    private lateinit var previewAssetLoader: PreviewAssetLoader
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
    private lateinit var liveGeoThreatPresenter: LiveGeoThreatPresenter
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
    private var geoAwarenessZones: List<GeoZone> = emptyList()
    private var geoAwarenessDatasetLoadAttempted = false
    private var geoZoneDatasetInfo: GeoZoneDatasetInfo? = null
    private var geoAwarenessHealth: GeoAwarenessHealth? = null
    private var geoAwarenessLoadError: Throwable? = null
    private var geoZoneValidationResult: GeoZoneValidationResult? = null
    private var geoZoneOverlayController: GeoZoneOverlayController? = null
    private var terrainCoverageOverlayController: TerrainCoverageOverlayController? = null
    private var latestGeoAwarenessResult: GeoAwarenessResult = GeoAwarenessResult.clear()
    private var liveGeoAwarenessChecker: LiveGeoAwarenessChecker? = null
    private var latestLiveGeoZones: List<GeoZone> = emptyList()
    private var latestLiveGeoProximity: LiveGeoAwarenessProximityResult? = null
    private var latestLiveGeoThreats: List<LiveGeoAwarenessProximityResult> = emptyList()
    private var liveGeoAwarenessStatusBinder: LiveGeoAwarenessPanelBinder? = null
    private var latestLiveDronePosition: LatLon? = null
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
    private var geoZoneReloadInProgress: Boolean = false
    private var obstaclePlacementMode: Boolean = false
    private var selectedObstacleMode: OsmdroidObstacleEditor.Mode = OsmdroidObstacleEditor.Mode.CIRCLE
    private var lastRenderedDownloadedMissionSignature: String? = null
    private var geoPlanningJob: Job? = null
    private var liveGeoUpdateJob: Job? = null
    private var lastLiveGeoUpdateUptimeMs = 0L
    private var lastTopLiveGeoStatusSignature: String? = null
    private var previewAssetLoadJob: Job? = null
    private var activePreviewMode: PreviewMode = PreviewMode.MAP
    private lateinit var orthoOverlayController: OrthoOverlayController
    private lateinit var previewModeRenderer: PreviewModeRenderer
    private val pointCloudMissionOverlayBuilder = PointCloudMissionOverlayBuilder()
    private val selectedSurveyWaypointIndex: Int?
        get() = missionEditorCoordinator.selectedIndex
    private val selectedTerrainWaypointWorkflow: PlanningWorkflow
        get() = missionEditorCoordinator.selectedWorkflow
    private var previewHeightColorModeEnabled: Boolean = false
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
        private const val GEO_ZONE_TOGGLE_TAG = "GeoZoneToggle"
        private const val GEO_PLANNING_STATUS_TAG = "GeoPlanningStatus"
        private const val GEO_UPLOAD_GUARD_TAG = "GeoUploadGuard"
        private const val LIVE_GEO_AWARENESS_TAG = "LiveGeoAwareness"
        private const val MAP_FLIGHT_TRACE_TAG = "MapFlightTrace"
        private const val TERRAIN_GRID_TAG = "TerrainGrid"
        private const val MIN_VALID_ABS_COORDINATE = 1e-4
        private const val DEFAULT_NEAR_ZONE_THRESHOLD_METERS = 100.0
        private const val MAX_INITIAL_DRONE_CENTER_ATTEMPTS = 20
        private const val PREVIEW_MAP_FIT_PADDING_PX = 96
        private const val MIN_PREVIEW_MAP_SPAN_METERS = 10.0
        private const val TILE_SIZE_PX = 256.0
        private const val MIN_MAP_VIEWPORT_PX = 320
        private const val MIN_PREVIEW_BOUNDS_SPAN_DEGREES = 0.000001
        private const val MIN_PREVIEW_MERCATOR_SPAN = 0.000001
        private const val MIN_MERCATOR_LATITUDE = -85.05112878
        private const val MAX_MERCATOR_LATITUDE = 85.05112878
        private const val MIN_PREVIEW_MAP_ZOOM = 2.0
        private const val MAX_PREVIEW_MAP_ZOOM = 21.0
        private const val LIVE_GEO_UPDATE_INTERVAL_MS = 500L
        private const val REQUEST_HOME_OPEN_TIFF = 3301
        private const val REQUEST_HOME_OPEN_WORLD = 3302
        private const val REQUEST_HOME_OPEN_PLY = 3303
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
        previewAssetStore = PreviewAssetStore(requireContext())
        previewAssetLoader = PreviewAssetLoader(requireContext())
        geoEventLogger = GeoAwarenessEventLogger(requireContext().applicationContext)
        val geoIncidentLogger = GeoIncidentLogger(
            GeoIncidentEncryptedLogStore(requireContext().applicationContext)
        )
        operatorEventLogger = OperatorFlightEventLogger(geoEventLogger)
        geoAwarenessDatasetController = GeoAwarenessDatasetController(requireContext())
        liveGeoThreatPresenter = LiveGeoThreatPresenter(requireContext())
        geoAwarenessDialogController = GeoAwarenessDialogController(requireContext(), geoAuthorizationSession)
        geoAwarenessEventTracker = GeoAwarenessEventTracker(
            eventLogger = geoEventLogger,
            incidentLogger = geoIncidentLogger,
            metadata = {
                GeoAwarenessEventTracker.Metadata(
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = geoAwarenessHealth?.state?.name,
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
        bindPreviewAssetButtons()
        missionMapUiActionController.applyMapInsets()
        observeDroneViewModel()
        observeMapState()
        observeHomeTelemetry()
        observeMissionMapViewModel()
        observeGeoAwarenessSharedState()
        observePreviewSettings()

        mapViewModel.restoreFromMapState(activityViewModel.mapState.value ?: MainActivityViewModel.MapState.Idle)
        activePreviewMode = PreviewMode.MAP
        renderPreviewMode()
        binding.root.post {
            if (_binding != null) {
                restorePersistedPreviewAssets()
            }
        }
    }

    private fun initializeMapView(view: View) {
        mapView = view.findViewById(R.id.osmMap)
        orthoOverlayController = OrthoOverlayController(
            mapView = mapView,
            assetsViewModel = previewAssetsViewModel,
            focusOverlay = ::focusOrthoOnMap,
        )
        previewModeRenderer = PreviewModeRenderer(
            context = requireContext(),
            binding = binding,
            assetsViewModel = previewAssetsViewModel,
            renderMapPath = ::renderCurrentSurveyPathOnMap,
            renderOrtho = ::renderHomeOrthoOverlay,
            clearOrtho = ::removeHomeOrthoOverlay,
            renderTerrainStatus = ::renderTerrainGridStatus,
            renderMissionOverlay = ::updatePointCloudMissionOverlay,
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
            onDisarmed = { resetCurrentFlightUgzAuthorizations("disarmed") },
        )
        osmdroidMapController.setSurveyWaypointEditCallbacks(
            onSelected = { index -> onSurveyWaypointSelected(index) },
            onMoved = { index, point -> onSurveyWaypointMoved(index, point) }
        )

        osmdroidPolygonEditor = OsmdroidPolygonEditor(requireActivity(), activityViewModel, mapView)
        osmdroidPolygonEditor.init()

        osmdroidRouteWaypointEditor = OsmdroidRouteWaypointEditor(requireContext(), activityViewModel, mapView)
        osmdroidRouteWaypointEditor.init()
        osmdroidRouteWaypointEditor.setTerrainWaypointSelectionCallback { index ->
            onTerrainWaypointSelected(PlanningWorkflow.POINTS, index)
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
                onTerrainWaypointSelected(PlanningWorkflow.AREA, selectedIndex)
            }
            if (selectedIndex != null) {
                positionHeightEditorNearViewPoint(binding.homePointCloudGlView, x, y)
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
                cancelObstaclePlacement()
            },
            onHomePlaced = ::placePlannedHome,
        )
        initialViewportController = InitialMapViewportController(
            rootViewProvider = { _binding?.root },
            mapController = osmdroidMapController,
            hasPendingPreviewFocus = previewAssetsViewModel::hasPendingMapFocusRequest,
            focusPendingPreview = ::focusPreviewAssetOnMapIfRequested,
            centerOnDrone = osmdroidMapController::centerOnDrone,
            maxDroneAttempts = MAX_INITIAL_DRONE_CENTER_ATTEMPTS,
        )
        viewLifecycleController = HomeMapViewLifecycleController(
            activityProvider = { activity },
            mapView = mapView,
            mapController = osmdroidMapController,
            pointCloudView = binding.homePointCloudGlView,
            refreshMapLabels = esriMapLayers::refreshLabelsEnabled,
            isPointCloudVisible = { activePreviewMode == PreviewMode.POINT_CLOUD },
        )

        terrainCoverageOverlayController = TerrainCoverageOverlayController(requireContext(), mapView)

        geoZoneOverlayController = GeoZoneOverlayController(requireContext(), mapView)
        liveGeoAwarenessChecker = LiveGeoAwarenessChecker()
        requireView().findViewById<View?>(R.id.liveGeoAwarenessPanel)?.visibility = View.GONE
        liveGeoAwarenessStatusBinder = null
        loadGeoAwarenessZonesIfNeeded()
        renderGeoAwarenessLayerIfVisible()
        updateTopLiveGeoStatus(getString(R.string.live_geo_unknown_status), "#AAB5C6")
        updateGeoAwarenessPlanningStatus()
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
            heightColorsEnabled = { previewHeightColorModeEnabled },
            removeObstacle = activityViewModel::removeMissionObstacle,
        )
        missionEditorCoordinator = MissionEditorCoordinator(
            context = requireContext(),
            binding = binding,
            viewModel = activityViewModel,
            mapController = osmdroidMapController,
            routeEditor = osmdroidRouteWaypointEditor,
            terrainSampler = terrainMissionCoordinator::resampleWaypoint,
            updateDistance = ::updateFlightDistance,
            renderPath = ::renderCurrentSurveyPathOnMap,
            updatePointCloudOverlay = ::updatePointCloudMissionOverlay,
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
        missionMapActionHandler = MissionMapActionHandler(
            context = requireContext(),
            root = requireView(),
            viewModel = activityViewModel,
            eventLogger = operatorEventLogger,
            cancelPlanning = ::cancelPendingMissionPlanning,
            cancelObstaclePlacement = ::cancelObstaclePlacement,
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
                handleGeoAwarenessBeforeUpload {
                    showMissionUploadSummaryDialog(onAllowed)
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
                renderDownloadedMission(droneViewModel.missionItems.value.orEmpty(), force = true)
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
                cyclePreviewMode = ::cyclePreviewMode,
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
                addObstacle = ::toggleObstaclePlacement,
                clearObstacles = ::clearMissionObstacles,
                selectCircleObstacle = { selectObstacleMode(OsmdroidObstacleEditor.Mode.CIRCLE) },
                selectPolygonObstacle = { selectObstacleMode(OsmdroidObstacleEditor.Mode.POLYGON) },
                obstacleRadiusChanged = {
                    activityViewModel.updateObstacleRadius(it)
                    renderObstacleControls()
                },
                deleteWaypoint = ::deleteSelectedSurveyWaypoint,
                cancelWaypoint = ::cancelSurveyWaypointSelection,
                decreaseWaypointHeight = { adjustSelectedSurveyWaypointHeight(-1.0) },
                increaseWaypointHeight = { adjustSelectedSurveyWaypointHeight(1.0) },
                applyWaypointHeight = ::applySelectedWaypointHeightField,
            ),
            initialObstacleRadiusMeters = (activityViewModel.obstacleRadiusMeters.value ?: 5.0).toInt(),
        )
        planningWorkflowUiController.bind(viewLifecycleOwner) { onPlanningWorkflowChanged() }
        renderAddHomeButton()
        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
    }
    private fun bindPreviewAssetButtons() {
        missionMapUiActionController.bindPreview(
            MissionMapUiActionController.PreviewActions(
                selectMode = ::selectPreviewMode,
                primary = {
                    when (activePreviewMode) {
                        PreviewMode.MAP -> Unit
                        PreviewMode.ORTHO -> openPreviewFilePicker(REQUEST_HOME_OPEN_TIFF)
                        PreviewMode.POINT_CLOUD -> openPreviewFilePicker(REQUEST_HOME_OPEN_PLY)
                    }
                },
                secondary = {
                    when (activePreviewMode) {
                        PreviewMode.MAP -> Unit
                        PreviewMode.ORTHO -> openPreviewFilePicker(REQUEST_HOME_OPEN_WORLD)
                        PreviewMode.POINT_CLOUD -> binding.homePointCloudGlView.resetCamera()
                    }
                },
                toggleHeightColors = {
                    previewAssetsViewModel.updateSettings {
                        copy(heightColorModeEnabled = !heightColorModeEnabled)
                    }
                },
                backgroundChanged = { checked ->
                    if (activePreviewMode == PreviewMode.ORTHO) {
                        binding.osmMap.overlayManager.tilesOverlay?.isEnabled = checked
                        binding.osmMap.invalidate()
                    }
                },
                opacityChanged = { opacity ->
                    orthoOverlayController.setOpacity(opacity)
                    binding.homePointCloudGlView.setPointCloudOpacity(opacity)
                    binding.osmMap.invalidate()
                },
            )
        )
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

    private fun toggleObstaclePlacement() {
        if (selectedObstacleMode == OsmdroidObstacleEditor.Mode.POLYGON && obstaclePlacementMode) {
            if (osmdroidObstacleEditor.finishPolygon()) obstaclePlacementMode = false
        } else {
            if (activePreviewMode == PreviewMode.POINT_CLOUD) selectPreviewMode(PreviewMode.MAP)
            obstaclePlacementMode = true
            if (selectedObstacleMode == OsmdroidObstacleEditor.Mode.CIRCLE) {
                osmdroidObstacleEditor.startCirclePlacement(activityViewModel.obstacleRadiusMeters.value ?: 5.0)
                Toast.makeText(requireContext(), R.string.obstacle_place_circle_prompt, Toast.LENGTH_SHORT).show()
            } else {
                osmdroidObstacleEditor.startPolygonPlacement()
                Toast.makeText(requireContext(), R.string.obstacle_place_polygon_prompt, Toast.LENGTH_SHORT).show()
            }
            osmdroidPolygonEditor.setEnabled(false)
        }
        renderObstacleControls()
    }

    private fun clearMissionObstacles() {
        obstaclePlacementMode = false
        osmdroidObstacleEditor.cancelPlacement()
        activityViewModel.clearMissionObstacles()
        renderObstacleControls()
    }

    private fun selectObstacleMode(mode: OsmdroidObstacleEditor.Mode) {
        selectedObstacleMode = mode
        obstaclePlacementMode = false
        osmdroidObstacleEditor.cancelPlacement()
        renderObstacleControls()
    }

    private fun deleteSelectedSurveyWaypoint() {
        missionEditorCoordinator.deleteSelected { updateFlightDistance(currentMissionPath()) }
    }

    private fun cancelSurveyWaypointSelection() {
        missionEditorCoordinator.cancelSelection()
    }
    private fun selectPreviewMode(mode: PreviewMode) {
        activePreviewMode = mode
        if (mode == PreviewMode.POINT_CLOUD && !isSurveyMode()) restorePointCloudOnDemand()
        renderPreviewMode()
    }

    private fun onPlanningWorkflowChanged() {
        obstaclePlacementMode = false
        osmdroidObstacleEditor.cancelPlacement()
        stopMissionSimulation()
        renderObstacleControls()
        updateRouteSummary()
        updateRouteEditorEnabled()
        updateSurveyWaypointEditorEnabled()
        updateGeoAwarenessPlanningStatus()
        updateMissionSummaryCard()
        updateTerrainCoverageOverlay()
    }

    private fun observePreviewSettings() {
        missionPresentationObserver.bindPreviewAssets(
            viewModel = previewAssetsViewModel,
            onSettings = { settings ->
            previewHeightColorModeEnabled = settings.heightColorModeEnabled
            binding.previewBackgroundSwitch.isChecked = settings.orthoBackgroundEnabled
            if (binding.previewOpacitySlider.value != settings.orthoOpacity) {
                binding.previewOpacitySlider.value = settings.orthoOpacity
            }
            binding.homePointCloudGlView.setPointCloudOpacity(settings.orthoOpacity)
            binding.homePointCloudGlView.setPointSize(settings.pointCloudPointSize)
            binding.homePointCloudGlView.setHeightColorModeEnabled(settings.heightColorModeEnabled)
            orthoOverlayController.setOpacity(settings.orthoOpacity)
            if (activePreviewMode == PreviewMode.ORTHO) {
                binding.osmMap.overlayManager.tilesOverlay?.isEnabled = settings.orthoBackgroundEnabled
            }
            renderCurrentSurveyPathOnMap()
            updatePointCloudMissionOverlay()
            renderPreviewMode()
            },
            onAssetVersion = {
            refreshPreviewAssets()
            renderCurrentSurveyPathOnMap()
            redrawAreaMissionIfEditable()
            },
        )
        missionPresentationObserver.bind(droneViewModel.terrainPreview) {
            if (isSurveyMode()) renderPreviewMode()
        }
    }

    private fun toggleObstaclePanel() {
        val panel = requireView().findViewById<View>(R.id.home_obstacle_panel)
        val show = panel.visibility != View.VISIBLE
        if (show) {
            mapViewModel.setPlanningPanelVisible(false)
            renderObstacleControls()
        } else {
            cancelObstaclePlacement()
        }
        panel.visibility = if (show) View.VISIBLE else View.GONE
        missionMapUiActionController.setDockButtonSelected(R.id.utility_obstacles_button, show)
    }

    private fun hideObstaclePanel() {
        requireView().findViewById<View>(R.id.home_obstacle_panel).visibility = View.GONE
        missionMapUiActionController.setDockButtonSelected(R.id.utility_obstacles_button, false)
        cancelObstaclePlacement()
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
            updateLiveGeoAwarenessFromActiveSource()
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
        renderObstacleControls()
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

    private fun renderObstacleControls() {
        planningWorkflowUiController.renderObstacleControls(
            mode = selectedObstacleMode,
            placementActive = obstaclePlacementMode,
            radiusMeters = (activityViewModel.obstacleRadiusMeters.value ?: 5.0).toInt(),
        )
    }
    private fun renderObstacleList(obstacles: List<MissionObstacle>) {
        missionMapRenderer.renderObstacleList(obstacles)
    }
    private fun updateRouteSummary() {
        val routeSummary = requireView().findViewById<TextView?>(R.id.right_panel_route_summary) ?: return
        val waypoints = activityViewModel.routeWaypoints.value.orEmpty()
        routeSummary.text = getString(R.string.route_summary_format, waypoints.size)
    }

    private fun updateMissionSummaryCard() {
        missionSummaryPresenter.renderCard()
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

    private fun pointRouteShouldReverse(terrainPath: List<TerrainWaypoint>): Boolean {
        return pointRouteCoordinator.shouldReverse(terrainPath)
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
        activePreviewMode = PreviewMode.MAP
        renderPreviewMode()
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

    private fun updateRouteEditorEnabled() {
        missionEditorCoordinator.updateRouteEnabled(homePlacementMode)
    }

    private fun updateSurveyWaypointEditorEnabled() {
        missionEditorCoordinator.updateSurveyEnabled()
    }

    private fun onSurveyWaypointSelected(index: Int?) {
        missionEditorCoordinator.selectSurvey(index)
    }

    private fun onTerrainWaypointSelected(workflow: PlanningWorkflow, index: Int?) {
        missionEditorCoordinator.select(workflow, index, activePreviewMode)
    }

    private fun positionHeightEditorNearViewPoint(source: View, x: Float, y: Float) {
        missionEditorCoordinator.positionNearViewPoint(source, x, y)
    }

    private fun onSurveyWaypointMoved(index: Int, point: LatLng) {
        missionEditorCoordinator.moveSurvey(index, point)
    }

    private fun adjustSelectedSurveyWaypointHeight(deltaMeters: Double) {
        missionEditorCoordinator.adjustHeight(deltaMeters)
    }

    private fun updateSelectedSurveyWaypointHeightLabel() {
        missionEditorCoordinator.refreshHeightLabel()
    }

    private fun applySelectedWaypointHeightField() {
        missionEditorCoordinator.applyHeightField()
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

            updateLiveGeoAwarenessFromActiveSource()
            updatePointCloudMissionOverlay()
        }

        missionMapObserverCoordinator.observe(droneViewModel.conStateLiveData) {
            if (it != true && missionSimulationController.usesLiveDroneHome) stopMissionSimulationIfActive()
            syncLatestDroneLocationSnapshot(droneViewModel.droneLocationLiveData.value)
            renderAddHomeButton()
            updateLiveGeoAwarenessFromActiveSource()
            updatePointCloudMissionOverlay()
        }

        missionMapObserverCoordinator.observe(droneViewModel.gpsFixType) {
            syncLatestDroneLocationSnapshot(droneViewModel.droneLocationLiveData.value)
            updateLiveGeoAwarenessFromActiveSource()
        }

        missionMapObserverCoordinator.observe(droneViewModel.droneHeading) { droneHeading ->
            droneHeading?.let { heading ->
                latestRealDroneHeadingDegrees = heading
                osmdroidMapController.updateDroneHeadingDegrees(heading.toFloat())
            }
            updatePointCloudMissionOverlay()
        }

        missionMapObserverCoordinator.observe(droneViewModel.droneGroundSpeedMetersPerSecond) { speed ->
            latestRealDroneGroundSpeedMetersPerSecond = speed
        }

        missionMapObserverCoordinator.observe(droneViewModel.droneVerticalSpeedMetersPerSecond) { speed ->
            latestRealDroneVerticalSpeedMetersPerSecond = speed
        }

        missionMapObserverCoordinator.observe(droneViewModel.droneAltitudeAmslMeters) { altitudeAmslMeters ->
            latestRealDroneAltitudeAmslMeters = altitudeAmslMeters
            updateLiveGeoAwarenessFromActiveSource()
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
            osmdroidPolygonEditor.setVertices(vertices)
            updateGeoAwarenessPlanningStatus()
            updateMissionSummaryCard()
            updatePointCloudMissionOverlay()
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
                osmdroidRouteWaypointEditor.setWaypoints(
                    activityViewModel.routeWaypoints.value.orEmpty(),
                    plannedPath = activityViewModel.plannedRoutePath.value.orEmpty(),
                    terrainPath = activityViewModel.terrainRouteWaypoints.value.orEmpty(),
                    reverseDirection = pointRouteShouldReverse(
                        activityViewModel.terrainRouteWaypoints.value.orEmpty()
                    )
                )
            }
            updateCurrentFlightDistance()
            updateMissionSummaryCard()
        }

        missionMapObserverCoordinator.observe(activityViewModel.missionObstacles) { obstacles ->
            stopMissionSimulationIfActive()
            if (selectedObstacleMode == OsmdroidObstacleEditor.Mode.CIRCLE) {
                obstaclePlacementMode = false
                osmdroidObstacleEditor.cancelPlacement()
            }
            osmdroidObstacleEditor.renderObstacles(obstacles.orEmpty())
            renderObstacleControls()
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
            renderDownloadedMission(missionItems.orEmpty())
            updatePointCloudMissionOverlay()
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

    private fun renderDownloadedMission(
        missionItems: List<MissionItemInt>,
        force: Boolean = false,
    ) {
        missionMapRenderer.renderDownloaded(missionItems, force)
    }
    private fun observeMapState() {
        missionMapObserverCoordinator.bindParameterObservers(
            MissionMapObserverCoordinator.Actions(
                stopSimulation = ::stopMissionSimulationIfActive,
                scheduleAreaRedraw = { scheduleAreaMissionRedraw() },
                flightAltitudeChanged = ::onFlightAltitudeChanged,
                surveyHeightChanged = ::updateSelectedSurveyWaypointHeightLabel,
                surveyGridChanged = ::onSurveyGridParametersChanged,
                planningModeChanged = ::onPlanningOperationModeChanged,
                updateTerrainOverlay = ::updateTerrainCoverageOverlay,
                updateMissionSummary = ::updateMissionSummaryCard,
                renderPreview = ::renderPreviewMode,
            )
        )
        missionMapObserverCoordinator.observe(activityViewModel.surveyPath) { surveyPath ->
            stopMissionSimulationIfActive()
            val hasPolygon = (activityViewModel.missionArea.value?.vertices?.size ?: 0) >= 3
            val hasRoute = activityViewModel.routeWaypoints.value.orEmpty().size >= 2
            mapViewModel.setMissionAreaAvailable(hasPolygon || hasRoute || !surveyPath.isNullOrEmpty())
            updateSurveyWaypointEditorEnabled()
            scheduleGeoAwarenessPlanningStatusUpdate()
            updateMissionSummaryCard()
            updatePointCloudMissionOverlay()
            updateTerrainCoverageOverlay()
        }

        missionMapObserverCoordinator.observe(activityViewModel.terrainSurveyWaypoints) {
            stopMissionSimulationIfActive()
            updatePointCloudMissionOverlay()
        }

        missionMapObserverCoordinator.observe(activityViewModel.routeWaypoints) { waypoints ->
            stopMissionSimulationIfActive()
            osmdroidRouteWaypointEditor.setWaypoints(
                waypoints.orEmpty(),
                plannedPath = activityViewModel.plannedRoutePath.value.orEmpty(),
                terrainPath = activityViewModel.terrainRouteWaypoints.value.orEmpty(),
                reverseDirection = pointRouteShouldReverse(
                    activityViewModel.terrainRouteWaypoints.value.orEmpty()
                )
            )
            val hasPolygon = (activityViewModel.missionArea.value?.vertices?.size ?: 0) >= 3
            val hasSurveyPath = !activityViewModel.surveyPath.value.isNullOrEmpty()
            mapViewModel.setMissionAreaAvailable(hasPolygon || hasSurveyPath || waypoints.orEmpty().size >= 2)
            if (activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS) {
                updateRouteDistance(waypoints.orEmpty())
            }
            updateRouteSummary()
            updateMissionSummaryCard()
            updateGeoAwarenessPlanningStatus()
            updatePointCloudMissionOverlay()
            updateSurveyWaypointEditorEnabled()
            generatePointRouteTerrainPath()
            updateTerrainCoverageOverlay()
        }

        missionMapObserverCoordinator.observe(activityViewModel.terrainRouteWaypoints) { terrainWaypoints ->
            stopMissionSimulationIfActive()
            osmdroidRouteWaypointEditor.setWaypoints(
                activityViewModel.routeWaypoints.value.orEmpty(),
                plannedPath = activityViewModel.plannedRoutePath.value.orEmpty(),
                terrainPath = terrainWaypoints.orEmpty(),
                reverseDirection = pointRouteShouldReverse(terrainWaypoints.orEmpty())
            )
            updateMissionSummaryCard()
            updatePointCloudMissionOverlay()
            updateTerrainCoverageOverlay()
        }

        missionMapObserverCoordinator.observe(activityViewModel.plannedRoutePath) { plannedPath ->
            stopMissionSimulationIfActive()
            osmdroidRouteWaypointEditor.setWaypoints(
                activityViewModel.routeWaypoints.value.orEmpty(),
                plannedPath = plannedPath.orEmpty(),
                terrainPath = activityViewModel.terrainRouteWaypoints.value.orEmpty(),
                reverseDirection = pointRouteShouldReverse(
                    activityViewModel.terrainRouteWaypoints.value.orEmpty()
                )
            )
            updateMissionSummaryCard()
            updatePointCloudMissionOverlay()
            updateTerrainCoverageOverlay()
        }

        missionMapObserverCoordinator.observe(activityViewModel.mapState) { mapState ->
            mapViewModel.updateFromMapState(mapState)
            renderWorkflowSelection(activityViewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA)
            updateRouteEditorEnabled()
            updateSurveyWaypointEditorEnabled()
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
            updateMissionSummaryCard()
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
            updateGeoAwarenessPlanningStatus()
            updateMissionSummaryCard()
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

    private fun cancelObstaclePlacement() {
        obstaclePlacementMode = false
        osmdroidObstacleEditor.cancelPlacement()
        renderObstacleControls()
    }

    private fun observeHomeTelemetry() {
        missionPresentationObserver.bindHomeTelemetry(homeTelemetryViewModel, homeMapTelemetryBinder)
    }



    private fun observeMissionMapViewModel() {
        missionPresentationObserver.bindMapUi(mapViewModel, ::renderHomeMapUiState)
    }

    private fun renderHomeMapUiState(state: HomeMapUiState) {
        isDrawingModeActive = state.interactionState.isDrawingEnabled
        geoZoneOverlayController?.setZoneDetailsEnabled(!state.interactionState.isDrawingEnabled)
        osmdroidPolygonEditor.setEnabled(
            state.interactionState.isDrawingEnabled &&
                activityViewModel.activePlanningWorkflow.value != PlanningWorkflow.POINTS &&
                !obstaclePlacementMode &&
                !homePlacementMode
        )
        updateRouteEditorEnabled()
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

    private fun renderCurrentSurveyPathOnMap() {
        missionMapRenderer.renderCurrentPath()
    }
    private fun isSurveyMode(): Boolean =
        (activityViewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY) ==
            PlanningOperationMode.SURVEY

    private fun current3dPointCloud(): PointCloudData? =
        if (isSurveyMode()) {
            droneViewModel.terrainPreview.value?.pointCloud
        } else {
            previewAssetsViewModel.pointCloudAsset?.pointCloud
        }

    private fun cyclePreviewMode() {
        activePreviewMode = if (isSurveyMode()) {
            when (activePreviewMode) {
                PreviewMode.MAP, PreviewMode.ORTHO -> PreviewMode.POINT_CLOUD
                PreviewMode.POINT_CLOUD -> PreviewMode.MAP
            }
        } else {
            when (activePreviewMode) {
                PreviewMode.MAP -> PreviewMode.ORTHO
                PreviewMode.ORTHO -> PreviewMode.POINT_CLOUD
                PreviewMode.POINT_CLOUD -> PreviewMode.MAP
            }
        }
        if (activePreviewMode == PreviewMode.POINT_CLOUD && !isSurveyMode()) restorePointCloudOnDemand()
        renderPreviewMode()
    }

    private fun refreshPreviewAssets() {
        val pointCloud = current3dPointCloud()
        if (pointCloud == null) {
            binding.homePointCloudGlView.clearPointCloud()
        }
        if (previewAssetsViewModel.orthoAsset == null) removeHomeOrthoOverlay()
        renderPreviewMode()
    }

    private fun renderPreviewMode() {
        if (_binding == null) return
        if (isSurveyMode() && activePreviewMode == PreviewMode.ORTHO) activePreviewMode = PreviewMode.MAP
        previewModeRenderer.render(activePreviewMode, isSurveyMode(), current3dPointCloud())
    }

    private fun renderTerrainGridStatus() {
        if (isSurveyMode()) {
            val terrain = droneViewModel.terrainPreview.value
            binding.previewTerrainStatus.visibility = View.VISIBLE
            binding.previewTerrainStatus.text = if (terrain == null) {
                getString(R.string.terrain_3d_not_ready)
            } else {
                getString(
                    R.string.terrain_3d_ready,
                    terrain.fileNames.joinToString(),
                    terrain.pointCloud.displayedPointCount,
                    terrain.gridSpacingMeters,
                )
            }
            return
        }
        val summary = previewAssetsViewModel.pointCloudTerrainSummary
        binding.previewTerrainStatus.visibility = View.VISIBLE
        binding.previewTerrainStatus.text = when {
            previewAssetsViewModel.pointCloudAsset == null -> "Terrain --"
            summary == null -> "Terrain building"
            !summary.isGeoreferenced -> "Terrain no GPS"
            else -> "Terrain ${formatCompactCount(summary.cellCount)}"
        }
    }

    private fun formatCompactCount(value: Int): String {
        return when {
            value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
            value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000.0)
            else -> value.toString()
        }
    }

    private fun renderHomeOrthoOverlay() {
        orthoOverlayController.render()
    }

    private fun removeHomeOrthoOverlay() {
        if (::orthoOverlayController.isInitialized) orthoOverlayController.clear()
    }

    private fun updatePointCloudMissionOverlay() {
        if (_binding == null) return
        if (activePreviewMode != PreviewMode.POINT_CLOUD) return
        val pointCloud = current3dPointCloud()
        val downloadedPoints = previewAssetsViewModel.retainedDroneMissionPath.value.orEmpty()
        val downloadedWaypoints = previewAssetsViewModel.retainedDroneMissionWaypoints.value.orEmpty()
        val showingDownloadedMission =
            activityViewModel.mapState.value == MainActivityViewModel.MapState.Idle && downloadedPoints.isNotEmpty()
        val areaVertices = if (showingDownloadedMission) {
            emptyList()
        } else {
            activityViewModel.missionArea.value?.vertices.orEmpty()
        }
        val droneLocation = currentOffsetDroneLocation()
        val pointWorkflow = activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS
        val surveyPoints = when {
            showingDownloadedMission -> downloadedPoints
            pointWorkflow -> emptyList()
            else -> activityViewModel.surveyPath.value.orEmpty()
        }
        val surveyZValues = when {
            showingDownloadedMission && downloadedWaypoints.size == surveyPoints.size -> downloadedWaypoints.map { it.altitudeMeters }
            showingDownloadedMission -> null
            else -> PointCloudTerrainStyleMapper.surveyZValues(
                surveyPoints,
                activityViewModel.terrainSurveyWaypoints.value.orEmpty(),
            )
        }
        val terrainRoute = activityViewModel.terrainRouteWaypoints.value.orEmpty()
        val routePoints = if (showingDownloadedMission || !pointWorkflow) emptyList() else {
            terrainRoute.takeIf { it.size >= 2 }?.map { LatLng(it.latLon.lat, it.latLon.lon) }
                ?: orderPathForPlannedHome(
                    activityViewModel.plannedRoutePath.value.orEmpty().takeIf { it.size >= 2 }
                        ?: activityViewModel.routeWaypoints.value.orEmpty().map { LatLng(it.latitude, it.longitude) }
                )
        }
        val routeZValues = terrainRoute.takeIf { it.size == routePoints.size && it.isNotEmpty() }
            ?.map { it.displayAltitudeMeters.toFloat() }
        binding.homePointCloudGlView.setMissionOverlay(
            pointCloudMissionOverlayBuilder.build(
                PointCloudMissionOverlayBuilder.Input(
                    pointCloud = pointCloud,
                    areaPoints = areaVertices,
                    areaZValues = PointCloudTerrainStyleMapper.groundZValues(
                        areaVertices,
                        previewAssetsViewModel.pointCloudTerrainModel,
                    ),
                    surveyPoints = surveyPoints,
                    surveyZValues = surveyZValues,
                    routePoints = routePoints,
                    routeZValues = routeZValues,
                    droneLocation = droneLocation?.let { LatLng(it.latitude, it.longitude) },
                    droneHeadingDegrees = droneViewModel.droneHeading.value ?: 0.0,
                    selectedWorkflow = selectedTerrainWaypointWorkflow,
                    selectedWaypointIndex = selectedSurveyWaypointIndex,
                )
            )
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        previewAssetStore.persistReadPermission(uri, data.flags)
        when (requestCode) {
            REQUEST_HOME_OPEN_TIFF -> loadHomeOrthoImage(uri)
            REQUEST_HOME_OPEN_WORLD -> loadHomeOrthoWorldFile(uri)
            REQUEST_HOME_OPEN_PLY -> loadHomePointCloud(uri)
        }
    }

    private fun openPreviewFilePicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, requestCode)
    }

    private fun loadHomeOrthoImage(uri: Uri) {
        val fileName = previewAssetStore.displayName(uri) ?: getString(R.string.ortho_unknown_image)
        if (!fileName.lowercase(Locale.US).endsWith(".tif") && !fileName.lowercase(Locale.US).endsWith(".tiff")) {
            Toast.makeText(requireContext(), R.string.ortho_select_tif, Toast.LENGTH_SHORT).show()
            return
        }

        previewAssetsViewModel.clearOrtho()
        removeHomeOrthoOverlay()
        previewAssetLoadJob?.cancel()
        previewAssetLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    previewAssetLoader.loadOrthoImage(uri)
                }
            }
            result.onSuccess { decoded ->
                previewAssetsViewModel.setOrthoImage(
                    bitmap = decoded.bitmap,
                    bitmapFileName = fileName,
                    bitmapUri = uri,
                    sourceWidth = decoded.sourceWidth,
                    sourceHeight = decoded.sourceHeight,
                    notifyChange = false
                )
                previewAssetStore.saveOrthoImage(uri, fileName)
                removeHomeOrthoOverlay()
                Toast.makeText(requireContext(), R.string.ortho_load_world_next, Toast.LENGTH_SHORT).show()
                renderPreviewMode()
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    getString(R.string.ortho_load_failed, error.message ?: error.javaClass.simpleName),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun loadHomeOrthoWorldFile(uri: Uri) {
        val fileName = previewAssetStore.displayName(uri) ?: getString(R.string.ortho_unknown_world)
        if (!fileName.lowercase(Locale.US).endsWith(".tfw") && !fileName.lowercase(Locale.US).endsWith(".wld")) {
            Toast.makeText(requireContext(), R.string.ortho_select_world, Toast.LENGTH_SHORT).show()
            return
        }
        val asset = previewAssetsViewModel.orthoAsset
        if (asset == null) {
            Toast.makeText(requireContext(), R.string.ortho_load_image_first, Toast.LENGTH_SHORT).show()
            return
        }

        previewAssetLoadJob?.cancel()
        previewAssetLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    previewAssetLoader.loadWorldFile(uri, asset.sourceWidth, asset.sourceHeight)
                }
            }
            result.onSuccess { bounds ->
                previewAssetsViewModel.setOrthoBounds(bounds, fileName, uri)
                previewAssetStore.saveOrthoWorld(uri, fileName)
                activePreviewMode = PreviewMode.ORTHO
                renderPreviewMode()
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    getString(R.string.ortho_load_failed, error.message ?: error.javaClass.simpleName),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun loadHomePointCloud(uri: Uri, activatePreview: Boolean = true) {
        val fileName = previewAssetStore.displayName(uri) ?: getString(R.string.point_cloud_unknown_file)
        if (!previewAssetStore.supportsPointCloud(fileName)) {
            Toast.makeText(requireContext(), R.string.point_cloud_select_ply, Toast.LENGTH_SHORT).show()
            return
        }

        previewAssetsViewModel.clearPointCloud()
        binding.homePointCloudGlView.clearPointCloud()
        previewAssetLoadJob?.cancel()
        previewAssetLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    previewAssetLoader.loadPointCloud(uri, fileName)
                }
            }
            result.onSuccess { pointCloud ->
                previewAssetsViewModel.setPointCloud(pointCloud, fileName, uri)
                previewAssetStore.savePointCloud(uri, fileName)
                if (activatePreview) {
                    activePreviewMode = PreviewMode.POINT_CLOUD
                    binding.homePointCloudGlView.setPointCloud(pointCloud)
                    binding.homePointCloudGlView.setHeightColorModeEnabled(previewHeightColorModeEnabled)
                }
                warmPointCloudTerrainGrid(showToast = activatePreview)
                generatePointRouteTerrainPath()
                updatePointCloudMissionOverlay()
                renderPreviewMode()
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    getString(R.string.point_cloud_load_failed, error.message ?: error.javaClass.simpleName),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun restorePersistedPreviewAssets() {
        val persistedAssets = previewAssetStore.readPersistedAssets()
        val imageUri = persistedAssets.orthoImageUri
        val imageName = persistedAssets.orthoImageName ?: getString(R.string.ortho_unknown_image)
        val worldUri = persistedAssets.orthoWorldUri
        val worldName = persistedAssets.orthoWorldName
        if (imageUri == null) {
            restorePointCloudOnDemand(activatePreview = false)
            return
        }

        previewAssetLoadJob?.cancel()
        previewAssetLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            if (previewAssetsViewModel.orthoAsset == null) {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            val decoded = previewAssetLoader.loadOrthoImage(imageUri)
                            val bounds = if (worldUri != null) {
                                previewAssetLoader.loadWorldFile(
                                    worldUri,
                                    decoded.sourceWidth,
                                    decoded.sourceHeight,
                                )
                            } else {
                                null
                            }
                            decoded to bounds
                        }
                    }
                    result.onSuccess { (decoded, bounds) ->
                        previewAssetsViewModel.setOrthoImage(
                            bitmap = decoded.bitmap,
                            bitmapFileName = imageName,
                            bitmapUri = imageUri,
                            sourceWidth = decoded.sourceWidth,
                            sourceHeight = decoded.sourceHeight,
                            notifyChange = bounds == null
                        )
                        if (bounds != null && worldUri != null && worldName != null) {
                            previewAssetsViewModel.setOrthoBounds(bounds, worldName, worldUri)
                            if (activePreviewMode == PreviewMode.ORTHO) renderPreviewMode()
                        }
                    }
            }
            // The terrain model is planning data, not just a 3D-view asset. Restore it in the
            // background so map and ortho planning can classify an accepted area immediately.
            previewAssetLoadJob = null
            restorePointCloudOnDemand(activatePreview = false)
        }
    }

    private fun restorePointCloudOnDemand(activatePreview: Boolean = true) {
        if (previewAssetsViewModel.pointCloudAsset != null || previewAssetLoadJob?.isActive == true) return
        val uri = previewAssetStore.readPersistedAssets().pointCloudUri ?: return
        loadHomePointCloud(uri, activatePreview)
    }

    private fun warmPointCloudTerrainGrid(showToast: Boolean) {
        terrainMissionCoordinator.warmGrid(
            showToast = showToast,
            renderStatus = {
                if (activePreviewMode == PreviewMode.POINT_CLOUD) renderTerrainGridStatus()
            },
            redrawMission = { redrawAreaMissionIfEditable() },
        )
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

    private fun showMissionUploadSummaryDialog(onConfirmed: () -> Unit) {
        missionSummaryPresenter.showUploadSummary(onConfirmed)
    }
    private fun updateTerrainCoverageOverlay() {
        missionMapRenderer.renderCoverage()
    }
    override fun onDestroyView() {
        viewLifecycleController.onDestroyView {
            if (::flightModeUiBinder.isInitialized) flightModeUiBinder.dismiss()
            if (::armUiBinder.isInitialized) armUiBinder.dismiss()
            stopMissionSimulation()
            missionGenerationCoordinator.cancel()
            geoPlanningJob?.cancel()
            missionSummaryJob?.cancel()
            liveGeoUpdateJob?.cancel()
            terrainMissionCoordinator.cancel()
            previewAssetLoadJob?.cancel()
            if (::windWeatherController.isInitialized) windWeatherController.dispose()
            cancelDroneOffsetAdjustment()
            geoZoneOverlayController?.clear()
            terrainCoverageOverlayController?.clear()
            osmdroidObstacleEditor.release()
            geoZoneOverlayController = null
            terrainCoverageOverlayController = null
            liveGeoAwarenessStatusBinder = null
            liveGeoAwarenessChecker = null
            clearFlightTrace()
        }
        super.onDestroyView()
        _binding = null
    }


    override fun onResume() {
        super.onResume()
        viewLifecycleController.onResume {
            refreshPreviewAssets()
            renderCurrentSurveyPathOnMap()
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

    private fun focusPreviewAssetOnMapIfRequested(): Boolean {
        val focus = previewAssetsViewModel.consumeMapFocusRequest() ?: return false
        val centered = when (focus) {
            PreviewMapFocus.POINT_CLOUD -> focusPointCloudOnMap() || focusOrthoOnMap()
            PreviewMapFocus.ORTHO -> focusOrthoOnMap() || focusPointCloudOnMap()
        }
        if (centered) {
            initialViewportController.markCenteredToDrone()
        }
        return centered
    }

    private fun focusOrthoOnMap(): Boolean {
        val bounds = previewAssetsViewModel.orthoAsset?.bounds ?: return false
        focusPreviewBoundsOnMap(bounds.minLat, bounds.maxLat, bounds.minLon, bounds.maxLon)
        mapView.invalidate()
        return true
    }

    private fun focusPointCloudOnMap(): Boolean {
        val pointCloud = previewAssetsViewModel.pointCloudAsset?.pointCloud ?: return false
        val frame = pointCloud.coordinateFrame ?: return false
        val halfSpanX = (pointCloud.bounds.spanX / 2f).toDouble().coerceAtLeast(MIN_PREVIEW_MAP_SPAN_METERS)
        val halfSpanY = (pointCloud.bounds.spanY / 2f).toDouble().coerceAtLeast(MIN_PREVIEW_MAP_SPAN_METERS)
        val corners = listOf(
            frame.localToLatLon(-halfSpanX, -halfSpanY),
            frame.localToLatLon(halfSpanX, halfSpanY)
        )
        val minLat = corners.minOf { it.first }
        val maxLat = corners.maxOf { it.first }
        val minLon = corners.minOf { it.second }
        val maxLon = corners.maxOf { it.second }
        focusPreviewBoundsOnMap(minLat, maxLat, minLon, maxLon)
        mapView.invalidate()
        return true
    }

    private fun focusPreviewBoundsOnMap(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ) {
        val centerLat = (minLat + maxLat) / 2.0
        val centerLon = (minLon + maxLon) / 2.0
        mapView.controller.setZoom(calculateSafePreviewZoom(minLat, maxLat, minLon, maxLon))
        mapView.controller.setCenter(GeoPoint(centerLat, centerLon))
    }

    private fun calculateSafePreviewZoom(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): Double {
        val lonSpan = (maxLon - minLon).coerceAtLeast(MIN_PREVIEW_BOUNDS_SPAN_DEGREES)
        val mercatorSpan = abs(mercatorY(maxLat) - mercatorY(minLat))
            .coerceAtLeast(MIN_PREVIEW_MERCATOR_SPAN)
        val mapWidth = max(mapView.width - PREVIEW_MAP_FIT_PADDING_PX * 2, MIN_MAP_VIEWPORT_PX)
        val mapHeight = max(mapView.height - PREVIEW_MAP_FIT_PADDING_PX * 2, MIN_MAP_VIEWPORT_PX)
        val lonZoom = log2(mapWidth * 360.0 / (TILE_SIZE_PX * lonSpan))
        val latZoom = log2(mapHeight * 2.0 * PI / (TILE_SIZE_PX * mercatorSpan))
        return min(lonZoom, latZoom).coerceIn(MIN_PREVIEW_MAP_ZOOM, MAX_PREVIEW_MAP_ZOOM)
    }

    private fun mercatorY(latitude: Double): Double {
        val radians = Math.toRadians(latitude.coerceIn(MIN_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE))
        return ln(tan(PI / 4.0 + radians / 2.0))
    }

    private fun log2(value: Double): Double = ln(value) / ln(2.0)

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

    private fun resetCurrentFlightUgzAuthorizations(reason: String) {
        val resetIds = geoAuthorizationSession.reset().toList()
        if (resetIds.isEmpty()) return
        geoEventLogger.logSimple(
            type = GeoAwarenessEventType.UGZ_AUTHORIZATION_RESET,
            severity = "INFO",
            message = "UGZ authorization confirmations reset",
            category = "GEO",
            datasetTitle = geoZoneDatasetInfo?.title,
            datasetVersion = geoZoneDatasetInfo?.version,
            healthState = geoAwarenessHealth?.state?.name,
            zoneIds = resetIds,
            details = mapOf(
                "reason" to reason,
                "resetScope" to "current_flight"
            )
        )
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

    private fun loadGeoAwarenessZonesIfNeeded(): Boolean {
        if (geoAwarenessZones.isNotEmpty()) {
            if (geoAwarenessHealth == null) {
                val health = geoAwarenessDatasetController.ensureHealth(
                    activityViewModel.geoZoneDatasetRecords.value.orEmpty()
                )
                geoAwarenessHealth = health
                activityViewModel.geoAwarenessHealth.value = health
            }
            return true
        }
        if (geoAwarenessDatasetLoadAttempted) {
            return geoAwarenessLoadError == null
        }
        geoAwarenessDatasetLoadAttempted = true

        try {
            val outcome = geoAwarenessDatasetController.loadCurrent()
            val loadResult = outcome.result
            applyGeoZoneLoadOutcome(outcome)
            geoAwarenessEventTracker.datasetLoaded(loadResult.datasetInfo)
            geoAwarenessEventTracker.datasetValidation(loadResult.validationResult, loadResult.datasetInfo)
            geoAwarenessEventTracker.multiDatasetLoaded(loadResult)
            geoAwarenessHealth?.let(geoAwarenessEventTracker::healthEvaluation)
            return true
        } catch (error: Exception) {
            Log.e(GEO_ZONE_TOGGLE_TAG, "Failed to load geo-awareness zones", error)
            applyGeoAwarenessSnapshot(
                geoAwarenessDatasetController.recordFailure(error, emptyList())
            )
              activityViewModel.geoZoneDatasetInfo.value = null
              activityViewModel.geoZoneValidationResult.value = null
              activityViewModel.geoZoneDatasetRecords.value = emptyList()
              activityViewModel.geoZoneImportedActive.value = false
              activityViewModel.geoAwarenessHealth.value = geoAwarenessHealth
              geoAwarenessEventTracker.datasetLoadFailed(error)
            geoAwarenessHealth?.let(geoAwarenessEventTracker::healthEvaluation)
        }

        return false
    }

    private fun observeGeoAwarenessSharedState() {
        missionPresentationObserver.bindGeoState(
            layerVisible = activityViewModel.geoAwarenessLayerVisible,
            reloadToken = activityViewModel.geoZoneReloadToken,
            renderLayer = ::renderGeoAwarenessLayerIfVisible,
            reload = ::reloadCurrentGeoAwarenessDataset,
        )
    }

    private fun reloadCurrentGeoAwarenessDataset() {
        if (geoZoneReloadInProgress) return
        geoZoneReloadInProgress = true
        lifecycleScope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) {
                    geoAwarenessDatasetController.reloadCurrent()
                }
                val loadResult = outcome.result
                if (_binding == null) return@launch
                geoAwarenessDatasetLoadAttempted = true
                applyGeoZoneLoadOutcome(outcome)
                geoAwarenessEventTracker.multiDatasetLoaded(loadResult)
                renderGeoAwarenessLayerIfVisible()
                updateGeoAwarenessPlanningStatus()
                updateLiveGeoAwarenessFromActiveSource()
                geoAwarenessHealth?.let(geoAwarenessEventTracker::healthEvaluation)
            } catch (error: Exception) {
                geoAwarenessDatasetLoadAttempted = true
                Log.e(GEO_ZONE_TOGGLE_TAG, "Failed to reload geo-awareness dataset", error)
                applyGeoAwarenessSnapshot(
                    geoAwarenessDatasetController.recordFailure(
                        error,
                        activityViewModel.geoZoneDatasetRecords.value.orEmpty(),
                    )
                )
                activityViewModel.geoAwarenessHealth.value = geoAwarenessHealth
                geoAwarenessHealth?.let(geoAwarenessEventTracker::healthEvaluation)
            } finally {
                geoZoneReloadInProgress = false
            }
        }
    }

    private fun applyGeoZoneLoadOutcome(outcome: GeoAwarenessDatasetController.LoadOutcome) {
        val result = outcome.result
        applyGeoAwarenessSnapshot(outcome.snapshot)
        activityViewModel.geoZoneDatasetInfo.value = result.datasetInfo
        activityViewModel.geoZoneValidationResult.value = result.validationResult
        activityViewModel.geoZoneDatasetRecords.value = result.datasetRecords
        activityViewModel.geoZoneImportedActive.value = outcome.snapshot.importedActive
        activityViewModel.geoAwarenessHealth.value = geoAwarenessHealth
    }

    private fun applyGeoAwarenessSnapshot(snapshot: GeoAwarenessDatasetController.Snapshot) {
        geoAwarenessZones = snapshot.zones
        geoZoneDatasetInfo = snapshot.datasetInfo
        geoZoneValidationResult = snapshot.validationResult
        geoAwarenessLoadError = snapshot.loadError
        geoAwarenessHealth = snapshot.health
    }

    private fun renderGeoAwarenessLayerIfVisible() {
        if (activityViewModel.geoAwarenessLayerVisible.value != true) {
            geoZoneOverlayController?.clear()
            geoEventLogger.logSimple(
                type = GeoAwarenessEventType.GEO_LAYER_HIDDEN,
                severity = "INFO",
                message = "Geo-awareness layer hidden",
                datasetTitle = geoZoneDatasetInfo?.title,
                datasetVersion = geoZoneDatasetInfo?.version,
                healthState = geoAwarenessHealth?.state?.name
            )
            return
        }

        if (!loadGeoAwarenessZonesIfNeeded()) {
            return
        }

        geoZoneOverlayController?.renderZones(geoAwarenessZones)
        geoEventLogger.logSimple(
            type = GeoAwarenessEventType.GEO_LAYER_SHOWN,
            severity = "INFO",
            message = "Geo-awareness layer shown",
            datasetTitle = geoZoneDatasetInfo?.title,
            datasetVersion = geoZoneDatasetInfo?.version,
            healthState = geoAwarenessHealth?.state?.name
        )
        Log.d(GEO_ZONE_TOGGLE_TAG, "Geo-awareness layer shown")
    }

    private fun toggleGeoAwarenessLayer() {
        if (activityViewModel.geoAwarenessLayerVisible.value == true) {
            geoZoneOverlayController?.clear()
            activityViewModel.geoAwarenessLayerVisible.value = false
            Log.d(GEO_ZONE_TOGGLE_TAG, "Geo-awareness layer hidden")
            return
        }

        if (!loadGeoAwarenessZonesIfNeeded()) {
            return
        }

        geoZoneOverlayController?.renderZones(geoAwarenessZones)
        activityViewModel.geoAwarenessLayerVisible.value = true
        Log.d(GEO_ZONE_TOGGLE_TAG, "Geo-awareness layer shown")
    }

    private fun updateGeoAwarenessPlanningStatus() {
        if (_binding == null) {
            return
        }

        latestGeoAwarenessResult = evaluateGeoAwarenessPlanningResult()
        ensureGeoAwarenessHealth()
        geoAwarenessEventTracker.planningStatus(latestGeoAwarenessResult)
        Log.d(
            GEO_PLANNING_STATUS_TAG,
            "Planning geo-awareness updated: conflicts=${latestGeoAwarenessResult.conflicts.size} highest=${latestGeoAwarenessResult.highestRestriction} canUpload=${latestGeoAwarenessResult.canUpload}"
        )
    }

    private fun scheduleGeoAwarenessPlanningStatusUpdate() {
        if (_binding == null) return
        val input = geoAwarenessPlanningInput()
        if (!input.hasGeometry) {
            geoPlanningJob?.cancel()
            latestGeoAwarenessResult = GeoAwarenessResult.clear()
            return
        }
        if (!loadGeoAwarenessZonesIfNeeded()) return
        val snapshot = input.copy(zones = geoAwarenessZones.toList())

        geoPlanningJob?.cancel()
        geoPlanningJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                geoAwarenessPlanningController.evaluate(snapshot)
            }
            if (_binding == null) return@launch
            latestGeoAwarenessResult = result
            ensureGeoAwarenessHealth()
            geoAwarenessEventTracker.planningStatus(result)
            Log.d(
                GEO_PLANNING_STATUS_TAG,
                "Planning geo-awareness updated: conflicts=${result.conflicts.size} " +
                    "highest=${result.highestRestriction} canUpload=${result.canUpload}"
            )
        }
    }

    private fun geoAwarenessPlanningInput(): GeoAwarenessPlanningController.Input {
        val missionPolygon = activityViewModel.missionArea.value?.vertices
            ?.takeIf { it.isNotEmpty() }
            ?.map { LatLon(lat = it.latitude, lon = it.longitude) }
        val surveyPath = activityViewModel.surveyPath.value
            ?.takeIf { it.isNotEmpty() }
            ?.map { LatLon(lat = it.latitude, lon = it.longitude) }
            .orEmpty()
        val pointRoutePath = activityViewModel.plannedRoutePath.value.orEmpty()
            .takeIf { it.isNotEmpty() }
            ?.map { LatLon(lat = it.latitude, lon = it.longitude) }
            ?: activityViewModel.routeWaypoints.value
                ?.takeIf { it.isNotEmpty() }
                ?.map { LatLon(lat = it.latitude, lon = it.longitude) }
                .orEmpty()
        val planningPath = if (activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS) {
            pointRoutePath
        } else {
            surveyPath
        }
        return GeoAwarenessPlanningController.Input(
            missionPolygon = missionPolygon,
            planningPath = planningPath,
            aglAltitudeMeters = activityViewModel.flightAltProgress.value?.toDouble(),
            zones = geoAwarenessZones,
        )
    }

    private fun evaluateGeoAwarenessPlanningResult(): GeoAwarenessResult {
        val input = geoAwarenessPlanningInput()
        if (!input.hasGeometry || !loadGeoAwarenessZonesIfNeeded()) return GeoAwarenessResult.clear()
        return geoAwarenessPlanningController.evaluate(input.copy(zones = geoAwarenessZones))
    }

    private fun ensureGeoAwarenessHealth(): GeoAwarenessHealth {
        if (geoAwarenessZones.isEmpty() && geoZoneDatasetInfo == null && geoAwarenessLoadError == null) {
            loadGeoAwarenessZonesIfNeeded()
        }

        val health = geoAwarenessDatasetController.ensureHealth(
            activityViewModel.geoZoneDatasetRecords.value.orEmpty()
        )
        geoAwarenessHealth = health
        activityViewModel.geoAwarenessHealth.value = health
        geoAwarenessEventTracker.healthEvaluation(health)
        return health
    }

    private fun handleGeoAwarenessBeforeUpload(onAllowed: () -> Unit) {
        val result = try {
            evaluateGeoAwarenessPlanningResult().also { latestGeoAwarenessResult = it }
        } catch (error: Exception) {
            Log.w(GEO_UPLOAD_GUARD_TAG, "Geo-awareness result unavailable; proceeding with existing unavailable policy", error)
            onAllowed()
            return
        }
        val health = ensureGeoAwarenessHealth()

        when (val decision = GeoUploadGuardPolicy.decide(result, geoAuthorizationSession)) {
            GeoUploadGuardPolicy.Decision.AllowClear -> {
                Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: clear, proceeding")
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.UPLOAD_GUARD_CLEAR,
                    severity = "INFO",
                    message = "Geo upload guard clear, proceeding",
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = health.state.name
                )
                onAllowed()
            }
            GeoUploadGuardPolicy.Decision.Block -> {
                Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: blocked conflicts=${result.conflicts.size}")
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.UPLOAD_BLOCKED,
                    severity = "BLOCKED",
                    message = "Geo upload blocked",
                    category = "MISSION",
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = health.state.name,
                    zoneIds = result.conflicts.map { it.zone.id }.distinct(),
                    zoneNames = result.conflicts.map { it.zone.name }.distinct(),
                    restriction = result.highestRestriction.name,
                    latitude = latestRealDronePosition?.lat,
                    longitude = latestRealDronePosition?.lon,
                    altitudeMeters = latestRealDroneAltitudeMeters
                )
                showGeoAwarenessBlockedDialog(result)
            }
            GeoUploadGuardPolicy.Decision.RequireProhibitedAcknowledgement -> {
                Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: prohibited acknowledgement required conflicts=${result.conflicts.size}")
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.UPLOAD_ACK_REQUIRED,
                    severity = "WARNING",
                    message = "Geo upload requires prohibited zone acknowledgement",
                    category = "MISSION",
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = health.state.name,
                    zoneIds = result.conflicts.map { it.zone.id }.distinct(),
                    zoneNames = result.conflicts.map { it.zone.name }.distinct(),
                    restriction = GeoZoneRestriction.PROHIBITED.name,
                    latitude = latestRealDronePosition?.lat,
                    longitude = latestRealDronePosition?.lon,
                    altitudeMeters = latestRealDroneAltitudeMeters
                )
                showGeoAwarenessProhibitedAcknowledgementDialog(result, health) {
                    Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: user proceeded after prohibited zone warning")
                    geoEventLogger.logSimple(
                        type = GeoAwarenessEventType.UPLOAD_ACKNOWLEDGED,
                        severity = "INFO",
                        message = "User acknowledged prohibited geo-zone upload warning",
                        category = "MISSION",
                        datasetTitle = geoZoneDatasetInfo?.title,
                        datasetVersion = geoZoneDatasetInfo?.version,
                        healthState = health.state.name,
                        zoneIds = result.conflicts.map { it.zone.id }.distinct(),
                        zoneNames = result.conflicts.map { it.zone.name }.distinct(),
                        restriction = GeoZoneRestriction.PROHIBITED.name,
                        latitude = latestRealDronePosition?.lat,
                        longitude = latestRealDronePosition?.lon,
                        altitudeMeters = latestRealDroneAltitudeMeters,
                        details = mapOf("pilotAcknowledgement" to "prohibited_zone_warning_seen")
                    )
                    onAllowed()
                }
            }
            is GeoUploadGuardPolicy.Decision.AllowPreviouslyAuthorized -> {
                Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: authorization already confirmed for current flight")
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.UPLOAD_ACKNOWLEDGED,
                    severity = "INFO",
                    message = "Geo upload authorization already confirmed for current flight",
                    category = "MISSION",
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = health.state.name,
                    zoneIds = decision.zones.map { it.id },
                    zoneNames = decision.zones.map { it.name },
                    restriction = GeoZoneRestriction.REQ_AUTHORISATION.name,
                    details = mapOf("authorizationScope" to "current_flight")
                )
                onAllowed()
            }
            is GeoUploadGuardPolicy.Decision.RequireAuthorization -> {
                val unconfirmedAuthorizationZones = decision.zones
                Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: authorization confirmation required conflicts=${result.conflicts.size}")
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.UPLOAD_ACK_REQUIRED,
                    severity = "WARNING",
                    message = "Geo upload requires acknowledgement",
                    category = "MISSION",
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = health.state.name,
                    zoneIds = unconfirmedAuthorizationZones.map { it.id },
                    zoneNames = unconfirmedAuthorizationZones.map { it.name },
                    restriction = GeoZoneRestriction.REQ_AUTHORISATION.name,
                    latitude = latestRealDronePosition?.lat,
                    longitude = latestRealDronePosition?.lon,
                    altitudeMeters = latestRealDroneAltitudeMeters,
                    details = mapOf("authorizationScope" to "current_flight")
                )
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.UGZ_AUTHORIZATION_REQUIRED,
                    severity = "WARNING",
                    message = "Geo upload requires UGZ authorization confirmation",
                    category = "MISSION",
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = health.state.name,
                    zoneIds = unconfirmedAuthorizationZones.map { it.id },
                    zoneNames = unconfirmedAuthorizationZones.map { it.name },
                    restriction = GeoZoneRestriction.REQ_AUTHORISATION.name,
                    latitude = latestRealDronePosition?.lat,
                    longitude = latestRealDronePosition?.lon,
                    altitudeMeters = latestRealDroneAltitudeMeters
                )
                showGeoAwarenessAcknowledgementDialog(result, health) {
                    val confirmedZones = geoAuthorizationSession.requiredZones(result)
                    geoAuthorizationSession.confirm(confirmedZones)
                    Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: user proceeded after acknowledgement")
                    geoEventLogger.logSimple(
                        type = GeoAwarenessEventType.UGZ_AUTHORIZATION_CONFIRMED,
                        severity = "INFO",
                        message = "Pilot declared UGZ authorization completed",
                        category = "MISSION",
                        datasetTitle = geoZoneDatasetInfo?.title,
                        datasetVersion = geoZoneDatasetInfo?.version,
                        healthState = health.state.name,
                        zoneIds = confirmedZones.map { it.id },
                        zoneNames = confirmedZones.map { it.name },
                        restriction = GeoZoneRestriction.REQ_AUTHORISATION.name,
                        latitude = latestRealDronePosition?.lat,
                        longitude = latestRealDronePosition?.lon,
                        altitudeMeters = latestRealDroneAltitudeMeters,
                        details = mapOf(
                            "confirmationScope" to "current_flight",
                            "pilotDeclaration" to "authorization_or_notification_completed",
                            "resetCondition" to "disarm_or_end_of_flight"
                        )
                    )
                    geoEventLogger.logSimple(
                        type = GeoAwarenessEventType.UPLOAD_ACKNOWLEDGED,
                        severity = "INFO",
                        message = "User acknowledged geo upload warning",
                        category = "MISSION",
                        datasetTitle = geoZoneDatasetInfo?.title,
                        datasetVersion = geoZoneDatasetInfo?.version,
                        healthState = health.state.name,
                        zoneIds = confirmedZones.map { it.id },
                        zoneNames = confirmedZones.map { it.name },
                        restriction = GeoZoneRestriction.REQ_AUTHORISATION.name,
                        latitude = latestRealDronePosition?.lat,
                        longitude = latestRealDronePosition?.lon,
                        altitudeMeters = latestRealDroneAltitudeMeters,
                        details = mapOf("authorizationScope" to "current_flight")
                    )
                    onAllowed()
                }
            }
            GeoUploadGuardPolicy.Decision.ShowNotice -> {
                Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: notice conflicts=${result.conflicts.size}")
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.UPLOAD_CONTINUED_WITH_WARNING,
                    severity = "WARNING",
                    message = "Geo upload warning shown",
                    category = "MISSION",
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = health.state.name,
                    zoneIds = result.conflicts.map { it.zone.id }.distinct(),
                    zoneNames = result.conflicts.map { it.zone.name }.distinct(),
                    restriction = result.highestRestriction.name,
                    latitude = latestRealDronePosition?.lat,
                    longitude = latestRealDronePosition?.lon,
                    altitudeMeters = latestRealDroneAltitudeMeters
                )
                showGeoAwarenessNoticeDialog(result, health) {
                    Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: user proceeded after notice")
                    onAllowed()
                }
            }
        }
    }

    private fun showGeoAwarenessBlockedDialog(result: GeoAwarenessResult) {
        geoAwarenessDialogController.showBlocked(result)
    }

    private fun showGeoAwarenessProhibitedAcknowledgementDialog(
        result: GeoAwarenessResult,
        health: GeoAwarenessHealth,
        onAcknowledged: () -> Unit
    ) {
        geoAwarenessDialogController.showProhibitedAcknowledgement(
            result = result,
            onAcknowledged = onAcknowledged,
            onCancelled = {
                Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: user cancelled prohibited zone warning")
                logGeoUploadCancellation(health, "User cancelled prohibited geo-zone upload warning")
            },
        )
    }

    private fun showGeoAwarenessAcknowledgementDialog(
        result: GeoAwarenessResult,
        health: GeoAwarenessHealth,
        onAcknowledged: () -> Unit
    ) {
        geoAwarenessDialogController.showAuthorizationAcknowledgement(
            result = result,
            onAcknowledged = onAcknowledged,
            onCancelled = {
                Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: user cancelled")
                logGeoUploadCancellation(health, "User cancelled geo upload acknowledgement")
            },
        )
    }

    private fun showGeoAwarenessNoticeDialog(
        result: GeoAwarenessResult,
        health: GeoAwarenessHealth,
        onContinue: () -> Unit
    ) {
        geoAwarenessDialogController.showNotice(
            result = result,
            onContinue = {
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.UPLOAD_ACKNOWLEDGED,
                    severity = "INFO",
                    message = "User continued after geo upload warning",
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = health.state.name,
                )
                onContinue()
            },
            onCancelled = {
                Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: user cancelled")
                logGeoUploadCancellation(health, "User cancelled geo upload warning")
            },
        )
    }

    private fun logGeoUploadCancellation(health: GeoAwarenessHealth, message: String) {
        geoEventLogger.logSimple(
            type = GeoAwarenessEventType.UPLOAD_CANCELLED,
            severity = "INFO",
            message = message,
            datasetTitle = geoZoneDatasetInfo?.title,
            datasetVersion = geoZoneDatasetInfo?.version,
            healthState = health.state.name,
        )
    }
    private fun updateLiveGeoAwarenessStatus(
        dronePosition: LatLon?,
        droneAltitudeMeters: Double?
    ) {
        latestLiveDronePosition = dronePosition

        liveGeoAwarenessDegradedReason()?.let { reason ->
            latestLiveGeoZones = emptyList()
            latestLiveGeoProximity = null
            latestLiveGeoThreats = emptyList()
            liveGeoAwarenessStatusBinder?.bindDegraded(reason)
            updateTopLiveGeoStatus(getString(R.string.live_geo_degraded), "#FFB26B")
            return
        }

        if (dronePosition == null) {
            latestLiveGeoZones = emptyList()
            latestLiveGeoProximity = null
            latestLiveGeoThreats = emptyList()
            liveGeoAwarenessStatusBinder?.bindUnknown("No drone position")
            updateTopLiveGeoStatus(getString(R.string.live_geo_unknown_status), "#AAB5C6")
            return
        }

        if (!loadGeoAwarenessZonesIfNeeded()) {
            latestLiveGeoZones = emptyList()
            latestLiveGeoProximity = null
            latestLiveGeoThreats = emptyList()
            liveGeoAwarenessStatusBinder?.bindUnknown("Geo-zones unavailable")
            updateTopLiveGeoStatus(getString(R.string.live_geo_unknown_status), "#AAB5C6")
            return
        }
        if (geoAwarenessZones.isEmpty()) {
            latestLiveGeoZones = emptyList()
            latestLiveGeoProximity = null
            latestLiveGeoThreats = emptyList()
            liveGeoAwarenessStatusBinder?.bindUnknown("Geo-zones unavailable")
            updateTopLiveGeoStatus(getString(R.string.live_geo_unknown_status), "#AAB5C6")
            return
        }

        val insideZones = liveGeoAwarenessChecker?.checkDronePosition(
            dronePosition = dronePosition,
            altitudeContext = GeoAltitudeContext(
                aglMeters = droneAltitudeMeters,
                amslMeters = latestRealDroneAltitudeAmslMeters
            ),
            zones = geoAwarenessZones
        ).orEmpty()

        geoAwarenessEventTracker.liveStatus(
            zones = insideZones,
            latitude = dronePosition.lat,
            longitude = dronePosition.lon,
            altitudeMeters = droneAltitudeMeters
        )
        latestLiveGeoZones = insideZones
        val nearThreats = liveGeoAwarenessChecker?.findZonesWithinThreshold(
            position = dronePosition,
            zones = geoAwarenessZones,
            thresholdMeters = DEFAULT_NEAR_ZONE_THRESHOLD_METERS,
            altitudeContext = GeoAltitudeContext(
                aglMeters = droneAltitudeMeters,
                amslMeters = latestRealDroneAltitudeAmslMeters
            ),
            groundSpeedMetersPerSecond = latestRealDroneGroundSpeedMetersPerSecond?.toDouble(),
            headingDegrees = latestRealDroneHeadingDegrees,
            verticalSpeedMetersPerSecond = latestRealDroneVerticalSpeedMetersPerSecond?.toDouble()
        ).orEmpty()
        val nearestZone = nearThreats.firstOrNull()
        latestLiveGeoProximity = nearestZone

        if (insideZones.isEmpty() && nearestZone == null) {
            latestLiveGeoThreats = emptyList()
            liveGeoAwarenessStatusBinder?.bindClear()
            updateTopLiveGeoStatus(getString(R.string.live_geo_clear_status), "#48D26D")
        } else {
            nearestZone?.let { proximity ->
                geoAwarenessEventTracker.liveProximity(
                    proximity = proximity,
                    latitude = dronePosition.lat,
                    longitude = dronePosition.lon,
                    altitudeMeters = droneAltitudeMeters
                )
            }
            val insideThreatRows = insideZones.map { zone -> liveGeoThreatPresenter.inside(zone, dronePosition) }
            val dedupedNearThreats = nearThreats.filterNot { proximity ->
                insideZones.any { inside -> inside.id == proximity.nearestZone.id }
            }
            val altitudes = LiveGeoThreatPresenter.Altitudes(
                aglMeters = latestRealDroneAltitudeMeters,
                amslMeters = latestRealDroneAltitudeAmslMeters,
            )
            val threatRows = (insideThreatRows + dedupedNearThreats.map {
                liveGeoThreatPresenter.proximity(it, dronePosition, altitudes)
            })
                .take(3)
            val remainingCount = (insideThreatRows.size + dedupedNearThreats.size - threatRows.size).coerceAtLeast(0)
            latestLiveGeoThreats = nearThreats
            val highestInside = insideZones.maxByOrNull { liveGeoThreatPresenter.restrictionPriority(it.restriction) }
            val statusRestriction = highestInside?.restriction ?: nearestZone?.restriction
            val statusLabel = when {
                highestInside != null -> getString(R.string.live_geo_inside_status, liveGeoThreatPresenter.restrictionLabel(highestInside.restriction))
                threatRows.size > 1 -> getString(R.string.live_geo_multiple)
                statusRestriction != null -> liveGeoThreatPresenter.nearRestrictionLabel(statusRestriction)
                else -> getString(R.string.live_geo_clear_status)
            }
            val statusColor = statusRestriction?.let(liveGeoThreatPresenter::restrictionColor) ?: "#48D26D"
            liveGeoAwarenessStatusBinder?.bindThreatSummary(
                statusLabel = statusLabel,
                statusColor = statusColor,
                threats = threatRows,
                remainingCount = remainingCount,
                headingDegrees = latestRealDroneHeadingDegrees,
                borderColor = highestInside?.restriction?.let(liveGeoThreatPresenter::restrictionColor) ?: "#00000000"
            )
            updateTopLiveGeoStatus(statusLabel, statusColor)
        }

        Log.d(
            LIVE_GEO_AWARENESS_TAG,
            "Live geo-awareness updated: inside=${insideZones.size} highest=${insideZones.firstOrNull()?.restriction}"
        )
    }

    private fun updateTopLiveGeoStatus(label: String, colorHex: String) {
        val signature = "$label|$colorHex"
        if (lastTopLiveGeoStatusSignature == signature) return
        lastTopLiveGeoStatusSignature = signature
        val statusView = view?.findViewById<TextView?>(R.id.top_live_geo_status_text) ?: return
        val color = android.graphics.Color.parseColor(colorHex)
        statusView.text = label
        statusView.setTextColor(color)
        view?.findViewById<ImageView?>(R.id.top_live_geo_icon)?.apply {
            setImageResource(
                if (label == getString(R.string.live_geo_clear_status)) {
                    R.drawable.ic_baseline_check_circle_outline_24
                } else {
                    R.drawable.ic_status_warning_24
                }
            )
            setColorFilter(color)
        }
    }

    private fun showLiveGeoAwarenessDetails() {
        val title: String
        val message: String

        when {
            latestLiveDronePosition == null -> {
                title = getString(R.string.live_geo_details_title)
                message = getString(R.string.live_geo_no_position)
            }
            latestLiveGeoZones.isNotEmpty() -> {
                val visibleZones = latestLiveGeoZones.take(5)
                val remainingCount = latestLiveGeoZones.size - visibleZones.size
                title = getString(R.string.live_geo_warning_title)
                message = buildString {
                    appendLine(getString(R.string.live_geo_inside_loaded))
                    appendLine()
                    visibleZones.forEach { zone ->
                        appendLine("- ${zone.name}")
                        appendLine("  ${getString(R.string.live_geo_restriction, zone.restriction)}")
                        appendLine("  ${getString(R.string.live_geo_message, zone.message ?: getString(R.string.geo_summary_no_message))}")
                    }
                    if (remainingCount > 0) {
                        appendLine(getString(R.string.geo_summary_more, remainingCount))
                    }
                    append(getString(R.string.live_geo_verify_authority))
                }
            }
            latestLiveGeoProximity != null -> {
                val proximity = latestLiveGeoProximity!!
                title = getString(R.string.live_geo_nearby_title)
                message = buildString {
                    appendLine(getString(R.string.live_geo_nearest_zone, proximity.nearestZone.name))
                    appendLine(getString(R.string.live_geo_restriction, proximity.restriction))
                    appendLine(getString(R.string.live_geo_distance, proximity.distanceMeters.toInt().coerceAtLeast(0)))
                    appendLine(getString(R.string.live_geo_configured_threshold, proximity.configuredThresholdMeters.toInt()))
                    appendLine(getString(R.string.live_geo_effective_threshold, proximity.effectiveThresholdMeters.toInt()))
                    appendLine(getString(R.string.live_geo_warning_time, proximity.requiredWarningSeconds))
                    proximity.groundSpeedMetersPerSecond?.let { speed ->
                        appendLine(getString(R.string.live_geo_ground_speed, "%.2f".format(Locale.US, speed)))
                    }
                    proximity.closingSpeedMetersPerSecond?.let { speed ->
                        appendLine(getString(R.string.live_geo_closing_speed, "%.2f".format(Locale.US, speed)))
                    }
                    proximity.timeToBoundarySeconds?.let { seconds ->
                        appendLine(getString(R.string.live_geo_boundary_time, "%.2f".format(Locale.US, seconds)))
                    }
                    proximity.verticalDistanceMeters?.let { distance ->
                        appendLine(getString(R.string.live_geo_vertical_distance, "%.2f".format(Locale.US, distance)))
                    }
                    proximity.verticalClosingSpeedMetersPerSecond?.let { speed ->
                        appendLine(getString(R.string.live_geo_vertical_speed, "%.2f".format(Locale.US, speed)))
                    }
                    proximity.verticalTimeToBoundarySeconds?.let { seconds ->
                        appendLine(getString(R.string.live_geo_vertical_time, "%.2f".format(Locale.US, seconds)))
                    }
                    appendLine(getString(R.string.live_geo_warning_mode, proximity.warningMode))
                    if (!geoZoneDatasetInfo?.title.isNullOrBlank()) {
                        appendLine(getString(R.string.live_geo_dataset, geoZoneDatasetInfo?.title, geoZoneDatasetInfo?.version ?: getString(R.string.settings_unavailable)))
                    }
                    if (!proximity.nearestZone.message.isNullOrBlank()) {
                        appendLine(getString(R.string.live_geo_message, proximity.nearestZone.message))
                    }
                    appendLine()
                    append(getString(R.string.live_geo_outside_near))
                }
            }
            latestLiveGeoZones.isEmpty() -> {
                title = getString(R.string.live_geo_details_title)
                message = buildString {
                    appendLine(getString(R.string.live_geo_outside_all))
                    append(getString(R.string.live_geo_verify_before_flight))
                }
            }
            else -> error("Unhandled live geo-awareness detail state")
        }

        geoAwarenessDialogController.showMessage(title, message)
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

    private fun updateLiveGeoAwarenessFromActiveSource() {
        if (_binding == null) return
        val elapsed = SystemClock.uptimeMillis() - lastLiveGeoUpdateUptimeMs
        if (elapsed >= LIVE_GEO_UPDATE_INTERVAL_MS && liveGeoUpdateJob == null) {
            lastLiveGeoUpdateUptimeMs = SystemClock.uptimeMillis()
            updateLiveGeoAwarenessStatus(latestRealDronePosition, latestRealDroneAltitudeMeters)
            return
        }
        if (liveGeoUpdateJob != null) return
        liveGeoUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
            delay((LIVE_GEO_UPDATE_INTERVAL_MS - elapsed).coerceAtLeast(0L))
            liveGeoUpdateJob = null
            if (_binding == null) return@launch
            lastLiveGeoUpdateUptimeMs = SystemClock.uptimeMillis()
            updateLiveGeoAwarenessStatus(latestRealDronePosition, latestRealDroneAltitudeMeters)
        }
    }

    private fun liveGeoAwarenessDegradedReason(): String? {
        if (droneViewModel.conStateLiveData.value != true) {
            return getString(R.string.live_geo_degraded_no_link)
        }
        return when (TelemetryMapping.gpsFixQuality(droneViewModel.gpsFixType.value, isConnected = true)) {
            GpsFixQuality.DISCONNECTED,
            GpsFixQuality.NO_GPS,
            GpsFixQuality.UNKNOWN -> getString(R.string.live_geo_degraded_gps)
            GpsFixQuality.FIX_2D,
            GpsFixQuality.FIX_3D,
            GpsFixQuality.DGPS,
            GpsFixQuality.RTK_FLOAT,
            GpsFixQuality.RTK_FIXED -> null
        }
    }

}
