package com.example.droneservicesapp.ui.home.components

import android.content.Context
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.MissionObstacle
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.terrain.TerrainWaypoint
import com.example.droneservicesapp.ui.home.ObstaclePanelState
import com.example.droneservicesapp.ui.home.WaypointEditDockState
import com.example.droneservicesapp.ui.home.map.MissionMapLayers
import com.example.droneservicesapp.ui.home.map.ObstacleEditorState
import com.example.droneservicesapp.ui.map.model.MapCoordinate
import com.example.droneservicesapp.ui.map.model.MapScreenPoint
import com.example.droneservicesapp.ui.preview.PreviewMode
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import java.util.Locale

/** Coordinates polygon, route, obstacle, and waypoint editor selection transitions. */
class MissionEditorCoordinator(
    private val context: Context,
    private val viewModel: MainActivityViewModel,
    private val layers: MissionMapLayers,
    private val dock: WaypointEditDockState,
    private val obstaclePanel: ObstaclePanelState,
    private val terrainSampler: (LatLng) -> TerrainWaypoint?,
    private val shouldReversePointRoute: (List<TerrainWaypoint>) -> Boolean,
    private val updateDistance: (List<LatLng>) -> Unit,
    private val renderPath: () -> Unit,
    private val updatePointCloudOverlay: () -> Unit,
    private val showMessage: (Int) -> Unit,
    private val showFormattedMessage: (String) -> Unit,
) {
    var selectedIndex: Int? = null
        private set
    var selectedWorkflow: PlanningWorkflow = PlanningWorkflow.AREA
        private set
    private var obstaclePlacementActive = false
    private var obstacleMode = ObstacleEditorState.Mode.CIRCLE

    fun toggleObstaclePlacement(previewMode: PreviewMode, selectMapPreview: () -> Unit) {
        if (obstacleMode == ObstacleEditorState.Mode.POLYGON && obstaclePlacementActive) {
            if (layers.obstacles.finishPolygon()) obstaclePlacementActive = false
        } else {
            if (previewMode == PreviewMode.POINT_CLOUD) selectMapPreview()
            obstaclePlacementActive = true
            if (obstacleMode == ObstacleEditorState.Mode.CIRCLE) {
                layers.obstacles.startCirclePlacement(viewModel.obstacleRadiusMeters.value ?: 5.0)
                showMessage(R.string.obstacle_place_circle_prompt)
            } else {
                layers.obstacles.startPolygonPlacement()
                showMessage(R.string.obstacle_place_polygon_prompt)
            }
            layers.polygon.setEnabled(false)
        }
        renderObstacleControls()
    }

    fun clearObstacles() {
        cancelObstaclePlacement(render = false)
        viewModel.clearMissionObstacles()
        renderObstacleControls()
    }

    fun selectObstacleMode(mode: ObstacleEditorState.Mode) {
        obstacleMode = mode
        cancelObstaclePlacement(render = false)
        renderObstacleControls()
    }

    fun cancelObstaclePlacement(render: Boolean = true) {
        obstaclePlacementActive = false
        layers.obstacles.cancelPlacement()
        if (render) renderObstacleControls()
    }

    fun onObstaclesChanged(obstacles: List<MissionObstacle>) {
        if (obstacleMode == ObstacleEditorState.Mode.CIRCLE) {
            cancelObstaclePlacement(render = false)
        }
        layers.obstacles.renderObstacles(obstacles)
        renderObstacleControls()
    }

    fun renderObstacleControls() {
        obstaclePanel.mode = obstacleMode
        obstaclePanel.placementActive = obstaclePlacementActive
        obstaclePanel.radiusMeters = (viewModel.obstacleRadiusMeters.value ?: 5.0).toInt().coerceIn(2, 100)
    }

    fun syncPolygonVertices(vertices: List<LatLng>) = layers.polygon.setVertices(vertices)

    fun syncRouteWaypoints() {
        val terrainPath = viewModel.terrainRouteWaypoints.value.orEmpty()
        layers.route.setWaypoints(
            viewModel.routeWaypoints.value.orEmpty(),
            plannedPath = viewModel.plannedRoutePath.value.orEmpty(),
            terrainPath = terrainPath,
            reverseDirection = shouldReversePointRoute(terrainPath),
        )
    }

    fun updatePolygonEnabled(isDrawingEnabled: Boolean, homePlacementActive: Boolean) {
        layers.polygon.setEnabled(
            isDrawingEnabled &&
                viewModel.activePlanningWorkflow.value != PlanningWorkflow.POINTS &&
                !obstaclePlacementActive &&
                !homePlacementActive,
        )
    }

    fun updateRouteEnabled(homePlacementActive: Boolean) {
        val points = viewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS
        val state = viewModel.mapState.value
        val enabled = points &&
            state in setOf(MainActivityViewModel.MapState.Draw, MainActivityViewModel.MapState.SetFlightParams) &&
            !homePlacementActive
        layers.route.setEnabled(enabled)
        layers.route.setAddingEnabled(enabled && state == MainActivityViewModel.MapState.Draw)
    }

    fun updateSurveyEnabled() {
        val enabled = viewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams &&
            viewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA &&
            viewModel.surveyPath.value.orEmpty().isNotEmpty()
        layers.survey.setEditingEnabled(enabled)
        if (!enabled) select(PlanningWorkflow.AREA, null, PreviewMode.MAP)
    }

    fun selectSurvey(index: Int?) = select(PlanningWorkflow.AREA, index, PreviewMode.MAP)

    fun select(workflow: PlanningWorkflow, index: Int?, previewMode: PreviewMode) {
        selectedWorkflow = workflow
        selectedIndex = index
        dock.visible = index != null
        updateHeightLabel()
        updatePointCloudOverlay()
        if (index == null || previewMode == PreviewMode.POINT_CLOUD) return
        val point = if (workflow == PlanningWorkflow.POINTS) {
            viewModel.terrainRouteWaypoints.value.orEmpty().getOrNull(index)?.latLon
        } else {
            viewModel.terrainSurveyWaypoints.value.orEmpty().getOrNull(index)?.latLon
                ?: viewModel.surveyPath.value.orEmpty().getOrNull(index)?.let { LatLon(it.latitude, it.longitude) }
        }
        point?.let(::positionNearMapPoint)
        if (workflow == PlanningWorkflow.AREA) {
            showFormattedMessage(context.getString(R.string.survey_waypoint_selected, index + 1))
        }
    }

    fun moveSurvey(index: Int, point: LatLng) {
        viewModel.updateSurveyWaypoint(index, point, terrainSampler(point))
        updateDistance(viewModel.surveyPath.value.orEmpty())
        renderPath()
    }

    fun adjustHeight(deltaMeters: Double) {
        val index = selectedIndex ?: return
        val altitude = altitude(index) ?: return
        if (selectedWorkflow == PlanningWorkflow.POINTS) {
            viewModel.updateTerrainRouteWaypointAltitude(index, altitude + deltaMeters)
        } else {
            viewModel.updateSurveyWaypointAltitude(index, altitude + deltaMeters)
        }
        refreshAfterHeightChange()
    }

    fun applyHeightField() {
        val index = selectedIndex ?: return
        val altitude = dock.heightText.trim().toDoubleOrNull() ?: return updateHeightLabel()
        if (selectedWorkflow == PlanningWorkflow.POINTS) {
            viewModel.updateTerrainRouteWaypointAltitude(index, altitude)
        } else {
            viewModel.updateSurveyWaypointAltitude(index, altitude)
        }
        refreshAfterHeightChange()
    }

    fun refreshHeightLabel() = updateHeightLabel()

    fun deleteSelected(onPointRouteChanged: () -> Unit) {
        val index = selectedIndex ?: return
        if (selectedWorkflow == PlanningWorkflow.POINTS) {
            viewModel.removeTerrainRouteWaypoint(index)
            layers.route.clearTerrainWaypointSelection()
            onPointRouteChanged()
        } else {
            viewModel.removeSurveyWaypoint(index)
            layers.survey.clearSelectedWaypoint()
            updateDistance(viewModel.surveyPath.value.orEmpty())
            renderPath()
        }
        showMessage(R.string.survey_waypoint_deleted)
    }

    fun cancelSelection() {
        layers.survey.clearSelectedWaypoint()
        layers.route.clearTerrainWaypointSelection()
        select(selectedWorkflow, null, PreviewMode.MAP)
    }

    /** Places the dock beside a point, in the screen coordinates of the map/3D surface. */
    fun positionNearScreenPoint(x: Float, y: Float) {
        dock.anchor = MapScreenPoint(x, y)
    }

    private fun refreshAfterHeightChange() {
        updateHeightLabel()
        updatePointCloudOverlay()
        renderPath()
    }

    private fun updateHeightLabel() {
        dock.heightText = String.format(Locale.US, "%.1f", selectedIndex?.let(::altitude) ?: 0.0)
    }

    private fun altitude(index: Int): Double? {
        if (selectedWorkflow == PlanningWorkflow.POINTS) {
            return viewModel.terrainRouteWaypoints.value.orEmpty().getOrNull(index)?.missionAltitudeMeters
        }
        viewModel.terrainSurveyWaypoints.value.orEmpty().getOrNull(index)?.let { return it.missionAltitudeMeters }
        if (index !in viewModel.surveyPath.value.orEmpty().indices) return null
        return if (viewModel.planningOperationMode.value == PlanningOperationMode.SPRAY) {
            viewModel.flightAltProgress.value ?: 0.0
        } else {
            viewModel.surveyHeightAboveTerrain.value ?: 0.0
        }
    }

    private fun positionNearMapPoint(point: LatLon) {
        val coordinate = runCatching { MapCoordinate(point.lat, point.lon) }.getOrNull() ?: return
        val screen = layers.surface?.toScreenPoint(coordinate) ?: return
        positionNearScreenPoint(screen.x, screen.y)
    }
}
