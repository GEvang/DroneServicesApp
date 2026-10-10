package com.example.droneservicesapp.ui.home.model

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeTelemetryViewModel : ViewModel() {
    private val initialState = HomeTelemetryUiState(
        connectionText = "",
        armedText = "",
    )
    private val mutableHomeTelemetryUiState = MutableStateFlow(initialState)
    private val legacyHomeTelemetryUiState = MutableLiveData(initialState)

    /** Primary state API for Compose. */
    val homeTelemetryUiStateFlow = mutableHomeTelemetryUiState.asStateFlow()

    /** Temporary compatibility mirror for XML toolbar and popup binders. */
    val homeTelemetryUiState: LiveData<HomeTelemetryUiState> = legacyHomeTelemetryUiState

    fun updateUiState(state: HomeTelemetryUiState) {
        mutableHomeTelemetryUiState.value = state
        legacyHomeTelemetryUiState.value = state
    }
}
