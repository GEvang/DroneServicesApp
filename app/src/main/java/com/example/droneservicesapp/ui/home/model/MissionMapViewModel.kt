package com.example.droneservicesapp.ui.home.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData
import com.example.droneservicesapp.ui.map.model.MapCamera
import com.example.droneservicesapp.ui.map.model.MapCoordinate
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

    /** LiveData mirror consumed by the mission controller's lifecycle-scoped observers. */
    val homeMapUiState = MutableLiveData(mutableHomeMapUiState.value)

    /** Whether [camera] holds a camera the operator actually used, rather than the default. */
    var hasRememberedCamera: Boolean = false
        private set

    /** Map camera kept across navigation so the map reopens where the operator left it. */
    var camera: MapCamera = DEFAULT_CAMERA
        set(value) {
            field = value
            hasRememberedCamera = true
        }

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

    companion object {
        /** The previous Fragment's default operating area and zoom. */
        val DEFAULT_CAMERA = MapCamera(MapCoordinate(35.3643003, 24.4721854), 18.0)
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
