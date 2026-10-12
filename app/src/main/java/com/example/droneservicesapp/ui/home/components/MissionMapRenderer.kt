package com.example.droneservicesapp.ui.home.components

import com.example.droneservicesapp.data.diagnostics.DiagnosticLog
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.planning.MissionPlanningCoordinator
import com.example.droneservicesapp.domain.terrain.TerrainWaypoint
import com.example.droneservicesapp.ui.home.map.MissionMapLayers
import com.example.droneservicesapp.ui.preview.PointCloudTerrainStyleMapper
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import io.dronefleet.mavlink.common.MissionItemInt

/** Publishes mission paths, downloaded missions, and terrain coverage to the map layers. */
class MissionMapRenderer(
    private val viewModel: MainActivityViewModel,
    private val previewAssets: PreviewAssetsViewModel,
    private val planner: MissionPlanningCoordinator,
    private val layers: MissionMapLayers,
    private val terrainCoordinator: TerrainMissionCoordinator,
    private val heightColorsEnabled: () -> Boolean,
) {
    private var downloadedSignature: String? = null

    fun renderDownloaded(missionItems: List<MissionItemInt>, force: Boolean = false) {
        previewAssets.retainDroneMission(missionItems)
        if (viewModel.mapState.value != MainActivityViewModel.MapState.Idle) return
        val path = previewAssets.retainedDroneMissionPath.value.orEmpty()
        val signature = path.joinToString("|") { "${it.latitude}:${it.longitude}" }
        if (!force && signature == downloadedSignature) return
        downloadedSignature = signature
        if (path.isEmpty()) layers.clearSurveyPath() else layers.survey.setSurveyPath(path)
        DiagnosticLog.event(
            "mission",
            "download_rendered",
            data = mapOf("downloadedItemCount" to missionItems.size, "drawnWaypointCount" to path.size),
        )
    }

    fun renderCurrentPath() {
        val downloaded = previewAssets.retainedDroneMissionPath.value.orEmpty()
        val path = if (viewModel.mapState.value == MainActivityViewModel.MapState.Idle && downloaded.size >= 2) {
            downloaded
        } else {
            viewModel.surveyPath.value.orEmpty()
        }
        if (path.size < 2) return layers.clearSurveyPath()
        layers.survey.setSurveyPath(
            path,
            viewModel.missionArea.value?.vertices.orEmpty().toList(),
            PointCloudTerrainStyleMapper.surveySegmentColors(
                heightColorsEnabled(),
                path,
                viewModel.terrainSurveyWaypoints.value.orEmpty(),
            ),
        )
    }

    fun publishGeneratedPath(
        path: List<LatLon>,
        areaVertices: List<LatLng>,
        terrainWaypoints: List<TerrainWaypoint> = emptyList(),
    ) {
        if (path.isEmpty()) {
            viewModel.surveyPath.postValue(emptyList())
            viewModel.clearPointCloudMissionProfile()
            layers.clearSurveyPath()
            return
        }
        val points = path.map { LatLng(it.lat, it.lon) }
        viewModel.flightDistance.postValue(planner.totalDistance(path, viewModel.plannedHomePosition.value).toInt())
        viewModel.surveyPath.value = points
        viewModel.terrainSurveyWaypoints.value = terrainWaypoints
        DiagnosticLog.event(
            "mission",
            "plan_generated",
            data = mapOf(
                "workflow" to (viewModel.activePlanningWorkflow.value?.name ?: "UNKNOWN"),
                "operationMode" to (viewModel.planningOperationMode.value?.name ?: "UNKNOWN"),
                "waypointCount" to points.size,
                "terrainWaypointCount" to terrainWaypoints.size,
            ),
        )
        layers.survey.setSurveyPath(points, areaVertices.toList())
    }

    fun renderCoverage() = terrainCoordinator.renderCoverage(layers.terrainCoverage)
}
