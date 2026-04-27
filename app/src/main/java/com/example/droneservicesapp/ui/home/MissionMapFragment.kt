package com.example.droneservicesapp.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.storage.MissionFileStore
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
    private lateinit var homeMapChromeBinder: HomeMapChromeBinder
    private lateinit var homeMapPanelsBinder: HomeMapPanelsBinder
    private lateinit var homeMapModeEffectsBinder: HomeMapModeEffectsBinder
    private lateinit var homeMapTelemetryBinder: HomeMapTelemetryBinder
    private lateinit var osmdroidMapController: OsmdroidMapController
    private lateinit var osmdroidPolygonEditor: OsmdroidPolygonEditor
    private lateinit var missionFileStore: MissionFileStore
    private var hasCenteredInitialViewport = false
    private var hasCenteredToDrone = false
    private var initialCenterAttemptCount = 0

    companion object {
        private const val DEFAULT_MAP_ZOOM = 18.0
        private const val DEFAULT_MAP_LAT = 35.36449
        private const val DEFAULT_MAP_LON = 24.48730
        private const val OFFLINE_MIN_ZOOM = 14
        private const val OFFLINE_MAX_ZOOM = 18

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

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeMapView(view)
        initControllers()
        bindUiButtons()
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
            droneViewModel = droneViewModel
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
                    Toast.makeText(requireContext(), "No missions saved yet", Toast.LENGTH_LONG).show()
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
            if (droneLocation != null) {
                osmdroidMapController.updateDronePosition(
                    droneLocation.latitude,
                    droneLocation.longitude
                )
                centerOnDroneIfNeeded()
            } else {
                osmdroidMapController.setDroneVisible(false)
                centerInitialViewportIfNeeded()
            }
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
        })

        activityViewModel.surveyPath.observe(viewLifecycleOwner) { surveyPath ->
            val hasPolygon = (activityViewModel.missionArea.value?.vertices?.size ?: 0) >= 3
            mapViewModel.setMissionAreaAvailable(hasPolygon || !surveyPath.isNullOrEmpty())
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
                    Snackbar.make(requireView(), "Upload complete", Snackbar.LENGTH_LONG).show()
                    activityViewModel.sendAction(MainActivityViewModel.MapAction.ResetToIdle)
                }
                is MainActivityViewModel.MapAction.UploadMissionFailed -> {
                    Toast.makeText(context, action.reason, Toast.LENGTH_LONG).show()
                    Snackbar.make(requireView(), "Upload failed: ${action.reason}", Snackbar.LENGTH_LONG).show()
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
        osmdroidPolygonEditor.setEnabled(state.interactionState.isDrawingEnabled)
        homeMapChromeBinder.renderShell(state.shellState)
        homeMapChromeBinder.renderInteraction(state.interactionState)
        homeMapChromeBinder.renderOverlayControls(state.overlayControlsState)
        homeMapPanelsBinder.renderShell(state.shellState)
        homeMapPanelsBinder.renderOverlays(state.panelState)
        homeMapModeEffectsBinder.render(state.screenMode)
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
                    Toast.makeText(requireContext(), "Offline download started", Toast.LENGTH_SHORT)
                        .show()
                }

                override fun setPossibleTilesInArea(total: Int) {}
                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {}
                override fun onTaskComplete() {
                    Toast.makeText(requireContext(), "Offline download complete", Toast.LENGTH_LONG)
                        .show()
                }

                override fun onTaskFailed(errors: Int) {
                    Toast.makeText(
                        requireContext(),
                        "Offline download failed ($errors)",
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
            binding.root.postDelayed({ centerInitialViewportIfNeeded() }, 1000L)
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
}
