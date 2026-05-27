package com.example.droneservicesapp.ui.shell

import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.droneservicesapp.Application
import com.example.droneservicesapp.R
import com.example.droneservicesapp.databinding.ActivityMainBinding
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.home.binders.HomeTelemetryCoordinator
import com.example.droneservicesapp.ui.home.model.HomeTelemetryViewModel
import com.example.droneservicesapp.ui.shell.binders.ShellBottomNavBinder
import com.example.droneservicesapp.ui.shell.binders.ShellToolbarBinder
import com.example.droneservicesapp.ui.shell.coordinators.LocationPermissionRequester
import com.example.droneservicesapp.ui.shell.coordinators.MavlinkSessionCoordinator
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private lateinit var droneViewModel: DroneViewModel
    private lateinit var activityViewModel: MainActivityViewModel
    private lateinit var homeTelemetryViewModel: HomeTelemetryViewModel
    private lateinit var toolbarBinder: ShellToolbarBinder
    private lateinit var bottomNavBinder: ShellBottomNavBinder
    private lateinit var homeTelemetryCoordinator: HomeTelemetryCoordinator
    private lateinit var mavlinkSessionCoordinator: MavlinkSessionCoordinator
    private lateinit var locationPermissionRequester: LocationPermissionRequester

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_DroneServicesApp_NoActionBar)
        super.onCreate(savedInstanceState)

        Application.getInstance().initAppLanguage(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureSystemUi()

        droneViewModel = ViewModelProvider(this)[DroneViewModel::class.java]
        activityViewModel = ViewModelProvider(this)[MainActivityViewModel::class.java]
        homeTelemetryViewModel = ViewModelProvider(this)[HomeTelemetryViewModel::class.java]

        homeTelemetryCoordinator = HomeTelemetryCoordinator(
            activity = this,
            droneViewModel = droneViewModel,
            homeTelemetryViewModel = homeTelemetryViewModel
        )
        homeTelemetryCoordinator.bind(this)

        toolbarBinder = ShellToolbarBinder(this, binding, homeTelemetryViewModel)
        toolbarBinder.bind(this)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_maps_home, R.id.nav_settings, R.id.nav_rtk, R.id.nav_geo_awareness, R.id.nav_debug),
            drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        bottomNavBinder = ShellBottomNavBinder(
            activity = this,
            bottomNavigationView = findViewById(R.id.bottom_nav_view),
            activityViewModel = activityViewModel
        )
        mavlinkSessionCoordinator = MavlinkSessionCoordinator(
            context = applicationContext,
            droneViewModel = droneViewModel
        )
        locationPermissionRequester = LocationPermissionRequester(this)

        bottomNavBinder.bind(this)
        locationPermissionRequester.requestIfNeeded()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun configureSystemUi() {
        binding.root.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

    override fun onResume() {
        super.onResume()
        mavlinkSessionCoordinator.onResume()
    }

    override fun onPause() {
        super.onPause()
        mavlinkSessionCoordinator.onPause()
    }
}
