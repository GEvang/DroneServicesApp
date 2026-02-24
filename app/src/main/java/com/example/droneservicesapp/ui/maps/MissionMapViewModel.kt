package com.example.droneservicesapp.ui.maps

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.droneservicesapp.ui.main.MainActivityViewModel

class MissionMapViewModel : ViewModel() {
    
    enum class UiState {
        Idle,
        Drawing,
        EditingParams,
        SavingMission,
        LoadingMission
    }
    
    val uiState: MutableLiveData<UiState> = MutableLiveData(UiState.Idle)
    
    fun setState(state: UiState) {
        uiState.postValue(state)
    }
    
    fun updateFromMapState(mapState: MainActivityViewModel.MapState) {
        val newState = when (mapState) {
            MainActivityViewModel.MapState.Idle,
            MainActivityViewModel.MapState.Reset -> UiState.Idle

            MainActivityViewModel.MapState.Draw,
            MainActivityViewModel.MapState.ClearKeepDrawing,
            MainActivityViewModel.MapState.ClearAll -> UiState.Drawing

            MainActivityViewModel.MapState.SetFlightParams -> UiState.EditingParams
            MainActivityViewModel.MapState.SaveMissionToFile -> UiState.SavingMission
            MainActivityViewModel.MapState.LoadMissionFromFile -> UiState.LoadingMission

            MainActivityViewModel.MapState.UploadMissionSuccess -> UiState.Idle
        }
        uiState.postValue(newState)
    }
}