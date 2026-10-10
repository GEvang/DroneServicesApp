package com.example.droneservicesapp.ui.home.model

import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.terrain.PointCloudCoverage
import com.example.droneservicesapp.mavserver.TerrainProvisioningState

sealed interface MissionParamsAction {
    enum class NumericField {
        ANGLE,
        LINE_DISTANCE,
        ALTITUDE,
        TAKEOFF_HEIGHT,
        SPRAYER_INTENSITY,
        SURVEY_STRIP_SPACING,
        SURVEY_HEIGHT,
        SURVEY_OVERLAP,
        SURVEY_GRID_ANGLE,
        SURVEY_TERRAIN_SEGMENT,
        SURVEY_CANOPY_SMOOTHING,
        FLIGHT_SPEED,
    }

    data class SetOperationMode(val mode: PlanningOperationMode) : MissionParamsAction
    data class SetAltitudeReference(val mode: AltitudeReferenceMode) : MissionParamsAction
    data class UpdateNumericField(val field: NumericField, val value: Double) : MissionParamsAction
    data class SelectSprayPreset(val presetId: String) : MissionParamsAction
    data object DownloadTerrain : MissionParamsAction
    data object UploadMission : MissionParamsAction
    data object StartMission : MissionParamsAction
    data object ResumeMission : MissionParamsAction
}

data class MissionParamsPresentation(
    val values: MissionParamsUiState,
    val showSurveyFields: Boolean,
    val showSprayFields: Boolean,
    val showAltitudeReference: Boolean,
    val showTerrainProfileFields: Boolean,
    val requiredAltitudeReference: AltitudeReferenceMode?,
    val terrainDownloadVisible: Boolean,
    val terrainDownloadEnabled: Boolean,
    val missionUploadEnabled: Boolean,
    val terrainProgressPercent: Int?,
)

object MissionParamsPresenter {
    fun present(
        state: MissionParamsUiState,
        workflow: PlanningWorkflow,
        pointCloudCoverage: PointCloudCoverage,
        terrainState: TerrainProvisioningState,
        droneConnected: Boolean,
        sourceReadyMatchesMission: Boolean,
    ): MissionParamsPresentation {
        val survey = state.operationMode == PlanningOperationMode.SURVEY
        val pointRoute = workflow == PlanningWorkflow.POINTS
        val threeDimensionalSpray = !survey && pointCloudCoverage == PointCloudCoverage.COMPLETE
        val usesFlightControllerTerrain = survey &&
            pointCloudCoverage != PointCloudCoverage.COMPLETE &&
            state.altitudeReferenceMode == AltitudeReferenceMode.TERRAIN
        val effectiveTerrainState = terrainState.takeIf { usesFlightControllerTerrain }
            ?: TerrainProvisioningState.Idle
        val sourceReady = effectiveTerrainState is TerrainProvisioningState.SourceReady &&
            sourceReadyMatchesMission
        val progress = when (effectiveTerrainState) {
            is TerrainProvisioningState.PreparingSource -> effectiveTerrainState.progressPercent.coerceIn(0, 100)
            is TerrainProvisioningState.SourceReady -> 100.takeIf { sourceReady }
            TerrainProvisioningState.WaitingForMissionUpload,
            is TerrainProvisioningState.Serving,
            is TerrainProvisioningState.Verifying,
            is TerrainProvisioningState.Ready -> 100
            TerrainProvisioningState.Idle,
            is TerrainProvisioningState.Failed -> null
        }
        return MissionParamsPresentation(
            values = state,
            showSurveyFields = survey,
            showSprayFields = !survey,
            showAltitudeReference = survey,
            showTerrainProfileFields = threeDimensionalSpray || pointRoute,
            requiredAltitudeReference = if (survey) null else if (threeDimensionalSpray) {
                AltitudeReferenceMode.RELATIVE
            } else {
                AltitudeReferenceMode.TERRAIN
            },
            terrainDownloadVisible = usesFlightControllerTerrain && !droneConnected && !sourceReady,
            terrainDownloadEnabled = usesFlightControllerTerrain && !droneConnected && !sourceReady &&
                effectiveTerrainState !is TerrainProvisioningState.PreparingSource,
            missionUploadEnabled = effectiveTerrainState is TerrainProvisioningState.Idle ||
                effectiveTerrainState is TerrainProvisioningState.SourceReady ||
                effectiveTerrainState is TerrainProvisioningState.Ready ||
                effectiveTerrainState is TerrainProvisioningState.Failed,
            terrainProgressPercent = progress,
        )
    }
}
