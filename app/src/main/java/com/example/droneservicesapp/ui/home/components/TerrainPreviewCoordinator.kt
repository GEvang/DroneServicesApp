package com.example.droneservicesapp.ui.home.components

import android.content.Context
import android.location.Location
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.pointcloud.PointCloudData
import com.example.droneservicesapp.databinding.FragmentHomeMapsBinding
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.preview.PointCloudMissionOverlayBuilder
import com.example.droneservicesapp.ui.preview.PointCloudTerrainStyleMapper
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.preview.PreviewMode
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns terrain-preview status, grid warm-up, and point-cloud mission overlay assembly. */
class TerrainPreviewCoordinator(
    private val context: Context,
    private val scope: LifecycleCoroutineScope,
    private val binding: FragmentHomeMapsBinding,
    private val viewModel: MainActivityViewModel,
    private val droneViewModel: DroneViewModel,
    private val previewAssets: PreviewAssetsViewModel,
    private val currentPointCloud: () -> PointCloudData?,
    private val currentDroneLocation: () -> Location?,
    private val currentMode: () -> PreviewMode,
    private val isSurveyMode: () -> Boolean,
    private val selectedWorkflow: () -> PlanningWorkflow,
    private val selectedWaypointIndex: () -> Int?,
    private val orderPointRoute: (List<LatLng>) -> List<LatLng>,
    private val redrawMission: () -> Unit,
    private val isActive: () -> Boolean,
) {
    private val overlayBuilder = PointCloudMissionOverlayBuilder()
    private var gridJob: Job? = null

    fun warmGrid(showToast: Boolean) {
        val model = previewAssets.pointCloudTerrainModel ?: return
        gridJob?.cancel()
        gridJob = scope.launch {
            val summary = withContext(Dispatchers.Default) { model.terrainGridSummary() }
            if (model !== previewAssets.pointCloudTerrainModel || !isActive()) return@launch
            previewAssets.setPointCloudTerrainSummary(summary)
            Log.d(
                TERRAIN_GRID_TAG,
                "cells=${summary.cellCount} points=${summary.pointCount} " +
                    "cellSize=${String.format(Locale.US, "%.2f", summary.cellSizeMeters)}m " +
                    "georef=${summary.isGeoreferenced}",
            )
            if (showToast) {
                Toast.makeText(
                    context,
                    "Terrain grid ready: ${summary.cellCount} cells, " +
                        if (summary.isGeoreferenced) "georeferenced" else "not georeferenced",
                    Toast.LENGTH_LONG,
                ).show()
            }
            if (currentMode() == PreviewMode.POINT_CLOUD) renderStatus()
            redrawMission()
        }
    }

    fun dispose() {
        gridJob?.cancel()
        gridJob = null
    }

    fun renderStatus() {
        if (!isActive()) return
        binding.previewTerrainStatus.visibility = View.VISIBLE
        if (isSurveyMode()) {
            val terrain = droneViewModel.terrainPreview.value
            binding.previewTerrainStatus.text = if (terrain == null) {
                context.getString(R.string.terrain_3d_not_ready)
            } else {
                context.getString(
                    R.string.terrain_3d_ready,
                    terrain.fileNames.joinToString(),
                    terrain.pointCloud.displayedPointCount,
                    terrain.gridSpacingMeters,
                )
            }
            return
        }
        val summary = previewAssets.pointCloudTerrainSummary
        binding.previewTerrainStatus.text = when {
            previewAssets.pointCloudAsset == null -> "Terrain --"
            summary == null -> "Terrain building"
            !summary.isGeoreferenced -> "Terrain no GPS"
            else -> "Terrain ${formatCompactCount(summary.cellCount)}"
        }
    }

    fun renderMissionOverlay() {
        if (!isActive() || currentMode() != PreviewMode.POINT_CLOUD) return
        val downloadedPoints = previewAssets.retainedDroneMissionPath.value.orEmpty()
        val downloadedWaypoints = previewAssets.retainedDroneMissionWaypoints.value.orEmpty()
        val showingDownloadedMission =
            viewModel.mapState.value == MainActivityViewModel.MapState.Idle && downloadedPoints.isNotEmpty()
        val pointWorkflow = viewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS
        val areaVertices = if (showingDownloadedMission) emptyList() else viewModel.missionArea.value?.vertices.orEmpty()
        val surveyPoints = when {
            showingDownloadedMission -> downloadedPoints
            pointWorkflow -> emptyList()
            else -> viewModel.surveyPath.value.orEmpty()
        }
        val surveyZValues = when {
            showingDownloadedMission && downloadedWaypoints.size == surveyPoints.size ->
                downloadedWaypoints.map { it.altitudeMeters }
            showingDownloadedMission -> null
            else -> PointCloudTerrainStyleMapper.surveyZValues(
                surveyPoints,
                viewModel.terrainSurveyWaypoints.value.orEmpty(),
            )
        }
        val terrainRoute = viewModel.terrainRouteWaypoints.value.orEmpty()
        val routePoints = if (showingDownloadedMission || !pointWorkflow) emptyList() else {
            terrainRoute.takeIf { it.size >= 2 }?.map { LatLng(it.latLon.lat, it.latLon.lon) }
                ?: orderPointRoute(
                    viewModel.plannedRoutePath.value.orEmpty().takeIf { it.size >= 2 }
                        ?: viewModel.routeWaypoints.value.orEmpty().map { LatLng(it.latitude, it.longitude) },
                )
        }
        val routeZValues = terrainRoute.takeIf { it.size == routePoints.size && it.isNotEmpty() }
            ?.map { it.displayAltitudeMeters.toFloat() }
        val droneLocation = currentDroneLocation()
        binding.homePointCloudGlView.setMissionOverlay(
            overlayBuilder.build(
                PointCloudMissionOverlayBuilder.Input(
                    pointCloud = currentPointCloud(),
                    areaPoints = areaVertices,
                    areaZValues = PointCloudTerrainStyleMapper.groundZValues(
                        areaVertices,
                        previewAssets.pointCloudTerrainModel,
                    ),
                    surveyPoints = surveyPoints,
                    surveyZValues = surveyZValues,
                    routePoints = routePoints,
                    routeZValues = routeZValues,
                    droneLocation = droneLocation?.let { LatLng(it.latitude, it.longitude) },
                    droneHeadingDegrees = droneViewModel.droneHeading.value ?: 0.0,
                    selectedWorkflow = selectedWorkflow(),
                    selectedWaypointIndex = selectedWaypointIndex(),
                ),
            ),
        )
    }

    private fun formatCompactCount(value: Int): String = when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000.0)
        else -> value.toString()
    }

    companion object {
        private const val TERRAIN_GRID_TAG = "TerrainGrid"
    }
}
