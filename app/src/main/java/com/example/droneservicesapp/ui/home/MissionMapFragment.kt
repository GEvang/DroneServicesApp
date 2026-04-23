package com.example.droneservicesapp.ui.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import androidx.preference.PreferenceManager
import com.example.droneservicesapp.R
import com.example.droneservicesapp.databinding.FragmentHomeMapsBinding
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.survey.SurveyPlanner
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.home.binders.MissionLoadController
import com.example.droneservicesapp.ui.home.binders.MissionParamsController
import com.example.droneservicesapp.ui.home.binders.MissionSaveController
import com.example.droneservicesapp.ui.home.components.EsriWorldImageryTileSource
import com.example.droneservicesapp.ui.home.components.OsmdroidMapController
import com.example.droneservicesapp.ui.home.components.OsmdroidPolygonEditor
import com.example.droneservicesapp.ui.home.model.MainActivityViewModel
import com.example.droneservicesapp.ui.home.model.MissionMapViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.maps.android.SphericalUtil
import io.dronefleet.mavlink.common.MavCmd
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MissionMapFragment : Fragment() {
    private var _binding: FragmentHomeMapsBinding? = null
    private val binding get() = _binding!!
    private lateinit var mapView: MapView

    private lateinit var droneViewModel: DroneViewModel
    private lateinit var activityViewModel: MainActivityViewModel
    private lateinit var mapViewModel: MissionMapViewModel

    private lateinit var missionParamsController: MissionParamsController
    private lateinit var missionSaveController: MissionSaveController
    private lateinit var missionLoadController: MissionLoadController
    private lateinit var osmdroidMapController: OsmdroidMapController
    private lateinit var osmdroidPolygonEditor: OsmdroidPolygonEditor

    private var droneMarker: Marker? = null

    private lateinit var paramsSideView: LinearLayoutCompat
    private lateinit var saveFileView: LinearLayoutCompat
    private lateinit var loadFileView: LinearLayoutCompat

    private var bottomNavigationView: BottomNavigationView? = null
    

    companion object {
        private const val LOG_TAG_FRONT_DISTANCE = "frontDistance"
        private const val LOG_TAG_BACK_DISTANCE = "backDistance"
        private const val MIN_DISTANCE_VALUE = 5
        private const val MAX_DISTANCE_VALUE = 15
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
        mapViewModel = ViewModelProvider(this)[MissionMapViewModel::class.java]

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        paramsSideView = view.findViewById(R.id.mission_params_side_view)
        saveFileView = view.findViewById(R.id.save_file_layout)
        loadFileView = view.findViewById(R.id.load_file_selector_layout)

        bottomNavigationView = requireActivity().findViewById(R.id.bottom_nav_view)

        initializeMapView(view)
        initControllers()
        bindUiButtons()
        observeDroneViewModel()
        observeMapState()
        observeUploadFeedback()
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

        droneMarker = Marker(mapView).apply {
            position = GeoPoint(0.0, 0.0)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            isEnabled = false
            setVisible(false)
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.drone_marker_36)
        }
        mapView.overlays.add(droneMarker)
        mapView.invalidate()
    }

    private fun initControllers() {
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
    }

    private fun bindUiButtons() {
        binding.downloadOfflineButton.setOnClickListener {
            downloadCurrentViewOffline(minZoom = OFFLINE_MIN_ZOOM, maxZoom = OFFLINE_MAX_ZOOM)
        }

        binding.myLocationButton.setOnClickListener {
            if (activityViewModel.drawEnableLiveData.value == false) {
                osmdroidMapController.centerOnUserIfPermitted()
            }
        }

        binding.droneLocationButton.setOnClickListener {
            if (droneViewModel.conStateLiveData.value == true) {
                osmdroidMapController.centerOnDrone()
            } else {
                Toast.makeText(context, getString(R.string.no_conn_msg), Toast.LENGTH_LONG).show()
            }
        }

        configureBottomNavigationView(bottomNavigationView)

        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
    }

    private fun configureBottomNavigationView(navigationView: BottomNavigationView?) {
        navigationView?.isVisible = true
    }

    private fun observeDroneViewModel() {
        droneViewModel.droneLocationLiveData.observe(viewLifecycleOwner) { droneLocation ->
            if (droneLocation != null) {
                osmdroidMapController.updateDronePosition(
                    droneLocation.latitude,
                    droneLocation.longitude
                )
            } else {
                osmdroidMapController.setDroneVisible(false)
            }
        }

        droneViewModel.droneHeading.observe(viewLifecycleOwner) { droneHeading ->
            droneHeading?.let { heading ->
                osmdroidMapController.updateDroneHeadingDegrees(heading.toFloat())
            }
        }

        droneViewModel.droneFrontDistance.distinctUntilChanged()
            .observe(viewLifecycleOwner) { frontDistance ->
                updateDistanceDisplay(
                    LOG_TAG_FRONT_DISTANCE,
                    frontDistance,
                    R.id.front_dist,
                    colorIndex = 0
                )
            }

        droneViewModel.droneBackDistance.distinctUntilChanged()
            .observe(viewLifecycleOwner) { backDistance ->
                updateDistanceDisplay(
                    LOG_TAG_BACK_DISTANCE,
                    backDistance,
                    R.id.back_dist,
                    colorIndex = 2
                )
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

    private fun updateDistanceDisplay(
        logTag: String,
        distance: Int,
        textViewId: Int,
        colorIndex: Int
    ) {
        Log.i(logTag, "------")
        Log.i(logTag, "$logTag: $distance")

        val color = getColor(distance)
        Log.i(logTag, "distance: $distance    color: $color")

        requireActivity().findViewById<TextView>(textViewId)?.text = "$distance m"

        val compassImageView = requireActivity().findViewById<ImageView>(R.id.avoidance_compass)
        val drawable = compassImageView?.drawable as? GradientDrawable

        drawable?.colors?.let { colors ->
            val newColors = colors.copyOf()
            newColors[colorIndex] = color
            drawable.colors = newColors
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

        activityViewModel.mapState.observe(viewLifecycleOwner) { mapState ->
            mapViewModel.updateFromMapState(mapState)

            when (mapState) {
                MainActivityViewModel.MapState.Idle -> handleIdleState()
                MainActivityViewModel.MapState.Draw -> handleDrawState()
                MainActivityViewModel.MapState.SetFlightParams -> handleSetFlightParamsState()
                MainActivityViewModel.MapState.LoadMissionFromFile -> handleLoadMissionState()
                MainActivityViewModel.MapState.SaveMissionToFile -> handleSaveMissionToFileState()
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
                    activityViewModel.sendAction(MainActivityViewModel.MapAction.ResetToIdle)
                }
                is MainActivityViewModel.MapAction.UploadMissionFailed -> {
                    Toast.makeText(context, action.reason, Toast.LENGTH_LONG).show()
                    activityViewModel.sendAction(MainActivityViewModel.MapAction.ResetToIdle)
                }
            }
        }
    }

    private fun observeUploadFeedback() {
        activityViewModel.mapAction.observe(viewLifecycleOwner) { event ->
            val action = event.getContentIfNotHandled() ?: return@observe

            when (action) {
                is MainActivityViewModel.MapAction.UploadMissionSuccess -> {
                    Snackbar.make(requireView(), "Upload complete", Snackbar.LENGTH_LONG).show()
                }
                is MainActivityViewModel.MapAction.UploadMissionFailed -> {
                    Snackbar.make(requireView(), "Upload failed: ${action.reason}", Snackbar.LENGTH_LONG).show()
                }
                else -> Unit
            }
        }
    }



    private fun observeMissionMapViewModel() {
        mapViewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                MissionMapViewModel.UiState.Idle, MissionMapViewModel.UiState.Drawing -> {
                    paramsSideView.isVisible = false
                    saveFileView.isVisible = false
                    loadFileView.isVisible = false
                    binding.myLocationButton.isVisible = true
                    binding.droneLocationButton.isVisible = true
                    binding.downloadOfflineButton.isVisible = true
                    
                    // Remove touch listeners when panels are hidden
                    paramsSideView.setOnTouchListener(null)
                    saveFileView.setOnTouchListener(null)
                    loadFileView.setOnTouchListener(null)
                }
                MissionMapViewModel.UiState.EditingParams -> {
                    paramsSideView.isVisible = true
                    saveFileView.isVisible = false
                    loadFileView.isVisible = false
                    binding.myLocationButton.isVisible = false
                    binding.droneLocationButton.isVisible = false
                    binding.downloadOfflineButton.isVisible = false
                    
                    // Attach touch listener to consume touches on visible panel
                    paramsSideView.setOnTouchListener { _, _ -> true }
                    // Remove listeners from hidden panels
                    saveFileView.setOnTouchListener(null)
                    loadFileView.setOnTouchListener(null)
                }
                MissionMapViewModel.UiState.SavingMission -> {
                    paramsSideView.isVisible = false
                    saveFileView.isVisible = true
                    loadFileView.isVisible = false
                    binding.myLocationButton.isVisible = false
                    binding.droneLocationButton.isVisible = false
                    binding.downloadOfflineButton.isVisible = false
                    
                    // Attach touch listener to consume touches on visible panel
                    saveFileView.setOnTouchListener { _, _ -> true }
                    // Remove listeners from hidden panels
                    paramsSideView.setOnTouchListener(null)
                    loadFileView.setOnTouchListener(null)
                }
                MissionMapViewModel.UiState.LoadingMission -> {
                    paramsSideView.isVisible = false
                    saveFileView.isVisible = false
                    loadFileView.isVisible = true
                    binding.myLocationButton.isVisible = false
                    binding.droneLocationButton.isVisible = false
                    binding.downloadOfflineButton.isVisible = false
                    
                    // Attach touch listener to consume touches on visible panel
                    loadFileView.setOnTouchListener { _, _ -> true }
                    // Remove listeners from hidden panels
                    paramsSideView.setOnTouchListener(null)
                    saveFileView.setOnTouchListener(null)
                }
            }
        }
    }

    private fun handleIdleState() {
        activityViewModel.drawEnableLiveData.value = false
        osmdroidPolygonEditor.setEnabled(false)
        droneViewModel.downloadMissionNew()
    }

    private fun handleDrawState() {
        activityViewModel.drawEnableLiveData.value = true
        osmdroidPolygonEditor.setEnabled(true)
    }

    private fun handleSetFlightParamsState() {
        activityViewModel.drawEnableLiveData.value = false
        missionParamsController.show()
    }

    private fun handleLoadMissionState() {
        missionLoadController.show()
    }

    private fun handleSaveMissionToFileState() {
        missionSaveController.show()
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

    private fun getColor(inValue: Int): Int {
        var value = when {
            inValue < MIN_DISTANCE_VALUE -> MIN_DISTANCE_VALUE
            inValue > MAX_DISTANCE_VALUE -> MAX_DISTANCE_VALUE
            else -> inValue
        }
        value = MAX_DISTANCE_VALUE + MIN_DISTANCE_VALUE - value

        val hue =
            ((120 * (MAX_DISTANCE_VALUE - value)) / (MAX_DISTANCE_VALUE - MIN_DISTANCE_VALUE)).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        osmdroidMapController.onResume()
    }

    override fun onPause() {
        osmdroidMapController.onPause()
        mapView.onPause()
        super.onPause()
    }
}
