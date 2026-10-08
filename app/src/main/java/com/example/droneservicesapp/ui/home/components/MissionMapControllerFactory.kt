package com.example.droneservicesapp.ui.home.components

import android.content.Context
import android.location.Location
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.preference.PreferenceManager
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.geoawareness.incident.GeoIncidentEncryptedLogStore
import com.example.droneservicesapp.data.geoawareness.incident.GeoIncidentLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.logging.OperatorFlightEventLogger
import com.example.droneservicesapp.databinding.FragmentHomeMapsBinding
import com.example.droneservicesapp.domain.planning.MissionPlanningCoordinator
import com.example.droneservicesapp.domain.planning.PointRouteCoordinator
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.home.binders.ArmUiBinder
import com.example.droneservicesapp.ui.home.binders.FlightModeUiBinder
import com.example.droneservicesapp.ui.home.binders.HomeMapChromeBinder
import com.example.droneservicesapp.ui.home.binders.HomeMapModeEffectsBinder
import com.example.droneservicesapp.ui.home.binders.HomeMapPanelsBinder
import com.example.droneservicesapp.ui.home.binders.HomeMapTelemetryBinder
import com.example.droneservicesapp.ui.home.binders.MissionLoadController
import com.example.droneservicesapp.ui.home.binders.MissionParamsController
import com.example.droneservicesapp.ui.home.binders.MissionSaveController
import com.example.droneservicesapp.ui.home.geoawareness.GeoAuthorizationSession
import com.example.droneservicesapp.ui.home.geoawareness.GeoAwarenessDatasetController
import com.example.droneservicesapp.ui.home.geoawareness.GeoAwarenessDialogController
import com.example.droneservicesapp.ui.home.geoawareness.GeoAwarenessEventTracker
import com.example.droneservicesapp.ui.home.geoawareness.GeoZoneOverlayController
import com.example.droneservicesapp.ui.home.model.HomeTelemetryViewModel
import com.example.droneservicesapp.ui.home.model.MissionMapViewModel
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.preview.OrthoOverlayController
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/** Builds the fragment's controller groups with one consistent set of view-scoped dependencies. */
class MissionMapControllerFactory(
    private val context: Context,
    private val activity: FragmentActivity,
    private val root: View,
    private val binding: FragmentHomeMapsBinding,
    private val activityViewModel: MainActivityViewModel,
    private val droneViewModel: DroneViewModel,
    private val homeTelemetryViewModel: HomeTelemetryViewModel,
    private val mapViewModel: MissionMapViewModel,
    private val previewAssets: PreviewAssetsViewModel,
    private val planner: MissionPlanningCoordinator,
) {
    data class Core(
        val uiActions: MissionMapUiActionController,
        val planningWorkflowUi: PlanningWorkflowUiController,
        val observers: MissionMapObserverCoordinator,
        val presentationObserver: MissionPresentationObserver,
        val pointRoute: PointRouteCoordinator,
        val terrainMission: TerrainMissionCoordinator,
    )

    data class Ui(
        val chrome: HomeMapChromeBinder,
        val panels: HomeMapPanelsBinder,
        val telemetry: HomeMapTelemetryBinder,
        val flightMode: FlightModeUiBinder,
        val arm: ArmUiBinder,
        val missionParams: MissionParamsController,
        val missionSave: MissionSaveController,
        val missionLoad: MissionLoadController,
        val modeEffects: HomeMapModeEffectsBinder,
    )

    data class GeoFoundation(
        val eventLogger: GeoAwarenessEventLogger,
        val operatorEventLogger: OperatorFlightEventLogger,
        val eventTracker: GeoAwarenessEventTracker,
        val dialogController: GeoAwarenessDialogController,
        val datasetController: GeoAwarenessDatasetController,
    )

    data class MapFoundation(
        val esriLayers: EsriMapLayers,
        val mapController: OsmdroidMapController,
        val droneTracking: DroneMapTrackingController,
        val polygonEditor: OsmdroidPolygonEditor,
        val routeEditor: OsmdroidRouteWaypointEditor,
        val obstacleEditor: OsmdroidObstacleEditor,
        val orthoOverlay: OrthoOverlayController,
        val terrainCoverage: TerrainCoverageOverlayController,
        val geoZoneOverlay: GeoZoneOverlayController,
    )

    fun createMapFoundation(
        mapView: MapView,
        focusOrtho: () -> Unit,
        onHomeCaptured: (com.example.droneservicesapp.domain.model.LatLon) -> Unit,
        onDisarmed: () -> Unit,
        onSurveySelected: (Int?) -> Unit,
        onSurveyMoved: (Int, LatLng) -> Unit,
        onTerrainSelected: (Int?) -> Unit,
    ): MapFoundation {
        mapView.setBuiltInZoomControls(false)
        mapView.setMultiTouchControls(true)
        val esriLayers = EsriMapLayers.install(context, mapView)
        mapView.isTilesScaledToDpi = true
        mapView.maxZoomLevel = 20.0
        mapView.controller.setZoom(DEFAULT_MAP_ZOOM)
        mapView.controller.setCenter(GeoPoint(DEFAULT_MAP_LAT, DEFAULT_MAP_LON))

        val mapController = OsmdroidMapController(context, mapView).also { it.initOverlays() }
        val droneTracking = DroneMapTrackingController(mapController, onHomeCaptured, onDisarmed)
        mapController.setSurveyWaypointEditCallbacks(onSurveySelected, onSurveyMoved)
        val polygonEditor = OsmdroidPolygonEditor(activity, activityViewModel, mapView).also { it.init() }
        val routeEditor = OsmdroidRouteWaypointEditor(context, activityViewModel, mapView).also {
            it.init()
            it.setTerrainWaypointSelectionCallback(onTerrainSelected)
        }
        val obstacleEditor = OsmdroidObstacleEditor(context, activityViewModel, mapView).also { it.init() }
        esriLayers.bringAttributionToFront()
        return MapFoundation(
            esriLayers = esriLayers,
            mapController = mapController,
            droneTracking = droneTracking,
            polygonEditor = polygonEditor,
            routeEditor = routeEditor,
            obstacleEditor = obstacleEditor,
            orthoOverlay = OrthoOverlayController(mapView, previewAssets, focusOrtho),
            terrainCoverage = TerrainCoverageOverlayController(context, mapView),
            geoZoneOverlay = GeoZoneOverlayController(context, mapView),
        )
    }

    fun createCore(
        lifecycleOwner: LifecycleOwner,
        scope: LifecycleCoroutineScope,
        isActive: () -> Boolean,
    ): Core = Core(
        uiActions = MissionMapUiActionController(root, binding),
        planningWorkflowUi = PlanningWorkflowUiController(context, root, activityViewModel),
        observers = MissionMapObserverCoordinator(lifecycleOwner, activityViewModel),
        presentationObserver = MissionPresentationObserver(lifecycleOwner),
        pointRoute = PointRouteCoordinator(scope, activityViewModel, previewAssets, planner, isActive),
        terrainMission = TerrainMissionCoordinator(context, scope, activityViewModel, previewAssets, planner),
    )

    fun createUi(
        lifecycleOwner: LifecycleOwner,
        currentDroneLocation: () -> Location?,
        beforeUploadGuard: (() -> Unit) -> Unit,
        beforeMissionUpload: () -> Unit,
        onEnterIdle: () -> Unit,
    ): Ui {
        val chrome = HomeMapChromeBinder(
            binding = binding,
            bottomActionBarViewProvider = { activity.findViewById(R.id.bottom_nav_view) },
        )
        val panels = HomeMapPanelsBinder(
            missionParamsView = root.findViewById(R.id.mission_params_side_view),
            planningPanelView = root.findViewById(R.id.planning_panel_container),
            saveMissionView = root.findViewById(R.id.save_file_layout),
            loadMissionView = root.findViewById(R.id.load_file_selector_layout),
        )
        val telemetry = HomeMapTelemetryBinder(binding.root)
        val flightMode = FlightModeUiBinder(binding.root, droneViewModel, homeTelemetryViewModel)
            .also { it.bind(lifecycleOwner) }
        val arm = ArmUiBinder(binding.root, droneViewModel).also { it.bind(lifecycleOwner) }
        root.findViewById<View>(R.id.home_obstacle_panel).apply {
            isClickable = true
            isFocusable = true
        }
        val missionParams = MissionParamsController(
            context = context,
            rootView = root,
            lifecycleOwner = lifecycleOwner,
            activityViewModel = activityViewModel,
            droneViewModel = droneViewModel,
            droneLocationProvider = currentDroneLocation,
            beforeUploadGuard = beforeUploadGuard,
            beforeMissionUpload = beforeMissionUpload,
        )
        val missionSave = MissionSaveController(activity, root, activityViewModel)
        val missionLoad = MissionLoadController(activity, root, activityViewModel)
        val modeEffects = HomeMapModeEffectsBinder(missionParams, missionSave, missionLoad, onEnterIdle)
        return Ui(chrome, panels, telemetry, flightMode, arm, missionParams, missionSave, missionLoad, modeEffects)
    }

    fun createGeoFoundation(
        authorizationSession: GeoAuthorizationSession,
        metadata: () -> GeoAwarenessEventTracker.Metadata,
        telemetry: () -> GeoAwarenessEventTracker.Telemetry,
    ): GeoFoundation {
        val eventLogger = GeoAwarenessEventLogger(context.applicationContext)
        val incidentLogger = GeoIncidentLogger(GeoIncidentEncryptedLogStore(context.applicationContext))
        return GeoFoundation(
            eventLogger = eventLogger,
            operatorEventLogger = OperatorFlightEventLogger(eventLogger),
            eventTracker = GeoAwarenessEventTracker(eventLogger, incidentLogger, metadata, telemetry),
            dialogController = GeoAwarenessDialogController(context, authorizationSession),
            datasetController = GeoAwarenessDatasetController(context),
        )
    }

    fun savePreference(key: String, value: String) {
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .putString(key, value)
            .apply()
    }

    fun persistMissionEditingPreferences() {
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .putString(
                context.getString(R.string.survey_angle_pref),
                (activityViewModel.angleProgress.value?.toInt() ?: 90).toString(),
            )
            .putString(
                context.getString(R.string.survey_line_distance_pref),
                (activityViewModel.lineDistanceProgress.value?.toInt() ?: 5).toString(),
            )
            .putString(
                context.getString(R.string.survey_altitude_pref),
                (activityViewModel.flightAltProgress.value?.toInt() ?: 0).toString(),
            )
            .putString(
                context.getString(R.string.survey_strip_spacing_pref),
                (activityViewModel.surveyStripSpacing.value?.toInt() ?: 70).toString(),
            )
            .putString(
                context.getString(R.string.survey_height_above_terrain_pref),
                (activityViewModel.surveyHeightAboveTerrain.value?.toInt() ?: 50).toString(),
            )
            .putString(
                context.getString(R.string.survey_overlap_pref),
                (activityViewModel.surveyOverlapPercent.value?.toInt() ?: 80).toString(),
            )
            .putString(
                context.getString(R.string.survey_grid_angle_pref),
                (activityViewModel.surveyGridAngle.value?.toInt() ?: 90).toString(),
            )
            .putString(
                context.getString(R.string.survey_terrain_segment_pref),
                (activityViewModel.surveyTerrainSegment.value ?: 2.5).toString(),
            )
            .putString(
                context.getString(R.string.survey_canopy_smoothing_pref),
                (activityViewModel.surveyCanopySmoothing.value?.toInt() ?: 5).toString(),
            )
            .apply()
    }

    companion object {
        private const val DEFAULT_MAP_ZOOM = 18.0
        private const val DEFAULT_MAP_LAT = 35.3643003
        private const val DEFAULT_MAP_LON = 24.4721854
    }
}
