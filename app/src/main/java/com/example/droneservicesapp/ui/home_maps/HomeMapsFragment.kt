package com.example.droneservicesapp.ui.home_maps

import android.content.Context
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
import androidx.core.graphics.createBitmap
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import androidx.preference.PreferenceManager
import com.example.droneservicesapp.R
import com.example.droneservicesapp.activities.MainActivityViewModel
import com.example.droneservicesapp.databinding.FragmentHomeMapsBinding
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.shape.Survey
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.dronefleet.mavlink.common.MavCmd
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker


// NOTE: Google Maps callback removed
class HomeMapsFragment : Fragment() {

    private var _binding: FragmentHomeMapsBinding? = null
    private val binding get() = _binding!!

    private lateinit var mapView: MapView

    private lateinit var droneViewModel: DroneViewModel
    private lateinit var activityViewModel: MainActivityViewModel

    private lateinit var missionParamsController: MissionParamsController
    private lateinit var polygonEditor: PolygonEditor
    private lateinit var mapController: MapController
    private lateinit var missionSaveController: MissionSaveController
    private lateinit var missionLoadController: MissionLoadController
    private lateinit var osmdroidMapController: OsmdroidMapController
    private lateinit var osmdroidPolygonEditor: OsmdroidPolygonEditor

    private var droneMarker: Marker? = null

    private var survey: Survey? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeMapsBinding.inflate(inflater, container, false)

        droneViewModel = activity?.let { ViewModelProvider(it)[DroneViewModel::class.java] }!!
        activityViewModel = activity?.let { ViewModelProvider(it)[MainActivityViewModel::class.java] }!!

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // osmdroid MapView from fragment_home_maps.xml
        mapView = view.findViewById(R.id.osmMap)
        mapView.setMultiTouchControls(true)

        // Satellite tiles
        mapView.setTileSource(com.example.droneservicesapp.maps.EsriWorldImageryTileSource)

        // Over-zoom behavior (visual scaling; Esri native max ~19)
        mapView.isTilesScaledToDpi = true
        mapView.maxZoomLevel = 20.0

        mapView.controller.setZoom(18.0)
        mapView.controller.setCenter(GeoPoint(35.36449, 24.48730)) // Rethymno

        // My location dot (osmdroid)
        osmdroidMapController = OsmdroidMapController(requireContext(), mapView)
        osmdroidMapController.initOverlays()

        osmdroidPolygonEditor = OsmdroidPolygonEditor(requireActivity(), activityViewModel, mapView)
        osmdroidPolygonEditor.init()


