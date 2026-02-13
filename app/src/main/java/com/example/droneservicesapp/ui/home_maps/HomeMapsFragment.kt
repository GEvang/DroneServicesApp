package com.example.droneservicesapp.ui.home_maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
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
import com.example.droneservicesapp.activities.MainActivityViewModel
import com.example.droneservicesapp.databinding.FragmentHomeMapsBinding
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.shape.Survey
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.dronefleet.mavlink.common.MavCmd


//abstract
class HomeMapsFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentHomeMapsBinding? = null

    private var mMap: GoogleMap? = null

    private lateinit var droneViewModel: DroneViewModel
    private lateinit var activityViewModel: MainActivityViewModel

    private lateinit var missionParamsController: MissionParamsController

    private lateinit var polygonEditor: PolygonEditor

    private lateinit var mapController: MapController

    private lateinit var missionSaveController: MissionSaveController

    private lateinit var missionLoadController: MissionLoadController

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private var sharedPreferences: SharedPreferences? = null

    private var survey: Survey? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        sharedPreferences =
            this.activity?.let { PreferenceManager.getDefaultSharedPreferences(it.applicationContext) }

        _binding = FragmentHomeMapsBinding.inflate(inflater, container, false)

        binding.myLocationButton.setOnClickListener {
            if (activityViewModel.drawEnableLiveData.value == false)
                mapController.zoomToCurrentLocation()
        }

        binding.droneLocationButton.setOnClickListener {
            if (droneViewModel.conStateLiveData.value == true)
                mapController.zoomToCurrentLocation()
            else
                Toast.makeText(context, getString(R.string.no_conn_msg), Toast.LENGTH_LONG).show()
        }

        droneViewModel = activity?.let { ViewModelProvider(it)[DroneViewModel::class.java] }!!

        activityViewModel =
            activity?.let { ViewModelProvider(it)[MainActivityViewModel::class.java] }!!

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        droneViewModel.droneLocationLiveData.observe(viewLifecycleOwner) { droneLocation ->
            if (droneLocation != null) {
                mapController.setDroneMarkerVisible(true)
                mapController.updateDroneLocation(droneLocation)
            }
        }

        droneViewModel.droneHeading.observe(viewLifecycleOwner) { droneHeading ->
            droneHeading?.let { heading ->
                mapController.updateDroneHeading(heading)
            }
        }

        droneViewModel.conStateLiveData.observe(viewLifecycleOwner) { connState ->
            mapController.setDroneMarkerVisible(connState == true)
        }

        droneViewModel.droneFrontDistance.distinctUntilChanged()
            .observe(this.viewLifecycleOwner) { frontDistance ->
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
            .observe(this.viewLifecycleOwner) { backDistance ->
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

        activityViewModel.angleProgress.observe(requireActivity(), Observer { angle ->
            Log.i(
                "Angle Progress Observer",
                "MainActivityViewModel.MapState: " + MainActivityViewModel.MapState.SetFlightParams
            )

            if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams) {
                Log.i(
                    "Angle Progress Observer",
                    "angle call distance: " + activityViewModel.lineDistanceProgress.value!! +
                            "   angle: " + activityViewModel.angleProgress.value!!.toInt()
                )

                this.run {
                    drawSurveyMissionOnMap(
                        activityViewModel.lineDistanceProgress.value!!,
                        angle.toInt(),
                        mMap!!
                    )
                }

                val sharedPref =
                    PreferenceManager.getDefaultSharedPreferences(requireActivity().applicationContext)
                        ?: return@Observer
                with(sharedPref.edit()) {
                    putString(getString(R.string.survey_angle_pref), angle.toInt().toString())
                    apply()
                }
            }
        })

        activityViewModel.lineDistanceProgress.observe(
            this.viewLifecycleOwner,
            Observer { distance ->
                if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams) {
                    Log.i(
                        Log.INFO.toString(),
                        "distance call distance: " + distance + "   angle: " +
                                activityViewModel.angleProgress.value!!.toInt()
                    )

                    this.run {
                        drawSurveyMissionOnMap(
                            distance,
                            activityViewModel.angleProgress.value!!.toInt(),
                            mMap!!
                        )
                    }

                    val sharedPref =
                        PreferenceManager.getDefaultSharedPreferences(requireActivity().applicationContext)
                            ?: return@Observer
                    with(sharedPref.edit()) {
                        putString(
                            getString(R.string.survey_line_distance_pref),
                            distance.toInt().toString()
                        )
                        apply()
                    }
                }
            })

        activityViewModel.flightAltProgress.observe(this.viewLifecycleOwner, Observer { altitude ->
            if (activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams) {
                // Log.i(Log.INFO.toString(), "altitude: $altitude")

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
            // Log.i(Log.INFO.toString(), "Map State Changed to: $mapState")

            // Idle: drawing is not allowed
            if (mapState == MainActivityViewModel.MapState.Idle) {
                activityViewModel.drawEnableLiveData.value = false

                if (mMap != null)
                    droneViewModel.downloadMissionNew()
            }

            // Reset: clear drawings and go to Idle State
            else if (mapState == MainActivityViewModel.MapState.Reset) {
                activityViewModel.area.value!!.clearDrawings()
                activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
            }

            // Clear: clear drawings and go to Draw State
            else if (mapState == MainActivityViewModel.MapState.ClearAll) {
                activityViewModel.area.value!!.clearDrawings()
                activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Draw)
            } else if (mapState == MainActivityViewModel.MapState.ClearKeepDrawing) {
                activityViewModel.area.value!!.clearSurveyPath()
                activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Draw)
            }

            // Draw: enable drawing
            else if (mapState == MainActivityViewModel.MapState.Draw) {
                activityViewModel.drawEnableLiveData.value = true
            }

            // set flight parameters state. set angle, line distance, height
            // throws popup window to set the flight parameters
            else if (mapState == MainActivityViewModel.MapState.SetFlightParams) {
                activityViewModel.drawEnableLiveData.value = false

                Log.i(
                    Log.INFO.toString(),
                    "generic distance: " + activityViewModel.lineDistanceProgress.value!! + "   angle: " +
                            activityViewModel.angleProgress.value!!.toInt()
                )

                this.run {
                    drawSurveyMissionOnMap(
                        activityViewModel.lineDistanceProgress.value!!,
                        activityViewModel.angleProgress.value!!.toInt(), mMap!!
                    )
                }

                missionParamsController.show()

            } else if (mapState == MainActivityViewModel.MapState.LoadMissionFromFile) {
                missionLoadController.show()
            }
        }

        droneViewModel.missionItems.observe(this.viewLifecycleOwner) { missionItems ->

            if (droneViewModel.conStateLiveData.value!! && missionItems.size > 0) {
                activityViewModel.area.value!!.clearSurveyPath()

                survey?.clearMarkers()
                survey = Survey(activityViewModel.area.value!!, requireActivity())

                val surveyPath = ArrayList<LatLng>()
                for (item in missionItems) {
                    if (item.seq() > 0 && item.command().entry() == MavCmd.MAV_CMD_NAV_WAYPOINT)
                        surveyPath.add(LatLng(item.x() * 10e-8, item.y() * 10e-8))
                }

                activityViewModel.area.value!!.surveyPath = surveyPath
                mMap?.let { activityViewModel.area.value!!.surveyPolylineOptions(it) }
            }
        }

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


        _binding = null

        missionParamsController = MissionParamsController(
            context = requireContext(),
            rootView = requireView(),
            lifecycleOwner = viewLifecycleOwner,
            activityViewModel = activityViewModel,
            droneViewModel = droneViewModel,
            onSaveMissionRequested = { missionSaveController.show() }
        )

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

    private fun drawSurveyMissionOnMap(distance: Double, angle: Int, map: GoogleMap) {
        activityViewModel.area.value!!.clearSurveyPath()

        survey?.clearMarkers()
        survey = Survey(activityViewModel.area.value!!, requireActivity())
        activityViewModel.area.value!!.surveyPath =
            survey?.createSurveyPath(distance, angle, this.context, map)!!

        if (activityViewModel.area.value!!.surveyPath.size == 0) {
            activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Draw)
        } else {
            mMap?.let { activityViewModel.area.value!!.surveyPolylineOptions(it) }
        }
    }


    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        val act = activity
        if (act != null) {
            val fineGranted = ContextCompat.checkSelfPermission(
                act.applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val coarseGranted = ContextCompat.checkSelfPermission(
                act.applicationContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (fineGranted || coarseGranted) {
                mapController.zoomToCurrentLocation()
            } else {
                Toast.makeText(
                    act.applicationContext,
                    getString(R.string.invalid_location_permissions),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        mapController.bind(googleMap)
        polygonEditor.bind(googleMap)
    }


    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        return ContextCompat.getDrawable(context, vectorResId)?.run {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            val bitmap =
                Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
            draw(Canvas(bitmap))
            BitmapDescriptorFactory.fromBitmap(bitmap)
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

}





