package com.example.droneservicesapp.ui.home.components

import android.content.Context
import android.view.View
import android.widget.Toast
import com.example.droneservicesapp.R
import com.example.droneservicesapp.databinding.FragmentHomeMapsBinding
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.terrain.TerrainWaypoint
import com.example.droneservicesapp.ui.preview.PreviewMode
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import java.util.Locale
import org.osmdroid.util.GeoPoint

/** Coordinates polygon, route, obstacle, and waypoint editor selection transitions. */
class MissionEditorCoordinator(
    private val context: Context,
    private val binding: FragmentHomeMapsBinding,
    private val viewModel: MainActivityViewModel,
    private val mapController: OsmdroidMapController,
    private val routeEditor: OsmdroidRouteWaypointEditor,
    private val terrainSampler: (LatLng) -> TerrainWaypoint?,
    private val updateDistance: (List<LatLng>) -> Unit,
    private val renderPath: () -> Unit,
    private val updatePointCloudOverlay: () -> Unit,
) {
    var selectedIndex: Int? = null
        private set
    var selectedWorkflow: PlanningWorkflow = PlanningWorkflow.AREA
        private set

    fun updateRouteEnabled(homePlacementActive: Boolean) {
        val points = viewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS
        val state = viewModel.mapState.value
        val enabled = points && state in setOf(MainActivityViewModel.MapState.Draw, MainActivityViewModel.MapState.SetFlightParams) && !homePlacementActive
        routeEditor.setEnabled(enabled)
        routeEditor.setAddingEnabled(enabled && state == MainActivityViewModel.MapState.Draw)
    }

    fun updateSurveyEnabled() {
        val enabled = viewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams &&
            viewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA && viewModel.surveyPath.value.orEmpty().isNotEmpty()
        mapController.setSurveyWaypointEditingEnabled(enabled)
        if (!enabled) select(PlanningWorkflow.AREA, null, PreviewMode.MAP)
    }

    fun selectSurvey(index: Int?) = select(PlanningWorkflow.AREA, index, PreviewMode.MAP)

    fun select(workflow: PlanningWorkflow, index: Int?, previewMode: PreviewMode) {
        selectedWorkflow = workflow
        selectedIndex = index
        binding.surveyWaypointEditDock.visibility = if (index == null) View.GONE else View.VISIBLE
        binding.surveyWaypointDeleteButton.visibility = View.VISIBLE
        updateHeightLabel()
        updatePointCloudOverlay()
        if (index == null || previewMode == PreviewMode.POINT_CLOUD) return
        val point = if (workflow == PlanningWorkflow.POINTS) {
            viewModel.terrainRouteWaypoints.value.orEmpty().getOrNull(index)?.latLon
        } else viewModel.terrainSurveyWaypoints.value.orEmpty().getOrNull(index)?.latLon
            ?: viewModel.surveyPath.value.orEmpty().getOrNull(index)?.let { LatLon(it.latitude, it.longitude) }
        point?.let { positionNearMapPoint(LatLng(it.lat, it.lon)) }
        if (workflow == PlanningWorkflow.AREA) Toast.makeText(context, context.getString(R.string.survey_waypoint_selected, index + 1), Toast.LENGTH_SHORT).show()
    }

    fun moveSurvey(index: Int, point: LatLng) {
        viewModel.updateSurveyWaypoint(index, point, terrainSampler(point))
        updateDistance(viewModel.surveyPath.value.orEmpty())
        renderPath()
    }

    fun adjustHeight(deltaMeters: Double) {
        val index = selectedIndex ?: return
        val altitude = altitude(index) ?: return
        if (selectedWorkflow == PlanningWorkflow.POINTS) viewModel.updateTerrainRouteWaypointAltitude(index, altitude + deltaMeters)
        else viewModel.updateSurveyWaypointAltitude(index, altitude + deltaMeters)
        refreshAfterHeightChange()
    }

    fun applyHeightField() {
        val index = selectedIndex ?: return
        val altitude = binding.surveyWaypointHeightLabel.text?.toString()?.trim()?.toDoubleOrNull()
            ?: return updateHeightLabel()
        if (selectedWorkflow == PlanningWorkflow.POINTS) viewModel.updateTerrainRouteWaypointAltitude(index, altitude)
        else viewModel.updateSurveyWaypointAltitude(index, altitude)
        refreshAfterHeightChange()
    }

    fun refreshHeightLabel() = updateHeightLabel()

    fun deleteSelected(onPointRouteChanged: () -> Unit) {
        val index = selectedIndex ?: return
        if (selectedWorkflow == PlanningWorkflow.POINTS) {
            viewModel.removeTerrainRouteWaypoint(index)
            routeEditor.clearTerrainWaypointSelection()
            onPointRouteChanged()
        } else {
            viewModel.removeSurveyWaypoint(index)
            mapController.clearSelectedSurveyWaypoint()
            updateDistance(viewModel.surveyPath.value.orEmpty())
            renderPath()
        }
        Toast.makeText(context, R.string.survey_waypoint_deleted, Toast.LENGTH_SHORT).show()
    }

    fun cancelSelection() {
        mapController.clearSelectedSurveyWaypoint()
        routeEditor.clearTerrainWaypointSelection()
        select(selectedWorkflow, null, PreviewMode.MAP)
    }

    fun positionNearViewPoint(source: View, x: Float, y: Float) {
        val dock = binding.surveyWaypointEditDock
        dock.post {
            if (dock.visibility != View.VISIBLE) return@post
            val sourceLocation = IntArray(2); val rootLocation = IntArray(2)
            source.getLocationInWindow(sourceLocation); binding.root.getLocationInWindow(rootLocation)
            val margin = context.resources.getDimensionPixelSize(R.dimen.ds_space_sm).toFloat()
            dock.x = (sourceLocation[0] - rootLocation[0] + x + margin).coerceIn(margin, (binding.root.width - dock.width).toFloat().coerceAtLeast(margin))
            dock.y = (sourceLocation[1] - rootLocation[1] + y - dock.height / 2f).coerceIn(margin, (binding.root.height - dock.height).toFloat().coerceAtLeast(margin))
            dock.bringToFront()
        }
    }

    private fun refreshAfterHeightChange() {
        updateHeightLabel(); updatePointCloudOverlay(); renderPath()
    }

    private fun updateHeightLabel() {
        binding.surveyWaypointHeightLabel.setText(String.format(Locale.US, "%.1f", selectedIndex?.let(::altitude) ?: 0.0))
    }

    private fun altitude(index: Int): Double? {
        if (selectedWorkflow == PlanningWorkflow.POINTS) return viewModel.terrainRouteWaypoints.value.orEmpty().getOrNull(index)?.missionAltitudeMeters
        viewModel.terrainSurveyWaypoints.value.orEmpty().getOrNull(index)?.let { return it.missionAltitudeMeters }
        if (index !in viewModel.surveyPath.value.orEmpty().indices) return null
        return if (viewModel.planningOperationMode.value == PlanningOperationMode.SPRAY) viewModel.flightAltProgress.value ?: 0.0
            else viewModel.surveyHeightAboveTerrain.value ?: 0.0
    }

    private fun positionNearMapPoint(point: LatLng) {
        val pixel = binding.osmMap.projection.toPixels(GeoPoint(point.latitude, point.longitude), null)
        positionNearViewPoint(binding.osmMap, pixel.x.toFloat(), pixel.y.toFloat())
    }
}
