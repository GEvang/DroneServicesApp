package com.example.droneservicesapp.ui.home.model

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

class MissionMapViewModel : ViewModel() {

    val homeMapUiState: MutableLiveData<HomeMapUiState> =
        MutableLiveData(HomeMapUiState.forScreenMode(HomeMapScreenMode.Idle))

    fun updateFromMapState(mapState: MainActivityViewModel.MapState) {
        val screenMode = when (mapState) {
            MainActivityViewModel.MapState.Idle -> HomeMapScreenMode.Idle
            MainActivityViewModel.MapState.Draw -> HomeMapScreenMode.Drawing
            MainActivityViewModel.MapState.SetFlightParams -> HomeMapScreenMode.EditingParams
            MainActivityViewModel.MapState.SaveMissionToFile -> HomeMapScreenMode.SavingMission
            MainActivityViewModel.MapState.LoadMissionFromFile -> HomeMapScreenMode.LoadingMission
        }
        homeMapUiState.postValue(HomeMapUiState.forScreenMode(screenMode))
    }
}
