package com.example.droneservicesapp.ui.home_maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
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
import com.example.droneservicesapp.MissionFileHandler
import com.example.droneservicesapp.R
import com.example.droneservicesapp.activities.MainActivityViewModel
import com.example.droneservicesapp.databinding.FragmentHomeMapsBinding
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.shape.Survey
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.dronefleet.mavlink.common.MavCmd
import java.io.File
import java.io.FileInputStream
import java.util.Locale


//abstract
class HomeMapsFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentHomeMapsBinding? = null

    private var mMap: GoogleMap? = null

    private lateinit var droneViewModel: DroneViewModel
    private lateinit var activityViewModel: MainActivityViewModel

    private lateinit var missionParamsController: MissionParamsController

    private lateinit var polygonEditor: PolygonEditor


    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private var droneMarker: Marker? = null

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
                zoomToCurrentLocation()
        }

        binding.droneLocationButton.setOnClickListener {
            if (droneViewModel.conStateLiveData.value == true)
                zoomToDroneLocation()
            else
                Toast.makeText(context, getString(R.string.no_conn_msg), Toast.LENGTH_LONG).show()
        }

        droneViewModel = activity?.let { ViewModelProvider(it)[DroneViewModel::class.java] }!!

        activityViewModel =
            activity?.let { ViewModelProvider(it)[MainActivityViewModel::class.java] }!!

        return binding.root
    }


    private fun setMarkerLocation(location: Location) {
        droneMarker?.position = LatLng(location.latitude, location.longitude)

        //Log.i(Log.INFO.toString(), "drone location: ${droneMarker?.position}")
    }

    private fun setMarkerRotation(heading: Double) {
        droneMarker?.rotation = heading.toFloat()

        //Log.i(Log.INFO.toString(), "drone heading: ${droneMarker?.rotation}")
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        droneViewModel.droneLocationLiveData.observe(this.viewLifecycleOwner) { droneLocation ->
            droneMarker?.let { setMarkerLocation(droneLocation) }


            droneViewModel.telemetryAliveLiveData.observe(viewLifecycleOwner) { telemetryAlive ->
                val connected = droneViewModel.conStateLiveData.value == true
                if (connected && !telemetryAlive) {
                    Toast.makeText(
                        context,
                        getString(R.string.no_telemetry_forwarding_msg),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        }

        droneViewModel.droneHeading.observe(this.viewLifecycleOwner) { droneHeading ->
            droneHeading?.let { setMarkerRotation(droneHeading) }
        }

        droneViewModel.conStateLiveData.observe(this.viewLifecycleOwner) { connState ->
            droneMarker?.isVisible = connState

            if (connState && activityViewModel.mapState.value == MainActivityViewModel.MapState.Idle) {
                droneViewModel.downloadMissionNew()
            }
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
                fileLoaderDialog()
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
            onSaveMissionRequested = { initMissionSave() }
        )

        polygonEditor = PolygonEditor(
            activity = requireActivity(),
            activityViewModel = activityViewModel,
            iconFactory = { act, drawableId ->
                bitmapDescriptorFromVector(act, drawableId)!!
            }
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


    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    // [START maps_current_place_update_location_ui]
    private fun zoomToCurrentLocation() {
        if (activity?.let {
                ContextCompat.checkSelfPermission(
                    it.applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            } != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this.context, getString(R.string.no_permissions_msg), Toast.LENGTH_LONG)
                .show()
            return
        }

        // zoom to drone or to user location
        val location: Location? = getLastKnownLocation()
        //Log.i(Log.INFO.toString(), "lat: " + location?.latitude + "   long: " + location?.longitude)

        if (location != null) {
            val cameraPosition = CameraPosition.Builder()
                .target(
                    LatLng(
                        location.latitude,
                        location.longitude
                    )
                ) // Sets the center of the map to location user
                .zoom(19f) // Sets the zoom
                .build() // Creates a CameraPosition from the builder
            mMap?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        }

    }
    // [END zoomToLocation]


    private fun zoomToDroneLocation() {
        // zoom to drone or to user location
        val location: Location? = droneViewModel.droneLocationLiveData.value
        //Log.i(Log.INFO.toString(), "drone lat: " + location?.latitude + "   drone long: " + location?.longitude)

        if (location != null) {
            val cameraPosition = CameraPosition.Builder()
                .target(
                    LatLng(
                        location.latitude,
                        location.longitude
                    )
                ) // Sets the center of the map to location user
                .zoom(19f) // Sets the zoom
                .build() // Creates a CameraPosition from the builder
            mMap?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        }

    }
    // [END zoomToDroneLocation]

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(): Location? {
        val mLocationManager =
            context?.getSystemService(LOCATION_SERVICE) as LocationManager
        val providers: List<String> = mLocationManager.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            val l: Location = mLocationManager.getLastKnownLocation(provider) ?: continue
            if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                // Found best last known location: %s", l);
                bestLocation = l
            }
        }
        return bestLocation
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
                // Enable user location layer
                googleMap.isMyLocationEnabled = true
                zoomToCurrentLocation()
            } else {
                Toast.makeText(
                    act.applicationContext,
                    getString(R.string.invalid_location_permissions),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // Configure map UI + location layer (if permitted)
        googleMap.uiSettings.isMyLocationButtonEnabled = false

        // Initialize drone marker (hidden until we get first position)
        droneMarker = googleMap.addMarker(
            MarkerOptions()
                .visible(false)
                .position(LatLng(0.0, 0.0))
                .anchor(0.5f, 0.5f)
                .icon(bitmapDescriptorFromVector(requireContext(), R.drawable.drone_marker_36)!!)
        )

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


    private fun fileLoaderDialog() {
        val loadFileView =
            requireActivity().findViewById<LinearLayoutCompat>(R.id.load_file_selector_layout)

        // Set the directory path where the ".waypoint" files are located
        val directory = context?.getString(
            R.string.mission_directory
        )?.let {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), it
            )
        }

        // Get a list of all files in the directory that end with ".waypoint"
        val files = directory?.listFiles { _, name ->
            name.lowercase(Locale.ROOT).endsWith(getString(R.string.DroneServicesFilePageSuffix))
        }

        if (files == null) {
            Toast.makeText(context, "Error in loading missions from directory", Toast.LENGTH_LONG)
                .show()
        } else if (files.isEmpty()) {
            Toast.makeText(context, "No missions saved yet", Toast.LENGTH_LONG).show()
        } else {
            // Extract file names from the list of files
            val fileNames = files.map { it.name.substring(0, it.name.lastIndexOf('.')) }

            val listView: ListView = requireActivity().findViewById(R.id.file_list)

            val itemList = mutableListOf<String>()
            val adapter =
                ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, itemList)

            adapter.addAll(fileNames)
            adapter.notifyDataSetChanged()
            listView.adapter = adapter

            loadFileView.isVisible = true

            listView.setOnItemClickListener { _, _, position, _ ->

                Log.d("File Selector", "Position Selected $position")
                Log.d("File Selector", "File Selected ${itemList[position]}")
                Log.d("File Selector", "File Path Selected ${files[position].path}")

                MissionFileHandler(requireActivity(), activityViewModel).parseXml(
                    FileInputStream(
                        files[position].path
                    )
                )

                Toast.makeText(
                    context,
                    "Selected file: ${files[position].path}",
                    Toast.LENGTH_SHORT
                ).show()

                loadFileView.isVisible = false
            }
        }

        requireActivity().findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            loadFileView.isVisible = false
            activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
        }
    }


    //PopupWindow display method
    private fun initMissionSave() {

        var overrideFile = false
        Log.i("MissionSave", "initiated Mission Save Layout")

        val saveFileView =
            requireActivity().findViewById<LinearLayoutCompat>(R.id.save_file_layout)!!
        saveFileView.isVisible = true

        val inputFilename = requireActivity().findViewById<EditText>(R.id.input_filename)!!
        inputFilename.text.clear()

        // retrieve override checkbox value
        val overrideCheckBox = requireActivity().findViewById<CheckBox>(R.id.override_checkbox)
        overrideCheckBox.setOnCheckedChangeListener { _, isChecked ->
            overrideFile = isChecked
        }

        // Set the OnClickListener for the Ok button to dismiss the popup window
        val buttonSaveMission = requireActivity().findViewById<Button>(R.id.save_button)
        buttonSaveMission.setOnClickListener {

            if (inputFilename.text.isBlank()) {
                Toast.makeText(
                    requireActivity().baseContext,
                    requireActivity().baseContext?.getString(R.string.error_name_empty),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Log.i(
                    "MissionSave",
                    "activityViewModel.area.value!!.polygonEdges, ${activityViewModel.area.value!!.polygonEdges}"
                )
                Log.i(
                    "MissionSave",
                    "activityViewModel.lineDistanceProgress, ${activityViewModel.lineDistanceProgress.value!!.toInt()}"
                )
                Log.i(
                    "MissionSave",
                    "activityViewModel.angleProgress, ${activityViewModel.angleProgress.value!!.toInt()}"
                )
                Log.i(
                    "MissionSave",
                    "activityViewModel.flightAltProgress, ${activityViewModel.flightAltProgress.value!!.toInt()}"
                )
                Log.i(
                    "MissionSave",
                    "activityViewModel.sprayerProgress, ${activityViewModel.sprayerProgress.value!!.toInt()}"
                )

                val isSaved =
                    MissionFileHandler(requireActivity(), activityViewModel).saveMissionXML(
                        activityViewModel.area.value!!.polygonEdges,
                        activityViewModel.lineDistanceProgress.value!!.toInt(),
                        activityViewModel.angleProgress.value!!.toInt(),
                        activityViewModel.flightAltProgress.value!!.toInt(),
                        activityViewModel.sprayerProgress.value!!.toInt(),
                        inputFilename.text.toString(),
                        overrideFile
                    )

                if (isSaved)
                    saveFileView.isVisible = false
            }
        }

        // Set the OnClickListener for the Cancel button to dismiss the popup window
        requireActivity().findViewById<Button>(R.id.cancel_button)?.setOnClickListener {
            // Perform the action you want when the user clicks Cancel
            saveFileView.isVisible = false
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





