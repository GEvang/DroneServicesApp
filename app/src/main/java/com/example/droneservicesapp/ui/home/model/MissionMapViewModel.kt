package com.example.droneservicesapp.ui.home.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MissionMapViewModel : ViewModel() {
    private var currentScreenMode: HomeMapScreenMode = HomeMapScreenMode.Idle
    private var isPlanningPanelVisible: Boolean = false
    private var hasMissionArea: Boolean = false
    private var arePanelsDismissed: Boolean = false

    private val mutableHomeMapUiState = MutableStateFlow(
        HomeMapUiState.forState(
            screenMode = currentScreenMode,
            isPlanningPanelVisible = isPlanningPanelVisible,
            hasMissionArea = hasMissionArea,
            arePanelsDismissed = arePanelsDismissed,
        )
    )

    /** Primary state API for Compose. */
    val homeMapUiStateFlow = mutableHomeMapUiState.asStateFlow()

    /** Temporary compatibility mirror for the current Fragment. */
    val homeMapUiState = MutableLiveData(mutableHomeMapUiState.value)

    fun updateFromMapState(mapState: MainActivityViewModel.MapState) {
        if (mapState == MainActivityViewModel.MapState.SetFlightParams) {
            isPlanningPanelVisible = true
            arePanelsDismissed = false
        }
        currentScreenMode = when (mapState) {
            MainActivityViewModel.MapState.Idle -> HomeMapScreenMode.Idle
            MainActivityViewModel.MapState.Draw -> HomeMapScreenMode.Drawing
            MainActivityViewModel.MapState.SetFlightParams -> HomeMapScreenMode.EditingParams
            MainActivityViewModel.MapState.SaveMissionToFile -> HomeMapScreenMode.SavingMission
            MainActivityViewModel.MapState.LoadMissionFromFile -> HomeMapScreenMode.LoadingMission
        }
        publishState()
    }

    /** Restores map mode after navigation without reopening stale side panels. */
    fun restoreFromMapState(mapState: MainActivityViewModel.MapState) {
        isPlanningPanelVisible = false
        arePanelsDismissed = true
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
        if (isVisible) {
            arePanelsDismissed = false
        }
        publishState()
    }

    fun togglePlanningPanelVisible() {
        val hasVisibleSidePanel = isPlanningPanelVisible || (hasMissionArea && !arePanelsDismissed)
        if (hasVisibleSidePanel) {
            dismissSidePanels()
            return
        }
        isPlanningPanelVisible = true
        arePanelsDismissed = false
        publishState()
    }

    fun dismissSidePanels() {
        isPlanningPanelVisible = false
        arePanelsDismissed = true
        publishState()
    }

    fun setMissionAreaAvailable(isAvailable: Boolean) {
        hasMissionArea = isAvailable
        if (!isAvailable) {
            arePanelsDismissed = false
        }
        publishState()
    }

    private fun publishState() {
        val state = HomeMapUiState.forState(
            screenMode = currentScreenMode,
            isPlanningPanelVisible = isPlanningPanelVisible,
            hasMissionArea = hasMissionArea,
            arePanelsDismissed = arePanelsDismissed,
        )
        mutableHomeMapUiState.value = state
        homeMapUiState.value = state
    }
}
