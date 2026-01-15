package com.example.droneservicesapp.activities

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.example.droneservicesapp.R
import com.example.droneservicesapp.databinding.ActivityMainBinding
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private lateinit var droneViewModel: DroneViewModel
    private lateinit var activityViewModel: MainActivityViewModel

    private var sharedPreferences : SharedPreferences? = null

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        com.example.droneservicesapp.Application.getInstance().initAppLanguage(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        setSupportActionBar(binding.root.findViewById(R.id.customToolbar))
        binding.appBarMain.customToolbar.background =
            ContextCompat.getDrawable(this.baseContext, R.drawable.action_bar_bg_red)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_maps_home, R.id.nav_settings, R.id.nav_test
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // ask for location permission
        getLocationPermission()

        droneViewModel = ViewModelProvider(this)[DroneViewModel::class.java]
        activityViewModel = ViewModelProvider(this)[MainActivityViewModel::class.java]

        val paramsSideView: LinearLayoutCompat =
            findViewById<LinearLayoutCompat>(R.id.mission_params_side_view)
        val saveFileView: LinearLayoutCompat =
            findViewById<LinearLayoutCompat>(R.id.save_file_layout)
        val loadFileView: LinearLayoutCompat =
            findViewById<LinearLayoutCompat>(R.id.load_file_selector_layout)
        val bottomNavigationView: BottomNavigationView =
            findViewById<BottomNavigationView>(R.id.bottom_nav_view)
        val userLocationFB: FloatingActionButton =
            findViewById<FloatingActionButton>(R.id.my_location_button)
        val droneLocationFB: FloatingActionButton =
            findViewById<FloatingActionButton>(R.id.drone_location_button)
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.action_draw -> {
                    activityViewModel.area.value!!.clearDrawings()
                    activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Draw)
                }
                R.id.action_accept -> {

                    // to get in state SetFlightParams at least 3 markers must be placed on map
                    if (activityViewModel.area.value!!.polygonEdges.size < 3)
                    {
                        Toast.makeText(
                            this,
                            getString(com.example.droneservicesapp.R.string.wrong_schema_msg),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else
                    {
                        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.SetFlightParams)
                    }
                }
                R.id.action_cancel -> {
                    activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Reset)
                }
                R.id.action_erase -> {
                    activityViewModel.mapState.postValue(MainActivityViewModel.MapState.ClearAll)
                }
                R.id.action_load -> {
                    activityViewModel.area.value!!.clearDrawings()
                    activityViewModel.mapState.postValue(MainActivityViewModel.MapState.LoadMissionFromFile)
                }
            }
            true
        }

        activityViewModel.mapState.observe(this) { mapState ->
            Log.i("Map State", "Map State Changed to: $mapState")

            when( mapState )
            {
                MainActivityViewModel.MapState.Idle -> {
                    bottomNavigationView.menu.findItem(R.id.action_cancel).isVisible = false
                    bottomNavigationView.menu.findItem(R.id.action_accept).isVisible = false
                    bottomNavigationView.menu.findItem(R.id.action_erase).isVisible = false
                    bottomNavigationView.menu.findItem(R.id.action_draw).isVisible = true
                    bottomNavigationView.menu.findItem(R.id.action_load).isVisible = true
                    paramsSideView.isVisible = false
                    saveFileView.isVisible = false
                    loadFileView.isVisible = false
                    userLocationFB.isVisible = true
                    droneLocationFB.isVisible = true
                }
                MainActivityViewModel.MapState.Reset -> {
                    bottomNavigationView.menu.findItem(R.id.action_cancel).isVisible = false
                    bottomNavigationView.menu.findItem(R.id.action_accept).isVisible = false
                    bottomNavigationView.menu.findItem(R.id.action_erase).isVisible = false
                    bottomNavigationView.menu.findItem(R.id.action_draw).isVisible = true
                    bottomNavigationView.menu.findItem(R.id.action_load).isVisible = true
                    paramsSideView.isVisible = false
                    saveFileView.isVisible = false
                    loadFileView.isVisible = false
                    userLocationFB.isVisible = true
                    droneLocationFB.isVisible = true

                    userLocationFB.isVisible = true
                    droneLocationFB.isVisible = true
                }
                MainActivityViewModel.MapState.ClearAll -> {
                    bottomNavigationView.menu.findItem(R.id.action_cancel).isVisible = true
                    bottomNavigationView.menu.findItem(R.id.action_accept).isVisible = true
                    bottomNavigationView.menu.findItem(R.id.action_erase).isVisible = true
                    bottomNavigationView.menu.findItem(R.id.action_draw).isVisible = false
                    bottomNavigationView.menu.findItem(R.id.action_load).isVisible = false
                    paramsSideView.isVisible = false
                    saveFileView.isVisible = false
                    loadFileView.isVisible = false
                    userLocationFB.isVisible = true
                    droneLocationFB.isVisible = true
                }
                MainActivityViewModel.MapState.ClearKeepDrawing -> {
                    bottomNavigationView.menu.findItem(R.id.action_cancel).isVisible = true
                    bottomNavigationView.menu.findItem(R.id.action_accept).isVisible = true
                    bottomNavigationView.menu.findItem(R.id.action_erase).isVisible = true
                    bottomNavigationView.menu.findItem(R.id.action_draw).isVisible = false
                    bottomNavigationView.menu.findItem(R.id.action_load).isVisible = false
                    paramsSideView.isVisible = false
                    saveFileView.isVisible = false
                    loadFileView.isVisible = false
                    userLocationFB.isVisible = true
                    droneLocationFB.isVisible = true
                }
                MainActivityViewModel.MapState.Draw -> {
                    bottomNavigationView.menu.findItem(R.id.action_cancel).isVisible = true
                    bottomNavigationView.menu.findItem(R.id.action_accept).isVisible = true
                    bottomNavigationView.menu.findItem(R.id.action_erase).isVisible = true
                    bottomNavigationView.menu.findItem(R.id.action_draw).isVisible = false
                    bottomNavigationView.menu.findItem(R.id.action_load).isVisible = false
                    paramsSideView.isVisible = false
                    saveFileView.isVisible = false
                    loadFileView.isVisible = false
                    userLocationFB.isVisible = true
                    droneLocationFB.isVisible = true
                }
                MainActivityViewModel.MapState.SetFlightParams -> {
                    bottomNavigationView.menu.findItem(R.id.action_cancel).isVisible = true
                    bottomNavigationView.menu.findItem(R.id.action_accept).isVisible = true
                    bottomNavigationView.menu.findItem(R.id.action_erase).isVisible = true
                    bottomNavigationView.menu.findItem(R.id.action_draw).isVisible = false
                    bottomNavigationView.menu.findItem(R.id.action_load).isVisible = false
                    paramsSideView.isVisible = true
                    saveFileView.isVisible = false
                    loadFileView.isVisible = false
                    userLocationFB.isVisible = false
                    droneLocationFB.isVisible = false
                }
                MainActivityViewModel.MapState.UploadMissionSuccess -> {

                }
                MainActivityViewModel.MapState.LoadMissionFromFile -> {
                    bottomNavigationView.menu.findItem(R.id.action_cancel).isVisible = true
                    bottomNavigationView.menu.findItem(R.id.action_accept).isVisible = true
                    bottomNavigationView.menu.findItem(R.id.action_erase).isVisible = true
                    bottomNavigationView.menu.findItem(R.id.action_draw).isVisible = false
                    bottomNavigationView.menu.findItem(R.id.action_load).isVisible = false
                    paramsSideView.isVisible = false
                    saveFileView.isVisible = false
                    loadFileView.isVisible = true
                    userLocationFB.isVisible = false
                    droneLocationFB.isVisible = false
                }
                MainActivityViewModel.MapState.SaveMissionToFile -> {

                }
            }
        }


        droneViewModel.conStateLiveData.observe(this) { connState ->
            if (connState) {
                binding.appBarMain.customToolbar.background =
                    ContextCompat.getDrawable(this.baseContext, R.drawable.action_bar_bg_green)

            } else {
                binding.appBarMain.customToolbar.background =
                    ContextCompat.getDrawable(this.baseContext, R.drawable.action_bar_bg_red)
            }
        }


        droneViewModel.droneBatteryPercentage.observe(this) { battery_percentage ->
            val df = DecimalFormat("#.##")
            df.roundingMode = RoundingMode.DOWN

            if (droneViewModel.conStateLiveData.value!!) {

                //Log.i(Log.INFO.toString(), "battery_level: $battery_level")
//                binding.appBarMain.customToolbar.
//                    findViewById<TextView>(R.id.drone_battery_voltage_text).text =
//                    "${df.format(battery_percentage * droneViewModel.droneBatteryVoltage.value!!)}V"

                binding.appBarMain.customToolbar.
                    findViewById<TextView>(R.id.drone_battery_percentage_text).text = "${df.format(droneViewModel.droneBatteryPercentage.value!! * 100.0F)}%"

                if( battery_percentage != -1.0F )
                {
                    if (battery_percentage >= 1.0F) {
                        binding.appBarMain.customToolbar.findViewById<ImageView>(R.id.drone_battery_image)
                            .setImageResource(R.drawable.ic_baseline_battery_full_24)
                    } else if (battery_percentage >= 0.7F) {
                        binding.appBarMain.customToolbar.findViewById<ImageView>(R.id.drone_battery_image)
                            .setImageResource(R.drawable.ic_baseline_battery_6_bar_24)
                    } else if (battery_percentage >= 0.55F) {
                        binding.appBarMain.customToolbar.findViewById<ImageView>(R.id.drone_battery_image)
                            .setImageResource(R.drawable.ic_baseline_battery_6_bar_24)
                    } else if (battery_percentage >= 0.4F) {
                        binding.appBarMain.customToolbar.findViewById<ImageView>(R.id.drone_battery_image)
                            .setImageResource(R.drawable.ic_baseline_battery_4_bar_24)
                    } else if (battery_percentage >= 0.25F) {
                        binding.appBarMain.customToolbar.findViewById<ImageView>(R.id.drone_battery_image)
                            .setImageResource(R.drawable.ic_baseline_battery_3_bar_24)
                    } else {
                        binding.appBarMain.customToolbar.findViewById<ImageView>(R.id.drone_battery_image)
                            .setImageResource(R.drawable.ic_baseline_battery_2_bar_24)
                    }
                }
                else {
                    binding.appBarMain.customToolbar.findViewById<ImageView>(R.id.drone_battery_image)
                        .setImageResource(R.drawable.ic_baseline_battery_alert_24)
                }
            }
        }

        droneViewModel.droneLocationLiveData.observe(this) { location ->
            if (droneViewModel.conStateLiveData.value!!)
            {
                binding.appBarMain.customToolbar.findViewById<TextView>(R.id.drone_alt_txt).text =
                    "${location.altitude.toInt()}m"
            }
        }

        droneViewModel.liquidLevel.observe(this){ liquid_level ->
            binding.appBarMain.sprayerFlowText.
                findViewById<TextView>(R.id.sprayer_flow_text).text = "$liquid_level%"
        }