        // Drone marker (osmdroid)
        droneMarker = Marker(mapView).apply {
            position = GeoPoint(0.0, 0.0) // temporary, hidden until we get first location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            isEnabled = false
            setVisible(false)

            // Use your existing drone icon drawable
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.drone_marker_48_black)
        }
        mapView.overlays.add(droneMarker)
        mapView.invalidate()

        initControllers()
        bindUiButtons()
        observeDroneViewModel()
        observeMapState()
    }

    private fun initControllers() {
        missionParamsController = MissionParamsController(
            context = requireContext(),
            rootView = requireView(),
            lifecycleOwner = viewLifecycleOwner,
            activityViewModel = activityViewModel,
            droneViewModel = droneViewModel,
            onSaveMissionRequested = { missionSaveController.show() }
        )

        // These controllers are still GoogleMap-based today.
        polygonEditor = PolygonEditor(
            activity = requireActivity(),
            activityViewModel = activityViewModel,
            iconFactory = { act, drawableId ->
                bitmapDescriptorFromVector(act, drawableId)!!
            }
        )

        mapController = MapController(
            activity = requireActivity(),
            context = requireContext(),
            iconFactory = { ctx, drawableId ->
                bitmapDescriptorFromVector(ctx, drawableId)!!
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
    }

    private fun bindUiButtons() {
        val paramsSideView: LinearLayoutCompat? =
            requireActivity().findViewById(R.id.mission_params_side_view)
        val saveFileView: LinearLayoutCompat? =
            requireActivity().findViewById(R.id.save_file_layout)
        val loadFileView: LinearLayoutCompat? =
            requireActivity().findViewById(R.id.load_file_selector_layout)
        val userLocationFB: FloatingActionButton? =
            requireActivity().findViewById(R.id.my_location_button)
        val droneLocationFB: FloatingActionButton? =
            requireActivity().findViewById(R.id.drone_location_button)
        val bottomNavigationView: BottomNavigationView? =
            requireActivity().findViewById(R.id.bottom_nav_view)

        // Download current viewport for offline use
        binding.downloadOfflineButton.setOnClickListener {
            downloadCurrentViewOffline(minZoom = 14, maxZoom = 18)
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


        bottomNavigationView?.isVisible = true
        bottomNavigationView?.menu?.findItem(R.id.action_cancel)?.isVisible = false
        bottomNavigationView?.menu?.findItem(R.id.action_accept)?.isVisible = false
        bottomNavigationView?.menu?.findItem(R.id.action_erase)?.isVisible = false
        bottomNavigationView?.menu?.findItem(R.id.action_draw)?.isVisible = true
        bottomNavigationView?.menu?.findItem(R.id.action_load)?.isVisible = true

        paramsSideView?.isVisible = false
        saveFileView?.isVisible = false
        loadFileView?.isVisible = false
        userLocationFB?.isVisible = true
        droneLocationFB?.isVisible = true

        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
    }

    private fun observeDroneViewModel() {
        // Drone position -> update marker
        droneViewModel.droneLocationLiveData.observe(viewLifecycleOwner) { droneLocation ->
            if (droneLocation != null) {
                osmdroidMapController.updateDronePosition(droneLocation.latitude, droneLocation.longitude)
            } else {
                osmdroidMapController.setDroneVisible(false)
            }
        }
        // Drone heading -> rotate marker (optional)
        droneViewModel.droneHeading.observe(viewLifecycleOwner) { droneHeading ->
            droneHeading?.let { heading ->
                osmdroidMapController.updateDroneHeadingDegrees(heading.toFloat())
            }
        }

        droneViewModel.conStateLiveData.observe(viewLifecycleOwner) { _ ->
            // TEMP: MapController migration required for marker visibility
        }

        droneViewModel.droneFrontDistance.distinctUntilChanged()
            .observe(viewLifecycleOwner) { frontDistance ->
                Log.i("frontDistance", "------")
                Log.i("frontDistance", "frontDistance $frontDistance ")
                val color = getColor(frontDistance)

                Log.i("frontDistance", "distance: $frontDistance    color: $color")

                requireActivity().findViewById<TextView>(R.id.front_dist).text =
                    frontDistance.toString() + "m"
                val drawable =
                    requireActivity().findViewById<ImageView>(R.id.avoidance_compass).drawable as GradientDrawable

                if (drawable.colors != null) {
                    drawable.colors = intArrayOf(color, drawable.colors!![1], drawable.colors!![2])
                }
            }

        droneViewModel.droneBackDistance.distinctUntilChanged()
            .observe(viewLifecycleOwner) { backDistance ->
                Log.i("backDistance", "------")
                Log.i("backDistance", "backDistance $backDistance ")
                val color = getColor(backDistance)

                Log.i("backDistance", "distance: $backDistance    color: $color")

                requireActivity().findViewById<TextView>(R.id.back_dist).text =
                    backDistance.toString() + "m"
                val drawable =
                    requireActivity().findViewById<ImageView>(R.id.avoidance_compass).drawable as GradientDrawable

                if (drawable.colors != null) {
                    drawable.colors = intArrayOf(drawable.colors!![0], drawable.colors!![1], color)
                }
            }

        droneViewModel.missionItems.observe(viewLifecycleOwner) { missionItems ->
            if (droneViewModel.conStateLiveData.value == true && missionItems.isNotEmpty()) {
                activityViewModel.area.value!!.clearSurveyPath()
                osmdroidMapController.clearSurveyPath()
                survey?.clearMarkers()
                survey = Survey(activityViewModel.area.value!!, requireActivity())

                val surveyPath = ArrayList<com.google.android.gms.maps.model.LatLng>()
                for (item in missionItems) {
                    if (item.seq() > 0 && item.command().entry() == MavCmd.MAV_CMD_NAV_WAYPOINT) {
                        surveyPath.add(
                            com.google.android.gms.maps.model.LatLng(
                                item.x() * 10e-8,
                                item.y() * 10e-8
                            )
                        )
                    }
                }

                activityViewModel.area.value!!.surveyPath = surveyPath

                // REMOVED: needs GoogleMap drawing
            }
        }
    }

    private fun observeMapState() {
        activityViewModel.angleProgress.observe(viewLifecycleOwner, Observer { angle ->
            if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams) {
                val sharedPref =
                    PreferenceManager.getDefaultSharedPreferences(requireActivity().applicationContext)
                        ?: return@Observer
                with(sharedPref.edit()) {
                    putString(getString(R.string.survey_angle_pref), angle.toInt().toString())
                    apply()
                }

                if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams) {
                    drawSurveyMissionOnMap(
                        activityViewModel.lineDistanceProgress.value!!,
                        angle.toInt()
                    )
                }

            }
        })

        activityViewModel.lineDistanceProgress.observe(viewLifecycleOwner, Observer { distance ->
            if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams) {
                val sharedPref =
                    PreferenceManager.getDefaultSharedPreferences(requireActivity().applicationContext)
                        ?: return@Observer
                with(sharedPref.edit()) {
                    putString(getString(R.string.survey_line_distance_pref), distance.toInt().toString())
                    apply()
                }

                if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams) {
                    drawSurveyMissionOnMap(
                        distance,
                        activityViewModel.angleProgress.value!!.toInt()
                    )
                }

            }
        })

        activityViewModel.flightAltProgress.observe(viewLifecycleOwner, Observer { altitude ->
            if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams) {
                val sharedPref =
                    PreferenceManager.getDefaultSharedPreferences(requireActivity().applicationContext)
                        ?: return@Observer
                with(sharedPref.edit()) {
                    putString(getString(R.string.survey_altitude_pref), altitude.toInt().toString())
                    apply()
                }
            }
        })

        activityViewModel.mapState.observe(viewLifecycleOwner) { mapState ->
            when (mapState) {
                MainActivityViewModel.MapState.Idle -> handleIdleState()
                MainActivityViewModel.MapState.Reset -> handleResetState()
                MainActivityViewModel.MapState.ClearAll -> handleClearAllState()
                MainActivityViewModel.MapState.ClearKeepDrawing -> handleClearKeepDrawingState()
                MainActivityViewModel.MapState.Draw -> handleDrawState()
                MainActivityViewModel.MapState.SetFlightParams -> handleSetFlightParamsState()
                MainActivityViewModel.MapState.LoadMissionFromFile -> handleLoadMissionState()
                MainActivityViewModel.MapState.UploadMissionSuccess -> handleUploadMissionSuccessState()
                MainActivityViewModel.MapState.SaveMissionToFile -> handleSaveMissionToFileState()
            }
        }
    }

    private fun handleIdleState() {
        activityViewModel.drawEnableLiveData.value = false
        osmdroidPolygonEditor.setEnabled(false)
        droneViewModel.downloadMissionNew()
    }

    private fun handleResetState() {
        activityViewModel.area.value!!.clearDrawings()
        osmdroidPolygonEditor.clear()
        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
    }

    private fun handleClearAllState() {
        activityViewModel.area.value!!.clearDrawings()
        osmdroidPolygonEditor.clear()
        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Draw)
    }

    private fun handleClearKeepDrawingState() {
        activityViewModel.area.value!!.clearSurveyPath()
        osmdroidMapController.clearSurveyPath()
        osmdroidPolygonEditor.clear()
        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Draw)
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

    private fun handleUploadMissionSuccessState() {
        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Reset)
    }

    private fun handleSaveMissionToFileState() {
        missionSaveController.show()
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
                    Toast.makeText(requireContext(), "Offline download started", Toast.LENGTH_SHORT).show()
                }

                override fun setPossibleTilesInArea(total: Int) {
                    // Optional: show estimated tiles
                }

                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                    // Optional: hook to progress UI later
                }

                override fun onTaskComplete() {
                    Toast.makeText(requireContext(), "Offline download complete", Toast.LENGTH_LONG).show()
                }

                override fun onTaskFailed(errors: Int) {
                    Toast.makeText(requireContext(), "Offline download failed ($errors)", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun drawSurveyMissionOnMap(distance: Double, angle: Int) {
        // Clear existing stored path + overlay
        activityViewModel.area.value!!.clearSurveyPath()
        osmdroidMapController.clearSurveyPath()

        // Compute new path
        survey?.clearMarkers()
        survey = Survey(activityViewModel.area.value!!, requireActivity())

        val path = survey!!.createSurveyPath(distance, angle, context)

        activityViewModel.area.value!!.surveyPath = path

        if (path.isEmpty()) {
            // Geometry invalid / too big / etc.
            activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Draw)
        } else {
            // Render on osmdroid
            osmdroidMapController.setSurveyPath(path)
        }
    }


    private fun bitmapDescriptorFromVector(
        context: Context,
        vectorResId: Int
    ): com.google.android.gms.maps.model.BitmapDescriptor? {
        return ContextCompat.getDrawable(context, vectorResId)?.run {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            val bitmap = createBitmap(intrinsicWidth, intrinsicHeight)
            draw(android.graphics.Canvas(bitmap))
            com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

    private fun getColor(inValue: Int): Int {
        val minValue = 5
        val maxValue = 15
        var value =
            if (inValue < minValue) minValue else if (inValue > maxValue) maxValue else inValue
        value = maxValue + minValue - value

        val hue = ((120 * (maxValue - value)) / (maxValue - minValue)).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        osmdroidMapController.onResume()    }

    override fun onPause() {
        osmdroidMapController.onResume()
        mapView.onPause()
        super.onPause()
    }
}
