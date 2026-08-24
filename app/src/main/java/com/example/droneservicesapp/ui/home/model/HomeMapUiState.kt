package com.example.droneservicesapp.ui.home.model

import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.PlanningOperationMode

data class HomeMapUiState(
    val screenMode: HomeMapScreenMode,
    val shellState: HomeMapShellUiState,
    val panelState: MissionPanelUiState,
    val overlayControlsState: MapOverlayControlsUiState,
    val interactionState: HomeMapInteractionUiState,
) {
    companion object {
        fun forState(
            screenMode: HomeMapScreenMode,
            isPlanningPanelVisible: Boolean,
            hasMissionArea: Boolean,
            arePanelsDismissed: Boolean,
        ): HomeMapUiState {
            val shellState = HomeMapShellUiState(
                isLeftPanelVisible = hasMissionArea &&
                    !arePanelsDismissed &&
                    screenMode != HomeMapScreenMode.SavingMission &&
                    screenMode != HomeMapScreenMode.LoadingMission,
                isRightPanelVisible = isPlanningPanelVisible &&
                    !arePanelsDismissed &&
                    screenMode != HomeMapScreenMode.SavingMission &&
                    screenMode != HomeMapScreenMode.LoadingMission,
                isBottomUtilityBarVisible = true,
                isPlanningActive = screenMode == HomeMapScreenMode.Drawing ||
                    screenMode == HomeMapScreenMode.EditingParams
            )
            return when (screenMode) {
                HomeMapScreenMode.Idle -> HomeMapUiState(
                    screenMode = screenMode,
                    shellState = shellState,
                    panelState = MissionPanelUiState.none(),
                    overlayControlsState = MapOverlayControlsUiState.defaultVisible(),
                    interactionState = HomeMapInteractionUiState(
                        isDrawingEnabled = false,
                        isBottomActionBarVisible = true,
                        isLegacyBottomActionBarVisible = false
                    )
                )
                HomeMapScreenMode.Drawing -> HomeMapUiState(
                    screenMode = screenMode,
                    shellState = shellState.copy(
                        isLeftPanelVisible = false,
                        isRightPanelVisible = false,
                        isBottomUtilityBarVisible = false
                    ),
                    panelState = MissionPanelUiState.none(),
                    overlayControlsState = MapOverlayControlsUiState.defaultVisible(),
                    interactionState = HomeMapInteractionUiState(
                        isDrawingEnabled = true,
                        isBottomActionBarVisible = false,
                        isLegacyBottomActionBarVisible = false,
                        isDrawActionButtonsVisible = true
                    )
                )
                HomeMapScreenMode.EditingParams -> HomeMapUiState(
                    screenMode = screenMode,
                    shellState = shellState,
                    panelState = MissionPanelUiState(
                        activePanel = MissionPanelUiState.ActivePanel.None,
                        consumesTouch = true
                    ),
                    overlayControlsState = MapOverlayControlsUiState.defaultVisible(),
                    interactionState = HomeMapInteractionUiState(
                        isDrawingEnabled = false,
                        isBottomActionBarVisible = true,
                        isLegacyBottomActionBarVisible = false
                    )
                )
                HomeMapScreenMode.SavingMission -> HomeMapUiState(
                    screenMode = screenMode,
                    shellState = shellState.copy(
                        isLeftPanelVisible = false,
                        isRightPanelVisible = false
                    ),
                    panelState = MissionPanelUiState(
                        activePanel = MissionPanelUiState.ActivePanel.SaveMission,
                        consumesTouch = true
                    ),
                    overlayControlsState = MapOverlayControlsUiState.defaultVisible(),
                    interactionState = HomeMapInteractionUiState(
                        isDrawingEnabled = false,
                        isBottomActionBarVisible = true,
                        isLegacyBottomActionBarVisible = false
                    )
                )
                HomeMapScreenMode.LoadingMission -> HomeMapUiState(
                    screenMode = screenMode,
                    shellState = shellState.copy(
                        isLeftPanelVisible = false,
                        isRightPanelVisible = false
                    ),
                    panelState = MissionPanelUiState(
                        activePanel = MissionPanelUiState.ActivePanel.LoadMission,
                        consumesTouch = true
                    ),
                    overlayControlsState = MapOverlayControlsUiState.defaultVisible(),
                    interactionState = HomeMapInteractionUiState(
                        isDrawingEnabled = false,
                        isBottomActionBarVisible = true,
                        isLegacyBottomActionBarVisible = false
                    )
                )
            }
        }
    }
}

data class HomeMapShellUiState(
    val isLeftPanelVisible: Boolean,
    val isRightPanelVisible: Boolean,
    val isBottomUtilityBarVisible: Boolean,
    val isPlanningActive: Boolean,
)

enum class HomeMapScreenMode {
    Idle,
    Drawing,
    EditingParams,
    SavingMission,
    LoadingMission,
}

data class MissionPanelUiState(
    val activePanel: ActivePanel,
    val consumesTouch: Boolean,
) {
    enum class ActivePanel {
        None,
        MissionParams,
        SaveMission,
        LoadMission,
    }

    companion object {
        fun none(): MissionPanelUiState = MissionPanelUiState(
            activePanel = ActivePanel.None,
            consumesTouch = false
        )
    }
}

data class MapOverlayControlsUiState(
    val showMyLocationButton: Boolean,
    val showDroneLocationButton: Boolean,
    val showDownloadOfflineButton: Boolean,
) {
    companion object {
        fun defaultVisible(): MapOverlayControlsUiState = MapOverlayControlsUiState(
            showMyLocationButton = true,
            showDroneLocationButton = true,
            showDownloadOfflineButton = true
        )

        fun hidden(): MapOverlayControlsUiState = MapOverlayControlsUiState(
            showMyLocationButton = false,
            showDroneLocationButton = false,
            showDownloadOfflineButton = false
        )
    }
}

data class HomeMapInteractionUiState(
    val isDrawingEnabled: Boolean,
    val isBottomActionBarVisible: Boolean,
    val isLegacyBottomActionBarVisible: Boolean = false,
    val isDrawActionButtonsVisible: Boolean = false,
)

data class MissionParamsUiState(
    val operationMode: PlanningOperationMode,
    val angle: Int,
    val lineDistance: Int,
    val altitude: Int,
    val sprayerIntensity: Int,
    val surveyStripSpacing: Int,
    val surveyHeightAboveTerrain: Int,
    val surveyOverlap: Int,
    val surveyGridAngle: Int,
    val surveyTerrainSegment: Double,
    val surveyCanopySmoothing: Int,
    val flightSpeed: Double,
    val estimatedFlightMinutes: Int,
    val altitudeReferenceMode: AltitudeReferenceMode = AltitudeReferenceMode.RELATIVE,
)

data class SaveMissionUiState(
    val isVisible: Boolean,
    val fileName: String = "",
    val overwriteExisting: Boolean = false,
)

data class LoadMissionUiState(
    val isVisible: Boolean,
    val availableFileCount: Int = 0,
)

data class TelemetrySummaryUiState(
    val isDroneConnected: Boolean,
    val frontDistanceMeters: Int? = null,
    val backDistanceMeters: Int? = null,
)
