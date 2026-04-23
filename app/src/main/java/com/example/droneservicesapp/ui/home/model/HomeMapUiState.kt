package com.example.droneservicesapp.ui.home.model

data class HomeMapUiState(
    val screenMode: HomeMapScreenMode,
    val panelState: MissionPanelUiState,
    val overlayControlsState: MapOverlayControlsUiState,
    val interactionState: HomeMapInteractionUiState,
) {
    companion object {
        fun forScreenMode(screenMode: HomeMapScreenMode): HomeMapUiState {
            return when (screenMode) {
                HomeMapScreenMode.Idle -> HomeMapUiState(
                    screenMode = screenMode,
                    panelState = MissionPanelUiState.none(),
                    overlayControlsState = MapOverlayControlsUiState.defaultVisible(),
                    interactionState = HomeMapInteractionUiState(
                        isDrawingEnabled = false,
                        isBottomActionBarVisible = true
                    )
                )
                HomeMapScreenMode.Drawing -> HomeMapUiState(
                    screenMode = screenMode,
                    panelState = MissionPanelUiState.none(),
                    overlayControlsState = MapOverlayControlsUiState.defaultVisible(),
                    interactionState = HomeMapInteractionUiState(
                        isDrawingEnabled = true,
                        isBottomActionBarVisible = true
                    )
                )
                HomeMapScreenMode.EditingParams -> HomeMapUiState(
                    screenMode = screenMode,
                    panelState = MissionPanelUiState(
                        activePanel = MissionPanelUiState.ActivePanel.MissionParams,
                        consumesTouch = true
                    ),
                    overlayControlsState = MapOverlayControlsUiState.hidden(),
                    interactionState = HomeMapInteractionUiState(
                        isDrawingEnabled = false,
                        isBottomActionBarVisible = true
                    )
                )
                HomeMapScreenMode.SavingMission -> HomeMapUiState(
                    screenMode = screenMode,
                    panelState = MissionPanelUiState(
                        activePanel = MissionPanelUiState.ActivePanel.SaveMission,
                        consumesTouch = true
                    ),
                    overlayControlsState = MapOverlayControlsUiState.hidden(),
                    interactionState = HomeMapInteractionUiState(
                        isDrawingEnabled = false,
                        isBottomActionBarVisible = true
                    )
                )
                HomeMapScreenMode.LoadingMission -> HomeMapUiState(
                    screenMode = screenMode,
                    panelState = MissionPanelUiState(
                        activePanel = MissionPanelUiState.ActivePanel.LoadMission,
                        consumesTouch = true
                    ),
                    overlayControlsState = MapOverlayControlsUiState.hidden(),
                    interactionState = HomeMapInteractionUiState(
                        isDrawingEnabled = false,
                        isBottomActionBarVisible = true
                    )
                )
            }
        }
    }
}

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
)

data class MissionParamsUiState(
    val angle: Int,
    val lineDistance: Int,
    val altitude: Int,
    val sprayerIntensity: Int,
    val flightSpeed: Int,
    val estimatedFlightMinutes: Int,
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
