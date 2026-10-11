package com.example.droneservicesapp.ui.shell

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.terrain.PointCloudCoverage
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.compose.navigation.AppDestination
import com.example.droneservicesapp.ui.compose.navigation.DroneServicesApp
import com.example.droneservicesapp.ui.compose.navigation.LocationPermissionEffect
import com.example.droneservicesapp.ui.compose.navigation.ProductionDestinationContent
import com.example.droneservicesapp.ui.compose.navigation.ShellNavigationViewModel
import com.example.droneservicesapp.ui.compose.navigation.ShellNavigator
import com.example.droneservicesapp.ui.compose.theme.DroneServicesTheme
import com.example.droneservicesapp.ui.home.binders.HomeTelemetryCoordinator
import com.example.droneservicesapp.ui.home.model.HomeTelemetryUiState
import com.example.droneservicesapp.ui.home.model.HomeTelemetryViewModel
import com.example.droneservicesapp.ui.shell.coordinators.MavlinkSessionCoordinator
import com.example.droneservicesapp.ui.shell.model.BottomActionBarUiState
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.example.droneservicesapp.ui.shell.model.ShellLiveGeoStatus

class MainActivity : AppCompatActivity(), ShellNavigator {

    private lateinit var droneViewModel: DroneViewModel
    private lateinit var activityViewModel: MainActivityViewModel
    private lateinit var homeTelemetryViewModel: HomeTelemetryViewModel
    private lateinit var navigationViewModel: ShellNavigationViewModel
    private lateinit var homeTelemetryCoordinator: HomeTelemetryCoordinator
    private lateinit var mavlinkSessionCoordinator: MavlinkSessionCoordinator
    private lateinit var bottomMissionActionHandler: BottomMissionActionHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_DroneServicesApp_NoActionBar)
        super.onCreate(savedInstanceState)

        val shellBackground = ContextCompat.getColor(this, R.color.ds_color_background)
        window.setBackgroundDrawable(ColorDrawable(shellBackground))
        window.decorView.setBackgroundColor(shellBackground)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        configureSystemUi()

        droneViewModel = ViewModelProvider(this)[DroneViewModel::class.java]
        activityViewModel = ViewModelProvider(this)[MainActivityViewModel::class.java]
        homeTelemetryViewModel = ViewModelProvider(this)[HomeTelemetryViewModel::class.java]
        navigationViewModel = ViewModelProvider(this)[ShellNavigationViewModel::class.java]

        loadPlanningOperationMode()
        bindPlanningParameterPolicy()
        homeTelemetryCoordinator = HomeTelemetryCoordinator(
            activity = this,
            droneViewModel = droneViewModel,
            homeTelemetryViewModel = homeTelemetryViewModel,
        ).also { it.bind(this) }
        mavlinkSessionCoordinator = MavlinkSessionCoordinator(
            context = applicationContext,
            droneViewModel = droneViewModel,
        )
        bottomMissionActionHandler = BottomMissionActionHandler(this, activityViewModel)

        setContent {
            DroneServicesTheme {
                val telemetryState by homeTelemetryViewModel.homeTelemetryUiState.observeAsState(
                    HomeTelemetryUiState(
                        connectionText = getString(R.string.shell_status_disconnected),
                        armedText = getString(R.string.disarmed),
                    )
                )
                val mapState by activityViewModel.mapState.observeAsState(
                    MainActivityViewModel.MapState.Idle
                )
                val bottomBarVisible by activityViewModel.shellBottomActionBarVisible.observeAsState(false)
                val liveGeoStatus by activityViewModel.shellLiveGeoStatus.observeAsState(
                    ShellLiveGeoStatus(label = getString(R.string.live_geo_unknown_status))
                )

                LocationPermissionEffect()
                DroneServicesApp(
                    telemetryState = telemetryState,
                    liveGeoStatus = liveGeoStatus,
                    bottomActionState = BottomActionBarUiState.fromMapState(mapState),
                    bottomActionBarVisible = bottomBarVisible,
                    onBottomAction = bottomMissionActionHandler::handle,
                    navigationRequests = navigationViewModel.requests,
                    destinationContent = { destination ->
                        ProductionDestinationContent(
                            destination = destination,
                            droneViewModel = droneViewModel,
                            activityViewModel = activityViewModel,
                        )
                    },
                )
            }
        }
    }

    override fun navigateTo(destination: AppDestination) {
        navigationViewModel.navigateTo(destination)
    }

    override fun returnToHome() {
        navigationViewModel.returnToHome()
    }

    override fun openNavigation() {
        navigationViewModel.openNavigation()
    }

    private fun configureSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun loadPlanningOperationMode() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val value = preferences.getString(
            getString(R.string.mission_operation_mode_pref),
            PlanningOperationMode.SURVEY.name,
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
        mavlinkSessionCoordinator.onPause()
        super.onPause()
    }
}
