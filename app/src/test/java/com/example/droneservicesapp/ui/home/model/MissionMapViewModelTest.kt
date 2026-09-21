package com.example.droneservicesapp.ui.home.model

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MissionMapViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun returningToMapRestoresMissionWithoutOpeningSidePanels() {
        val viewModel = MissionMapViewModel()
        viewModel.setMissionAreaAvailable(true)
        viewModel.updateFromMapState(MainActivityViewModel.MapState.SetFlightParams)

        val editingState = viewModel.homeMapUiState.value!!
        assertTrue(editingState.shellState.isLeftPanelVisible)
        assertTrue(editingState.shellState.isRightPanelVisible)

        viewModel.restoreFromMapState(MainActivityViewModel.MapState.SetFlightParams)

        val restoredState = viewModel.homeMapUiState.value!!
        assertFalse(restoredState.shellState.isLeftPanelVisible)
        assertFalse(restoredState.shellState.isRightPanelVisible)
    }

    @Test
    fun idleMapNeverOpensFlightSettingsForAnExistingMission() {
        val viewModel = MissionMapViewModel()

        viewModel.setMissionAreaAvailable(true)
        viewModel.updateFromMapState(MainActivityViewModel.MapState.Idle)

        assertFalse(viewModel.homeMapUiState.value!!.shellState.isLeftPanelVisible)
    }
}
