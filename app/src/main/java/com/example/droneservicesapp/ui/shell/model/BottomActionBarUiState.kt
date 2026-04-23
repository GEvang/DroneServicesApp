package com.example.droneservicesapp.ui.shell.model

data class BottomActionBarUiState(
    val showCancel: Boolean,
    val showAccept: Boolean,
    val showErase: Boolean,
    val showDraw: Boolean,
    val showLoad: Boolean,
    val preserveExistingMenu: Boolean = false,
) {
    companion object {
        fun fromMapState(mapState: MainActivityViewModel.MapState): BottomActionBarUiState {
            return when (mapState) {
                MainActivityViewModel.MapState.Idle -> BottomActionBarUiState(
                    showCancel = false,
                    showAccept = false,
                    showErase = false,
                    showDraw = true,
                    showLoad = true
                )
                MainActivityViewModel.MapState.Draw,
                MainActivityViewModel.MapState.SetFlightParams,
                MainActivityViewModel.MapState.LoadMissionFromFile -> BottomActionBarUiState(
                    showCancel = true,
                    showAccept = true,
                    showErase = true,
                    showDraw = false,
                    showLoad = false
                )
                MainActivityViewModel.MapState.SaveMissionToFile -> BottomActionBarUiState(
                    showCancel = false,
                    showAccept = false,
                    showErase = false,
                    showDraw = false,
                    showLoad = false,
                    preserveExistingMenu = true
                )
            }
        }
    }
}
