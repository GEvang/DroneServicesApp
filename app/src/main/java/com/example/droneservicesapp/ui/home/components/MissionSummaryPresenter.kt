package com.example.droneservicesapp.ui.home.components

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.planning.MissionPlanningCoordinator
import com.example.droneservicesapp.domain.planning.MissionResourcePlan
import com.example.droneservicesapp.domain.terrain.TerrainPathFailure
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Builds mission summary state and owns summary-card asynchronous rendering. */
class MissionSummaryPresenter(
    private val context: Context,
    private val root: View,
    private val scope: LifecycleCoroutineScope,
    private val viewModel: MainActivityViewModel,
    private val planner: MissionPlanningCoordinator,
    private val currentPath: () -> List<LatLng>,
    private val isActive: () -> Boolean,
    private val updateGeometryActions: () -> Unit,
    private val updateSimulationButton: (Boolean) -> Unit,
    private val stopSimulation: () -> Unit,
    private val setServiceMarkers: (List<LatLon>, List<LatLon>) -> Unit,
) {
    private var renderJob: Job? = null

    fun dispose() {
        renderJob?.cancel()
    }

    fun renderCard() {
        val card = root.findViewById<TextView?>(R.id.home_mission_summary_card) ?: return
        updateGeometryActions()
        val path = currentPath()
        val usable = viewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams && path.size >= 2
        updateSimulationButton(usable)
        if (!usable) {
            renderJob?.cancel()
            card.visibility = View.GONE
            setServiceMarkers(emptyList(), emptyList())
            stopSimulation()
            return
        }
        val area = viewModel.missionArea.value?.vertices.orEmpty()
        val areaMeters = if (area.size >= 3) SphericalUtil.computeArea(area) else 0.0
        val passes = (if (path.size % 2 == 0) path.size / 2 else path.size - 1).coerceAtLeast(0)
        val mode = viewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY
        val altitude = if (mode == PlanningOperationMode.SURVEY) viewModel.surveyHeightAboveTerrain.value ?: 0.0
            else viewModel.flightAltProgress.value ?: 0.0
        val spraying = mode == PlanningOperationMode.SPRAY
        val sprayRate = if (spraying) viewModel.sprayFlowLitersPerMinute() else 0.0
        renderJob?.cancel()
        renderJob = scope.launch {
            val plan = withContext(Dispatchers.Default) { resourcePlan(path, viewModel.plannedHomePosition.value) }
            if (!isActive() || currentPath() != path || viewModel.mapState.value != MainActivityViewModel.MapState.SetFlightParams) return@launch
            setServiceMarkers(plan.batteryReturnPoints, if (spraying) plan.tankRefillPoints else emptyList())
            card.text = buildString {
                if (areaMeters > 0.0) append("Area: ${formatArea(areaMeters)}   |   ")
                append("Passes: $passes   |   Time: ${formatTime(plan.estimatedFlightSeconds.toInt())}")
                appendLine()
                append("ALT (AGL): ${altitude.toInt()} m   |   Batteries: ${plan.batteryCount}")
                if (spraying) {
                    appendLine()
                    append("Flow: ${decimal(sprayRate)} Lt/min   |   Liquid: ${decimal(plan.totalSprayLiters)} Lt   |   Tank refills: ${plan.tankRefillCount}")
                }
            }
            card.visibility = View.VISIBLE
        }
    }

    fun showUploadSummary(onConfirmed: () -> Unit) {
        val workflow = viewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA
        val path = currentPath()
        val mode = viewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY
        val area = viewModel.missionArea.value?.vertices.orEmpty()
        val areaMeters = if (area.size >= 3) SphericalUtil.computeArea(area) else 0.0
        val altitude = viewModel.flightAltProgress.value ?: 0.0
        val plan = resourcePlan(path, viewModel.plannedHomePosition.value)
        val message = buildString {
            appendLine(context.getString(R.string.mission_summary_mode, context.getString(if (mode == PlanningOperationMode.SPRAY) R.string.spray else R.string.survey)))
            appendLine(context.getString(R.string.mission_summary_workflow, context.getString(if (workflow == PlanningWorkflow.AREA) R.string.area else R.string.points)))
            if (areaMeters > 0.0) appendLine(context.getString(R.string.mission_summary_area, formatArea(areaMeters)))
            appendLine(context.getString(R.string.mission_summary_path_length, formatDistance(planner.totalDistance(path.map { LatLon(it.latitude, it.longitude) }, viewModel.plannedHomePosition.value))))
            appendLine(context.getString(R.string.mission_summary_lines, if (path.size < 2) 0 else if (path.size % 2 == 0) path.size / 2 else path.size - 1))
            appendLine(context.getString(R.string.mission_summary_altitude, altitude.toInt()))
            if (mode == PlanningOperationMode.SPRAY && viewModel.pointCloudMissionFailure.value == TerrainPathFailure.NONE) {
                val profile = viewModel.terrainSurveyWaypoints.value.orEmpty()
                appendLine(context.getString(R.string.mission_summary_point_cloud_clearance, altitude.toInt()))
                val min = profile.minOfOrNull { it.missionAltitudeMeters }
                val max = profile.maxOfOrNull { it.missionAltitudeMeters }
                if (min != null && max != null) appendLine(context.getString(R.string.mission_summary_relative_altitude_range, min, max))
                appendLine(context.getString(R.string.mission_summary_point_cloud_validated))
            }
            appendLine(context.getString(R.string.mission_summary_speed, decimal(viewModel.flightSpeed.value ?: 5.0)))
            appendLine(context.getString(R.string.mission_summary_estimated_time, formatTime(plan.estimatedFlightSeconds.toInt())))
            appendLine(context.getString(R.string.mission_summary_batteries, plan.batteryCount))
            if (mode == PlanningOperationMode.SPRAY) {
                appendLine(context.getString(R.string.mission_summary_spray_flow, decimal(viewModel.sprayFlowLitersPerMinute())))
                appendLine(context.getString(R.string.mission_summary_liquid, decimal(plan.totalSprayLiters)))
                appendLine(context.getString(R.string.mission_summary_refills, plan.tankRefillCount))
            }
        }
        AlertDialog.Builder(context, R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(R.string.mission_summary_title).setMessage(message)
            .setNegativeButton(R.string.decline, null)
            .setPositiveButton(R.string.confirm) { _, _ -> onConfirmed() }.show().also { dialog ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#212121"))
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#212121"))
            }
    }

    private fun resourcePlan(path: List<LatLng>, home: LatLon?): MissionResourcePlan = planner.buildResourcePlan(
        path.map { LatLon(it.latitude, it.longitude) }, home,
        viewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY,
        (viewModel.flightSpeed.value ?: 5.0).coerceAtLeast(0.1), viewModel.sprayFlowLitersPerMinute(),
    )

    private fun decimal(value: Double) = String.format(Locale.US, "%.1f", value)
    private fun formatTime(seconds: Int) = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
    private fun formatDistance(meters: Double) = if (meters >= 1000) String.format(Locale.US, "%.2f km", meters / 1000) else "${meters.toInt().coerceAtLeast(0)} m"
    private fun formatArea(squareMeters: Double) = if (squareMeters >= 10_000) String.format(Locale.US, "%.2f ha", squareMeters / 10_000) else "${squareMeters.toInt().coerceAtLeast(0)} m2"
}
