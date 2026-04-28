package com.example.droneservicesapp.ui.home.binders

import com.example.droneservicesapp.ui.home.model.MissionParamsUiState
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

class MissionParamsStateMapper(
    private val activityViewModel: MainActivityViewModel,
) {
    fun currentUiState(): MissionParamsUiState {
        return MissionParamsUiState(
            angle = activityViewModel.angleProgress.value?.toInt() ?: 1,
            lineDistance = activityViewModel.lineDistanceProgress.value?.toInt() ?: 1,
            altitude = activityViewModel.flightAltProgress.value?.toInt() ?: 2,
            sprayerIntensity = activityViewModel.sprayerProgress.value?.toInt() ?: 0,
            flightSpeed = activityViewModel.flightSpeed.value?.toInt() ?: 1,
            estimatedFlightMinutes = activityViewModel.estimatedFlightMinutes.value ?: 1
        )
    }
}
