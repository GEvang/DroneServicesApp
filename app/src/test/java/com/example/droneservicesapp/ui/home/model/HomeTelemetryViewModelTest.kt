package com.example.droneservicesapp.ui.home.model

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeTelemetryViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun stateFlowAndLegacyAdapterReceiveTheSameImmutableState() {
        val viewModel = HomeTelemetryViewModel()
        val state = HomeTelemetryUiState(connectionText = "Connected", armedText = "Disarmed")

        viewModel.updateUiState(state)

        assertEquals(state, viewModel.homeTelemetryUiStateFlow.value)
        assertEquals(state, viewModel.homeTelemetryUiState.value)
    }
}
