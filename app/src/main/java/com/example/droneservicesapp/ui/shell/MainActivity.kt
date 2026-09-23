package com.example.droneservicesapp.ui.shell

import android.os.Bundle
import android.graphics.drawable.ColorDrawable
import android.content.res.ColorStateList
import android.view.Menu
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.example.droneservicesapp.Application
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.terrain.PointCloudCoverage
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

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_DroneServicesApp_NoActionBar)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val shellBackground = ContextCompat.getColor(this, R.color.ds_color_background)
        window.setBackgroundDrawable(ColorDrawable(shellBackground))
        window.decorView.setBackgroundColor(shellBackground)
        binding.root.setBackgroundColor(shellBackground)
        // Safety: keep live aircraft telemetry visible while this activity is in the foreground.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        configureSystemUi()

        droneViewModel = ViewModelProvider(this)[DroneViewModel::class.java]
        activityViewModel = ViewModelProvider(this)[MainActivityViewModel::class.java]
        loadPlanningOperationMode()
        bindPlanningParameterPolicy()
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
            setOf(
                R.id.nav_maps_home,
                R.id.nav_settings,
                R.id.nav_mavlink_network,
                R.id.nav_datasets,
                R.id.nav_rtk,
                R.id.nav_geo_awareness,
                R.id.nav_parameters,
                R.id.nav_debug
            ),
            drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        centerToolbarNavigationButton()
        navController.addOnDestinationChangedListener { _, _, _ ->
            centerToolbarNavigationButton()
            binding.appBarMain.customToolbar.post { toolbarBinder.renderLatest() }
        }
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

    private fun centerToolbarNavigationButton() {
        val toolbar = binding.appBarMain.customToolbar
        fun applyNavigationAppearance() {
            val navigationColor = ContextCompat.getColor(this, R.color.ds_color_shell_active)
            toolbar.navigationIcon?.let { icon ->
                if (icon is DrawerArrowDrawable) icon.color = navigationColor
                val tintedIcon = DrawableCompat.wrap(icon.mutate())
                DrawableCompat.setTintList(tintedIcon, ColorStateList.valueOf(navigationColor))
                toolbar.navigationIcon = tintedIcon
            }
            for (index in 0 until toolbar.childCount) {
                val child = toolbar.getChildAt(index)
                if (child is ImageButton) {
                    val params = child.layoutParams as? Toolbar.LayoutParams ?: continue
                    params.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    params.height = toolbar.height.coerceAtLeast(
                        resources.getDimensionPixelSize(R.dimen.ds_toolbar_height)
                    )
                    child.layoutParams = params
                    child.foregroundGravity = Gravity.CENTER
                    child.imageTintList = ColorStateList.valueOf(navigationColor)
                    child.alpha = 1f
                }
            }
        }
        toolbar.post {
            applyNavigationAppearance()
            // NavigationUI may install its DrawerArrowDrawable after the first layout pass.
            toolbar.postDelayed(::applyNavigationAppearance, 250L)
            toolbar.postDelayed(::applyNavigationAppearance, 1_000L)
        }
    }

    private fun loadPlanningOperationMode() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val value = preferences.getString(
            getString(R.string.mission_operation_mode_pref),
            PlanningOperationMode.SURVEY.name
        )
        val mode = runCatching {
            PlanningOperationMode.valueOf(value.orEmpty())
        }.getOrDefault(PlanningOperationMode.SURVEY)
        activityViewModel.setPlanningOperationMode(mode)
    }

    private fun bindPlanningParameterPolicy() {
        fun applyPolicy() {
            val operationMode = activityViewModel.planningOperationMode.value
                ?: PlanningOperationMode.SURVEY
            val hasPointCloudProfile =
                activityViewModel.pointCloudCoverage.value == PointCloudCoverage.COMPLETE ||
                    activityViewModel.terrainRouteWaypoints.value.orEmpty().isNotEmpty() ||
                    activityViewModel.activeMissionUsesPointCloudProfile.value == true
            droneViewModel.applyPlanningParameterPolicy(operationMode, hasPointCloudProfile)
        }

        activityViewModel.planningOperationMode.observe(this) { applyPolicy() }
        activityViewModel.pointCloudCoverage.observe(this) { applyPolicy() }
        activityViewModel.terrainRouteWaypoints.observe(this) { applyPolicy() }
        activityViewModel.activeMissionUsesPointCloudProfile.observe(this) { applyPolicy() }
        droneViewModel.conStateLiveData.observe(this) { connected ->
            if (connected == true) applyPolicy()
        }
        applyPolicy()
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
