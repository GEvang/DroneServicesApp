package com.example.droneservicesapp.ui.home.components

import android.content.Context
import android.view.View
import android.widget.Toast
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.geoawareness.logging.OperatorFlightEventLogger
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel.ServiceMissionState
import com.google.android.material.snackbar.Snackbar

/** Applies one-shot map actions and owns their user-facing completion reactions. */
class MissionMapActionHandler(
    private val context: Context,
    private val root: View,
    private val viewModel: MainActivityViewModel,
    private val eventLogger: OperatorFlightEventLogger,
    private val cancelPlanning: () -> Unit,
    private val cancelObstaclePlacement: () -> Unit,
    private val clearEditors: () -> Unit,
) {
    fun handle(action: MainActivityViewModel.MapAction) {
        when (action) {
            is MainActivityViewModel.MapAction.ClearAll -> clear(resetHome = true, obstacles = true, routes = true, next = MainActivityViewModel.MapState.Draw)
            is MainActivityViewModel.MapAction.ClearAreaOnly -> clear(next = MainActivityViewModel.MapState.Idle)
            is MainActivityViewModel.MapAction.ClearKeepDrawing -> clear(
                clearPolygon = false,
                next = MainActivityViewModel.MapState.Draw,
            )
            is MainActivityViewModel.MapAction.ResetToIdle -> {
                viewModel.clearServiceMission()
                clear(resetHome = true, obstacles = true, routes = true, next = MainActivityViewModel.MapState.Idle)
            }
            is MainActivityViewModel.MapAction.UploadMissionSuccess -> uploadSucceeded()
            is MainActivityViewModel.MapAction.UploadMissionFailed -> uploadFailed(action.reason)
        }
    }

    private fun clear(
        resetHome: Boolean = false,
        obstacles: Boolean = false,
        routes: Boolean = false,
        clearPolygon: Boolean = true,
        next: MainActivityViewModel.MapState,
    ) {
        cancelPlanning()
        cancelObstaclePlacement()
        if (resetHome) viewModel.clearPlannedHomePosition()
        if (clearPolygon) viewModel.clearPolygonVertices()
        if (obstacles) viewModel.clearMissionObstacles()
        if (routes) viewModel.clearRouteWaypoints()
        viewModel.surveyPath.postValue(emptyList())
        viewModel.clearPointCloudMissionProfile()
        clearEditors()
        viewModel.mapState.postValue(next)
    }

    private fun uploadSucceeded() {
        viewModel.confirmMissionUpload()
        eventLogger.logMissionUploadSucceeded(viewModel.surveyPath.value?.size)
        val serviceAfter = viewModel.currentServiceMissionLeg()?.serviceAfter
        if (viewModel.markServiceLegUploadSucceeded()) {
            Snackbar.make(root, context.getString(if (serviceAfter == null) R.string.mission_final_leg_uploaded else R.string.mission_leg_uploaded), Snackbar.LENGTH_LONG).show()
        } else {
            Snackbar.make(root, context.getString(R.string.upload_complete), Snackbar.LENGTH_LONG).show()
            viewModel.sendAction(MainActivityViewModel.MapAction.ResetToIdle)
        }
    }

    private fun uploadFailed(reason: String) {
        viewModel.cancelMissionUpload()
        if (viewModel.serviceMissionState.value == ServiceMissionState.LEG_UPLOADING) viewModel.markServiceLegUploadFailed()
        eventLogger.logMissionUploadFailed(reason)
        Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
        Snackbar.make(root, context.getString(R.string.upload_failed_with_reason, reason), Snackbar.LENGTH_LONG).show()
    }
}
