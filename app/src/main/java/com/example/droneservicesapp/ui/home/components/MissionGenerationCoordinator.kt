package com.example.droneservicesapp.ui.home.components

import androidx.lifecycle.LifecycleCoroutineScope
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.planning.MissionPlanningCoordinator
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns area-generation lifecycle, stale-result protection, and survey-grid generation. */
class MissionGenerationCoordinator(
    private val scope: LifecycleCoroutineScope,
    private val viewModel: MainActivityViewModel,
    private val planner: MissionPlanningCoordinator,
    private val isActive: () -> Boolean,
    private val persistPreferences: () -> Unit,
    private val generateSpray: (distance: Double, angle: Int, generation: Long) -> Unit,
    private val publishPath: (List<LatLon>, List<LatLng>) -> Unit,
    private val cancelPointRoute: () -> Unit,
    private val cancelTerrain: () -> Unit,
) {
    data class OrderedPath(val points: List<LatLon>, val terrainWaypoints: List<com.example.droneservicesapp.domain.terrain.TerrainWaypoint>)
    private var generation = 0L
    private var debounceJob: Job? = null
    private var surveyJob: Job? = null

    fun scheduleAreaRedraw(immediate: Boolean = false) {
        cancelJobs(incrementGeneration = true)
        val requestedGeneration = generation
        debounceJob = scope.launch {
            if (!immediate) delay(140L)
            generateArea(requestedGeneration)
        }
    }

    fun redrawIfEditable(debounced: Boolean = false) {
        if (
            viewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams &&
            viewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA &&
            (viewModel.missionArea.value?.vertices?.size ?: 0) >= 3
        ) scheduleAreaRedraw(immediate = !debounced)
    }

    fun cancel() = cancelJobs(incrementGeneration = true)

    fun isCurrent(requestedGeneration: Long): Boolean = requestedGeneration == generation && isActive()

    fun orderForPublishing(
        path: List<LatLon>,
        terrainWaypoints: List<com.example.droneservicesapp.domain.terrain.TerrainWaypoint>,
    ): OrderedPath {
        val ordered = planner.orderPathForHome(
            path,
            viewModel.plannedHomePosition.value,
            viewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY,
        )
        val reversed = path.size >= 2 && ordered.firstOrNull() == path.lastOrNull()
        return OrderedPath(ordered, if (reversed) terrainWaypoints.reversed() else terrainWaypoints)
    }

    private fun generateArea(requestedGeneration: Long) {
        persistPreferences()
        when (viewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY) {
            PlanningOperationMode.SPRAY -> generateSpray(
                viewModel.lineDistanceProgress.value ?: 5.0,
                viewModel.angleProgress.value?.toInt() ?: 90,
                requestedGeneration,
            )
            PlanningOperationMode.SURVEY -> generateSurveyGrid(requestedGeneration)
        }
    }

    private fun generateSurveyGrid(requestedGeneration: Long) {
        surveyJob?.cancel()
        val area = viewModel.missionArea.value ?: return
        val polygon = area.vertices.map { LatLon(it.latitude, it.longitude) }
        val params = viewModel.surveyGridParams.value ?: return
        val obstacles = viewModel.missionObstacles.value.orEmpty()
        viewModel.clearPointCloudMissionProfile()
        surveyJob = scope.launch {
            val path = withContext(Dispatchers.Default) {
                planner.buildAreaPath(polygon, PlanningOperationMode.SURVEY, obstacles, 0.0, 0, params)
            }
            if (isCurrent(requestedGeneration)) publishPath(path, area.vertices)
        }
    }

    private fun cancelJobs(incrementGeneration: Boolean) {
        debounceJob?.cancel()
        surveyJob?.cancel()
        cancelPointRoute()
        cancelTerrain()
        if (incrementGeneration) generation += 1L
    }
}
