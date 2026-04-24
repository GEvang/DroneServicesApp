package com.example.droneservicesapp.ui.home.model

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

class MissionMapViewModel : ViewModel() {
    private var currentScreenMode: HomeMapScreenMode = HomeMapScreenMode.Idle
    private var isPlanningPanelVisible: Boolean = false
    private var hasMissionArea: Boolean = false

    val homeMapUiState: MutableLiveData<HomeMapUiState> =
        MutableLiveData(
            HomeMapUiState.forState(
                screenMode = currentScreenMode,
                isPlanningPanelVisible = isPlanningPanelVisible,
                hasMissionArea = hasMissionArea
            )
        )

    fun updateFromMapState(mapState: MainActivityViewModel.MapState) {
        currentScreenMode = when (mapState) {
            MainActivityViewModel.MapState.Idle -> HomeMapScreenMode.Idle
            MainActivityViewModel.MapState.Draw -> HomeMapScreenMode.Drawing
            MainActivityViewModel.MapState.SetFlightParams -> HomeMapScreenMode.EditingParams
            MainActivityViewModel.MapState.SaveMissionToFile -> HomeMapScreenMode.SavingMission
            MainActivityViewModel.MapState.LoadMissionFromFile -> HomeMapScreenMode.LoadingMission
        }
        publishState()
    }

    fun setPlanningPanelVisible(isVisible: Boolean) {
        isPlanningPanelVisible = isVisible
        publishState()
    }

    fun togglePlanningPanelVisible() {
        isPlanningPanelVisible = !isPlanningPanelVisible
        publishState()
    }

    fun setMissionAreaAvailable(isAvailable: Boolean) {
        hasMissionArea = isAvailable
        publishState()
    }

    private fun publishState() {
        homeMapUiState.postValue(
            HomeMapUiState.forState(
                screenMode = currentScreenMode,
                isPlanningPanelVisible = isPlanningPanelVisible,
                hasMissionArea = hasMissionArea
            )
        )
    }
}
