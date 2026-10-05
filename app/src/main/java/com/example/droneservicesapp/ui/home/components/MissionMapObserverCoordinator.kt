package com.example.droneservicesapp.ui.home.components

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

/** Centralizes parameter observers and the conditions that trigger mission presentation updates. */
class MissionMapObserverCoordinator(
    private val lifecycleOwner: LifecycleOwner,
    private val viewModel: MainActivityViewModel,
) {
    data class Actions(
        val stopSimulation: () -> Unit,
        val scheduleAreaRedraw: () -> Unit,
        val flightAltitudeChanged: (Double) -> Unit,
        val surveyHeightChanged: () -> Unit,
        val surveyGridChanged: () -> Unit,
        val planningModeChanged: () -> Unit,
        val updateTerrainOverlay: () -> Unit,
        val updateMissionSummary: () -> Unit,
        val renderPreview: () -> Unit,
    )

    fun bindParameterObservers(actions: Actions) {
        viewModel.angleProgress.observe(lifecycleOwner) { redrawForSprayParameter(actions) }
        viewModel.lineDistanceProgress.observe(lifecycleOwner) { redrawForSprayParameter(actions) }
        viewModel.flightSpeed.observe(lifecycleOwner) {
            redrawForSprayParameter(actions)
            actions.updateMissionSummary()
        }
        viewModel.sprayerProgress.observe(lifecycleOwner) {
            redrawForSprayParameter(actions)
            actions.updateMissionSummary()
        }
        viewModel.flightAltProgress.observe(lifecycleOwner) { altitude ->
            actions.stopSimulation()
            actions.flightAltitudeChanged(altitude)
        }
        viewModel.surveyHeightAboveTerrain.observe(lifecycleOwner) {
            actions.stopSimulation()
            actions.surveyHeightChanged()
        }
        viewModel.surveyGridParams.observe(lifecycleOwner) {
            actions.stopSimulation()
            actions.surveyGridChanged()
        }
        viewModel.planningOperationMode.observe(lifecycleOwner) {
            actions.stopSimulation()
            actions.planningModeChanged()
            actions.updateMissionSummary()
            actions.updateTerrainOverlay()
            actions.renderPreview()
        }
        viewModel.altitudeReferenceMode.observe(lifecycleOwner) { actions.updateTerrainOverlay() }
        viewModel.pointCloudCoverage.observe(lifecycleOwner) { actions.updateTerrainOverlay() }
    }

    fun <T> observe(source: LiveData<T>, reaction: (T) -> Unit) {
        source.observe(lifecycleOwner, reaction)
    }

    private fun redrawForSprayParameter(actions: Actions) {
        actions.stopSimulation()
        if (
            viewModel.mapState.value == MainActivityViewModel.MapState.SetFlightParams &&
            viewModel.activePlanningWorkflow.value == PlanningWorkflow.AREA &&
            viewModel.planningOperationMode.value == PlanningOperationMode.SPRAY
        ) actions.scheduleAreaRedraw()
    }
}
