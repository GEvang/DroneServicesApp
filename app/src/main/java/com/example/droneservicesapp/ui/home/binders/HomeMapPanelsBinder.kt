package com.example.droneservicesapp.ui.home.binders

import android.view.View
import androidx.core.view.isVisible
import com.example.droneservicesapp.ui.home.model.MissionPanelUiState

class HomeMapPanelsBinder(
    private val missionParamsView: View,
    private val saveMissionView: View,
    private val loadMissionView: View,
) {
    fun render(state: MissionPanelUiState) {
        missionParamsView.isVisible =
            state.activePanel == MissionPanelUiState.ActivePanel.MissionParams
        saveMissionView.isVisible =
            state.activePanel == MissionPanelUiState.ActivePanel.SaveMission
        loadMissionView.isVisible =
            state.activePanel == MissionPanelUiState.ActivePanel.LoadMission

        applyTouchConsumption(
            targetView = missionParamsView,
            shouldConsumeTouch = state.activePanel == MissionPanelUiState.ActivePanel.MissionParams &&
                state.consumesTouch
        )
        applyTouchConsumption(
            targetView = saveMissionView,
            shouldConsumeTouch = state.activePanel == MissionPanelUiState.ActivePanel.SaveMission &&
                state.consumesTouch
        )
        applyTouchConsumption(
            targetView = loadMissionView,
            shouldConsumeTouch = state.activePanel == MissionPanelUiState.ActivePanel.LoadMission &&
                state.consumesTouch
        )
    }

    private fun applyTouchConsumption(targetView: View, shouldConsumeTouch: Boolean) {
        targetView.setOnTouchListener(
            if (shouldConsumeTouch) {
                View.OnTouchListener { _, _ -> true }
            } else {
                null
            }
        )
    }
}