//        droneViewModel.rcStatus.observe(this){ rc ->
//
//            if( rc.isAvailable && !rc.signalStrengthPercent.isNaN() &&
//                rc.signalStrengthPercent in 0.0..1.0 )
//            {
//                binding.appBarMain.rcSignalStrengthTxt.
//                    findViewById<TextView>(R.id.rc_signal_strength_txt).text =
//                        "${rc.signalStrengthPercent * 100.0}%"
//            }
//        }

//        droneViewModel.conStateLiveData.observe(this){ connState ->
//            if( connState ){
//                binding.appBarMain.droneConnectedText.
//                findViewById<TextView>(R.id.drone_connected_text).text =
//                    getString(R.string.connected)
//            }
//            else{
//                binding.appBarMain.droneConnectedText.
//                findViewById<TextView>(R.id.drone_connected_text).text =
//                    getString(R.string.disconnected)
//            }
//        }

        droneViewModel.armedState.observe(this){ armedState ->
            if( armedState ){
                binding.appBarMain.droneArmText.findViewById<TextView>(R.id.drone_arm_text).text =
                    getString(R.string.armed)
            }
            else{
                binding.appBarMain.droneArmText.findViewById<TextView>(R.id.drone_arm_text).text =
                    getString(R.string.disarmed)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)

        return true
    }


    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    /**
     * Prompts the user for permission to use the device location.
     */
    private fun getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            } else {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            }
        }
    }
    // [END maps_current_place_location_permission]

    override fun onResume() {
        super.onResume()

        val mavInterface = sharedPreferences?.getString(getString(R.string.mavlink_interface_pref) , "UDP")?.lowercase(Locale.getDefault())
        val mavPort = sharedPreferences?.getString(getString(R.string.mavlink_lan_port_pref), "14550")?.toInt()

        val connString = "$mavInterface://:$mavPort"

        //Log.d(Log.DEBUG.toString(), "connString $connString")

        if (mavPort != null) {
            droneViewModel.mavlinkCommunicationLiveData.value?.startConn(connString, mavPort)
        }
    }

    override fun onPause() {
        super.onPause()

        droneViewModel.mavlinkCommunicationLiveData.value?.stopConn()
    }








}

