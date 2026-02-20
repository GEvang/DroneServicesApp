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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import com.example.droneservicesapp.mavlink.MavlinkConfig
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import java.math.RoundingMode
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private lateinit var droneViewModel: DroneViewModel
    private lateinit var activityViewModel: MainActivityViewModel

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var bottomNavigationView: BottomNavigationView

    private fun restartMavlinkFromPrefs() {
        val ifaceStr = sharedPreferences.getString(
            getString(R.string.mavlink_interface_pref),
            "UDP"
        ) ?: "UDP"

        val port = sharedPreferences.getString(
            getString(R.string.mavlink_lan_port_pref),
            "14550"
        )?.toIntOrNull() ?: 14550

        val iface = runCatching { MavlinkConfig.InterfaceType.valueOf(ifaceStr.uppercase()) }
            .getOrDefault(MavlinkConfig.InterfaceType.UDP)

        val config = MavlinkConfig(interfaceType = iface, port = port)
        droneViewModel.startMavlink(config)
    }

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (
                key == getString(R.string.mavlink_lan_port_pref) ||
                key == getString(R.string.mavlink_interface_pref)
            ) {
                restartMavlinkFromPrefs()
            }
        }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefListener)

        com.example.droneservicesapp.Application.getInstance().initAppLanguage(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        setSupportActionBar(binding.appBarMain.customToolbar)
        binding.appBarMain.customToolbar.background =
            ContextCompat.getDrawable(this.baseContext, R.drawable.action_bar_bg_red)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_maps_home, R.id.nav_settings),
            drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        getLocationPermission()

        droneViewModel = ViewModelProvider(this)[DroneViewModel::class.java]
        activityViewModel = ViewModelProvider(this)[MainActivityViewModel::class.java]

        bottomNavigationView = findViewById(R.id.bottom_nav_view)

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.action_draw -> {
                    activityViewModel.area.value!!.clearDrawings()
                    activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Draw)
                }
                R.id.action_accept -> {
                    if (activityViewModel.area.value!!.polygonEdges.size < 3) {
                        Toast.makeText(
                            this,
                            getString(R.string.wrong_schema_msg),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
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

        fun applyMapStateToBottomNav(mapState: MainActivityViewModel.MapState) {
            Log.i("Map State", "Map State Changed to: $mapState")

            val menu = bottomNavigationView.menu
            fun setMenuVisibility(cancel: Boolean, accept: Boolean, erase: Boolean, draw: Boolean, load: Boolean) {
                menu.findItem(R.id.action_cancel).isVisible = cancel
                menu.findItem(R.id.action_accept).isVisible = accept
                menu.findItem(R.id.action_erase).isVisible = erase
                menu.findItem(R.id.action_draw).isVisible = draw
                menu.findItem(R.id.action_load).isVisible = load
            }

            when (mapState) {
                MainActivityViewModel.MapState.Idle,
                MainActivityViewModel.MapState.Reset -> {
                    setMenuVisibility(cancel = false, accept = false, erase = false, draw = true, load = true)
                }
                MainActivityViewModel.MapState.Draw,
                MainActivityViewModel.MapState.ClearKeepDrawing,
                MainActivityViewModel.MapState.ClearAll -> {
                    setMenuVisibility(cancel = true, accept = true, erase = true, draw = false, load = false)
                }
                MainActivityViewModel.MapState.SetFlightParams -> {
                    setMenuVisibility(cancel = true, accept = true, erase = true, draw = false, load = false)
                }
                MainActivityViewModel.MapState.LoadMissionFromFile -> {
                    setMenuVisibility(cancel = true, accept = true, erase = true, draw = false, load = false)
                }
                MainActivityViewModel.MapState.UploadMissionSuccess,
                MainActivityViewModel.MapState.SaveMissionToFile -> {
                    // no-op
                }
            }
        }

        activityViewModel.mapState.observe(this) { mapState ->
            applyMapStateToBottomNav(mapState)
        }

        droneViewModel.conStateLiveData.observe(this) { connState ->
            binding.appBarMain.customToolbar.setBackgroundResource(
                if (connState) R.drawable.action_bar_bg_green else R.drawable.action_bar_bg_red
            )
        }

        droneViewModel.droneBatteryPercentage.observe(this) { batteryPercentage ->
            if (droneViewModel.conStateLiveData.value != true) return@observe

            val df = DecimalFormat("#.##").apply { roundingMode = RoundingMode.DOWN }
            val toolbar = binding.appBarMain.customToolbar

            toolbar.findViewById<TextView>(R.id.drone_battery_percentage_text).text =
                "${df.format(batteryPercentage * 100.0F)}%"

            val imageView = toolbar.findViewById<ImageView>(R.id.drone_battery_image)
            val iconRes = when {
                batteryPercentage == -1.0F -> R.drawable.ic_baseline_battery_alert_24
                batteryPercentage >= 1.0F -> R.drawable.ic_baseline_battery_full_24
                batteryPercentage >= 0.7F -> R.drawable.ic_baseline_battery_6_bar_24
                batteryPercentage >= 0.4F -> R.drawable.ic_baseline_battery_4_bar_24
                batteryPercentage >= 0.25F -> R.drawable.ic_baseline_battery_3_bar_24
                else -> R.drawable.ic_baseline_battery_2_bar_24
            }
            imageView.setImageResource(iconRes)
        }

        droneViewModel.droneLocationLiveData.observe(this) { location ->
            if (droneViewModel.conStateLiveData.value != true) return@observe
            binding.appBarMain.customToolbar.findViewById<TextView>(R.id.drone_alt_txt).text =
                "${location.altitude.toInt()}m"
        }

        droneViewModel.liquidLevel.observe(this) { liquid_level ->
            binding.appBarMain.sprayerFlowText.findViewById<TextView>(R.id.sprayer_flow_text).text =
                "$liquid_level%"
        }

        droneViewModel.armedState.observe(this) { armedState ->
            val resId = if (armedState) R.string.armed else R.string.disarmed
            binding.appBarMain.droneArmText.findViewById<TextView>(R.id.drone_arm_text).text =
                getString(resId)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun getLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }
    }

    override fun onResume() {
        super.onResume()
        restartMavlinkFromPrefs()
    }

    override fun onPause() {
        super.onPause()
        droneViewModel.mavlinkRepository.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}
