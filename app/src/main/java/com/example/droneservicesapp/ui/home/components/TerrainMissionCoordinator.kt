package com.example.droneservicesapp.ui.home.components

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.terrain.PointCloudCoverage
import com.example.droneservicesapp.domain.terrain.TerrainCoveragePlanner
import com.example.droneservicesapp.domain.terrain.TerrainWaypoint
import com.example.droneservicesapp.domain.terrain.TerrainPathFailure
import com.example.droneservicesapp.domain.terrain.TerrainPathResult
import com.example.droneservicesapp.domain.planning.MissionPlanningCoordinator
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns terrain sampling, grid warm-up, and terrain-coverage presentation decisions. */
class TerrainMissionCoordinator(
    private val context: Context,
    private val scope: LifecycleCoroutineScope,
    private val viewModel: MainActivityViewModel,
    private val previewAssets: PreviewAssetsViewModel,
    private val planner: MissionPlanningCoordinator,
) {
    private var sprayJob: kotlinx.coroutines.Job? = null

    fun cancel() {
        sprayJob?.cancel()
        sprayJob = null
    }

    fun generateSpray(
        distance: Double,
        angle: Int,
        generation: Long,
        plannedHome: LatLon?,
        droneAltitudeAmsl: Double?,
        isCurrent: (Long) -> Boolean,
        publishPath: (List<LatLon>, List<LatLng>, List<TerrainWaypoint>) -> Unit,
        clearRenderedPath: () -> Unit,
    ) {
        cancel()
        val classify = viewModel.beginPointCloudCoverageCheck()
        val lockedCoverage = viewModel.pointCloudCoverage.value ?: PointCloudCoverage.NONE
        if (classify) viewModel.pointCloudCoversMissionArea.value = false
        resetProfile(TerrainPathFailure.VALIDATION_PENDING)
        val area = viewModel.missionArea.value ?: return
        val polygon = area.vertices.map { LatLon(it.latitude, it.longitude) }
        val obstacles = viewModel.missionObstacles.value.orEmpty()
        val model = previewAssets.pointCloudTerrainModel?.takeIf { it.isGeoreferenced }
        val height = viewModel.flightAltProgress.value ?: 0.0
        val segment = viewModel.surveyTerrainSegment.value ?: 2.5
        val smoothing = viewModel.surveyCanopySmoothing.value ?: 5.0
        sprayJob = scope.launch {
            data class Generated(val base: List<LatLon>, val work: TerrainPathResult, val coverage: PointCloudCoverage)
            val generated = withContext(Dispatchers.Default) {
                val raw = planner.buildAreaPath(polygon, PlanningOperationMode.SPRAY, obstacles, distance, angle, null)
                val base = planner.orderPathForHome(raw, plannedHome, PlanningOperationMode.SPRAY)
                val coverage = if (classify) model?.classifyAreaCoverage(polygon) ?: PointCloudCoverage.NONE else lockedCoverage
                val work = if (model != null && base.isNotEmpty() && coverage == PointCloudCoverage.COMPLETE) {
                    model.buildValidatedTerrainPath(
                        path = base,
                        home = plannedHome ?: base.first(),
                        heightAboveTerrainMeters = height,
                        segmentMeters = segment,
                        canopySmoothingMeters = smoothing,
                        requireHomeCoverage = false,
                        homeAltitudeAmslMeters = droneAltitudeAmsl,
                    )
                } else TerrainPathResult(
                    failure = if (model == null) TerrainPathFailure.NO_GEOREFERENCE else TerrainPathFailure.PATH_UNCOVERED
                )
                Generated(base, work, coverage)
            }
            if (!isCurrent(generation)) return@launch
            if (generated.work.isValid) {
                viewModel.pointCloudCoversMissionArea.value = true
                if (classify) viewModel.lockPointCloudCoverage(generated.coverage)
                resetProfile(TerrainPathFailure.NONE)
                publishPath(generated.work.waypoints.map { it.latLon }, area.vertices, generated.work.waypoints)
            } else {
                viewModel.pointCloudCoversMissionArea.value = false
                if (classify) viewModel.lockPointCloudCoverage(PointCloudCoverage.classify(generated.coverage != PointCloudCoverage.NONE, false))
                resetProfile(generated.work.failure, generated.work.firstUncoveredPoint)
                if (generated.base.isEmpty()) {
                    clearRenderedPath()
                    viewModel.surveyPath.value = emptyList()
                    viewModel.terrainSurveyWaypoints.value = emptyList()
                    viewModel.mapState.value = MainActivityViewModel.MapState.Draw
                } else publishPath(generated.base, area.vertices, emptyList())
            }
        }
    }

    private fun resetProfile(failure: TerrainPathFailure, uncovered: LatLon? = null) {
        viewModel.pointCloudMissionFailure.value = failure
        viewModel.pointCloudMissionFirstUncoveredPoint.value = uncovered
        viewModel.pointCloudProfileHome.value = null
        viewModel.terrainOutboundWaypoints.value = emptyList()
        viewModel.terrainReturnWaypoints.value = emptyList()
        viewModel.terrainServiceCorridors.value = emptyList()
    }
    fun resampleWaypoint(point: LatLng): TerrainWaypoint? {
        if (viewModel.terrainSurveyWaypoints.value.orEmpty().isEmpty()) return null
        val model = previewAssets.pointCloudTerrainModel ?: return null
        val frame = model.coordinateFrame ?: return null
        val params = viewModel.surveyGridParams.value ?: return null
        val spraying = viewModel.planningOperationMode.value == PlanningOperationMode.SPRAY
        val height = if (spraying) viewModel.flightAltProgress.value ?: 0.0
            else params.heightAboveTerrainMeters.toDouble()
        val smoothing = if (spraying) viewModel.surveyCanopySmoothing.value ?: 5.0
            else params.canopySmoothingMeters.toDouble()
        val (x, y) = frame.latLonToLocal(point.latitude, point.longitude)
        val altitude = model.terrainHeightAt(
            xMeters = x,
            yMeters = y,
            canopyRadiusMeters = smoothing,
        ) + height
        return TerrainWaypoint(LatLon(point.latitude, point.longitude), altitude, altitude)
    }

    fun renderCoverage(overlay: TerrainCoverageOverlayController?) {
        overlay ?: return
        val hasProfile = viewModel.pointCloudCoverage.value == PointCloudCoverage.COMPLETE ||
            viewModel.terrainRouteWaypoints.value.orEmpty().isNotEmpty()
        val usesTerrain = (viewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY) == PlanningOperationMode.SURVEY &&
            !hasProfile && (viewModel.altitudeReferenceMode.value ?: AltitudeReferenceMode.TERRAIN) == AltitudeReferenceMode.TERRAIN
        if (!usesTerrain) return overlay.clear()
        val path = when (viewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA) {
            PlanningWorkflow.AREA -> viewModel.surveyPath.value.orEmpty().map { LatLon(it.latitude, it.longitude) }
            PlanningWorkflow.POINTS -> viewModel.plannedRoutePath.value.orEmpty().takeIf { it.size >= 2 }
                ?.map { LatLon(it.latitude, it.longitude) }
                ?: viewModel.routeWaypoints.value.orEmpty().map { LatLon(it.latitude, it.longitude) }
        }
        if (path.isEmpty()) overlay.clear() else overlay.render(
            TerrainCoveragePlanner.missionCenter(path),
            TerrainCoveragePlanner.COVERAGE_RADIUS_METERS,
        )
    }
}
