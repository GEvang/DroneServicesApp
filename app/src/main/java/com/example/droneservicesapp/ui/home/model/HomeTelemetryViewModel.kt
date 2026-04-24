package com.example.droneservicesapp.ui.home.model

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeTelemetryViewModel : ViewModel() {
    val homeTelemetryUiState: MutableLiveData<HomeTelemetryUiState> = MutableLiveData(
        HomeTelemetryUiState(
            connectionText = "",
            armedText = ""
        )
    )
}
