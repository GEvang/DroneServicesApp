package com.example.droneservicesapp.ui.home.binders

import androidx.core.view.isVisible
import com.example.droneservicesapp.R
import com.example.droneservicesapp.databinding.FragmentHomeMapsBinding
import com.example.droneservicesapp.ui.home.model.HomeMapInteractionUiState
import com.example.droneservicesapp.ui.home.model.HomeMapShellUiState
import com.example.droneservicesapp.ui.home.model.MapOverlayControlsUiState

class HomeMapChromeBinder(
    private val binding: FragmentHomeMapsBinding,
    private val onBottomActionBarVisibilityChanged: (Boolean) -> Unit,
) {
    private val drawBarReplacesDock =
        binding.root.resources.getBoolean(R.bool.config_map_draw_bar_replaces_dock)
    private var isDrawActionBarVisible: Boolean = false

    fun bindActions(
        onDownloadOffline: () -> Unit,
        onCenterOnUser: () -> Unit,
        onCenterOnDrone: () -> Unit,
        onToggleObstacles: () -> Unit,
        onStartDroneOffset: () -> Unit,
        onCyclePreviewMode: () -> Unit,
        onOpenSettings: () -> Unit,
        onTogglePlanning: () -> Unit,
    ) {
        binding.utilityDownloadButton.setOnClickListener { onDownloadOffline() }
        binding.utilityCenterOperatorButton.setOnClickListener { onCenterOnUser() }
        binding.utilityCenterDroneButton.setOnClickListener { onCenterOnDrone() }
        binding.utilityObstaclesButton.setOnClickListener { onToggleObstacles() }
        binding.utilityOffsetButton.setOnClickListener { onStartDroneOffset() }
        binding.previewModeCycleButton.setOnClickListener { onCyclePreviewMode() }
        binding.utilitySettingsButton.setOnClickListener { onOpenSettings() }
        binding.utilityPlanningButton.setOnClickListener { onTogglePlanning() }
    }

    fun renderOverlayControls(state: MapOverlayControlsUiState) {
        binding.utilityCenterOperatorButton.isVisible = state.showMyLocationButton
        binding.utilityCenterDroneButton.isVisible = state.showDroneLocationButton
        binding.utilityObstaclesButton.isVisible = state.showDroneLocationButton
        binding.utilityOffsetButton.isVisible = state.showDroneLocationButton
        binding.utilityDownloadButton.isVisible = state.showDownloadOfflineButton
    }

    fun renderInteraction(state: HomeMapInteractionUiState) {
        onBottomActionBarVisibilityChanged(state.isLegacyBottomActionBarVisible)
        isDrawActionBarVisible = state.isDrawActionButtonsVisible
        binding.homeBottomUtilityDock.isVisible =
            state.isBottomActionBarVisible && !(drawBarReplacesDock && isDrawActionBarVisible)
        binding.homeBottomPlanningLabel.isVisible = false
        binding.homeDrawActionBar.isVisible = isDrawActionBarVisible
    }

    fun renderShell(state: HomeMapShellUiState) {
        binding.homeBottomUtilityDock.isVisible =
            state.isBottomUtilityBarVisible && !(drawBarReplacesDock && isDrawActionBarVisible)
        binding.homeBottomPlanningLabel.isVisible = false
        binding.utilityPlanningButton.isSelected = state.isPlanningActive || state.isRightPanelVisible
        binding.utilityPlanningButton.alpha = 1.0f
    }
}
