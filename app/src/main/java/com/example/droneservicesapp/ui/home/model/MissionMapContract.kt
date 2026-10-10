package com.example.droneservicesapp.ui.home.model

import com.example.droneservicesapp.ui.architecture.CommonUiEffect
import com.example.droneservicesapp.ui.preview.PreviewMode

sealed interface MissionMapAction {
    enum class ObstacleShape { CIRCLE, POLYGON }

    data object DownloadOffline : MissionMapAction
    data object CenterOnOperator : MissionMapAction
    data object CenterOnDrone : MissionMapAction
    data object ToggleObstacles : MissionMapAction
    data object StartDroneOffset : MissionMapAction
    data object CyclePreviewMode : MissionMapAction
    data object OpenSettings : MissionMapAction
    data object TogglePlanning : MissionMapAction
    data object LoadMission : MissionMapAction
    data object AddPlannedHome : MissionMapAction
    data object SimulateMission : MissionMapAction
    data object ResumeMission : MissionMapAction
    data object ClosePanel : MissionMapAction
    data object AcceptGeometry : MissionMapAction
    data object DeclineGeometry : MissionMapAction
    data object DrawGeometry : MissionMapAction
    data object ClearGeometry : MissionMapAction
    data object AddObstacle : MissionMapAction
    data object ClearObstacles : MissionMapAction
    data class SelectObstacleShape(val shape: ObstacleShape) : MissionMapAction
    data class SelectPreviewMode(val mode: PreviewMode) : MissionMapAction
    data class SetObstacleRadius(val meters: Int) : MissionMapAction
    data class SetPreviewOpacity(val opacity: Float) : MissionMapAction
    data class SetPreviewBackground(val light: Boolean) : MissionMapAction
    data object TogglePreviewHeightColors : MissionMapAction
    data object RunPreviewPrimaryAction : MissionMapAction
    data object RunPreviewSecondaryAction : MissionMapAction
    data object DeleteSelectedWaypoint : MissionMapAction
    data object CancelSelectedWaypoint : MissionMapAction
    data class ChangeSelectedWaypointHeight(val deltaMeters: Int) : MissionMapAction
    data object ApplySelectedWaypointHeight : MissionMapAction
}

sealed interface MissionMapEffect {
    data class Platform(val effect: CommonUiEffect) : MissionMapEffect
    data class Execute(val action: MissionMapAction) : MissionMapEffect
}
