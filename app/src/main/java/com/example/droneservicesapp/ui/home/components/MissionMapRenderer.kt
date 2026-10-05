package com.example.droneservicesapp.ui.home.components

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.diagnostics.DiagnosticLog
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.MissionObstacle
import com.example.droneservicesapp.domain.model.MissionObstacleShape
import com.example.droneservicesapp.domain.planning.MissionPlanningCoordinator
import com.example.droneservicesapp.domain.terrain.TerrainWaypoint
import com.example.droneservicesapp.ui.preview.PointCloudTerrainStyleMapper
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import io.dronefleet.mavlink.common.MissionItemInt
import kotlin.math.PI

/** Renders mission paths, downloaded missions, obstacle lists, and terrain coverage overlays. */
class MissionMapRenderer(
    private val context: Context,
    private val obstacleList: LinearLayout?,
    private val viewModel: MainActivityViewModel,
    private val previewAssets: PreviewAssetsViewModel,
    private val planner: MissionPlanningCoordinator,
    private val mapController: OsmdroidMapController,
    private val obstacleEditor: OsmdroidObstacleEditor,
    private val terrainCoordinator: TerrainMissionCoordinator,
    private val coverageOverlay: () -> TerrainCoverageOverlayController?,
    private val heightColorsEnabled: () -> Boolean,
    private val removeObstacle: (String) -> Unit,
) {
    private var downloadedSignature: String? = null

    fun renderDownloaded(missionItems: List<MissionItemInt>, force: Boolean = false) {
        previewAssets.retainDroneMission(missionItems)
        if (viewModel.mapState.value != MainActivityViewModel.MapState.Idle) return
        val path = previewAssets.retainedDroneMissionPath.value.orEmpty()
        val signature = path.joinToString("|") { "${it.latitude}:${it.longitude}" }
        if (!force && signature == downloadedSignature) return
        downloadedSignature = signature
        if (path.isEmpty()) mapController.clearSurveyPath() else mapController.setSurveyPath(path)
        DiagnosticLog.event("mission", "download_rendered", data = mapOf("downloadedItemCount" to missionItems.size, "drawnWaypointCount" to path.size))
    }

    fun renderCurrentPath() {
        val downloaded = previewAssets.retainedDroneMissionPath.value.orEmpty()
        val path = if (viewModel.mapState.value == MainActivityViewModel.MapState.Idle && downloaded.size >= 2) downloaded
            else viewModel.surveyPath.value.orEmpty()
        if (path.size < 2) return mapController.clearSurveyPath()
        mapController.setSurveyPath(
            path,
            viewModel.missionArea.value?.vertices.orEmpty(),
            PointCloudTerrainStyleMapper.surveySegmentColors(
                heightColorsEnabled(), path, viewModel.terrainSurveyWaypoints.value.orEmpty()
            ),
        )
        obstacleEditor.bringToFront()
    }

    fun publishGeneratedPath(
        path: List<LatLon>,
        areaVertices: List<LatLng>,
        terrainWaypoints: List<TerrainWaypoint> = emptyList(),
    ) {
        if (path.isEmpty()) {
            viewModel.surveyPath.postValue(emptyList())
            viewModel.clearPointCloudMissionProfile()
            mapController.clearSurveyPath()
            return
        }
        val points = path.map { LatLng(it.lat, it.lon) }
        viewModel.flightDistance.postValue(planner.totalDistance(path, viewModel.plannedHomePosition.value).toInt())
        viewModel.surveyPath.value = points
        viewModel.terrainSurveyWaypoints.value = terrainWaypoints
        DiagnosticLog.event("mission", "plan_generated", data = mapOf(
            "workflow" to (viewModel.activePlanningWorkflow.value?.name ?: "UNKNOWN"),
            "operationMode" to (viewModel.planningOperationMode.value?.name ?: "UNKNOWN"),
            "waypointCount" to points.size,
            "terrainWaypointCount" to terrainWaypoints.size,
        ))
        mapController.setSurveyPath(points, areaVertices)
        obstacleEditor.bringToFront()
    }

    fun renderCoverage() = terrainCoordinator.renderCoverage(coverageOverlay())

    fun renderObstacleList(obstacles: List<MissionObstacle>) {
        val list = obstacleList ?: return
        list.removeAllViews()
        obstacles.filter { it.isValid() }.forEachIndexed { index, obstacle ->
            val letter = ('A'.code + index).toChar().toString()
            val size = when (obstacle.shape) {
                MissionObstacleShape.CIRCLE -> PI * obstacle.radiusMeters * obstacle.radiusMeters
                MissionObstacleShape.POLYGON -> SphericalUtil.computeArea(obstacle.vertices.map { LatLng(it.lat, it.lon) })
            }
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(ContextCompat.getColor(context, R.color.ds_color_text_primary))
                textSize = 12f
                text = context.getString(if (obstacle.shape == MissionObstacleShape.CIRCLE) R.string.obstacle_circle_item_format else R.string.obstacle_polygon_item_format, letter, size)
            })
            row.addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(context.resources.getDimensionPixelSize(R.dimen.phone_map_bottom_dock_icon_size), context.resources.getDimensionPixelSize(R.dimen.phone_map_bottom_dock_icon_size))
                gravity = Gravity.CENTER; text = "×"; textSize = 24f
                setTextColor(ContextCompat.getColor(context, R.color.ds_color_shell_danger))
                contentDescription = context.getString(R.string.delete_obstacle_format, letter)
                setOnClickListener { removeObstacle(obstacle.id) }
            })
            list.addView(row)
        }
    }
}
