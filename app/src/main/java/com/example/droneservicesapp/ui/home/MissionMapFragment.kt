package com.example.droneservicesapp.ui.home

import android.os.Bundle
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
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.example.droneservicesapp.BuildConfig
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.geoawareness.GeoZoneAssetDataSource
import com.example.droneservicesapp.data.geoawareness.GeoZoneRepository
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.data.storage.MissionFileStore
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessChecker
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
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import com.example.droneservicesapp.databinding.FragmentHomeMapsBinding
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.survey.SurveyPlanner
import com.example.droneservicesapp.mavserver.DroneViewModel
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
import com.example.droneservicesapp.ui.home.geoawareness.GeoAwarenessStatusViewBinder
import com.example.droneservicesapp.ui.home.geoawareness.LiveGeoAwarenessStatusViewBinder
import com.example.droneservicesapp.ui.home.geoawareness.GeoZoneOverlayController
import com.example.droneservicesapp.ui.home.model.HomeTelemetryViewModel
import com.example.droneservicesapp.ui.home.model.HomeMapUiState
import com.example.droneservicesapp.ui.home.model.MissionMapViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import com.google.maps.android.SphericalUtil
import io.dronefleet.mavlink.common.MavCmd
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

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
    private lateinit var homeMapChromeBinder: HomeMapChromeBinder
    private lateinit var homeMapPanelsBinder: HomeMapPanelsBinder
    private lateinit var homeMapModeEffectsBinder: HomeMapModeEffectsBinder
    private lateinit var homeMapTelemetryBinder: HomeMapTelemetryBinder
    private lateinit var osmdroidMapController: OsmdroidMapController
    private lateinit var osmdroidPolygonEditor: OsmdroidPolygonEditor
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
    private var liveGeoAwarenessStatusBinder: LiveGeoAwarenessStatusViewBinder? = null
    private var latestLiveDronePosition: LatLon? = null
    private var latestRealDronePosition: LatLon? = null
    private var latestRealDroneAltitudeMeters: Double? = null
    private var virtualGeoTestMarker: Marker? = null
    private var virtualGeoTestEventsOverlay: MapEventsOverlay? = null
    private var lastPlanningLogSignature: String? = null
    private var lastConflictLogSignature: String? = null
    private var lastHealthLogSignature: String? = null
    private var lastHealthState: GeoAwarenessHealthState? = null
    private var lastLiveZoneSignature: String? = null
    private var isDrawingModeActive = false
    private var hasCenteredInitialViewport = false
    private var hasCenteredToDrone = false
    private var initialCenterAttemptCount = 0

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
        private const val GEO_TEST_MODE_TAG = "GeoTestMode"
        private const val MIN_VALID_ABS_COORDINATE = 1e-4
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

        mapViewModel.updateFromMapState(activityViewModel.mapState.value ?: MainActivityViewModel.MapState.Idle)
    }

    private fun initializeMapView(view: View) {
        mapView = view.findViewById(R.id.osmMap)
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

        geoZoneOverlayController = GeoZoneOverlayController(requireContext(), mapView)
        geoAwarenessChecker = GeoAwarenessChecker()
        geoAwarenessStatusBinder = GeoAwarenessStatusViewBinder(
            requireContext(),
            binding.geoAwarenessStatusChip
        )
        liveGeoAwarenessChecker = LiveGeoAwarenessChecker()
        liveGeoAwarenessStatusBinder = LiveGeoAwarenessStatusViewBinder(
            requireContext(),
            binding.liveGeoAwarenessStatusChip
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
        initializeVirtualGeoTestMode()
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
                    activityViewModel.missionArea.value?.clearAll()
                    activityViewModel.mapState.postValue(MainActivityViewModel.MapState.LoadMissionFromFile)
                }
            }

        requireView().findViewById<com.google.android.material.button.MaterialButton>(R.id.right_panel_close_button)
            .setOnClickListener {
                mapViewModel.dismissSidePanels()
            }

        val surveyButton = requireView().findViewById<TextView>(R.id.right_panel_survey_button)
        val sprayButton = requireView().findViewById<TextView>(R.id.right_panel_spray_button)
        bindPlanningModeToggle(surveyButton, sprayButton)

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

        requireView().findViewById<TextView>(R.id.right_panel_area_button).setOnClickListener {
            startAreaDrawing()
        }

        requireView().findViewById<TextView>(R.id.right_panel_draw_area_button).setOnClickListener {
            startAreaDrawing()
        }

        requireView().findViewById<TextView>(R.id.right_panel_points_button).setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.work_in_progress), Toast.LENGTH_SHORT).show()
        }

        requireView().findViewById<TextView>(R.id.right_panel_clear_area_button).setOnClickListener {
            activityViewModel.sendAction(MainActivityViewModel.MapAction.ClearAreaOnly)
        }
        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
    }

    private fun startAreaDrawing() {
        mapViewModel.setPlanningPanelVisible(false)
        activityViewModel.missionArea.value?.clearAll()
        activityViewModel.mapState.value = MainActivityViewModel.MapState.Draw
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

    private fun bindPlanningModeToggle(surveyButton: TextView, sprayButton: TextView) {
        fun applySelection(isSurveySelected: Boolean) {
            surveyButton.setBackgroundResource(
                if (isSurveySelected) R.drawable.bg_ds_panel_pill_active
                else R.drawable.bg_ds_panel_pill_inactive
            )
            surveyButton.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isSurveySelected) R.color.ds_color_shell_selected_content else R.color.ds_color_text_primary
                )
            )
            surveyButton.setTypeface(surveyButton.typeface, if (isSurveySelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

            sprayButton.setBackgroundResource(
                if (isSurveySelected) R.drawable.bg_ds_panel_pill_inactive
                else R.drawable.bg_ds_panel_pill_active
            )
            sprayButton.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isSurveySelected) R.color.ds_color_text_primary else R.color.ds_color_shell_selected_content
                )
            )
            sprayButton.setTypeface(sprayButton.typeface, if (isSurveySelected) android.graphics.Typeface.NORMAL else android.graphics.Typeface.BOLD)
        }

        surveyButton.setOnClickListener { applySelection(true) }
        sprayButton.setOnClickListener { applySelection(false) }
        applySelection(true)
    }

    private fun observeDroneViewModel() {
        droneViewModel.droneLocationLiveData.observe(viewLifecycleOwner) { droneLocation ->
            val livePosition = droneLocation?.takeIf(::isUsableDroneLocation)?.let {
                LatLon(lat = it.latitude, lon = it.longitude)
            }
            val liveAltitudeMeters = droneLocation?.takeIf(::isUsableDroneLocation)?.altitude
            latestRealDronePosition = livePosition
            latestRealDroneAltitudeMeters = liveAltitudeMeters

            if (livePosition != null) {
                osmdroidMapController.updateDronePosition(
                    livePosition.lat,
                    livePosition.lon
                )
                centerOnDroneIfNeeded()
            } else {
                osmdroidMapController.setDroneVisible(false)
                centerInitialViewportIfNeeded()
            }

            updateLiveGeoAwarenessFromActiveSource()
        }

        droneViewModel.droneHeading.observe(viewLifecycleOwner) { droneHeading ->
            droneHeading?.let { heading ->
                osmdroidMapController.updateDroneHeadingDegrees(heading.toFloat())
            }
        }

        activityViewModel.missionArea.observe(viewLifecycleOwner) { missionArea ->
            val vertices = missionArea?.vertices ?: emptyList()
            mapViewModel.setMissionAreaAvailable(vertices.size >= 3)
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
    }

    private fun observeMapState() {
        activityViewModel.angleProgress.observe(viewLifecycleOwner, Observer { angle ->
            if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams) {
                savePreference(getString(R.string.survey_angle_pref), angle.toInt().toString())
                drawSurveyMissionOnMap(
                    activityViewModel.lineDistanceProgress.value!!,
                    angle.toInt()
                )
            }
        })

        activityViewModel.lineDistanceProgress.observe(viewLifecycleOwner, Observer { distance ->
            if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams) {
                savePreference(
                    getString(R.string.survey_line_distance_pref),
                    distance.toInt().toString()
                )
                drawSurveyMissionOnMap(
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

        activityViewModel.surveyPath.observe(viewLifecycleOwner) { surveyPath ->
            val hasPolygon = (activityViewModel.missionArea.value?.vertices?.size ?: 0) >= 3
            mapViewModel.setMissionAreaAvailable(hasPolygon || !surveyPath.isNullOrEmpty())
            updateGeoAwarenessPlanningStatus()
        }

        activityViewModel.mapState.observe(viewLifecycleOwner) { mapState ->
            mapViewModel.updateFromMapState(mapState)
            if (mapState == MainActivityViewModel.MapState.SetFlightParams) {
                val hasPolygon = (activityViewModel.missionArea.value?.vertices?.size ?: 0) >= 3
                if (hasPolygon) {
                    drawSurveyMissionOnMap(
                        activityViewModel.lineDistanceProgress.value ?: 0.0,
                        activityViewModel.angleProgress.value?.toInt() ?: 0
                    )
                }
            }
            updatePlanningGeoAwarenessVisibility()
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
                    Snackbar.make(requireView(), getString(R.string.upload_complete), Snackbar.LENGTH_LONG).show()
                    activityViewModel.sendAction(MainActivityViewModel.MapAction.ResetToIdle)
                }
                is MainActivityViewModel.MapAction.UploadMissionFailed -> {
                    Toast.makeText(context, action.reason, Toast.LENGTH_LONG).show()
                    Snackbar.make(
                        requireView(),
                        getString(R.string.upload_failed_with_reason, action.reason),
                        Snackbar.LENGTH_LONG
                    ).show()
                    activityViewModel.sendAction(MainActivityViewModel.MapAction.ResetToIdle)
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
        val wasDrawingModeActive = isDrawingModeActive
        isDrawingModeActive = state.interactionState.isDrawingEnabled
        osmdroidPolygonEditor.setEnabled(state.interactionState.isDrawingEnabled)
        homeMapChromeBinder.renderShell(state.shellState)
        homeMapChromeBinder.renderInteraction(state.interactionState)
        homeMapChromeBinder.renderOverlayControls(state.overlayControlsState)
        homeMapPanelsBinder.renderShell(state.shellState)
        homeMapPanelsBinder.renderOverlays(state.panelState)
        homeMapModeEffectsBinder.render(state.screenMode)

        if (!wasDrawingModeActive && isDrawingModeActive && activityViewModel.geoTestModeEnabled.value == true) {
            setVirtualGeoTestModeEnabled(false)
            Toast.makeText(
                requireContext(),
                "Geo Test disabled while drawing",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            updateVirtualGeoTestTapOverlay()
        }
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

    private fun drawSurveyMissionOnMap(distance: Double, angle: Int) {
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

    override fun onDestroyView() {
        geoZoneOverlayController?.clear()
        geoZoneOverlayController = null
        geoAwarenessStatusBinder = null
        geoAwarenessChecker = null
        liveGeoAwarenessStatusBinder = null
        liveGeoAwarenessChecker = null
        removeVirtualGeoTestMarker()
        removeVirtualGeoTestTapOverlay()
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

    private fun loadGeoAwarenessZonesIfNeeded(): Boolean {
        if (geoAwarenessZones.isNotEmpty()) {
            if (geoAwarenessHealth == null) {
                val health = GeoAwarenessHealthEvaluator.evaluate(
                    datasetInfo = geoZoneDatasetInfo,
                    zones = geoAwarenessZones,
                    validationResult = geoZoneValidationResult,
                    loadError = geoAwarenessLoadError
                )
                geoAwarenessHealth = health
                activityViewModel.geoAwarenessHealth.value = health
            }
            return true
        }

        try {
            val repository = GeoZoneRepository(
                GeoZoneAssetDataSource(requireContext().applicationContext)
            )
            val loadResult = repository.loadDummyRethymnoDataset()
            geoAwarenessZones = loadResult.zones
            geoZoneDatasetInfo = loadResult.datasetInfo
            geoZoneValidationResult = loadResult.validationResult
            geoAwarenessLoadError = null
            geoAwarenessHealth = GeoAwarenessHealthEvaluator.evaluate(
                datasetInfo = loadResult.datasetInfo,
                zones = loadResult.zones,
                validationResult = loadResult.validationResult
            )
            activityViewModel.geoZoneDatasetInfo.value = loadResult.datasetInfo
            activityViewModel.geoZoneValidationResult.value = loadResult.validationResult
            activityViewModel.geoAwarenessHealth.value = geoAwarenessHealth
            logDatasetLoaded(loadResult.datasetInfo)
            logDatasetValidation(loadResult.validationResult, loadResult.datasetInfo)
            geoAwarenessHealth?.let { logHealthEvaluationIfNeeded(it) }
            return true
        } catch (error: Exception) {
            Log.e(GEO_ZONE_TOGGLE_TAG, "Failed to load geo-awareness dummy zones", error)
            geoAwarenessZones = emptyList()
            geoZoneDatasetInfo = null
            geoAwarenessLoadError = error
            geoZoneValidationResult = null
            geoAwarenessHealth = GeoAwarenessHealthEvaluator.evaluate(
                datasetInfo = null,
                zones = emptyList(),
                loadError = error
            )
            activityViewModel.geoZoneDatasetInfo.value = null
            activityViewModel.geoZoneValidationResult.value = null
            activityViewModel.geoAwarenessHealth.value = geoAwarenessHealth
            logDatasetLoadFailed(error)
            geoAwarenessHealth?.let { logHealthEvaluationIfNeeded(it) }
        }

        return false
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
        val altitudeMeters = activityViewModel.flightAltProgress.value?.toDouble()
        // TODO: Confirm altitude source unit is meters.

        if (missionPolygon.isNullOrEmpty() && surveyPath.isEmpty()) {
            return GeoAwarenessResult.clear()
        }

        if (!loadGeoAwarenessZonesIfNeeded()) {
            return GeoAwarenessResult.clear()
        }

        return geoAwarenessChecker?.checkMission(
            missionPolygon = missionPolygon,
            surveyPath = surveyPath,
            missionAltitudeMeters = altitudeMeters,
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
                appendLine("No dummy geo-zone conflicts detected for the current mission plan.")
                append("Development-only dummy data. Verify official restrictions in DAGR before flight.")
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
                append("Development-only dummy data. Verify official restrictions in DAGR before flight.")
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
            validationResult = geoZoneValidationResult,
            loadError = geoAwarenessLoadError
        )
        geoAwarenessHealth = health
        activityViewModel.geoAwarenessHealth.value = health
        logHealthEvaluationIfNeeded(health)
        return health
    }

    private fun shouldWarnForGeoHealthBeforeUpload(
        result: GeoAwarenessResult,
        health: GeoAwarenessHealth
    ): Boolean {
        return !result.hasConflicts && health.requiresAcknowledgementBeforeUpload
    }

    private fun handleGeoAwarenessBeforeUpload(onAllowed: () -> Unit) {
        val result = try {
            calculateGeoAwarenessPlanningResult().also { latestGeoAwarenessResult = it }
        } catch (error: Exception) {
            Log.w(GEO_UPLOAD_GUARD_TAG, "Geo-awareness result unavailable; proceeding in dummy mode", error)
            onAllowed()
            return
        }
        val health = ensureGeoAwarenessHealth()

        if (shouldWarnForGeoHealthBeforeUpload(result, health)) {
            Log.w(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: health warning state=${health.state}")
            showGeoAwarenessHealthAcknowledgementDialog(health) {
                Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: user proceeded after health acknowledgement")
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.UPLOAD_CONTINUED_WITH_WARNING,
                    severity = "WARNING",
                    message = "Upload continued with geo-awareness health warning",
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = health.state.name
                )
                onAllowed()
            }
            return
        }

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
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = health.state.name,
                    zoneIds = result.conflicts.map { it.zone.id }.distinct(),
                    zoneNames = result.conflicts.map { it.zone.name }.distinct(),
                    restriction = result.highestRestriction.name
                )
                showGeoAwarenessBlockedDialog(result, health)
            }
            result.requiresAcknowledgement -> {
                Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: acknowledgement required conflicts=${result.conflicts.size}")
                geoEventLogger.logSimple(
                    type = GeoAwarenessEventType.UPLOAD_ACK_REQUIRED,
                    severity = "WARNING",
                    message = "Geo upload requires acknowledgement",
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = health.state.name,
                    zoneIds = result.conflicts.map { it.zone.id }.distinct(),
                    zoneNames = result.conflicts.map { it.zone.name }.distinct(),
                    restriction = result.highestRestriction.name
                )
                showGeoAwarenessAcknowledgementDialog(result, health) {
                    Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: user proceeded after acknowledgement")
                    geoEventLogger.logSimple(
                        type = GeoAwarenessEventType.UPLOAD_ACKNOWLEDGED,
                        severity = "INFO",
                        message = "User acknowledged geo upload warning",
                        datasetTitle = geoZoneDatasetInfo?.title,
                        datasetVersion = geoZoneDatasetInfo?.version,
                        healthState = health.state.name
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
                    datasetTitle = geoZoneDatasetInfo?.title,
                    datasetVersion = geoZoneDatasetInfo?.version,
                    healthState = health.state.name,
                    zoneIds = result.conflicts.map { it.zone.id }.distinct(),
                    zoneNames = result.conflicts.map { it.zone.name }.distinct(),
                    restriction = result.highestRestriction.name
                )
                showGeoAwarenessNoticeDialog(result, health) {
                    Log.d(GEO_UPLOAD_GUARD_TAG, "Geo upload guard: user proceeded after notice")
                    onAllowed()
                }
            }
        }
    }

    private fun showGeoAwarenessBlockedDialog(result: GeoAwarenessResult, health: GeoAwarenessHealth) {
        val message = buildString {
            appendLine("This mission intersects a prohibited dummy geo-zone.")
            appendLine("Upload is blocked in this development build to validate the geo-awareness guard.")
            appendLine(health.message)
            appendLine(buildGeoHealthNotice(health))
            appendLine("Verify official restrictions in DAGR before flight.")
            appendLine()
            append(buildGeoConflictSummary(result, health))
        }
        showGeoAwarenessDialog(
            title = "Geo-awareness upload blocked",
            message = message
        )
    }

    private fun showGeoAwarenessAcknowledgementDialog(
        result: GeoAwarenessResult,
        health: GeoAwarenessHealth,
        onAcknowledged: () -> Unit
    ) {
        val message = buildString {
            appendLine("This mission intersects a dummy authorization-required geo-zone.")
            appendLine("Proceed only if you have verified the required authorization.")
            appendLine(health.message)
            appendLine(buildGeoHealthNotice(health))
            appendLine()
            append(buildGeoConflictSummary(result, health))
        }
        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle("Geo-awareness authorization warning")
            .setMessage(message)
            .setPositiveButton("Proceed") { _, _ ->
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

    private fun showGeoAwarenessNoticeDialog(
        result: GeoAwarenessResult,
        health: GeoAwarenessHealth,
        onContinue: () -> Unit
    ) {
        val message = buildString {
            appendLine("This mission intersects dummy conditional/information geo-zones.")
            appendLine()
            appendLine(buildGeoConflictSummary(result, health))
            appendLine()
            append(buildGeoHealthNotice(health))
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
        health: GeoAwarenessHealth,
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
            appendLine("Geo-awareness health: ${health.state}")
            appendLine("Health message: ${health.message}")
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
            GeoAwarenessHealthState.DUMMY_DATA -> "This is development-only dummy data. It is not official DAGR/HCAA data."
            GeoAwarenessHealthState.STALE -> "Geo-awareness data may be stale."
            GeoAwarenessHealthState.DEGRADED -> "Geo-awareness data is degraded or unofficial."
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
                "official" to info.isOfficial.toString(),
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
        val sortedIds = zones.map { it.id }.sorted()
        val newSignature = sortedIds.joinToString(",")
        if (newSignature == lastLiveZoneSignature) {
            return
        }

        val previousIds = lastLiveZoneSignature
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
        val currentIds = sortedIds.toSet()
        val entered = zones.filter { it.id !in previousIds }
        val exitedIds = previousIds - currentIds

        if (entered.isNotEmpty()) {
            geoEventLogger.logSimple(
                type = GeoAwarenessEventType.LIVE_ZONE_ENTERED,
                severity = when (entered.first().restriction) {
                    GeoZoneRestriction.INFORMATION -> "INFO"
                    else -> "WARNING"
                },
                message = "Live position entered geo-zone(s)",
                datasetTitle = geoZoneDatasetInfo?.title,
                datasetVersion = geoZoneDatasetInfo?.version,
                healthState = geoAwarenessHealth?.state?.name,
                zoneIds = entered.map { it.id },
                zoneNames = entered.map { it.name },
                restriction = entered.first().restriction.name,
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altitudeMeters
            )
        }

        if (exitedIds.isNotEmpty()) {
            geoEventLogger.logSimple(
                type = GeoAwarenessEventType.LIVE_ZONE_EXITED,
                severity = "INFO",
                message = "Live position exited geo-zone(s)",
                datasetTitle = geoZoneDatasetInfo?.title,
                datasetVersion = geoZoneDatasetInfo?.version,
                healthState = geoAwarenessHealth?.state?.name,
                zoneIds = exitedIds.sorted(),
                zoneNames = exitedIds.sorted(),
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altitudeMeters
            )
        }

        geoEventLogger.logSimple(
            type = GeoAwarenessEventType.LIVE_STATUS_CHANGED,
            severity = if (zones.isEmpty()) "INFO" else "WARNING",
            message = if (zones.isEmpty()) "Live geo-awareness clear" else "Live geo-awareness status changed",
            datasetTitle = geoZoneDatasetInfo?.title,
            datasetVersion = geoZoneDatasetInfo?.version,
            healthState = geoAwarenessHealth?.state?.name,
            zoneIds = sortedIds,
            zoneNames = zones.map { it.name },
            restriction = zones.firstOrNull()?.restriction?.name,
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters
        )
        lastLiveZoneSignature = newSignature
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

        if (dronePosition == null) {
            latestLiveGeoZones = emptyList()
            liveGeoAwarenessStatusBinder?.bindUnknown("No drone position")
            return
        }

        if (!loadGeoAwarenessZonesIfNeeded()) {
            latestLiveGeoZones = emptyList()
            liveGeoAwarenessStatusBinder?.bindUnknown("Geo-zones unavailable")
            return
        }

        val insideZones = liveGeoAwarenessChecker?.checkDronePosition(
            dronePosition = dronePosition,
            droneAltitudeMeters = droneAltitudeMeters,
            zones = geoAwarenessZones
        ).orEmpty()

        logLiveStatusIfNeeded(
            zones = insideZones,
            latitude = dronePosition.lat,
            longitude = dronePosition.lon,
            altitudeMeters = droneAltitudeMeters
        )
        latestLiveGeoZones = insideZones
        if (insideZones.isEmpty()) {
            liveGeoAwarenessStatusBinder?.bindClear()
        } else {
            liveGeoAwarenessStatusBinder?.bindInsideMultiple(insideZones)
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
            latestLiveGeoZones.isEmpty() -> {
                title = "Live geo-awareness"
                message = buildString {
                    appendLine("Drone is not inside any loaded dummy geo-zone.")
                    append("Development-only dummy data. Verify official restrictions in DAGR before flight.")
                }
            }
            else -> {
                val visibleZones = latestLiveGeoZones.take(5)
                val remainingCount = latestLiveGeoZones.size - visibleZones.size
                title = "Live geo-awareness warning"
                message = buildString {
                    appendLine("Drone is inside loaded dummy geo-zone(s):")
                    appendLine()
                    visibleZones.forEach { zone ->
                        appendLine("- ${zone.name}")
                        appendLine("  Restriction: ${zone.restriction}")
                        appendLine("  Message: ${zone.message ?: "No message"}")
                    }
                    if (remainingCount > 0) {
                        appendLine("...and $remainingCount more.")
                    }
                    append("Development-only dummy data. Verify official restrictions in DAGR before flight.")
                }
            }
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

    private fun initializeVirtualGeoTestMode() {
        if (!BuildConfig.DEBUG) {
            removeVirtualGeoTestMarker()
            removeVirtualGeoTestTapOverlay()
            return
        }

        activityViewModel.geoTestModeEnabled.observe(viewLifecycleOwner) { enabled ->
            if (enabled == true && isDrawingModeActive) {
                activityViewModel.geoTestModeEnabled.value = false
                Toast.makeText(
                    requireContext(),
                    "Finish drawing before using Geo Test",
                    Toast.LENGTH_SHORT
                ).show()
                return@observe
            }

            if (enabled != true && activityViewModel.virtualGeoTestPosition.value != null) {
                activityViewModel.virtualGeoTestPosition.value = null
                return@observe
            }

            updateVirtualGeoTestTapOverlay()
            updateLiveGeoAwarenessFromActiveSource()
        }

        activityViewModel.virtualGeoTestPosition.observe(viewLifecycleOwner) { position: LatLon? ->
            if (position == null) {
                removeVirtualGeoTestMarker()
            } else {
                ensureVirtualGeoTestMarker()
                updateVirtualGeoTestMarker(position)
            }
            updateVirtualGeoTestTapOverlay()
            updateLiveGeoAwarenessFromActiveSource()
        }
    }

    private fun setVirtualGeoTestModeEnabled(enabled: Boolean) {
        val currentEnabled = activityViewModel.geoTestModeEnabled.value == true
        val currentPosition = activityViewModel.virtualGeoTestPosition.value
        if (currentEnabled == enabled && !(enabled && currentPosition == null)) {
            return
        }

        if (!enabled) {
            activityViewModel.virtualGeoTestPosition.value = null
        }
        activityViewModel.geoTestModeEnabled.value = enabled
    }

    private fun clearVirtualGeoTestPosition() {
        activityViewModel.virtualGeoTestPosition.value = null
        geoEventLogger.logSimple(
            type = GeoAwarenessEventType.GEO_TEST_POSITION_CLEARED,
            severity = "INFO",
            message = "Virtual geo test position cleared",
            datasetTitle = geoZoneDatasetInfo?.title,
            datasetVersion = geoZoneDatasetInfo?.version,
            healthState = geoAwarenessHealth?.state?.name
        )
        Log.d(GEO_TEST_MODE_TAG, "Virtual geo test position cleared")
    }

    private fun updateVirtualGeoTestTapOverlay() {
        if (!BuildConfig.DEBUG || _binding == null) {
            removeVirtualGeoTestTapOverlay()
            return
        }

        val shouldInterceptTaps = activityViewModel.geoTestModeEnabled.value == true && !isDrawingModeActive
        if (!shouldInterceptTaps) {
            removeVirtualGeoTestTapOverlay()
            return
        }

        if (virtualGeoTestEventsOverlay != null) {
            return
        }

        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (activityViewModel.geoTestModeEnabled.value != true || isDrawingModeActive) {
                    return false
                }

                setVirtualGeoTestPosition(LatLon(lat = p.latitude, lon = p.longitude))
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        }

        virtualGeoTestEventsOverlay = MapEventsOverlay(receiver)
        mapView.overlays.add(virtualGeoTestEventsOverlay)
        mapView.invalidate()
    }

    private fun removeVirtualGeoTestTapOverlay() {
        val overlay = virtualGeoTestEventsOverlay ?: return
        mapView.overlays.remove(overlay)
        virtualGeoTestEventsOverlay = null
        mapView.invalidate()
    }

    private fun setVirtualGeoTestPosition(position: LatLon) {
        activityViewModel.virtualGeoTestPosition.value = position
        geoEventLogger.logSimple(
            type = GeoAwarenessEventType.GEO_TEST_POSITION_SET,
            severity = "INFO",
            message = "Virtual geo test position set",
            datasetTitle = geoZoneDatasetInfo?.title,
            datasetVersion = geoZoneDatasetInfo?.version,
            healthState = geoAwarenessHealth?.state?.name,
            latitude = position.lat,
            longitude = position.lon
        )
        Log.d(
            GEO_TEST_MODE_TAG,
            "Virtual geo test position set: lat=${position.lat} lon=${position.lon}"
        )
    }

    private fun ensureVirtualGeoTestMarker() {
        if (virtualGeoTestMarker != null) {
            return
        }

        virtualGeoTestMarker = Marker(mapView).apply {
            title = "Geo Test Drone"
            subDescription = "Debug-only virtual test position"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.drone_marker_36)
            isEnabled = true
            setVisible(true)
        }
        mapView.overlays.add(virtualGeoTestMarker)
        mapView.invalidate()
    }

    private fun updateVirtualGeoTestMarker(position: LatLon) {
        val marker = virtualGeoTestMarker ?: return
        marker.position = GeoPoint(position.lat, position.lon)
        marker.isEnabled = true
        marker.setVisible(true)
        mapView.invalidate()
    }

    private fun removeVirtualGeoTestMarker() {
        val marker = virtualGeoTestMarker ?: return
        mapView.overlays.remove(marker)
        virtualGeoTestMarker = null
        mapView.invalidate()
    }

    private fun updateLiveGeoAwarenessFromActiveSource() {
        val virtualPosition = activityViewModel.virtualGeoTestPosition.value
        val useVirtualPosition = activityViewModel.geoTestModeEnabled.value == true && virtualPosition != null
        val positionToUse = if (useVirtualPosition) {
            virtualPosition
        } else {
            latestRealDronePosition
        }
        val altitudeToUse = if (useVirtualPosition) {
            null
        } else {
            latestRealDroneAltitudeMeters
        }
        updateLiveGeoAwarenessStatus(positionToUse, altitudeToUse)
    }
}
