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
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
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
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.dronefleet.mavlink.common.MavCmd
import java.io.File
import java.io.FileInputStream
import java.util.*


//abstract
class HomeMapsFragment : Fragment() , GoogleMap.OnMapClickListener, GoogleMap.OnMarkerClickListener,
    GoogleMap.OnMarkerDragListener
{

    private var _binding: FragmentHomeMapsBinding? = null

    private var mMap: GoogleMap? = null

    private lateinit var droneViewModel: DroneViewModel
    private lateinit var activityViewModel: MainActivityViewModel

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private var droneMarker : Marker? = null

    private var sharedPreferences : SharedPreferences? = null

    private var survey : Survey? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View
    {
        sharedPreferences = this.activity?.let { PreferenceManager.getDefaultSharedPreferences(it.applicationContext) }

        _binding = FragmentHomeMapsBinding.inflate(inflater, container, false)

        binding.myLocationButton.setOnClickListener{
            if(activityViewModel.drawEnableLiveData.value == false)
                zoomToCurrentLocation()
        }

        binding.droneLocationButton.setOnClickListener {
            if(droneViewModel.conStateLiveData.value == true )
                zoomToDroneLocation()
            else
                Toast.makeText(context, getString(R.string.no_conn_msg), Toast.LENGTH_LONG).show()
        }

        droneViewModel = activity?.let { ViewModelProvider(it)[DroneViewModel::class.java] }!!
        droneViewModel.mavlinkCommunicationLiveData.value?.setActivity(activity)

        activityViewModel = activity?.let { ViewModelProvider(it)[MainActivityViewModel::class.java] }!!

        return binding.root
    }


    private fun setMarkerLocation(location: Location) {
        droneMarker?.position = LatLng(location.latitude, location.longitude)

        //Log.i(Log.INFO.toString(), "drone location: ${droneMarker?.position}")
    }

    private fun setMarkerRotation(heading : Double) {
        droneMarker?.rotation = heading.toFloat()

        //Log.i(Log.INFO.toString(), "drone heading: ${droneMarker?.rotation}")
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(callback)

        droneViewModel.droneLocationLiveData.observe(this.viewLifecycleOwner) { droneLocation ->
            droneMarker?.let { setMarkerLocation(droneLocation) }

//            Log.i(
//                Log.DEBUG.toString(),
//                "position lat: " + droneLocation.latitude + "  long: " + droneLocation.longitude +
//                        "  rel-alt: " + droneLocation.altitude
//            )
        }

        droneViewModel.droneHeading.observe(this.viewLifecycleOwner) { droneHeading ->
            droneHeading?.let { setMarkerRotation(droneHeading) }
        }

        droneViewModel.conStateLiveData.observe(this.viewLifecycleOwner) { connState ->
            droneMarker?.isVisible = connState

            if(connState && activityViewModel.mapState.value == MainActivityViewModel.MapState.Idle)
            {
                droneViewModel.mavlinkCommunicationLiveData.value?.
                    downloadMission()
            }
        }

        droneViewModel.droneFrontDistance.distinctUntilChanged().observe(this.viewLifecycleOwner) { frontDistance ->
            Log.i("frontDistance", "------")
            Log.i("frontDistance", "frontDistance $frontDistance ")
            val color = getColor(frontDistance)

            Log.i("frontDistance", "distance: $frontDistance    color: $color")

            requireActivity().findViewById<TextView>(R.id.front_dist).text = frontDistance.toString() + "m"
            val drawable = requireActivity().
            findViewById<ImageView>(R.id.avoidance_compass).drawable as GradientDrawable

            if( drawable.colors != null ) {
                drawable.colors = intArrayOf(color, drawable.colors!![1], drawable.colors!![2])
            }
        }

        droneViewModel.droneBackDistance.distinctUntilChanged().observe(this.viewLifecycleOwner) { backDistance ->
            Log.i("backDistance", "------")
            Log.i("backDistance", "backDistance $backDistance ")
            val color = getColor(backDistance)

            Log.i("backDistance", "distance: $backDistance    color: $color")

            requireActivity().findViewById<TextView>(R.id.back_dist).text = backDistance.toString() + "m"
            val drawable = requireActivity().
            findViewById<ImageView>(R.id.avoidance_compass).drawable as GradientDrawable

            if( drawable.colors != null ) {
                drawable.colors = intArrayOf(drawable.colors!![0], drawable.colors!![1], color)
            }
        }

        activityViewModel.angleProgress.observe(requireActivity(), Observer { angle ->
            Log.i("Angle Progress Observer",
                "MainActivityViewModel.MapState: " + MainActivityViewModel.MapState.SetFlightParams)

            if(activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams)
            {
                Log.i("Angle Progress Observer",
                    "angle call distance: " + activityViewModel.lineDistanceProgress.value!! +
                            "   angle: " + activityViewModel.angleProgress.value!!.toInt())

                this.run{ drawSurveyMissionOnMap(activityViewModel.lineDistanceProgress.value!!, angle.toInt(), mMap!!) }

                val sharedPref = PreferenceManager.getDefaultSharedPreferences(requireActivity().applicationContext) ?: return@Observer
                with (sharedPref.edit()) {
                    putString(getString(R.string.survey_angle_pref), angle.toInt().toString())
                    apply()
                }
            }
        } )

        activityViewModel.lineDistanceProgress.observe(this.viewLifecycleOwner, Observer { distance ->
            if(activityViewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams)
            {
                Log.i(Log.INFO.toString(),
                    "distance call distance: " + distance + "   angle: " +
                            activityViewModel.angleProgress.value!!.toInt())

                this.run{ drawSurveyMissionOnMap(distance, activityViewModel.angleProgress.value!!.toInt(), mMap!!) }

                val sharedPref = PreferenceManager.getDefaultSharedPreferences(requireActivity().applicationContext) ?: return@Observer
                with (sharedPref.edit()) {
                    putString(getString(R.string.survey_line_distance_pref), distance.toInt().toString())
                    apply()
                }
            }
        } )

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
            if (mapState == MainActivityViewModel.MapState.Idle)
            {
                activityViewModel.drawEnableLiveData.value = false

                if( mMap != null )
                    droneViewModel.mavlinkCommunicationLiveData.value?.
                        downloadMission()
            }

            // Reset: clear drawings and go to Idle State
            else if (mapState == MainActivityViewModel.MapState.Reset)
            {
                activityViewModel.area.value!!.clearDrawings()
                activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
            }

            // Clear: clear drawings and go to Draw State
            else if (mapState == MainActivityViewModel.MapState.ClearAll)
            {
                activityViewModel.area.value!!.clearDrawings()
                activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Draw)
            }

            else if(mapState == MainActivityViewModel.MapState.ClearKeepDrawing)
            {
                activityViewModel.area.value!!.clearSurveyPath()
                activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Draw)
            }

            // Draw: enable drawing
            else if (mapState == MainActivityViewModel.MapState.Draw)
            {
                activityViewModel.drawEnableLiveData.value = true
            }

            // set flight parameters state. set angle, line distance, height
            // throws popup window to set the flight parameters
            else if (mapState == MainActivityViewModel.MapState.SetFlightParams)
            {
                activityViewModel.drawEnableLiveData.value = false

                Log.i(Log.INFO.toString(),
                    "generic distance: " + activityViewModel.lineDistanceProgress.value!! + "   angle: " +
                            activityViewModel.angleProgress.value!!.toInt())

                this.run{ drawSurveyMissionOnMap(activityViewModel.lineDistanceProgress.value!!,
                    activityViewModel.angleProgress.value!!.toInt(), mMap!!) }

                initMissionParams()
            }
            else if( mapState == MainActivityViewModel.MapState.LoadMissionFromFile )
            {
                fileLoaderDialog()
            }
        }

        droneViewModel.missionItems.observe(this.viewLifecycleOwner) { missionItems ->

            if( droneViewModel.conStateLiveData.value!! && missionItems.size > 0 )
            {
                activityViewModel.area.value!!.clearSurveyPath()

                survey?.clearMarkers()
                survey = Survey(activityViewModel.area.value!!, requireActivity())

                val surveyPath = ArrayList<LatLng>()
                for(item in missionItems)
                {
                    if( item.seq() > 0 && item.command().entry() == MavCmd.MAV_CMD_NAV_WAYPOINT )
                        surveyPath.add( LatLng(item.x() * 10e-8, item.y() * 10e-8) )
                }

                activityViewModel.area.value!!.surveyPath = surveyPath
                mMap?.let { activityViewModel.area.value!!.surveyPolylineOptions(it) }
            }
        }

        droneViewModel.conStateLiveData.observe(viewLifecycleOwner) { connState ->

            if (connState) {
                Log.i("connState","drone connected!")
                droneViewModel.mavlinkCommunicationLiveData.value?.downloadMission()

            } else {
                Log.i("connState","drone disconnected!")
                activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Reset)
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
        val bottomNavigationView : BottomNavigationView? =
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
    }

    private fun drawSurveyMissionOnMap(distance : Double, angle : Int, map: GoogleMap)
    {
        activityViewModel.area.value!!.clearSurveyPath()

        survey?.clearMarkers()
        survey = Survey(activityViewModel.area.value!!, requireActivity())
        activityViewModel.area.value!!.surveyPath =
                survey?.createSurveyPath(distance, angle, this.context, map)!!

        if(activityViewModel.area.value!!.surveyPath.size == 0) {
            activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Draw)
        }
        else {
            mMap?.let { activityViewModel.area.value!!.surveyPolylineOptions(it) }
        }
    }


    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    // [START maps_current_place_update_location_ui]
    private fun zoomToCurrentLocation()
    {
        if (activity?.let {
                ContextCompat.checkSelfPermission(
                    it.applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION)
            } != PackageManager.PERMISSION_GRANTED)
        {
            Toast.makeText(this.context, getString(R.string.no_permissions_msg), Toast.LENGTH_LONG).show()
            return
        }

        // zoom to drone or to user location
        val location : Location? = getLastKnownLocation()
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


    private fun zoomToDroneLocation()
    {
        // zoom to drone or to user location
        val location : Location? = droneViewModel.droneLocationLiveData.value
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
    private val callback = OnMapReadyCallback { googleMap ->
        mMap = googleMap

        if (activity?.let {
                ContextCompat.checkSelfPermission(
                    it.applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION)
            } == PackageManager.PERMISSION_GRANTED)
        {
            mMap?.isMyLocationEnabled = true
            mMap?.uiSettings?.isMyLocationButtonEnabled = false

            zoomToCurrentLocation()

            droneMarker = mMap?.addMarker(
                            MarkerOptions()
                                .visible(false)
                                .position(LatLng(0.0, 0.0))
                                .anchor(0.5F, 0.5F)
                                .icon(context?.let { bitmapDescriptorFromVector(it, R.drawable.drone_marker_36) })
            )!!

            mMap?.setOnMapClickListener(this)
            mMap?.setOnMarkerClickListener(this)
            mMap?.setOnMarkerDragListener(this)
        }
        else
            Toast.makeText(activity?.applicationContext, getString(R.string.invalid_location_permissions), Toast.LENGTH_LONG)
    }

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        return ContextCompat.getDrawable(context, vectorResId)?.run {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
            draw(Canvas(bitmap))
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

//    private fun getBitmapFromTextView(layoutId: Int): BitmapDescriptor {
//        val view =
//            layoutInflater.inflate(layoutId, null) as LinearLayout
//
//        val dist = view.findViewById<View>(R.id.lineDistance) as TextView
//        dist.text = "10m"
//
//        val bitmap = Bitmap.createBitmap(
//            view.width, view.height, Bitmap.Config.ARGB_8888
//        )
//        view.draw(Canvas(bitmap))
//
//        return BitmapDescriptorFactory.fromBitmap(bitmap)
//    }


    override fun onMapClick(p0: LatLng) {
        if(activityViewModel.drawEnableLiveData.value == true)
        {
            val marker = mMap?.addMarker(MarkerOptions().anchor(0.5f, 0.5f)
                    .position(p0)
                    .icon(context?.let { bitmapDescriptorFromVector(it, R.drawable.ic_baseline_mission_marker) })
                    .draggable(true)
                )!!

            // add marker to location clicked
            activityViewModel.area.value!!.latLngArrayListMarkers.add(marker)

            activityViewModel.area.value!!.addEdgeToPolygon(marker, mMap!!, requireContext())


//            for (i in 0 until polygonEdges.size)
//            {
//                if( i < polygonEdges.size )
//                {
//                    val midpoint = midPoint(polygonEdges[i],
//                        polygonEdges[(i+1) % polygonEdges.size])
//                    val distance = SphericalUtil.computeDistanceBetween(polygonEdges[i],
//                        polygonEdges[(i+1) % polygonEdges.size])
//
//                    val iconFactory = IconGenerator(context)
//                    val marker3 = mMap?.addMarker(MarkerOptions().anchor(0.5f, 0.5f)
//                        .position(p0)
//                        .icon(BitmapDescriptorFactory.fromBitmap(iconFactory.makeIcon("Marker 3")))
//                        .title("test")
//                    )!!
//                    latLngArrayListMarkers.add()
//                }
//            }
        }
    }


    override fun onMarkerClick(p0: Marker): Boolean {

        val index = activityViewModel.area.value!!.latLngArrayListMarkers.indexOf(p0)

        if( index != -1 && activityViewModel.drawEnableLiveData.value == true)
        {
            mMap?.let { activityViewModel.area.value!!.removeEdgeFromPolygon(p0, it, requireContext()) }
            return true
        }

        return false
    }

    override fun onMarkerDrag(p0: Marker) { }

    override fun onMarkerDragEnd(p0: Marker) {
        activityViewModel.area.value!!.adjustEdgeToPolygon(p0, mMap!!, requireActivity())
    }

    override fun onMarkerDragStart(p0: Marker) { }


    private fun fileLoaderDialog()
    {
        val loadFileView = requireActivity().
            findViewById<LinearLayoutCompat>(R.id.load_file_selector_layout)

        // Set the directory path where the ".waypoint" files are located
        val directory = context?.getString(
            R.string.mission_directory)?.let { File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), it) }

        // Get a list of all files in the directory that end with ".waypoint"
        val files = directory?.listFiles { _, name ->
            name.lowercase(Locale.ROOT).endsWith(getString(R.string.DroneServicesFilePageSuffix))
        }

        if( files == null )
        {
            Toast.makeText(context, "Error in loading missions from directory", Toast.LENGTH_LONG).show()
        }
        else if(files.isEmpty())
        {
            Toast.makeText(context, "No missions saved yet", Toast.LENGTH_LONG).show()
        }
        else
        {
            // Extract file names from the list of files
            val fileNames = files.map { it.name.substring(0, it.name.lastIndexOf('.')) }

            val listView = requireActivity().findViewById(R.id.file_list) as ListView

            val itemList = mutableListOf<String>()
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, itemList)

            adapter.addAll(fileNames)
            adapter.notifyDataSetChanged()
            listView.adapter = adapter

            loadFileView.isVisible = true

            listView.setOnItemClickListener { _, _, position, _ ->

                Log.d("File Selector","Position Selected $position" )
                Log.d("File Selector","File Selected ${itemList[position]}" )
                Log.d("File Selector","File Path Selected ${files[position].path}" )

                MissionFileHandler(requireActivity(), activityViewModel).
                parseXml(FileInputStream( files[position].path ))

                Toast.makeText(context, "Selected file: ${files[position].path}", Toast.LENGTH_SHORT).show()

                loadFileView.isVisible = false
            }
        }

        requireActivity().findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            loadFileView.isVisible = false
            activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
        }
    }



    @SuppressLint("CheckResult")
    private fun initMissionParams() {
        val minSpeed = 1
        val maxSpeed = 5

        getWindowPreferences()

        val missionParamsSideView =
            activity?.findViewById<LinearLayoutCompat>(R.id.mission_params_side_view)!!
        missionParamsSideView.isVisible = true

        val flightTimeText = activity?.findViewById<TextView>(R.id.flight_time)
        val flightSpeedView = activity?.findViewById<TextView>(R.id.flight_speed)

        flightSpeedView?.text = activityViewModel.flightSpeed.value?.toInt().toString()
        activityViewModel.flightSpeed.observe(viewLifecycleOwner) { flightSpeed ->
            flightSpeedView?.text = flightSpeed.toInt().toString()

            // surveyDistance in m / ( flightSpeed in m/sec * 60 sec/min ) =>
            //  m / ( m/sec * sec/min ) => m / (m / min) => min
            val time =
                ( activityViewModel.flightDistance.value!! / ( flightSpeed * 60) ).toInt()
            if(time > 0 )
                flightTimeText?.text  = time.toString()
            else
                flightTimeText?.text  = "1"
        }

        activityViewModel.flightDistance.observe(viewLifecycleOwner) { distance ->

            // surveyDistance in m / ( flightSpeed in m/sec * 60 sec/min ) =>
            //  m / ( m/sec * sec/min ) => m / (m / min) => min
            val time = ( distance / (activityViewModel.flightSpeed.value!! * 60) ).toInt()
            if(time > 0 )
                flightTimeText?.text  = time.toString()
            else
                flightTimeText?.text  = "1"
        }

        activityViewModel.mapState.observe(viewLifecycleOwner){ mapState ->
            if( mapState == MainActivityViewModel.MapState.UploadMissionSuccess )
            {
                activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Reset)

                missionParamsSideView.isVisible = false
            }
        }

        val angleSeekBarValue = activity?.findViewById<EditText>(R.id.line_angle_value)
        val angleSeekBar = activity?.findViewById<SeekBar>(R.id.line_angle_seekbar)
        if ( angleSeekBar != null && angleSeekBarValue != null ) {
            initSeekbar(angleSeekBar, angleSeekBarValue, activityViewModel.angleProgress)
        }

        val lineDistanceSeekBarValue = activity?.findViewById<EditText>(R.id.line_distance_value)
        val lineDistanceSeekBar = activity?.findViewById<SeekBar>(R.id.line_distance_seekbar)
        if ( lineDistanceSeekBar != null && lineDistanceSeekBarValue != null ) {
            initSeekbar(lineDistanceSeekBar, lineDistanceSeekBarValue, activityViewModel.lineDistanceProgress)
        }

        val altitudeSeekBarValue = activity?.findViewById<EditText>(R.id.altitude_value)
        val altitudeSeekBar = activity?.findViewById<SeekBar>(R.id.altitude_seekbar)
        if ( altitudeSeekBar != null && altitudeSeekBarValue != null ) {
            initSeekbar(altitudeSeekBar, altitudeSeekBarValue, activityViewModel.flightAltProgress)
        }

        val sprayerSeekbarValue = activity?.findViewById<EditText>(R.id.sprayer_seekbar_value)
        val sprayerSeekBar = activity?.findViewById<SeekBar>(R.id.sprayer_seekbar)
        if ( sprayerSeekBar != null && sprayerSeekbarValue != null ) {
            initSeekbar(sprayerSeekBar, sprayerSeekbarValue, activityViewModel.sprayerProgress)
        }

        val buttonMinus = activity?.findViewById<Button>(R.id.minus_button)
        buttonMinus?.setOnClickListener{
            if(activityViewModel.flightSpeed.value!!.toInt() > minSpeed)
            {
                activityViewModel.flightSpeed.postValue(activityViewModel.flightSpeed.value!!.toDouble() - 1)
            }
        }

        val buttonPlus = activity?.findViewById<Button>(R.id.plus_button)
        buttonPlus?.setOnClickListener{
            if(activityViewModel.flightSpeed.value!!.toInt() < maxSpeed)
            {
                activityViewModel.flightSpeed.postValue(activityViewModel.flightSpeed.value!!.toDouble() + 1)
            }
        }

        //Initialize the elements of our window, install the handler
        val buttonUploadMission = activity?.findViewById<Button>(R.id.uploadMission)
        buttonUploadMission?.setOnClickListener { //As an example, display the message
            if(droneViewModel.conStateLiveData.value == null || !droneViewModel.conStateLiveData.value!!)
            {
                Toast.makeText(
                    activity?.baseContext,
                    activity?.getString(R.string.no_conn_msg),
                    Toast.LENGTH_LONG
                ).show()
            }
            else if ( activityViewModel.flightAltProgress.value == null )
            {
                Toast.makeText(
                    activity?.baseContext,
                    activity?.getString(R.string.select_alt_msg),
                    Toast.LENGTH_LONG
                ).show()
            }
            else
            {

//                droneViewModel.mavlinkCommunicationLiveData.value?.clearMission()
//                    ?.subscribeOn(Schedulers.io()) // Optional: specify the scheduler for the operation
//                    ?.observeOn(AndroidSchedulers.mainThread()) // Optional: specify the scheduler for the result handling
//                    ?.subscribe(
//                        { result ->
//                            // Handle the success result
//                            Log.i("clearMission", "Result: $result")
//                            Toast.makeText(context, "Mission clear success!", Toast.LENGTH_LONG).show()
//                        },
//                        { error ->
//                            // Handle any errors that may occur during the execution of clearMission()
//                            Log.e("clearMission", "Error: ${error.message}")
//                            Toast.makeText(context, "Mission clear failed!", Toast.LENGTH_LONG).show()
//                        }
//                    )


                    val missionItems =
                        droneViewModel.mavlinkCommunicationLiveData.value
                            ?.setupMission(
                                activityViewModel.area.value!!.surveyPath,
                                droneViewModel.droneLocationLiveData.value!!,
                                activityViewModel.flightAltProgress.value!!.toFloat(),
                                activityViewModel.sprayerProgress.value!!.toInt(),
                                activity?.baseContext!!
                            )!!

                    droneViewModel.mavlinkCommunicationLiveData.value?.uploadMission(missionItems)

                    missionParamsSideView.isVisible = false
            }

            setWindowPreferences()
        }

        val buttonExit = activity?.findViewById<Button>(R.id.exit)
        buttonExit?.setOnClickListener {
            setWindowPreferences()
            activityViewModel.mapState.postValue(MainActivityViewModel.MapState.ClearKeepDrawing)

            missionParamsSideView.isVisible = false
        }

        val buttonSaveMission = activity?.findViewById<Button>(R.id.save_mission)
        buttonSaveMission?.setOnClickListener {

            missionParamsSideView.isVisible = false
            initMissionSave()
        }
    }


    private fun initSeekbar(seekbar: SeekBar, seekbarValue: EditText, mutable: MutableLiveData<Double>)
    {
        seekbar.progress = mutable.value!!.toInt()
        seekbarValue.setText("${mutable.value!!.toInt()}")

        seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // Display the current progress of SeekBar
                seekbarValue.setText("$progress")
                mutable.postValue(progress.toDouble())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) { }

            override fun onStopTrackingTouch(seekBar: SeekBar) { }
        })

        seekbarValue.addTextChangedListener(object: TextWatcher {

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                seekbarValue.setSelection(seekbarValue.length())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }

            override fun afterTextChanged(s: Editable?) {
                try {
                    val progress = s.toString().toInt()
                    seekbar.setProgress(progress, true)
                }
                catch (e : NumberFormatException) {
                    Log.d("SeekBar Mission Params","Error on casting String to Int on TextChange " )
                }
            }
        })
    }

    private fun getWindowPreferences()
    {
        getSharedPreferenceById(R.string.survey_angle_pref, activityViewModel.angleProgress, "0")
        getSharedPreferenceById(R.string.survey_line_distance_pref, activityViewModel.lineDistanceProgress, "1")
        getSharedPreferenceById(R.string.survey_altitude_pref, activityViewModel.flightAltProgress, "2")
        getSharedPreferenceById(R.string.sprayer_intensity, activityViewModel.sprayerProgress, "0")
        getSharedPreferenceById(R.string.flight_speed_pref, activityViewModel.flightSpeed, "1")
    }

    private fun setWindowPreferences()
    {
        setSharedPreferenceById(R.id.survey_angle_pref, activityViewModel.angleProgress.value!!.toInt())
        setSharedPreferenceById(R.id.survey_line_dist_pos_pref, activityViewModel.lineDistanceProgress.value!!.toInt())
        setSharedPreferenceById(R.id.mission_alt_pref, activityViewModel.flightAltProgress.value!!.toInt())
        setSharedPreferenceById(R.id.sprayer_intensity_pref, activityViewModel.sprayerProgress.value!!.toInt())
        setSharedPreferenceById(R.id.flight_speed_pref, activityViewModel.flightSpeed.value!!.toInt())
    }

    private fun getSharedPreferenceById(stringResourceId: Int, mutable : MutableLiveData<Double>, defValue : String)
    {
        val prefs = activity?.let { PreferenceManager.getDefaultSharedPreferences(it.applicationContext) }

        mutable.postValue(
            prefs?.getString(activity?.getString(stringResourceId), defValue)?.toDouble() )
    }

    private fun setSharedPreferenceById(id: Int, value : Int)
    {
        val prefs = activity?.let { PreferenceManager.getDefaultSharedPreferences(it.applicationContext) }
        val editor = prefs?.edit()
        if (editor != null) {
            editor.putString(
                com.example.droneservicesapp.Application.getInstance().applicationContext.getString(id),
                value.toString()
            )

            editor.apply()
        }
    }


    //PopupWindow display method
    private fun initMissionSave() {

        var overrideFile = false
        Log.i("MissionSave", "initiated Mission Save Layout")

        val saveFileView = requireActivity().findViewById<LinearLayoutCompat>(R.id.save_file_layout)!!
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

            if( inputFilename.text.isBlank() )
            {
                Toast.makeText(
                    requireActivity().baseContext,
                    requireActivity().baseContext?.getString(R.string.error_name_empty), Toast.LENGTH_LONG).show()
            }
            else
            {
                Log.i("MissionSave", "activityViewModel.area.value!!.polygonEdges, ${activityViewModel.area.value!!.polygonEdges}")
                Log.i("MissionSave", "activityViewModel.lineDistanceProgress, ${activityViewModel.lineDistanceProgress.value!!.toInt()}")
                Log.i("MissionSave", "activityViewModel.angleProgress, ${activityViewModel.angleProgress.value!!.toInt()}")
                Log.i("MissionSave", "activityViewModel.flightAltProgress, ${activityViewModel.flightAltProgress.value!!.toInt()}")
                Log.i("MissionSave", "activityViewModel.sprayerProgress, ${activityViewModel.sprayerProgress.value!!.toInt()}")

                val isSaved = MissionFileHandler(requireActivity(), activityViewModel).
                    saveMissionXML(
                        activityViewModel.area.value!!.polygonEdges,
                        activityViewModel.lineDistanceProgress.value!!.toInt(),
                        activityViewModel.angleProgress.value!!.toInt(),
                        activityViewModel.flightAltProgress.value!!.toInt(),
                        activityViewModel.sprayerProgress.value!!.toInt(),
                        inputFilename.text.toString(),
                        overrideFile)

                if( isSaved )
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





