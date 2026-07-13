package com.example.droneservicesapp.ui.home.binders

import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.ui.home.model.MissionParamsUiState
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

class MissionParamsStateMapper(
    private val activityViewModel: MainActivityViewModel,
) {
    fun currentUiState(): MissionParamsUiState {
        return MissionParamsUiState(
            operationMode = activityViewModel.planningOperationMode.value ?: PlanningOperationMode.SURVEY,
            angle = activityViewModel.angleProgress.value?.toInt() ?: 1,
            lineDistance = activityViewModel.lineDistanceProgress.value?.toInt() ?: 1,
            altitude = activityViewModel.flightAltProgress.value?.toInt() ?: 2,
            sprayerIntensity = activityViewModel.sprayerProgress.value?.toInt() ?: 0,
            surveyStripSpacing = activityViewModel.surveyStripSpacing.value?.toInt() ?: 8,
            surveyHeightAboveTerrain = activityViewModel.surveyHeightAboveTerrain.value?.toInt() ?: 5,
            surveyOverlap = activityViewModel.surveyOverlapPercent.value?.toInt() ?: 20,
            surveyGridAngle = activityViewModel.surveyGridAngle.value?.toInt() ?: 0,
            surveyTerrainSegment = activityViewModel.surveyTerrainSegment.value ?: 2.5,
            surveyCanopySmoothing = activityViewModel.surveyCanopySmoothing.value?.toInt() ?: 5,
            flightSpeed = activityViewModel.flightSpeed.value ?: 1.0,
            estimatedFlightMinutes = activityViewModel.estimatedFlightMinutes.value ?: 1,
            altitudeReferenceMode = activityViewModel.altitudeReferenceMode.value
                ?: com.example.droneservicesapp.domain.model.AltitudeReferenceMode.RELATIVE
        )
    }
}
