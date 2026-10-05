package com.example.droneservicesapp.domain.planning

import androidx.lifecycle.LifecycleCoroutineScope
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.terrain.TerrainWaypoint
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Generates, orders, and terrain-samples point-workflow routes. */
class PointRouteCoordinator(
    private val scope: LifecycleCoroutineScope,
    private val viewModel: MainActivityViewModel,
    private val previewAssets: PreviewAssetsViewModel,
    private val planner: MissionPlanningCoordinator,
    private val isActive: () -> Boolean,
) {
    private var terrainJob: Job? = null

    fun cancel() {
        terrainJob?.cancel()
        terrainJob = null
    }

    fun generate() {
        cancel()
        if (viewModel.activePlanningWorkflow.value != PlanningWorkflow.POINTS) return
        val route = viewModel.routeWaypoints.value.orEmpty()
        val rawPath = route.map { LatLng(it.latitude, it.longitude) }
        val planned = buildObstacleAwareRoute(rawPath)
        if (viewModel.plannedRoutePath.value.orEmpty() != planned) viewModel.plannedRoutePath.value = planned
        val terrainModel = previewAssets.pointCloudTerrainModel
        if (route.size < 2 || terrainModel?.isGeoreferenced != true) {
            if (viewModel.terrainRouteWaypoints.value.orEmpty().isNotEmpty()) {
                viewModel.terrainRouteWaypoints.value = emptyList()
            }
            return
        }
        val ordered = orderForHome(planned).map { LatLon(it.latitude, it.longitude) }
        val height = if (viewModel.planningOperationMode.value == PlanningOperationMode.SPRAY) {
            viewModel.flightAltProgress.value ?: 0.0
        } else viewModel.surveyHeightAboveTerrain.value ?: 50.0
        val segment = viewModel.surveyTerrainSegment.value ?: 2.5
        val smoothing = viewModel.surveyCanopySmoothing.value ?: 5.0
        viewModel.terrainRouteWaypoints.value = emptyList()
        terrainJob = scope.launch {
            val sampled = withContext(Dispatchers.Default) {
                terrainModel.buildTerrainPath(ordered, height, segment, smoothing)
            }
            if (isActive() && viewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS) {
                viewModel.terrainRouteWaypoints.value = sampled
            }
        }
    }

    fun buildObstacleAwareRoute(points: List<LatLng>): List<LatLng> = planner.buildPointRoute(
        points.map { LatLon(it.latitude, it.longitude) },
        viewModel.missionObstacles.value.orEmpty(),
    ).map { LatLng(it.lat, it.lon) }

    fun orderForHome(
        path: List<LatLng>,
        home: LatLon? = viewModel.plannedHomePosition.value,
    ): List<LatLng> = planner.orderPathForHome(
        path = path.map { LatLon(it.latitude, it.longitude) },
        home = home,
        operationMode = viewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY,
    ).map { LatLng(it.lat, it.lon) }

    fun shouldReverse(terrainPath: List<TerrainWaypoint>): Boolean {
        if (terrainPath.size >= 2) return false
        val raw = viewModel.plannedRoutePath.value.orEmpty().takeIf { it.size >= 2 }
            ?: viewModel.routeWaypoints.value.orEmpty().map { LatLng(it.latitude, it.longitude) }
        return raw.size >= 2 && orderForHome(raw).first() != raw.first()
    }
}
