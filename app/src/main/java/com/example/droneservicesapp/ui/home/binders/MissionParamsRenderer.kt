package com.example.droneservicesapp.ui.home.binders

import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.ui.home.model.MissionParamsUiState
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

class MissionParamsRenderer(
    private val views: MissionParamsViews,
    private val lifecycleOwner: LifecycleOwner,
    private val activityViewModel: MainActivityViewModel,
    private val stateMapper: MissionParamsStateMapper,
) {
    private var missionParamsUiState = MissionParamsUiState(
        angle = 1,
        lineDistance = 1,
        altitude = 2,
        sprayerIntensity = 0,
        flightSpeed = 1,
        estimatedFlightMinutes = 1
    )

    fun bind() {
        missionParamsUiState = stateMapper.currentUiState()
        renderFlightSummary(missionParamsUiState)

        activityViewModel.flightSpeed.observe(lifecycleOwner) { flightSpeed ->
            missionParamsUiState = missionParamsUiState.copy(flightSpeed = flightSpeed.toInt())
            renderFlightSummary(missionParamsUiState)
        }

        activityViewModel.estimatedFlightMinutes.observe(lifecycleOwner) { minutes ->
            missionParamsUiState = missionParamsUiState.copy(estimatedFlightMinutes = minutes)
            renderFlightSummary(missionParamsUiState)
        }
    }

    private fun renderFlightSummary(state: MissionParamsUiState) {
        views.flightSpeedValue.text = state.flightSpeed.toString()
        views.flightTimeValue.text = state.estimatedFlightMinutes.toString()
    }
}
