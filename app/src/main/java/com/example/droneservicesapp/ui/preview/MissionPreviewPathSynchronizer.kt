package com.example.droneservicesapp.ui.preview

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.survey.SurveyGridPlanner
import com.example.droneservicesapp.domain.survey.SurveyPlanner
import com.example.droneservicesapp.domain.terrain.TerrainWaypoint
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MissionPreviewPathSynchronizer(
    private val lifecycleOwner: LifecycleOwner,
    private val activityViewModel: MainActivityViewModel,
    private val previewAssetsViewModel: PreviewAssetsViewModel
) {
    private var terrainSurveyJob: Job? = null

    private val redrawObserver = Observer<Any?> { redrawAreaMissionIfEditable() }

    fun bind() {
        activityViewModel.mapState.observe(lifecycleOwner, redrawObserver)
        activityViewModel.activePlanningWorkflow.observe(lifecycleOwner, redrawObserver)
        activityViewModel.planningOperationMode.observe(lifecycleOwner, redrawObserver)
        activityViewModel.missionArea.observe(lifecycleOwner, redrawObserver)
        activityViewModel.missionObstacles.observe(lifecycleOwner, redrawObserver)
        activityViewModel.surveyGridParams.observe(lifecycleOwner, redrawObserver)
        activityViewModel.lineDistanceProgress.observe(lifecycleOwner, redrawObserver)
        activityViewModel.angleProgress.observe(lifecycleOwner, redrawObserver)
    }

    fun clear() {
        terrainSurveyJob?.cancel()
        terrainSurveyJob = null
    }

    private fun redrawAreaMissionIfEditable() {
        if (
            activityViewModel.mapState.value != MainActivityViewModel.MapState.SetFlightParams ||
            activityViewModel.activePlanningWorkflow.value != PlanningWorkflow.AREA ||
            (activityViewModel.missionArea.value?.vertices?.size ?: 0) < 3
        ) {
            return
        }

        when (activityViewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY) {
            PlanningOperationMode.SPRAY -> drawSprayMission(
                distance = activityViewModel.lineDistanceProgress.value ?: 0.0,
                angle = activityViewModel.angleProgress.value?.toInt() ?: 0
            )
            PlanningOperationMode.SURVEY -> drawSurveyGridMission()
        }
    }

    private fun drawSprayMission(distance: Double, angle: Int) {
        terrainSurveyJob?.cancel()
        val area = activityViewModel.missionArea.value ?: return
        val polygonLatLon = area.vertices.map { LatLon(it.latitude, it.longitude) }
        val pathLatLon = SurveyPlanner().buildSurveyPath(
            polygon = polygonLatLon,
            distanceMeters = distance,
            angleDeg = angle,
            obstacles = activityViewModel.missionObstacles.value.orEmpty()
        )
        publishPath(pathLatLon)
    }

    private fun drawSurveyGridMission() {
        terrainSurveyJob?.cancel()
        val area = activityViewModel.missionArea.value ?: return
        val polygonLatLon = area.vertices.map { LatLon(it.latitude, it.longitude) }
        val params = activityViewModel.surveyGridParams.value ?: return
        val obstacles = activityViewModel.missionObstacles.value.orEmpty()
        val terrainModel = previewAssetsViewModel.pointCloudTerrainModel
            ?.takeIf { it.isGeoreferenced && obstacles.isEmpty() }

        if (terrainModel != null) {
            terrainSurveyJob = lifecycleOwner.lifecycleScope.launch {
                delay(TERRAIN_SURVEY_REDRAW_DEBOUNCE_MS)
                val terrainWaypoints = withContext(Dispatchers.Default) {
                    terrainModel.buildTerrainSurveyPath(
                        polygon = polygonLatLon,
                        params = params
                    )
                }
                publishPath(
                    pathLatLon = terrainWaypoints.map { it.latLon },
                    terrainWaypoints = terrainWaypoints
                )
            }
            return
        }

        publishPath(
            SurveyGridPlanner().buildSurveyPath(
                polygon = polygonLatLon,
                params = params,
                obstacles = obstacles
            )
        )
    }

    private fun publishPath(
        pathLatLon: List<LatLon>,
        terrainWaypoints: List<TerrainWaypoint> = emptyList()
    ) {
        if (pathLatLon.isEmpty()) {
            activityViewModel.surveyPath.value = emptyList()
            activityViewModel.terrainSurveyWaypoints.value = emptyList()
            activityViewModel.flightDistance.value = 0
            return
        }
        val path = pathLatLon.map { LatLng(it.lat, it.lon) }
        val distance = path.zipWithNext().sumOf { (from, to) ->
            SphericalUtil.computeDistanceBetween(from, to)
        }
        activityViewModel.surveyPath.value = path
        activityViewModel.terrainSurveyWaypoints.value = terrainWaypoints
        activityViewModel.flightDistance.value = distance.toInt()
    }

    companion object {
        private const val TERRAIN_SURVEY_REDRAW_DEBOUNCE_MS = 250L
    }
}
