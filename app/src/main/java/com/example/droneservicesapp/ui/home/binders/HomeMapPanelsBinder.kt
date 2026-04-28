package com.example.droneservicesapp.ui.home.binders

import android.view.View
import androidx.core.view.isVisible
import com.example.droneservicesapp.ui.home.model.HomeMapShellUiState
import com.example.droneservicesapp.ui.home.model.MissionPanelUiState

class HomeMapPanelsBinder(
    private val missionParamsView: View,
    private val planningPanelView: View,
    private val saveMissionView: View,
    private val loadMissionView: View,
) {
    init {
        blockMapPassthrough(missionParamsView)
        blockMapPassthrough(planningPanelView)
    }

    fun renderShell(state: HomeMapShellUiState) {
        missionParamsView.isVisible = state.isLeftPanelVisible
        planningPanelView.isVisible = state.isRightPanelVisible
    }

    fun renderOverlays(state: MissionPanelUiState) {
        saveMissionView.isVisible =
            state.activePanel == MissionPanelUiState.ActivePanel.SaveMission
        loadMissionView.isVisible =
            state.activePanel == MissionPanelUiState.ActivePanel.LoadMission

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
        targetView.setOnTouchListener(null)
        targetView.isClickable = shouldConsumeTouch
        targetView.isFocusable = false
    }

    private fun blockMapPassthrough(targetView: View) {
        targetView.isClickable = true
        targetView.isFocusable = true
    }
}
