package com.example.droneservicesapp.ui.home

import android.os.Bundle
import android.graphics.Typeface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.geoawareness.GeoZoneImportedFileDataSource
import com.example.droneservicesapp.data.geoawareness.GeoZoneRepository
import com.example.droneservicesapp.data.geoawareness.incident.GeoIncidentEncryptedLogStore
import com.example.droneservicesapp.data.geoawareness.incident.GeoIncidentLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.data.geoawareness.logging.OperatorFlightEventLogger
import com.example.droneservicesapp.data.rtk.RtkForwardingState
import com.example.droneservicesapp.data.storage.MissionFileStore
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessChecker
import com.example.droneservicesapp.domain.geoawareness.GeoAltitudeContext
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthEvaluator
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthState
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessResult
import com.example.droneservicesapp.domain.geoawareness.GeoConflictType
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneConflict
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction
import com.example.droneservicesapp.domain.geoawareness.LiveGeoAwarenessChecker
import com.example.droneservicesapp.domain.geoawareness.LiveGeoAwarenessProximityResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import com.example.droneservicesapp.databinding.FragmentHomeMapsBinding
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.survey.SurveyPlanner
import com.example.droneservicesapp.domain.survey.SurveyGridPlanner
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.mavserver.GpsFixQuality
import com.example.droneservicesapp.mavserver.TelemetryMapping
import com.example.droneservicesapp.ui.home.binders.HomeMapChromeBinder
import com.example.droneservicesapp.ui.home.binders.HomeMapModeEffectsBinder
import com.example.droneservicesapp.ui.home.binders.HomeMapPanelsBinder
import com.example.droneservicesapp.ui.home.binders.HomeMapTelemetryBinder
import com.example.droneservicesapp.ui.home.binders.MissionLoadController
import com.example.droneservicesapp.ui.home.binders.MissionParamsController
import com.example.droneservicesapp.ui.home.binders.MissionSaveController
import com.example.droneservicesapp.ui.home.components.EsriWorldImageryTileSource
import com.example.droneservicesapp.ui.home.components.OsmdroidMapController
import com.example.droneservicesapp.ui.home.components.OsmdroidPolygonEditor
import com.example.droneservicesapp.ui.home.components.OsmdroidRouteWaypointEditor
import com.example.droneservicesapp.ui.home.geoawareness.GeoAwarenessStatusViewBinder
import com.example.droneservicesapp.ui.home.geoawareness.GeoZoneOverlayController
import com.example.droneservicesapp.ui.home.geoawareness.LiveGeoAwarenessPanelBinder
import com.example.droneservicesapp.ui.home.geoawareness.LiveGeoThreatUiModel
import com.example.droneservicesapp.ui.home.model.HomeTelemetryViewModel
import com.example.droneservicesapp.ui.home.model.HomeMapUiState
import com.example.droneservicesapp.ui.home.model.MissionMapViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import com.google.maps.android.SphericalUtil
import io.dronefleet.mavlink.common.MavCmd
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MissionMapFragment : Fragment() {
    private var _binding: FragmentHomeMapsBinding? = null
    private val binding get() = _binding!!
    private lateinit var mapView: MapView

    private lateinit var droneViewModel: DroneViewModel
    private lateinit var activityViewModel: MainActivityViewModel
    private lateinit var homeTelemetryViewModel: HomeTelemetryViewModel
    private lateinit var mapViewModel: MissionMapViewModel

    private lateinit var missionParamsController: MissionParamsController
    private lateinit var missionSaveController: MissionSaveController
    private lateinit var missionLoadController: MissionLoadController
    private lateinit var geoEventLogger: GeoAwarenessEventLogger
    private lateinit var geoIncidentLogger: GeoIncidentLogger
    private lateinit var operatorEventLogger: OperatorFlightEventLogger
    private lateinit var homeMapChromeBinder: HomeMapChromeBinder
    private lateinit var homeMapPanelsBinder: HomeMapPanelsBinder
    private lateinit var homeMapModeEffectsBinder: HomeMapModeEffectsBinder
    private lateinit var homeMapTelemetryBinder: HomeMapTelemetryBinder
    private lateinit var osmdroidMapController: OsmdroidMapController
    private lateinit var osmdroidPolygonEditor: OsmdroidPolygonEditor
    private lateinit var osmdroidRouteWaypointEditor: OsmdroidRouteWaypointEditor
    private lateinit var missionFileStore: MissionFileStore
    private var geoAwarenessZones: List<GeoZone> = emptyList()
    private var geoZoneDatasetInfo: GeoZoneDatasetInfo? = null
    private var geoAwarenessHealth: GeoAwarenessHealth? = null
    private var geoAwarenessLoadError: Throwable? = null
    private var geoZoneValidationResult: GeoZoneValidationResult? = null
    private var geoZoneOverlayController: GeoZoneOverlayController? = null
    private var geoAwarenessChecker: GeoAwarenessChecker? = null
    private var latestGeoAwarenessResult: GeoAwarenessResult = GeoAwarenessResult.clear()
    private var geoAwarenessStatusBinder: GeoAwarenessStatusViewBinder? = null
    private var liveGeoAwarenessChecker: LiveGeoAwarenessChecker? = null
    private var latestLiveGeoZones: List<GeoZone> = emptyList()
    private var latestLiveGeoProximity: LiveGeoAwarenessProximityResult? = null
    private var latestLiveGeoThreats: List<LiveGeoAwarenessProximityResult> = emptyList()
    private var liveGeoAwarenessStatusBinder: LiveGeoAwarenessPanelBinder? = null
    private var latestLiveDronePosition: LatLon? = null
    private var latestRealDronePosition: LatLon? = null
    private var latestRealDroneAltitudeMeters: Double? = null
    private var latestRealDroneAltitudeAmslMeters: Double? = null
    private var latestRealDroneHorizontalAccuracyMeters: Float? = null
    private var latestRealDroneVerticalAccuracyMeters: Float? = null
    private var latestRealDroneGroundSpeedMetersPerSecond: Float? = null
    private var latestRealDroneVerticalSpeedMetersPerSecond: Float? = null
    private var latestRealDroneHeadingDegrees: Double? = null
    private var lastPlanningLogSignature: String? = null
    private var lastConflictLogSignature: String? = null
    private var lastHealthLogSignature: String? = null
    private var lastHealthState: GeoAwarenessHealthState? = null
    private var lastLiveZoneIdentityMap: Map<String, GeoZone> = emptyMap()
    private var lastLiveProximityIdentity: String? = null
    private val authorizedUgzIdsForCurrentFlight = mutableSetOf<String>()
    private var lastRtkStreamingActive: Boolean? = null
    private var isDrawingModeActive = false
    private var hasCenteredInitialViewport = false
    private var hasCenteredToDrone = false
    private var homePosition: LatLon? = null
    private var wasDroneArmed: Boolean = false
    private var pendingHomeMarkerAfterArm: Boolean = false
    private var lastTracePosition: LatLon? = null
    private var flightTracePointCount: Int = 0
    private var initialCenterAttemptCount = 0
    private var initialDroneCenterAttemptCount = 0
    private var lastGeoZoneReloadToken: Long = 0L
    private var geoZoneReloadInProgress: Boolean = false

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
        private const val MIN_VALID_ABS_COORDINATE = 1e-4
        private const val MIN_TRACE_POINT_DISTANCE_METERS = 2.0
        private const val DEFAULT_NEAR_ZONE_THRESHOLD_METERS = 100.0
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
        geoIncidentLogger = GeoIncidentLogger(
            GeoIncidentEncryptedLogStore(requireContext().applicationContext)
        )
        operatorEventLogger = OperatorFlightEventLogger(geoEventLogger)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeMapView(view)
        initControllers()
        bindUiButtons()
        applyMapInsets()
        observeDroneViewModel()
        observeMapState()
        observeHomeTelemetry()
        observeMissionMapViewModel()
        observeGeoAwarenessSharedState()

        mapViewModel.updateFromMapState(activityViewModel.mapState.value ?: MainActivityViewModel.MapState.Idle)
    }

    private fun initializeMapView(view: View) {
        mapView = view.findViewById(R.id.osmMap)
        mapView.setBuiltInZoomControls(false)
        mapView.setMultiTouchControls(true)
        mapView.setTileSource(EsriWorldImageryTileSource)
        mapView.isTilesScaledToDpi = true
        mapView.maxZoomLevel = 20.0
        mapView.controller.setZoom(DEFAULT_MAP_ZOOM)
        mapView.controller.setCenter(GeoPoint(DEFAULT_MAP_LAT, DEFAULT_MAP_LON))

        osmdroidMapController = OsmdroidMapController(requireContext(), mapView)
        osmdroidMapController.initOverlays()

        osmdroidPolygonEditor = OsmdroidPolygonEditor(requireActivity(), activityViewModel, mapView)
        osmdroidPolygonEditor.init()

        osmdroidRouteWaypointEditor = OsmdroidRouteWaypointEditor(requireContext(), activityViewModel, mapView)
        osmdroidRouteWaypointEditor.init()

        geoZoneOverlayController = GeoZoneOverlayController(requireContext(), mapView)
        geoAwarenessChecker = GeoAwarenessChecker()
        geoAwarenessStatusBinder = GeoAwarenessStatusViewBinder(
            requireContext(),
            binding.geoAwarenessStatusChip
        )
        liveGeoAwarenessChecker = LiveGeoAwarenessChecker()
        liveGeoAwarenessStatusBinder = LiveGeoAwarenessPanelBinder(
            requireView().findViewById(R.id.liveGeoAwarenessPanel)
        )
        loadGeoAwarenessZonesIfNeeded()
        renderGeoAwarenessLayerIfVisible()
        geoAwarenessStatusBinder?.clear()
        geoAwarenessStatusBinder?.setOnClickListener(View.OnClickListener {
            showGeoAwarenessPlanningDetails()
        })
        liveGeoAwarenessStatusBinder?.bindUnknown("No drone position")
        liveGeoAwarenessStatusBinder?.setOnClickListener(View.OnClickListener {
            showLiveGeoAwarenessDetails()
        })
        updateGeoAwarenessPlanningStatus()
    }

    private fun initControllers() {
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

        missionParamsController = MissionParamsController(
            context = requireContext(),
            rootView = requireView(),
            lifecycleOwner = viewLifecycleOwner,
            activityViewModel = activityViewModel,
            droneViewModel = droneViewModel,
            beforeUploadGuard = { onAllowed ->
                handleGeoAwarenessBeforeUpload(onAllowed)
            }
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
            onEnterIdle = { droneViewModel.downloadMissionNew() }
        )
    }

    private fun bindUiButtons() {
        homeMapChromeBinder.bindActions(
            onDownloadOffline = {
                downloadCurrentViewOffline(minZoom = OFFLINE_MIN_ZOOM, maxZoom = OFFLINE_MAX_ZOOM)
            },
            onCenterOnUser = {
                if (mapViewModel.homeMapUiState.value?.interactionState?.isDrawingEnabled != true) {
                    osmdroidMapController.centerOnUserIfPermitted()
                }
            },
            onCenterOnDrone = {
                if (droneViewModel.conStateLiveData.value == true) {
                    osmdroidMapController.centerOnDrone()
                } else {
                    Toast.makeText(context, getString(R.string.no_conn_msg), Toast.LENGTH_LONG).show()
                }
            },
            onOpenSettings = {
                requireActivity()
                    .findViewById<DrawerLayout>(R.id.drawer_layout)
                    .openDrawer(GravityCompat.START)
            },
            onTogglePlanning = {
                mapViewModel.togglePlanningPanelVisible()
            }
        )

        requireView().findViewById<com.google.android.material.button.MaterialButton>(R.id.right_panel_load_button)
            .setOnClickListener {
                if (missionFileStore.listMissionFiles().isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.no_saved_missions_yet), Toast.LENGTH_LONG).show()
                    activityViewModel.mapState.value = MainActivityViewModel.MapState.Idle
                } else {
                    activityViewModel.mapState.postValue(MainActivityViewModel.MapState.LoadMissionFromFile)
                }
            }

        requireView().findViewById<com.google.android.material.button.MaterialButton?>(R.id.right_panel_close_button)
            ?.setOnClickListener {
                mapViewModel.dismissSidePanels()
            }

        binding.homeDrawAcceptButton.setOnClickListener {
            val verts = activityViewModel.missionArea.value?.vertices ?: emptyList()
            if (verts.size < 3) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.wrong_schema_msg),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                activityViewModel.mapState.value = MainActivityViewModel.MapState.SetFlightParams
            }
        }

        binding.homeDrawDeclineButton.setOnClickListener {
            activityViewModel.sendAction(MainActivityViewModel.MapAction.ResetToIdle)
        }

        bindWorkflowToggle()

        requireView().findViewById<TextView>(R.id.right_panel_draw_area_button).setOnClickListener {
            if (activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS) {
                Toast.makeText(requireContext(), getString(R.string.tap_map_to_add_points), Toast.LENGTH_SHORT).show()
            } else {
                startAreaDrawing()
            }
        }

        requireView().findViewById<TextView>(R.id.right_panel_clear_area_button).setOnClickListener {
            if (activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS) {
                activityViewModel.clearRouteWaypoints()
            } else {
                activityViewModel.sendAction(MainActivityViewModel.MapAction.ClearAreaOnly)
            }
        }

        requireView().findViewById<TextView?>(R.id.right_panel_undo_route_button)?.setOnClickListener {
            activityViewModel.undoLastRouteWaypoint()
        }
        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
    }

    private fun startAreaDrawing() {
        mapViewModel.setPlanningPanelVisible(false)
        activityViewModel.setPlanningWorkflow(PlanningWorkflow.AREA)
        activityViewModel.missionArea.value?.clearAll()
        activityViewModel.mapState.value = MainActivityViewModel.MapState.Draw
    }

    private fun bindWorkflowToggle() {
        val areaButton = requireView().findViewById<TextView>(R.id.right_panel_area_button)
        val pointsButton = requireView().findViewById<TextView>(R.id.right_panel_points_button)

        listOf(areaButton, pointsButton).forEach { button ->
            button.includeFontPadding = false
            button.gravity = android.view.Gravity.CENTER
        }

        areaButton.setOnClickListener {
            activityViewModel.setPlanningWorkflow(PlanningWorkflow.AREA)
            activityViewModel.mapState.value = MainActivityViewModel.MapState.SetFlightParams
        }

        pointsButton.setOnClickListener {
            activityViewModel.setPlanningWorkflow(PlanningWorkflow.POINTS)
            activityViewModel.mapState.value = MainActivityViewModel.MapState.SetFlightParams
            Toast.makeText(requireContext(), getString(R.string.tap_map_to_add_points), Toast.LENGTH_SHORT).show()
        }

        activityViewModel.activePlanningWorkflow.observe(viewLifecycleOwner) { workflow ->
            renderWorkflowSelection(workflow)
            updateRouteEditorEnabled()
            updateGeoAwarenessPlanningStatus()
        }
    }

    private fun applyMapInsets() {
        val dockBottomMargin = (binding.homeBottomUtilityDock.layoutParams as MarginLayoutParams).bottomMargin
        val drawBottomMargin = (binding.homeDrawActionBar.layoutParams as MarginLayoutParams).bottomMargin
        val planningLabelBottomMargin = (binding.homeBottomPlanningLabel.layoutParams as MarginLayoutParams).bottomMargin

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bottomInset = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            ).bottom

            updateBottomMargin(binding.homeBottomUtilityDock, dockBottomMargin + bottomInset)
            updateBottomMargin(binding.homeDrawActionBar, drawBottomMargin + bottomInset)
            updateBottomMargin(binding.homeBottomPlanningLabel, planningLabelBottomMargin + bottomInset)

            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun updateBottomMargin(view: View, marginBottom: Int) {
        val layoutParams = view.layoutParams as? MarginLayoutParams ?: return
        if (layoutParams.bottomMargin == marginBottom) {
            return
        }
        layoutParams.bottomMargin = marginBottom
        view.layoutParams = layoutParams
    }

    private fun renderWorkflowSelection(workflow: PlanningWorkflow) {
        val areaButton = requireView().findViewById<TextView>(R.id.right_panel_area_button)
        val pointsButton = requireView().findViewById<TextView>(R.id.right_panel_points_button)
        val drawButton = requireView().findViewById<TextView>(R.id.right_panel_draw_area_button)
        val clearButton = requireView().findViewById<TextView>(R.id.right_panel_clear_area_button)
        val undoButton = requireView().findViewById<TextView?>(R.id.right_panel_undo_route_button)
        val routeSummary = requireView().findViewById<TextView?>(R.id.right_panel_route_summary)
        val selectedTextColor = if (resources.getBoolean(R.bool.config_tablet_planning_dock)) {
            R.color.ds_color_shell_active
        } else {
            R.color.ds_color_shell_selected_content
        }

        fun style(button: TextView, selected: Boolean) {
            button.setBackgroundResource(
                if (selected) R.drawable.bg_ds_panel_pill_active
                else R.drawable.bg_ds_panel_pill_inactive
            )
            button.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) selectedTextColor else R.color.ds_color_text_primary
                )
            )
            button.setTypeface(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
            button.includeFontPadding = false
            button.gravity = android.view.Gravity.CENTER
        }

        val isPoints = workflow == PlanningWorkflow.POINTS
        style(areaButton, !isPoints)
        style(pointsButton, isPoints)
        drawButton.text = getString(if (isPoints) R.string.add_route_points else R.string.draw_area)
        clearButton.text = getString(if (isPoints) R.string.clear_route else R.string.clear_area)
        undoButton?.visibility = if (isPoints) View.VISIBLE else View.GONE
        routeSummary?.visibility = if (isPoints) View.VISIBLE else View.GONE
        updateRouteSummary()
    }

    private fun updateRouteSummary() {
        val routeSummary = requireView().findViewById<TextView?>(R.id.right_panel_route_summary) ?: return
        val waypoints = activityViewModel.routeWaypoints.value.orEmpty()
        routeSummary.text = getString(R.string.route_summary_format, waypoints.size)
    }

    private fun updateRouteEditorEnabled() {
        if (!::osmdroidRouteWaypointEditor.isInitialized) return
        osmdroidRouteWaypointEditor.setEnabled(
            activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS
        )
    }

    private fun updateRouteDistance(waypoints: List<com.example.droneservicesapp.domain.model.RouteWaypoint>) {
        if (waypoints.size < 2) {
            activityViewModel.flightDistance.postValue(0)
            return
        }
        val distance = waypoints.zipWithNext().sumOf { (from, to) ->
            SphericalUtil.computeDistanceBetween(
                LatLng(from.latitude, from.longitude),
                LatLng(to.latitude, to.longitude)
            )
        }
        activityViewModel.flightDistance.postValue(distance.toInt())
    }

    private fun observeDroneViewModel() {
        droneViewModel.droneLocationLiveData.observe(viewLifecycleOwner) { droneLocation ->
            syncLatestDroneLocationSnapshot(droneLocation)

            val livePosition = latestRealDronePosition
            if (livePosition != null) {
                osmdroidMapController.updateDronePosition(
                    livePosition.lat,
                    livePosition.lon
                )
                maybeSetPendingHomeMarker(livePosition)
                maybeAppendFlightTrace(livePosition)
                centerOnDroneIfNeeded()
            } else {
                osmdroidMapController.setDroneVisible(false)
                centerInitialViewportIfNeeded()
            }

            updateLiveGeoAwarenessFromActiveSource()
        }

        droneViewModel.conStateLiveData.observe(viewLifecycleOwner) {
            syncLatestDroneLocationSnapshot(droneViewModel.droneLocationLiveData.value)
            updateLiveGeoAwarenessFromActiveSource()
        }

        droneViewModel.gpsFixType.observe(viewLifecycleOwner) {
            syncLatestDroneLocationSnapshot(droneViewModel.droneLocationLiveData.value)
            updateLiveGeoAwarenessFromActiveSource()
        }

        droneViewModel.droneHeading.observe(viewLifecycleOwner) { droneHeading ->
            droneHeading?.let { heading ->
                latestRealDroneHeadingDegrees = heading
                osmdroidMapController.updateDroneHeadingDegrees(heading.toFloat())
            }
        }

        droneViewModel.droneGroundSpeedMetersPerSecond.observe(viewLifecycleOwner) { speed ->
            latestRealDroneGroundSpeedMetersPerSecond = speed
        }

        droneViewModel.droneVerticalSpeedMetersPerSecond.observe(viewLifecycleOwner) { speed ->
            latestRealDroneVerticalSpeedMetersPerSecond = speed
        }

        droneViewModel.droneAltitudeAmslMeters.observe(viewLifecycleOwner) { altitudeAmslMeters ->
            latestRealDroneAltitudeAmslMeters = altitudeAmslMeters
            updateLiveGeoAwarenessFromActiveSource()
        }

        droneViewModel.armedState.observe(viewLifecycleOwner) { armed ->
            handleArmedStateChanged(armed == true)
        }

        activityViewModel.missionArea.observe(viewLifecycleOwner) { missionArea ->
            val vertices = missionArea?.vertices ?: emptyList()
            val hasRoute = activityViewModel.routeWaypoints.value.orEmpty().size >= 2
            mapViewModel.setMissionAreaAvailable(vertices.size >= 3 || hasRoute)
            osmdroidPolygonEditor.setVertices(vertices)
            updateGeoAwarenessPlanningStatus()
        }

        droneViewModel.missionItems.observe(viewLifecycleOwner) { missionItems ->
            if (droneViewModel.conStateLiveData.value == true && missionItems.isNotEmpty()) {
                osmdroidMapController.clearSurveyPath()

                val surveyPath = ArrayList<LatLng>()
                for (item in missionItems) {
                    if (item.seq() > 0 && item.command().entry() == MavCmd.MAV_CMD_NAV_WAYPOINT) {
                        surveyPath.add(
                            LatLng(
                                item.x() * 10e-8,
                                item.y() * 10e-8
                            )
                        )
                    }
                }

                activityViewModel.surveyPath.postValue(surveyPath)
            }
        }

        droneViewModel.rtkForwardingState.observe(viewLifecycleOwner) { state ->
            val streaming = state is RtkForwardingState.Streaming
            if (lastRtkStreamingActive == streaming) {
                return@observe
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
        activityViewModel.angleProgress.observe(viewLifecycleOwner, Observer { angle ->
            if (
                activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams &&
                activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA &&
                activityViewModel.planningOperationMode.value == PlanningOperationMode.SPRAY
            ) {
                savePreference(getString(R.string.survey_angle_pref), angle.toInt().toString())
                drawSprayMissionOnMap(
                    activityViewModel.lineDistanceProgress.value!!,
                    angle.toInt()
                )
            }
        })

        activityViewModel.lineDistanceProgress.observe(viewLifecycleOwner, Observer { distance ->
            if (
                activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams &&
                activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA &&
                activityViewModel.planningOperationMode.value == PlanningOperationMode.SPRAY
            ) {
                savePreference(
                    getString(R.string.survey_line_distance_pref),
                    distance.toInt().toString()
                )
                drawSprayMissionOnMap(
                    distance,
                    activityViewModel.angleProgress.value!!.toInt()
                )
            }
        })

        activityViewModel.flightAltProgress.observe(viewLifecycleOwner, Observer { altitude ->
            if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams) {
                savePreference(
                    getString(R.string.survey_altitude_pref),
                    altitude.toInt().toString()
                )
            }
            updateGeoAwarenessPlanningStatus()
        })

        activityViewModel.surveyStripSpacing.observe(viewLifecycleOwner) { spacing ->
            if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams &&
                activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA &&
                activityViewModel.planningOperationMode.value == PlanningOperationMode.SURVEY
            ) {
                savePreference(getString(R.string.survey_strip_spacing_pref), spacing?.toInt().toString())
                redrawAreaMissionOnMap()
            }
        }

        activityViewModel.surveyHeightAboveTerrain.observe(viewLifecycleOwner) { height ->
            if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams &&
                activityViewModel.planningOperationMode.value == PlanningOperationMode.SURVEY
            ) {
                savePreference(getString(R.string.survey_height_above_terrain_pref), height?.toInt().toString())
            }
            updateGeoAwarenessPlanningStatus()
        }

        activityViewModel.surveyOverlapPercent.observe(viewLifecycleOwner) { overlap ->
            if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams &&
                activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA &&
                activityViewModel.planningOperationMode.value == PlanningOperationMode.SURVEY
            ) {
                savePreference(getString(R.string.survey_overlap_pref), overlap?.toInt().toString())
                redrawAreaMissionOnMap()
            }
        }

        activityViewModel.surveyGridAngle.observe(viewLifecycleOwner) { angle ->
            if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams &&
                activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA &&
                activityViewModel.planningOperationMode.value == PlanningOperationMode.SURVEY
            ) {
                savePreference(getString(R.string.survey_grid_angle_pref), angle?.toInt().toString())
                redrawAreaMissionOnMap()
            }
        }

        activityViewModel.surveyTerrainSegment.observe(viewLifecycleOwner) { segment ->
            if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams &&
                activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA &&
                activityViewModel.planningOperationMode.value == PlanningOperationMode.SURVEY
            ) {
                savePreference(getString(R.string.survey_terrain_segment_pref), segment.toString())
                redrawAreaMissionOnMap()
            }
        }

        activityViewModel.surveyCanopySmoothing.observe(viewLifecycleOwner) { canopy ->
            if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams &&
                activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA &&
                activityViewModel.planningOperationMode.value == PlanningOperationMode.SURVEY
            ) {
                savePreference(getString(R.string.survey_canopy_smoothing_pref), canopy?.toInt().toString())
                redrawAreaMissionOnMap()
            }
        }

        activityViewModel.surveyPath.observe(viewLifecycleOwner) { surveyPath ->
            val hasPolygon = (activityViewModel.missionArea.value?.vertices?.size ?: 0) >= 3
            val hasRoute = activityViewModel.routeWaypoints.value.orEmpty().size >= 2
            mapViewModel.setMissionAreaAvailable(hasPolygon || hasRoute || !surveyPath.isNullOrEmpty())
            updateGeoAwarenessPlanningStatus()
        }

        activityViewModel.routeWaypoints.observe(viewLifecycleOwner) { waypoints ->
            osmdroidRouteWaypointEditor.setWaypoints(waypoints.orEmpty())
            val hasPolygon = (activityViewModel.missionArea.value?.vertices?.size ?: 0) >= 3
            val hasSurveyPath = !activityViewModel.surveyPath.value.isNullOrEmpty()
            mapViewModel.setMissionAreaAvailable(hasPolygon || hasSurveyPath || waypoints.orEmpty().size >= 2)
            if (activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS) {
                updateRouteDistance(waypoints.orEmpty())
            }
            updateRouteSummary()
            updateGeoAwarenessPlanningStatus()
        }

        activityViewModel.mapState.observe(viewLifecycleOwner) { mapState ->
            mapViewModel.updateFromMapState(mapState)
            if (
                mapState == MainActivityViewModel.MapState.SetFlightParams &&
                activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA
            ) {
                val hasPolygon = (activityViewModel.missionArea.value?.vertices?.size ?: 0) >= 3
                if (hasPolygon) {
                    redrawAreaMissionOnMap()
                }
            }
            updatePlanningGeoAwarenessVisibility()
        }

        activityViewModel.planningOperationMode.observe(viewLifecycleOwner) {
            if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams &&
                activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA &&
                (activityViewModel.missionArea.value?.vertices?.size ?: 0) >= 3
            ) {
                redrawAreaMissionOnMap()
            }
        }

        activityViewModel.mapAction.observe(viewLifecycleOwner) { event ->
            val action = event?.getContentIfNotHandled() ?: return@observe
            when (action) {
                is MainActivityViewModel.MapAction.ClearAll -> {
                    activityViewModel.clearPolygonVertices()
                    activityViewModel.surveyPath.postValue(emptyList())
                    osmdroidPolygonEditor.clear()
                    osmdroidMapController.clearSurveyPath()
                    activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Draw)
                }
                is MainActivityViewModel.MapAction.ClearAreaOnly -> {
                    activityViewModel.clearPolygonVertices()
                    activityViewModel.surveyPath.postValue(emptyList())
                    osmdroidPolygonEditor.clear()
                    osmdroidMapController.clearSurveyPath()
                    activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
                }
                is MainActivityViewModel.MapAction.ClearKeepDrawing -> {
                    activityViewModel.surveyPath.postValue(emptyList())
                    osmdroidMapController.clearSurveyPath()
                    osmdroidPolygonEditor.clear()
                    activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Draw)
                }
                is MainActivityViewModel.MapAction.ResetToIdle -> {
                    activityViewModel.clearPolygonVertices()
                    activityViewModel.surveyPath.postValue(emptyList())
                    osmdroidPolygonEditor.clear()
                    osmdroidMapController.clearSurveyPath()
                    activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
                }
                is MainActivityViewModel.MapAction.UploadMissionSuccess -> {
                    operatorEventLogger.logMissionUploadSucceeded(activityViewModel.surveyPath.value?.size)
                    Snackbar.make(requireView(), getString(R.string.upload_complete), Snackbar.LENGTH_LONG).show()
                    activityViewModel.sendAction(MainActivityViewModel.MapAction.ResetToIdle)
                }
                is MainActivityViewModel.MapAction.UploadMissionFailed -> {
                    operatorEventLogger.logMissionUploadFailed(action.reason)
                    Toast.makeText(context, action.reason, Toast.LENGTH_LONG).show()
                    Snackbar.make(
                        requireView(),
                        getString(R.string.upload_failed_with_reason, action.reason),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun observeHomeTelemetry() {
        homeTelemetryViewModel.homeTelemetryUiState.observe(viewLifecycleOwner) { state ->
            homeMapTelemetryBinder.render(state)
        }
    }



    private fun observeMissionMapViewModel() {
        mapViewModel.homeMapUiState.observe(viewLifecycleOwner) { state ->
            renderHomeMapUiState(state)
        }
    }

    private fun renderHomeMapUiState(state: HomeMapUiState) {
        isDrawingModeActive = state.interactionState.isDrawingEnabled
        geoZoneOverlayController?.setZoneDetailsEnabled(!state.interactionState.isDrawingEnabled)
        osmdroidPolygonEditor.setEnabled(
            state.interactionState.isDrawingEnabled &&
                activityViewModel.activePlanningWorkflow.value != PlanningWorkflow.POINTS
        )
        updateRouteEditorEnabled()
        homeMapChromeBinder.renderShell(state.shellState)
        homeMapChromeBinder.renderInteraction(state.interactionState)
        homeMapChromeBinder.renderOverlayControls(state.overlayControlsState)
        homeMapPanelsBinder.renderShell(state.shellState)
        homeMapPanelsBinder.renderOverlays(state.panelState)
        homeMapModeEffectsBinder.render(state.screenMode)

        updatePlanningGeoAwarenessVisibility()
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
        val cacheManager = CacheManager(mapView)

        cacheManager.downloadAreaAsync(
            requireContext(),
            bbox,
            minZoom,
            maxZoom,
            object : CacheManager.CacheManagerCallback {
                override fun downloadStarted() {
                    Toast.makeText(requireContext(), getString(R.string.offline_download_started), Toast.LENGTH_SHORT)
                        .show()
                }

                override fun setPossibleTilesInArea(total: Int) {}
                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {}
                override fun onTaskComplete() {
                    Toast.makeText(requireContext(), getString(R.string.offline_download_complete), Toast.LENGTH_LONG)
                        .show()
                }

                override fun onTaskFailed(errors: Int) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.offline_download_failed, errors),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun redrawAreaMissionOnMap() {
        when (activityViewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY) {
            PlanningOperationMode.SPRAY -> drawSprayMissionOnMap(
                activityViewModel.lineDistanceProgress.value ?: 0.0,
                activityViewModel.angleProgress.value?.toInt() ?: 0
            )
            PlanningOperationMode.SURVEY -> drawSurveyGridMissionOnMap()
        }
    }

    private fun drawSprayMissionOnMap(distance: Double, angle: Int) {
        osmdroidMapController.clearSurveyPath()

        val area = activityViewModel.missionArea.value ?: return

        // Convert current polygon vertices to domain-level LatLon
        val polygonLatLon = area.vertices.map { LatLon(it.latitude, it.longitude) }

        // Build survey path using pure planner
        val planner = SurveyPlanner()
        val pathLatLon = planner.buildSurveyPath(
            polygon = polygonLatLon,
            distanceMeters = distance,
            angleDeg = angle
        )

        if (pathLatLon.isEmpty()) {
            activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Draw)
            return
        }

        // Convert to Google LatLng only where required (map drawing + mission building)
        val gmsPath = pathLatLon.map { LatLng(it.lat, it.lon) }

        // Estimate flight distance: sum each consecutive pair length when even-sized path
        if (gmsPath.size >= 2 && gmsPath.size % 2 == 0) {
            var surveyDistance = 0.0
            var i = 0
            while (i < gmsPath.size) {
                surveyDistance += SphericalUtil.computeDistanceBetween(gmsPath[i], gmsPath[i + 1])
                i += 2
            }
            activityViewModel.flightDistance.postValue(surveyDistance.toInt())
        }

        activityViewModel.surveyPath.postValue(gmsPath)
        osmdroidMapController.setSurveyPath(gmsPath)
    }

    private fun drawSurveyGridMissionOnMap() {
        osmdroidMapController.clearSurveyPath()

        val area = activityViewModel.missionArea.value ?: return
        val polygonLatLon = area.vertices.map { LatLon(it.latitude, it.longitude) }
        val planner = SurveyGridPlanner()
        val pathLatLon = planner.buildSurveyPath(
            polygon = polygonLatLon,
            params = activityViewModel.surveyGridParams.value ?: return
        )

        if (pathLatLon.isEmpty()) {
            activityViewModel.surveyPath.postValue(emptyList())
            return
        }

        val gmsPath = pathLatLon.map { LatLng(it.lat, it.lon) }
        if (gmsPath.size >= 2 && gmsPath.size % 2 == 0) {
            var surveyDistance = 0.0
            var i = 0
            while (i < gmsPath.size) {
                surveyDistance += SphericalUtil.computeDistanceBetween(gmsPath[i], gmsPath[i + 1])
                i += 2
            }
            activityViewModel.flightDistance.postValue(surveyDistance.toInt())
        }

        activityViewModel.surveyPath.postValue(gmsPath)
        osmdroidMapController.setSurveyPath(gmsPath)
    }

    override fun onDestroyView() {
        geoZoneOverlayController?.clear()
        geoZoneOverlayController = null
        geoAwarenessStatusBinder = null
        geoAwarenessChecker = null
        liveGeoAwarenessStatusBinder = null
        liveGeoAwarenessChecker = null
        clearFlightTrace()
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<Toolbar>(R.id.customToolbar)?.navigationIcon = null
        mapView.onResume()
        osmdroidMapController.onResume()
        binding.root.post { centerInitialViewportIfNeeded() }
    }

    override fun onPause() {
        osmdroidMapController.onPause()
        mapView.onPause()
        super.onPause()
    }

    private fun centerInitialViewportIfNeeded() {
        if (_binding == null) return
        if (hasCenteredToDrone) return

        if (osmdroidMapController.hasDronePosition()) {
            centerOnDroneIfNeeded()
            return
        }

        if (initialDroneCenterAttemptCount < MAX_INITIAL_DRONE_CENTER_ATTEMPTS) {
            initialDroneCenterAttemptCount += 1
            _binding?.root?.postDelayed({ centerInitialViewportIfNeeded() }, 1000L)
            return
        }

        if (hasCenteredInitialViewport) return

        val centered = osmdroidMapController.centerOnUserIfPermitted(showErrors = false)
        if (centered) {
            hasCenteredInitialViewport = true
        } else if (initialCenterAttemptCount < 10) {
            initialCenterAttemptCount += 1
            _binding?.root?.postDelayed({ centerInitialViewportIfNeeded() }, 1000L)
        }
    }

    private fun centerOnDroneIfNeeded() {
        if (hasCenteredToDrone) return

        if (osmdroidMapController.centerOnDrone()) {
            hasCenteredToDrone = true
            hasCenteredInitialViewport = true
        } else {
            centerInitialViewportIfNeeded()
        }
    }

    private fun handleArmedStateChanged(isArmed: Boolean) {
        if (!wasDroneArmed && isArmed) {
            val currentPosition = latestRealDronePosition
            if (currentPosition != null && isValidDronePosition(currentPosition)) {
                setHomeMarker(currentPosition)
            } else {
                pendingHomeMarkerAfterArm = true
            }
        } else if (wasDroneArmed && !isArmed) {
            resetCurrentFlightUgzAuthorizations("disarmed")
        }
        wasDroneArmed = isArmed
    }

    private fun syncLatestDroneLocationSnapshot(droneLocation: android.location.Location?) {
        val usableLocation = droneLocation?.takeIf(::isUsableDroneLocation)
        latestRealDronePosition = usableLocation?.let {
            LatLon(lat = it.latitude, lon = it.longitude)
        }
        latestRealDroneAltitudeMeters = usableLocation?.altitude
        latestRealDroneHorizontalAccuracyMeters = usableLocation
            ?.takeIf { it.hasAccuracy() }
            ?.accuracy
        latestRealDroneVerticalAccuracyMeters = usableLocation
            ?.takeIf { android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && it.hasVerticalAccuracy() }
            ?.verticalAccuracyMeters
    }

    private fun resetCurrentFlightUgzAuthorizations(reason: String) {
        if (authorizedUgzIdsForCurrentFlight.isEmpty()) return
        val resetIds = authorizedUgzIdsForCurrentFlight.toList()
        authorizedUgzIdsForCurrentFlight.clear()
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
        if (!pendingHomeMarkerAfterArm) return
        if (!isValidDronePosition(position)) return
        setHomeMarker(position)
        pendingHomeMarkerAfterArm = false
    }

    private fun setHomeMarker(position: LatLon) {
        if (homePosition != null) return
        if (!isValidDronePosition(position)) return
        if (osmdroidMapController.setHomeMarker(position.lat, position.lon)) {
            homePosition = position
            Log.d(MAP_FLIGHT_TRACE_TAG, "home marker set lat=${position.lat} lon=${position.lon}")
        }
    }

    private fun maybeAppendFlightTrace(position: LatLon) {
        if (droneViewModel.conStateLiveData.value != true) return
        if (droneViewModel.armedState.value != true) return
        appendFlightTracePoint(position)
    }

    private fun appendFlightTracePoint(position: LatLon) {
        if (!shouldAppendTracePoint(position)) return
        osmdroidMapController.appendFlightTracePoint(position.lat, position.lon)
        lastTracePosition = position
        flightTracePointCount += 1
        when {
            flightTracePointCount == 1 -> Log.d(MAP_FLIGHT_TRACE_TAG, "trace started")
            flightTracePointCount % 50 == 0 -> Log.d(MAP_FLIGHT_TRACE_TAG, "trace point count=$flightTracePointCount")
        }
    }

    private fun clearFlightTrace() {
        osmdroidMapController.clearFlightTraceAndHome()
        homePosition = null
        pendingHomeMarkerAfterArm = false
        lastTracePosition = null
        flightTracePointCount = 0
    }

    private fun shouldAppendTracePoint(position: LatLon): Boolean {
        if (!isValidDronePosition(position)) return false
        val lastPosition = lastTracePosition ?: return true
        return distanceMeters(lastPosition, position) >= MIN_TRACE_POINT_DISTANCE_METERS
    }

    private fun isValidDronePosition(position: LatLon): Boolean {
        return position.lat.isFinite() &&
            position.lon.isFinite() &&
            position.lat in -90.0..90.0 &&
            position.lon in -180.0..180.0 &&
            (kotlin.math.abs(position.lat) > MIN_VALID_ABS_COORDINATE ||
                kotlin.math.abs(position.lon) > MIN_VALID_ABS_COORDINATE)
    }

    private fun distanceMeters(start: LatLon, end: LatLon): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(end.lat - start.lat)
        val dLon = Math.toRadians(end.lon - start.lon)
        val startLat = Math.toRadians(start.lat)
        val endLat = Math.toRadians(end.lat)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(startLat) * cos(endLat) * sin(dLon / 2.0) * sin(dLon / 2.0)
        return earthRadiusMeters * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun loadGeoAwarenessZonesIfNeeded(): Boolean {
        if (geoAwarenessZones.isNotEmpty()) {
            if (geoAwarenessHealth == null) {
                val health = GeoAwarenessHealthEvaluator.evaluate(
                    datasetInfo = geoZoneDatasetInfo,
                    zones = geoAwarenessZones,
                    datasetRecords = activityViewModel.geoZoneDatasetRecords.value.orEmpty(),
                    validationResult = geoZoneValidationResult,
                    loadError = geoAwarenessLoadError
                )
                geoAwarenessHealth = health
                activityViewModel.geoAwarenessHealth.value = health
            }
            return true
        }

        try {
            val repository = buildGeoZoneRepository()
            val loadResult = repository.loadCurrentDataset()
            applyGeoZoneLoadResult(loadResult, repository.hasImportedDatasets())
            logDatasetLoaded(loadResult.datasetInfo)
            logDatasetValidation(loadResult.validationResult, loadResult.datasetInfo)
            logMultiDatasetLoadedIfNeeded(loadResult)
            geoAwarenessHealth?.let { logHealthEvaluationIfNeeded(it) }
            return true
        } catch (error: Exception) {
            Log.e(GEO_ZONE_TOGGLE_TAG, "Failed to load geo-awareness zones", error)
            geoAwarenessZones = emptyList()
            geoZoneDatasetInfo = null
            geoAwarenessLoadError = error
            geoZoneValidationResult = null
            geoAwarenessHealth = GeoAwarenessHealthEvaluator.evaluate(
                datasetInfo = null,
                zones = emptyList(),
                datasetRecords = emptyList(),
                loadError = error
              )
              activityViewModel.geoZoneDatasetInfo.value = null
              activityViewModel.geoZoneValidationResult.value = null
              activityViewModel.geoZoneDatasetRecords.value = emptyList()
              activityViewModel.geoZoneImportedActive.value = false
              activityViewModel.geoAwarenessHealth.value = geoAwarenessHealth
              logDatasetLoadFailed(error)
            geoAwarenessHealth?.let { logHealthEvaluationIfNeeded(it) }
        }

        return false
    }

    private fun observeGeoAwarenessSharedState() {
        activityViewModel.geoAwarenessLayerVisible.observe(viewLifecycleOwner) {
            renderGeoAwarenessLayerIfVisible()
        }
        activityViewModel.geoZoneReloadToken.observe(viewLifecycleOwner) { token ->
            if (token == null || token <= 0L || token == lastGeoZoneReloadToken) {
                return@observe
            }
            lastGeoZoneReloadToken = token
            reloadCurrentGeoAwarenessDataset()
        }
    }

    private fun reloadCurrentGeoAwarenessDataset() {
        if (geoZoneReloadInProgress) return
        geoZoneReloadInProgress = true
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            try {
                val reloadResult = withContext(Dispatchers.IO) {
                    val repository = GeoZoneRepository(
                        importedFileDataSource = GeoZoneImportedFileDataSource(appContext)
                    )
                    repository.loadCurrentDataset() to repository.hasImportedDatasets()
                }
                val (loadResult, importedActive) = reloadResult
                if (_binding == null) return@launch
                applyGeoZoneLoadResult(loadResult, importedActive)
                logMultiDatasetLoadedIfNeeded(loadResult)
                renderGeoAwarenessLayerIfVisible()
                updateGeoAwarenessPlanningStatus()
                updateLiveGeoAwarenessFromActiveSource()
                geoAwarenessHealth?.let { logHealthEvaluationIfNeeded(it) }
            } catch (error: Exception) {
                Log.e(GEO_ZONE_TOGGLE_TAG, "Failed to reload geo-awareness dataset", error)
                geoAwarenessLoadError = error
                geoAwarenessHealth = GeoAwarenessHealthEvaluator.evaluate(
                    datasetInfo = geoZoneDatasetInfo,
                    zones = geoAwarenessZones,
                    datasetRecords = activityViewModel.geoZoneDatasetRecords.value.orEmpty(),
                    validationResult = geoZoneValidationResult,
                    loadError = error
                )
                activityViewModel.geoAwarenessHealth.value = geoAwarenessHealth
                geoAwarenessHealth?.let { logHealthEvaluationIfNeeded(it) }
            } finally {
                geoZoneReloadInProgress = false
            }
        }
    }

    private fun buildGeoZoneRepository(): GeoZoneRepository {
        val appContext = requireContext().applicationContext
        return GeoZoneRepository(
            importedFileDataSource = GeoZoneImportedFileDataSource(appContext)
        )
    }

    private fun applyGeoZoneLoadResult(
        result: com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult,
        importedActive: Boolean
    ) {
        geoAwarenessZones = result.zones
        geoZoneDatasetInfo = result.datasetInfo
        geoZoneValidationResult = result.validationResult
        geoAwarenessLoadError = null
        geoAwarenessHealth = GeoAwarenessHealthEvaluator.evaluate(
            datasetInfo = result.datasetInfo,
            zones = result.zones,
            datasetRecords = result.datasetRecords,
            validationResult = result.validationResult
        )
        activityViewModel.geoZoneDatasetInfo.value = result.datasetInfo
        activityViewModel.geoZoneValidationResult.value = result.validationResult
        activityViewModel.geoZoneDatasetRecords.value = result.datasetRecords
        activityViewModel.geoZoneImportedActive.value = importedActive
        activityViewModel.geoAwarenessHealth.value = geoAwarenessHealth
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

        latestGeoAwarenessResult = calculateGeoAwarenessPlanningResult()
        val health = ensureGeoAwarenessHealth()
        if (!latestGeoAwarenessResult.hasConflicts && health.state != GeoAwarenessHealthState.AVAILABLE) {
            geoAwarenessStatusBinder?.bindHealth(health)
        } else {
            geoAwarenessStatusBinder?.bindResult(latestGeoAwarenessResult)
        }
        updatePlanningGeoAwarenessVisibility()
        logPlanningStatusIfNeeded(latestGeoAwarenessResult)
        Log.d(
            GEO_PLANNING_STATUS_TAG,
            "Planning geo-awareness updated: conflicts=${latestGeoAwarenessResult.conflicts.size} highest=${latestGeoAwarenessResult.highestRestriction} canUpload=${latestGeoAwarenessResult.canUpload}"
        )
    }

    private fun updatePlanningGeoAwarenessVisibility() {
        if (_binding == null) {
            return
        }

        binding.geoAwarenessStatusChip.visibility = if (isDrawingModeActive) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun calculateGeoAwarenessPlanningResult(): GeoAwarenessResult {
        val missionPolygon = activityViewModel.missionArea.value?.vertices
            ?.takeIf { it.isNotEmpty() }
            ?.map { LatLon(lat = it.latitude, lon = it.longitude) }
        val surveyPath = activityViewModel.surveyPath.value
            ?.takeIf { it.isNotEmpty() }
            ?.map { LatLon(lat = it.latitude, lon = it.longitude) }
            .orEmpty()
        val pointRoutePath = activityViewModel.routeWaypoints.value
            ?.takeIf { it.isNotEmpty() }
            ?.map { LatLon(lat = it.latitude, lon = it.longitude) }
            .orEmpty()
        val planningPath = if (activityViewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS) {
            pointRoutePath
        } else {
            surveyPath
        }
        val altitudeMeters = activityViewModel.flightAltProgress.value?.toDouble()
        // TODO: Confirm altitude source unit is meters.

        if (missionPolygon.isNullOrEmpty() && planningPath.isEmpty()) {
            return GeoAwarenessResult.clear()
        }

        if (!loadGeoAwarenessZonesIfNeeded()) {
            return GeoAwarenessResult.clear()
        }

        return geoAwarenessChecker?.checkMission(
            missionPolygon = missionPolygon,
            surveyPath = planningPath,
            altitudeContext = GeoAltitudeContext(aglMeters = altitudeMeters),
            zones = geoAwarenessZones
        ) ?: GeoAwarenessResult.clear()
    }

    private fun showGeoAwarenessPlanningDetails() {
        val result = latestGeoAwarenessResult
        val title: String
        val message: String

        if (!result.hasConflicts) {
            title = "Geo-awareness"
            message = buildString {
                appendLine("No geo-zone conflicts detected for the current mission plan.")
                append("Verify dataset validity and operational restrictions before flight.")
            }
        } else {
            val orderedConflicts = result.conflicts.sortedWith(
                compareByDescending<GeoZoneConflict> { restrictionRank(it.restriction) }
                    .thenBy { it.zone.name }
                    .thenBy { it.conflictType.name }
            )
            val visibleConflicts = orderedConflicts.take(6)
            val remainingCount = orderedConflicts.size - visibleConflicts.size

            title = "Geo-awareness warning"
            message = buildString {
                appendLine("Highest restriction: ${result.highestRestriction}")
                appendLine("Upload allowed: ${if (result.canUpload) "Yes" else "No"}")
                appendLine("Acknowledgement required: ${if (result.requiresAcknowledgement) "Yes" else "No"}")
                appendLine()
                visibleConflicts.forEach { conflict ->
                    appendLine("- ${conflict.zone.name}")
                    appendLine("  Restriction: ${conflict.restriction}")
                    appendLine("  Type: ${formatConflictType(conflict.conflictType)}")
                    appendLine("  Message: ${conflict.message ?: "No message"}")
                }
                if (remainingCount > 0) {
                    appendLine("...and $remainingCount more.")
                }
                append("Verify dataset validity and operational restrictions before flight.")
            }
        }

        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
    }

    private fun formatConflictType(conflictType: GeoConflictType): String {
        return when (conflictType) {
            GeoConflictType.MISSION_AREA_INTERSECTS_ZONE -> "Mission area intersects zone"
            GeoConflictType.SURVEY_PATH_INTERSECTS_ZONE -> "Survey path intersects zone"
            GeoConflictType.WAYPOINT_INSIDE_ZONE -> "Waypoint inside zone"
        }
    }

    private fun restrictionRank(restriction: GeoZoneRestriction): Int {
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED -> 4
            GeoZoneRestriction.REQ_AUTHORISATION -> 3
            GeoZoneRestriction.CONDITIONAL -> 2
            GeoZoneRestriction.INFORMATION -> 1
            GeoZoneRestriction.UNKNOWN -> 0
        }
    }

    private fun ensureGeoAwarenessHealth(): GeoAwarenessHealth {
        if (geoAwarenessZones.isEmpty() && geoZoneDatasetInfo == null && geoAwarenessLoadError == null) {
            loadGeoAwarenessZonesIfNeeded()
        }

        val health = GeoAwarenessHealthEvaluator.evaluate(
            datasetInfo = geoZoneDatasetInfo,
            zones = geoAwarenessZones,
            datasetRecords = activityViewModel.geoZoneDatasetRecords.value.orEmpty(),
            validationResult = geoZoneValidationResult,
            loadError = geoAwarenessLoadError
        )
        geoAwarenessHealth = health
        activityViewModel.geoAwarenessHealth.value = health
        logHealthEvaluationIfNeeded(health)
        return health
    }

    private fun handleGeoAwarenessBeforeUpload(onAllowed: () -> Unit) {
        val result = try {
            calculateGeoAwarenessPlanningResult().also { latestGeoAwarenessResult = it }
        } catch (error: Exception) {
            Log.w(GEO_UPLOAD_GUARD_TAG, "Geo-awareness result unavailable; proceeding with existing unavailable policy", error)
            onAllowed()
            return
        }
        val health = ensureGeoAwarenessHealth()

        if (!result.hasConflicts) {
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
            return
        }

        when {
            !result.canUpload -> {
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
            result.highestRestriction == GeoZoneRestriction.PROHIBITED -> {
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
            result.requiresAcknowledgement -> {
                val authorizationZones = authorizationRequiredZones(result)
                val unconfirmedAuthorizationZones = authorizationZones
                    .filter { it.id !in authorizedUgzIdsForCurrentFlight }
                    .distinctBy { it.id }
                if (unconfirmedAuthorizationZones.isEmpty()) {
                    Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: authorization already confirmed for current flight")
                    geoEventLogger.logSimple(
                        type = GeoAwarenessEventType.UPLOAD_ACKNOWLEDGED,
                        severity = "INFO",
                        message = "Geo upload authorization already confirmed for current flight",
                        category = "MISSION",
                        datasetTitle = geoZoneDatasetInfo?.title,
                        datasetVersion = geoZoneDatasetInfo?.version,
                        healthState = health.state.name,
                        zoneIds = authorizationZones.map { it.id }.distinct(),
                        zoneNames = authorizationZones.map { it.name }.distinct(),
                        restriction = GeoZoneRestriction.REQ_AUTHORISATION.name,
                        details = mapOf("authorizationScope" to "current_flight")
                    )
                    onAllowed()
                    return
                }

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
                    val confirmedZones = authorizationRequiredZones(result).distinctBy { it.id }
                    authorizedUgzIdsForCurrentFlight += confirmedZones.map { it.id }
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
            else -> {
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
        val message = buildString {
            appendLine("This mission intersects a prohibited geo-zone.")
            appendLine("Upload is blocked by the geo-awareness guard.")
            appendLine("Verify official restrictions in DAGR before flight.")
            appendLine()
            append(buildGeoConflictSummary(result))
        }
        showGeoAwarenessDialog(
            title = "Geo-awareness upload blocked",
            message = message
        )
    }

    private fun showGeoAwarenessProhibitedAcknowledgementDialog(
        result: GeoAwarenessResult,
        health: GeoAwarenessHealth,
        onAcknowledged: () -> Unit
    ) {
        val prohibitedZones = result.conflicts
            .map { it.zone }
            .filter { it.restriction == GeoZoneRestriction.PROHIBITED }
            .distinctBy { it.id }
        val message = buildString {
            appendLine("This mission intersects prohibited UAS geographical zone(s).")
            appendLine("Geo-awareness is advisory in this application. Upload can continue only after the remote pilot acknowledges this warning and verifies official restrictions.")
            appendLine()
            prohibitedZones.forEach { zone ->
                appendLine("- ${zone.name}")
                appendLine("  UGZ ID: ${zone.id}")
                zone.authorities.firstOrNull()?.let { authority ->
                    appendLine("  Authority: ${authority.name ?: "Not specified"}")
                    appendLine("  Purpose: ${authority.purpose ?: "Not specified"}")
                }
            }
            appendLine()
            append(buildGeoConflictSummary(result))
        }
        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle("Prohibited geo-zone warning")
            .setMessage(message)
            .setPositiveButton("Acknowledge and upload") { _, _ ->
                onAcknowledged()
            }
            .setNegativeButton("Cancel") { _, _ ->
                Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: user cancelled prohibited zone warning")
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.UPLOAD_CANCELLED,
                    severity = "INFO",
                    message = "User cancelled prohibited geo-zone upload warning",
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = health.state.name
                )
            }
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
    }

    private fun showGeoAwarenessAcknowledgementDialog(
        result: GeoAwarenessResult,
        health: GeoAwarenessHealth,
        onAcknowledged: () -> Unit
    ) {
        val authorizationZones = authorizationRequiredZones(result).distinctBy { it.id }
        val message = buildString {
            appendLine("This mission intersects authorization-required UAS geographical zone(s).")
            appendLine("Confirm only if notification or authorization has been completed with the relevant authority for each listed UGZ.")
            appendLine("This confirmation is valid for the current flight only and resets when the drone disarms.")
            appendLine()
            authorizationZones.forEach { zone ->
                appendLine("- ${zone.name}")
                appendLine("  UGZ ID: ${zone.id}")
                zone.authorities.firstOrNull()?.let { authority ->
                    appendLine("  Authority: ${authority.name ?: "Not specified"}")
                    appendLine("  Purpose: ${authority.purpose ?: "Not specified"}")
                }
            }
            appendLine()
            append(buildGeoConflictSummary(result))
        }
        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle("Confirm UGZ authorization")
            .setMessage(message)
            .setPositiveButton("Confirm authorization") { _, _ ->
                onAcknowledged()
            }
            .setNegativeButton("Cancel") { _, _ ->
                Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: user cancelled")
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.UPLOAD_CANCELLED,
                    severity = "INFO",
                    message = "User cancelled geo upload acknowledgement",
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = health.state.name
                )
            }
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
    }

    private fun authorizationRequiredZones(result: GeoAwarenessResult): List<GeoZone> {
        return result.conflicts
            .map { it.zone }
            .filter { it.restriction == GeoZoneRestriction.REQ_AUTHORISATION }
            .distinctBy { it.id }
    }

    private fun showGeoAwarenessNoticeDialog(
        result: GeoAwarenessResult,
        health: GeoAwarenessHealth,
        onContinue: () -> Unit
    ) {
        val message = buildString {
            appendLine("This mission intersects conditional/information geo-zones.")
            appendLine()
            append(buildGeoConflictSummary(result))
        }
        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle("Geo-awareness notice")
            .setMessage(message)
            .setPositiveButton("Continue") { _, _ ->
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.UPLOAD_ACKNOWLEDGED,
                    severity = "INFO",
                    message = "User continued after geo upload warning",
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = health.state.name
                )
                onContinue()
            }
            .setNegativeButton("Cancel") { _, _ ->
                Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: user cancelled")
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.UPLOAD_CANCELLED,
                    severity = "INFO",
                    message = "User cancelled geo upload warning",
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = health.state.name
                )
            }
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
    }

    private fun showGeoAwarenessHealthAcknowledgementDialog(
        health: GeoAwarenessHealth,
        onContinue: () -> Unit
    ) {
        val message = buildString {
            appendLine("Current geo-awareness state: ${health.state}")
            appendLine(health.message)
            appendLine(buildGeoHealthNotice(health))
            appendLine("Verify official restrictions in DAGR before flight.")
        }
        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle("Geo-awareness health warning")
            .setMessage(message)
            .setPositiveButton("Continue") { _, _ ->
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.UPLOAD_ACKNOWLEDGED,
                    severity = "INFO",
                    message = "User acknowledged geo-awareness health warning",
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = health.state.name
                )
                onContinue()
            }
            .setNegativeButton("Cancel") { _, _ ->
                Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: user cancelled")
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.UPLOAD_CANCELLED,
                    severity = "INFO",
                    message = "User cancelled geo-awareness health warning",
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = health.state.name
                )
            }
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
    }

    private fun buildGeoConflictSummary(
        result: GeoAwarenessResult,
        maxItems: Int = 5
    ): String {
        val orderedConflicts = result.conflicts.sortedWith(
            compareByDescending<GeoZoneConflict> { restrictionRank(it.restriction) }
                .thenBy { it.zone.name }
                .thenBy { it.conflictType.name }
        )
        val visibleConflicts = orderedConflicts.take(maxItems)
        val remainingCount = orderedConflicts.size - visibleConflicts.size

        return buildString {
            appendLine("Highest restriction: ${result.highestRestriction}")
            appendLine("Upload allowed: ${if (result.canUpload) "Yes" else "No"}")
            appendLine("Acknowledgement required: ${if (result.requiresAcknowledgement) "Yes" else "No"}")
            appendLine()
            visibleConflicts.forEach { conflict ->
                appendLine("- ${conflict.zone.name}")
                appendLine("  Restriction: ${conflict.restriction}")
                appendLine("  Type: ${formatConflictType(conflict.conflictType)}")
                appendLine("  Message: ${conflict.message ?: "No message"}")
            }
            if (remainingCount > 0) {
                append("...and $remainingCount more")
            }
        }
    }

    private fun buildGeoHealthNotice(health: GeoAwarenessHealth): String {
        return when (health.state) {
            GeoAwarenessHealthState.STALE -> "Geo-awareness data may be stale."
            GeoAwarenessHealthState.DEGRADED -> "Geo-awareness data has validation warnings."
            GeoAwarenessHealthState.UNAVAILABLE -> "Geo-awareness data is unavailable. Continuing will bypass geo-awareness protection."
            GeoAwarenessHealthState.AVAILABLE -> "Geo-awareness data is available."
        }
    }

    private fun logDatasetLoaded(info: GeoZoneDatasetInfo) {
        geoEventLogger.logSimple(
            type = GeoAwarenessEventType.DATASET_LOADED,
            severity = "INFO",
            message = "Geo-awareness dataset loaded",
            datasetTitle = info.title,
            datasetVersion = info.version,
            details = mapOf(
                "zoneCount" to info.zoneCount.toString(),
                "circleGeometryCount" to info.circleGeometryCount.toString(),
                "polygonGeometryCount" to info.polygonGeometryCount.toString(),
                "validNonDummyDataset" to (info.zoneCount > 0 && !info.isDummy).toString(),
                "dummy" to info.isDummy.toString()
            )
        )
    }

    private fun logDatasetLoadFailed(error: Throwable) {
        geoEventLogger.logSimple(
            type = GeoAwarenessEventType.DATASET_LOAD_FAILED,
            severity = "ERROR",
            message = "Geo-awareness dataset failed to load: ${error::class.java.simpleName}: ${error.message}",
            details = mapOf(
                "errorClass" to error::class.java.name,
                "errorMessage" to (error.message ?: "unknown")
            )
        )
    }

    private fun logDatasetValidation(
        result: GeoZoneValidationResult,
        info: GeoZoneDatasetInfo
    ) {
        val details = mapOf(
            "errorCount" to result.errorCount.toString(),
            "warningCount" to result.warningCount.toString(),
            "infoCount" to result.infoCount.toString(),
            "issueCodes" to result.issues.take(10).joinToString(",") { it.code }
        )
        when {
            result.hasErrors -> geoEventLogger.logSimple(
                type = GeoAwarenessEventType.DATASET_VALIDATION_FAILED,
                severity = "ERROR",
                message = "Geo-awareness dataset validation failed",
                datasetTitle = info.title,
                datasetVersion = info.version,
                healthState = geoAwarenessHealth?.state?.name,
                details = details
            )
            result.hasWarnings -> geoEventLogger.logSimple(
                type = GeoAwarenessEventType.DATASET_VALIDATED,
                severity = "WARNING",
                message = "Geo-awareness dataset validation completed with warnings",
                datasetTitle = info.title,
                datasetVersion = info.version,
                healthState = geoAwarenessHealth?.state?.name,
                details = details
            )
            else -> geoEventLogger.logSimple(
                type = GeoAwarenessEventType.DATASET_VALIDATED,
                severity = "INFO",
                message = "Geo-awareness dataset validation passed",
                datasetTitle = info.title,
                datasetVersion = info.version,
                healthState = geoAwarenessHealth?.state?.name,
                details = details
            )
        }
    }

    private fun logMultiDatasetLoadedIfNeeded(
        result: com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult
    ) {
        if (result.datasetRecords.size <= 1) {
            return
        }
        geoEventLogger.logSimple(
            type = GeoAwarenessEventType.MULTI_DATASET_LOADED,
            severity = if (result.validationResult.hasWarnings) "WARNING" else "INFO",
            message = "Multiple geo-zone datasets loaded",
            datasetTitle = result.datasetInfo.title,
            datasetVersion = result.datasetInfo.version,
            healthState = geoAwarenessHealth?.state?.name,
            details = mapOf(
                "datasetCount" to result.datasetRecords.size.toString(),
                "totalZones" to result.datasetInfo.zoneCount.toString(),
                "totalWarnings" to result.validationResult.warningCount.toString(),
                "totalErrors" to result.validationResult.errorCount.toString()
            )
        )
    }

    private fun logHealthEvaluationIfNeeded(health: GeoAwarenessHealth) {
        val signature = "${health.state}|${health.message}"
        val severity = when (health.state) {
            GeoAwarenessHealthState.AVAILABLE -> "INFO"
            GeoAwarenessHealthState.UNAVAILABLE -> "ERROR"
            else -> "WARNING"
        }

        if (lastHealthLogSignature != signature) {
            geoEventLogger.logSimple(
                type = GeoAwarenessEventType.HEALTH_EVALUATED,
                severity = severity,
                message = health.message,
                datasetTitle = geoZoneDatasetInfo?.title,
                datasetVersion = geoZoneDatasetInfo?.version,
                healthState = health.state.name
            )
            lastHealthLogSignature = signature
        }

        if (lastHealthState != null && lastHealthState != health.state) {
            geoEventLogger.logSimple(
                type = GeoAwarenessEventType.HEALTH_CHANGED,
                severity = severity,
                message = "Geo-awareness health changed to ${health.state}",
                datasetTitle = geoZoneDatasetInfo?.title,
                datasetVersion = geoZoneDatasetInfo?.version,
                healthState = health.state.name,
                details = mapOf(
                    "previousState" to lastHealthState!!.name,
                    "newState" to health.state.name
                )
            )
        }
        lastHealthState = health.state
    }

    private fun logPlanningStatusIfNeeded(result: GeoAwarenessResult) {
        val sortedZoneIds = result.conflicts.map { it.zone.id }.distinct().sorted()
        val planningSignature = buildString {
            append(result.conflicts.size)
            append('|')
            append(result.highestRestriction.name)
            append('|')
            append(result.canUpload)
            append('|')
            append(result.requiresAcknowledgement)
            append('|')
            append(sortedZoneIds.joinToString(","))
        }
        if (planningSignature != lastPlanningLogSignature) {
            geoEventLogger.logSimple(
                type = GeoAwarenessEventType.PLANNING_CHECKED,
                severity = if (result.hasConflicts) "WARNING" else "INFO",
                message = if (result.hasConflicts) "Planning geo-awareness check found conflicts" else "Planning geo-awareness check clear",
                datasetTitle = geoZoneDatasetInfo?.title,
                datasetVersion = geoZoneDatasetInfo?.version,
                healthState = geoAwarenessHealth?.state?.name,
                zoneIds = sortedZoneIds,
                zoneNames = result.conflicts.map { it.zone.name }.distinct().sorted(),
                restriction = result.highestRestriction.name,
                details = mapOf(
                    "conflicts" to result.conflicts.size.toString(),
                    "highestRestriction" to result.highestRestriction.name,
                    "canUpload" to result.canUpload.toString(),
                    "requiresAcknowledgement" to result.requiresAcknowledgement.toString()
                )
            )
            lastPlanningLogSignature = planningSignature
        }

        if (result.hasConflicts && planningSignature != lastConflictLogSignature) {
            geoEventLogger.logSimple(
                type = GeoAwarenessEventType.PLANNING_CONFLICT_DETECTED,
                severity = "WARNING",
                message = "Planning geo-awareness conflict detected",
                datasetTitle = geoZoneDatasetInfo?.title,
                datasetVersion = geoZoneDatasetInfo?.version,
                healthState = geoAwarenessHealth?.state?.name,
                zoneIds = sortedZoneIds,
                zoneNames = result.conflicts.map { it.zone.name }.distinct().sorted(),
                restriction = result.highestRestriction.name
            )
            lastConflictLogSignature = planningSignature
        } else if (!result.hasConflicts) {
            lastConflictLogSignature = null
        }
    }

    private fun logLiveStatusIfNeeded(
        zones: List<GeoZone>,
        latitude: Double?,
        longitude: Double?,
        altitudeMeters: Double?
    ) {
        val currentZoneMap = zones.associateBy(::buildLiveZoneIdentity)
        if (currentZoneMap.keys == lastLiveZoneIdentityMap.keys) {
            return
        }

        val entered = currentZoneMap
            .filterKeys { it !in lastLiveZoneIdentityMap }
            .values
            .toList()
        val exited = lastLiveZoneIdentityMap
            .filterKeys { it !in currentZoneMap }
            .values
            .toList()
        if (entered.isNotEmpty()) {
            geoIncidentLogger.logZoneEntered(
                zones = entered,
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altitudeMeters,
                datasetTitle = geoZoneDatasetInfo?.title,
                datasetVersion = geoZoneDatasetInfo?.version,
                healthState = geoAwarenessHealth?.state?.name,
                source = "live_drone",
                details = liveVerificationDetails("alert_active")
            )
        }

        if (exited.isNotEmpty()) {
            geoIncidentLogger.logZoneExited(
                zones = exited,
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altitudeMeters,
                datasetTitle = geoZoneDatasetInfo?.title,
                datasetVersion = geoZoneDatasetInfo?.version,
                healthState = geoAwarenessHealth?.state?.name,
                source = "live_drone",
                details = liveVerificationDetails("alert_cleared")
            )
        }

        lastLiveZoneIdentityMap = currentZoneMap
    }

    private fun logLiveProximityIfNeeded(
        proximity: LiveGeoAwarenessProximityResult,
        latitude: Double?,
        longitude: Double?,
        altitudeMeters: Double?
    ) {
        val identity = buildLiveProximityIdentity(proximity)
        if (identity == lastLiveProximityIdentity) return
        lastLiveProximityIdentity = identity
        geoIncidentLogger.logApproachWarning(
            zone = proximity.nearestZone,
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            datasetTitle = geoZoneDatasetInfo?.title,
            datasetVersion = geoZoneDatasetInfo?.version,
            healthState = geoAwarenessHealth?.state?.name,
            source = "live_drone",
            details = liveVerificationDetails("approach_warning") + proximityEvidenceDetails(proximity)
        )
    }

    private fun liveVerificationDetails(alertStatus: String): Map<String, String> {
        return buildMap {
            put("verificationSchema", "prEN4709-003-7.1-live-behaviour-v1")
            put("utcTimeMillis", System.currentTimeMillis().toString())
            put("alertStatus", alertStatus)
            latestRealDroneAltitudeMeters?.let { put("heightAglOrRelativeMeters", it.toString()) }
            latestRealDroneAltitudeAmslMeters?.let { put("altitudeAmslMeters", it.toString()) }
            latestRealDroneHorizontalAccuracyMeters?.let { put("horizontalPositionAccuracyMeters", it.toString()) }
            latestRealDroneVerticalAccuracyMeters?.let { put("verticalPositionAccuracyMeters", it.toString()) }
            latestRealDroneGroundSpeedMetersPerSecond?.let { put("groundSpeedMetersPerSecond", it.toString()) }
            latestRealDroneVerticalSpeedMetersPerSecond?.let { put("verticalSpeedMetersPerSecond", it.toString()) }
            latestRealDroneHeadingDegrees?.let { put("headingDegrees", it.toString()) }
            put("altitudeReferenceSupport", "AGL_from_relative_altitude_or_mission_height;AMSL_from_GLOBAL_POSITION_INT.alt")
        }
    }

    private fun proximityEvidenceDetails(proximity: LiveGeoAwarenessProximityResult): Map<String, String> {
        return buildMap {
            put("approachWarningSchema", "prEN4709-003-3-second-approach-warning-v1")
            put("nearestZoneId", proximity.nearestZone.id)
            put("nearestZoneName", proximity.nearestZone.name)
            put("nearestZoneRestriction", proximity.restriction.name)
            put("timeApplicabilityActive", "true")
            put("timeApplicabilityRule", "inactive future/expired non-permanent UGZ windows are excluded before warning evaluation")
            put("timeApplicabilityPermanent", proximity.nearestZone.applicability.any { it.permanent }.toString())
            proximity.nearestZone.applicability.mapNotNull { it.startDateTime }.minOrNull()
                ?.let { put("timeApplicabilityStartUtc", it) }
            proximity.nearestZone.applicability.mapNotNull { it.endDateTime }.maxOrNull()
                ?.let { put("timeApplicabilityEndUtc", it) }
            put("distanceToBoundaryMeters", proximity.distanceMeters.toString())
            put("configuredDistanceThresholdMeters", proximity.configuredThresholdMeters.toString())
            put("effectiveWarningThresholdMeters", proximity.effectiveThresholdMeters.toString())
            put("requiredWarningTimeSeconds", proximity.requiredWarningSeconds.toString())
            proximity.minimumWarningDistanceMeters?.let { put("minimumSpeedBasedWarningDistanceMeters", it.toString()) }
            proximity.groundSpeedMetersPerSecond?.let { put("groundSpeedMetersPerSecond", it.toString()) }
            proximity.headingDegrees?.let { put("headingDegrees", it.toString()) }
            proximity.closingSpeedMetersPerSecond?.let { put("closingSpeedMetersPerSecond", it.toString()) }
            proximity.timeToBoundarySeconds?.let { put("timeToBoundarySeconds", it.toString()) }
            proximity.verticalDistanceMeters?.let { put("verticalDistanceToBoundaryMeters", it.toString()) }
            proximity.verticalClosingSpeedMetersPerSecond?.let { put("verticalClosingSpeedMetersPerSecond", it.toString()) }
            proximity.verticalTimeToBoundarySeconds?.let { put("verticalTimeToBoundarySeconds", it.toString()) }
            proximity.verticalBoundaryReference?.let { put("verticalBoundaryReference", it.name) }
            proximity.warningMeetsRequiredTime?.let { put("warningMeetsRequiredTime", it.toString()) }
            put("warningMode", proximity.warningMode)
            put("verticalRelevance", proximity.verticalRelevance.toString())
            put("triggerRule", "horizontal distanceToBoundaryMeters <= configuredDistanceThresholdMeters OR horizontal timeToBoundarySeconds <= requiredWarningTimeSeconds when closingSpeedMetersPerSecond > 0 OR verticalDistanceToBoundaryMeters <= verticalWarningBufferMeters OR verticalTimeToBoundarySeconds <= requiredWarningTimeSeconds when verticalClosingSpeedMetersPerSecond > 0")
        }
    }

    private fun buildLiveZoneIdentity(zone: GeoZone): String {
        return "${zone.id}|${zone.name}|${zone.restriction.name}"
    }

    private fun buildLiveProximityIdentity(proximity: LiveGeoAwarenessProximityResult): String {
        return "${proximity.nearestZone.id}|${proximity.nearestZone.name}|${proximity.restriction.name}"
    }

    private fun showGeoAwarenessDialog(title: String, message: String) {
        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
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
            lastLiveProximityIdentity = null
            liveGeoAwarenessStatusBinder?.bindDegraded(reason)
            return
        }

        if (dronePosition == null) {
            latestLiveGeoZones = emptyList()
            latestLiveGeoProximity = null
            latestLiveGeoThreats = emptyList()
            lastLiveProximityIdentity = null
            liveGeoAwarenessStatusBinder?.bindUnknown("No drone position")
            return
        }

        if (!loadGeoAwarenessZonesIfNeeded()) {
            latestLiveGeoZones = emptyList()
            latestLiveGeoProximity = null
            latestLiveGeoThreats = emptyList()
            lastLiveProximityIdentity = null
            liveGeoAwarenessStatusBinder?.bindUnknown("Geo-zones unavailable")
            return
        }
        if (geoAwarenessZones.isEmpty()) {
            latestLiveGeoZones = emptyList()
            latestLiveGeoProximity = null
            latestLiveGeoThreats = emptyList()
            lastLiveProximityIdentity = null
            liveGeoAwarenessStatusBinder?.bindUnknown("Geo-zones unavailable")
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

        logLiveStatusIfNeeded(
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
            lastLiveProximityIdentity = null
            liveGeoAwarenessStatusBinder?.bindClear()
        } else {
            nearestZone?.let { proximity ->
                logLiveProximityIfNeeded(
                    proximity = proximity,
                    latitude = dronePosition.lat,
                    longitude = dronePosition.lon,
                    altitudeMeters = droneAltitudeMeters
                )
            }
            val insideThreatRows = insideZones.map { zone -> zone.toInsideThreatUiModel(dronePosition) }
            val dedupedNearThreats = nearThreats.filterNot { proximity ->
                insideZones.any { inside -> inside.id == proximity.nearestZone.id }
            }
            val threatRows = (insideThreatRows + dedupedNearThreats.map { it.toThreatUiModel(dronePosition) })
                .take(3)
            val remainingCount = (insideThreatRows.size + dedupedNearThreats.size - threatRows.size).coerceAtLeast(0)
            latestLiveGeoThreats = nearThreats
            val highestInside = insideZones.maxByOrNull { restrictionPriority(it.restriction) }
            val statusRestriction = highestInside?.restriction ?: nearestZone?.restriction
            val statusLabel = when {
                highestInside != null -> "IN ${restrictionShortLabel(highestInside.restriction)}"
                threatRows.size > 1 -> "MULTIPLE"
                statusRestriction != null -> nearRestrictionBadgeLabel(statusRestriction)
                else -> "CLEAR"
            }
            val statusColor = statusRestriction?.let(::restrictionColorHex) ?: "#48D26D"
            liveGeoAwarenessStatusBinder?.bindThreatSummary(
                statusLabel = statusLabel,
                statusColor = statusColor,
                threats = threatRows,
                remainingCount = remainingCount,
                headingDegrees = latestRealDroneHeadingDegrees,
                borderColor = highestInside?.restriction?.let(::restrictionColorHex) ?: "#00000000"
            )
        }

        Log.d(
            LIVE_GEO_AWARENESS_TAG,
            "Live geo-awareness updated: inside=${insideZones.size} highest=${insideZones.firstOrNull()?.restriction}"
        )
    }

    private fun showLiveGeoAwarenessDetails() {
        val title: String
        val message: String

        when {
            latestLiveDronePosition == null -> {
                title = "Live geo-awareness"
                message = "No drone position available yet."
            }
            latestLiveGeoZones.isNotEmpty() -> {
                val visibleZones = latestLiveGeoZones.take(5)
                val remainingCount = latestLiveGeoZones.size - visibleZones.size
                title = "Live geo-awareness warning"
                message = buildString {
                    appendLine("Drone is inside loaded geo-zone(s):")
                    appendLine()
                    visibleZones.forEach { zone ->
                        appendLine("- ${zone.name}")
                        appendLine("  Restriction: ${zone.restriction}")
                        appendLine("  Message: ${zone.message ?: "No message"}")
                    }
                    if (remainingCount > 0) {
                        appendLine("...and $remainingCount more.")
                    }
                    append("Verify restrictions with the responsible authority before flight.")
                }
            }
            latestLiveGeoProximity != null -> {
                val proximity = latestLiveGeoProximity!!
                title = "Nearby geo-zone"
                message = buildString {
                    appendLine("Nearest zone: ${proximity.nearestZone.name}")
                    appendLine("Restriction: ${proximity.restriction}")
                    appendLine("Distance: ${proximity.distanceMeters.toInt().coerceAtLeast(0)} m")
                    appendLine("Configured threshold: ${proximity.configuredThresholdMeters.toInt()} m")
                    appendLine("Effective threshold: ${proximity.effectiveThresholdMeters.toInt()} m")
                    appendLine("Required warning time: ${proximity.requiredWarningSeconds} s")
                    proximity.groundSpeedMetersPerSecond?.let { speed ->
                        appendLine("Ground speed: ${"%.2f".format(Locale.US, speed)} m/s")
                    }
                    proximity.closingSpeedMetersPerSecond?.let { speed ->
                        appendLine("Closing speed: ${"%.2f".format(Locale.US, speed)} m/s")
                    }
                    proximity.timeToBoundarySeconds?.let { seconds ->
                        appendLine("Time to boundary: ${"%.2f".format(Locale.US, seconds)} s")
                    }
                    proximity.verticalDistanceMeters?.let { distance ->
                        appendLine("Vertical distance to limit: ${"%.2f".format(Locale.US, distance)} m")
                    }
                    proximity.verticalClosingSpeedMetersPerSecond?.let { speed ->
                        appendLine("Vertical closing speed: ${"%.2f".format(Locale.US, speed)} m/s")
                    }
                    proximity.verticalTimeToBoundarySeconds?.let { seconds ->
                        appendLine("Vertical time to limit: ${"%.2f".format(Locale.US, seconds)} s")
                    }
                    appendLine("Warning mode: ${proximity.warningMode}")
                    if (!geoZoneDatasetInfo?.title.isNullOrBlank()) {
                        appendLine("Dataset: ${geoZoneDatasetInfo?.title} (${geoZoneDatasetInfo?.version ?: "N/A"})")
                    }
                    if (!proximity.nearestZone.message.isNullOrBlank()) {
                        appendLine("Message: ${proximity.nearestZone.message}")
                    }
                    appendLine()
                    append("The drone is outside this zone but within the near-zone warning threshold.")
                }
            }
            latestLiveGeoZones.isEmpty() -> {
                title = "Live geo-awareness"
                message = buildString {
                    appendLine("Drone is not inside any loaded geo-zone.")
                    append("Verify dataset validity and operational restrictions before flight.")
                }
            }
            else -> error("Unhandled live geo-awareness detail state")
        }

        showGeoAwarenessDialog(title, message)
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
        updateLiveGeoAwarenessStatus(latestRealDronePosition, latestRealDroneAltitudeMeters)
    }

    private fun liveGeoAwarenessDegradedReason(): String? {
        if (droneViewModel.conStateLiveData.value != true) {
            return "Geo-awareness degraded: no drone link"
        }
        return when (TelemetryMapping.gpsFixQuality(droneViewModel.gpsFixType.value, isConnected = true)) {
            GpsFixQuality.DISCONNECTED,
            GpsFixQuality.NO_GPS,
            GpsFixQuality.UNKNOWN -> "Geo-awareness degraded: GPS position is not reliable"
            GpsFixQuality.FIX_2D,
            GpsFixQuality.FIX_3D,
            GpsFixQuality.DGPS,
            GpsFixQuality.RTK_FLOAT,
            GpsFixQuality.RTK_FIXED -> null
        }
    }

    private fun horizontalDirectionLabel(
        fromPosition: LatLon,
        zone: GeoZone
    ): String? {
        val target = representativePoint(zone) ?: return null
        val bearing = bearingDegrees(fromPosition, target)
        return compassDirectionLabel(bearing)
    }

    private fun representativePoint(zone: GeoZone): LatLon? {
        val geometry = zone.geometries.firstOrNull() ?: return null
        return when (geometry) {
            is com.example.droneservicesapp.domain.geoawareness.GeoZoneGeometry.Circle -> geometry.center
            is com.example.droneservicesapp.domain.geoawareness.GeoZoneGeometry.Polygon -> {
                val outerRing = geometry.rings.firstOrNull().orEmpty()
                if (outerRing.isEmpty()) return null
                val lat = outerRing.map { it.lat }.average()
                val lon = outerRing.map { it.lon }.average()
                LatLon(lat = lat, lon = lon)
            }
        }
    }

    private fun bearingDegrees(from: LatLon, to: LatLon): Double {
        val startLat = Math.toRadians(from.lat)
        val endLat = Math.toRadians(to.lat)
        val deltaLon = Math.toRadians(to.lon - from.lon)
        val y = sin(deltaLon) * cos(endLat)
        val x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(deltaLon)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    private fun compassDirectionLabel(bearingDegrees: Double): String {
        val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = (((bearingDegrees + 22.5) % 360.0) / 45.0).toInt()
        return directions[index]
    }

    private fun verticalRelationLabel(proximity: LiveGeoAwarenessProximityResult): String {
        val zone = proximity.nearestZone
        val geometry = zone.geometries.firstOrNull() ?: return "ABOVE"
        val aglAltitude = latestRealDroneAltitudeMeters
        val amslAltitude = latestRealDroneAltitudeAmslMeters
        val lowerAltitude = when (geometry.lowerVerticalReference) {
            com.example.droneservicesapp.domain.geoawareness.GeoVerticalReference.AGL -> aglAltitude
            com.example.droneservicesapp.domain.geoawareness.GeoVerticalReference.AMSL -> amslAltitude
            com.example.droneservicesapp.domain.geoawareness.GeoVerticalReference.UNKNOWN -> aglAltitude ?: amslAltitude
        }
        val lowerLimit = geometry.lowerLimitMeters
        val upperAltitude = when (geometry.upperVerticalReference) {
            com.example.droneservicesapp.domain.geoawareness.GeoVerticalReference.AGL -> aglAltitude
            com.example.droneservicesapp.domain.geoawareness.GeoVerticalReference.AMSL -> amslAltitude
            com.example.droneservicesapp.domain.geoawareness.GeoVerticalReference.UNKNOWN -> aglAltitude ?: amslAltitude
        }
        val upperLimit = geometry.upperLimitMeters
        return when {
            lowerLimit != null && lowerAltitude != null && lowerAltitude < lowerLimit -> "ABOVE"
            upperLimit != null && upperAltitude != null && upperAltitude > upperLimit -> "BELOW"
            else -> "ABOVE"
        }
    }

    private fun LiveGeoAwarenessProximityResult.toThreatUiModel(dronePosition: LatLon): LiveGeoThreatUiModel {
        val isVertical = warningMode.startsWith("VERTICAL")
        val direction = horizontalDirectionLabel(dronePosition, nearestZone) ?: "--"
        val distance = "H: ${formatLiveGeoDistance(distanceMeters)}"
        val altitude = if (isVertical) "V: ${formatLiveGeoDistance(verticalDistanceMeters)}" else "V: --"
        val zoneIsAboveDrone = verticalRelationLabel(this) == "ABOVE"
        val normalizedRatio = (distanceMeters / effectiveThresholdMeters)
            .takeIf { it.isFinite() }
            ?.coerceIn(0.0, 1.0)
            ?.toFloat()
            ?: 1f
        val radialRatio = 0.25f + (normalizedRatio * 0.75f)
        return LiveGeoThreatUiModel(
            label = restrictionShortLabel(restriction),
            colorHex = restrictionColorHex(restriction),
            directionText = direction,
            distanceText = distance,
            altitudeText = altitude,
            verticalArrowText = when {
                !isVertical -> ""
                zoneIsAboveDrone -> "\u2191"
                else -> "\u2193"
            },
            radialDistanceRatio = radialRatio,
            bearingDegrees = representativePoint(nearestZone)?.let { bearingDegrees(dronePosition, it) },
            verticalIndicator = when {
                !isVertical -> com.example.droneservicesapp.ui.home.geoawareness.VerticalIndicator.NONE
                zoneIsAboveDrone -> com.example.droneservicesapp.ui.home.geoawareness.VerticalIndicator.UP
                else -> com.example.droneservicesapp.ui.home.geoawareness.VerticalIndicator.DOWN
            },
            showCompassMarker = true
        )
    }

    private fun GeoZone.toInsideThreatUiModel(dronePosition: LatLon): LiveGeoThreatUiModel {
        return LiveGeoThreatUiModel(
            label = restrictionShortLabel(restriction),
            colorHex = restrictionColorHex(restriction),
            directionText = horizontalDirectionLabel(dronePosition, this) ?: "IN",
            distanceText = "H: IN",
            altitudeText = "V: IN",
            radialDistanceRatio = 0.18f,
            bearingDegrees = null,
            showCompassMarker = false,
            isInsideZone = true
        )
    }

    private fun restrictionShortLabel(restriction: GeoZoneRestriction): String {
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED -> "PROHIBITED"
            GeoZoneRestriction.REQ_AUTHORISATION -> "AUTH REQUIRED"
            GeoZoneRestriction.CONDITIONAL -> "CONDITIONAL"
            GeoZoneRestriction.INFORMATION -> "INFO"
            GeoZoneRestriction.UNKNOWN -> "UNKNOWN"
        }
    }

    private fun restrictionBadgeLabel(restriction: GeoZoneRestriction): String {
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED -> "PROHIBITED"
            GeoZoneRestriction.REQ_AUTHORISATION -> "AUTH REQUIRED"
            GeoZoneRestriction.CONDITIONAL -> "CONDITIONAL"
            GeoZoneRestriction.INFORMATION -> "INFO"
            GeoZoneRestriction.UNKNOWN -> "UNKNOWN"
        }
    }

    private fun nearRestrictionBadgeLabel(restriction: GeoZoneRestriction): String {
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED -> "NEAR PROHIBITED"
            GeoZoneRestriction.REQ_AUTHORISATION -> "NEAR AUTH REQUIRED"
            GeoZoneRestriction.CONDITIONAL -> "NEAR CONDITIONAL"
            GeoZoneRestriction.INFORMATION -> "INFO"
            GeoZoneRestriction.UNKNOWN -> "NEAR UNKNOWN"
        }
    }

    private fun restrictionColorHex(restriction: GeoZoneRestriction): String {
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED -> "#FF4F45"
            GeoZoneRestriction.REQ_AUTHORISATION -> "#FF972E"
            GeoZoneRestriction.CONDITIONAL -> "#F4C73D"
            GeoZoneRestriction.INFORMATION -> "#4C9DFF"
            GeoZoneRestriction.UNKNOWN -> "#8D6E63"
        }
    }

    private fun restrictionPriority(restriction: GeoZoneRestriction): Int {
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED -> 4
            GeoZoneRestriction.REQ_AUTHORISATION -> 3
            GeoZoneRestriction.CONDITIONAL -> 2
            GeoZoneRestriction.INFORMATION -> 1
            GeoZoneRestriction.UNKNOWN -> 0
        }
    }

    private fun formatLiveGeoDistance(distanceMeters: Double?): String {
        val value = distanceMeters?.takeIf { it.isFinite() } ?: return "--"
        return if (value >= 1000.0) {
            String.format(Locale.US, "%.1f km", value / 1000.0)
        } else {
            "${value.toInt().coerceAtLeast(0)} m"
        }
    }
}

