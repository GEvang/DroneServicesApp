package com.example.droneservicesapp.ui.home.binders

import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.droneservicesapp.databinding.FragmentHomeMapsBinding
import com.example.droneservicesapp.R
import com.example.droneservicesapp.ui.home.model.HomeMapInteractionUiState
import com.example.droneservicesapp.ui.home.model.HomeMapShellUiState
import com.example.droneservicesapp.ui.home.model.MapOverlayControlsUiState

class HomeMapChromeBinder(
    private val binding: FragmentHomeMapsBinding,
    private val bottomActionBarViewProvider: () -> View?
) {
    fun bindActions(
        onDownloadOffline: () -> Unit,
        onCenterOnUser: () -> Unit,
        onCenterOnDrone: () -> Unit,
        onOpenSettings: () -> Unit,
        onTogglePlanning: () -> Unit,
    ) {
        binding.utilityDownloadButton.setOnClickListener { onDownloadOffline() }
        binding.utilityCenterOperatorButton.setOnClickListener { onCenterOnUser() }
        binding.utilityCenterDroneButton.setOnClickListener { onCenterOnDrone() }
        binding.utilitySettingsButton.setOnClickListener { onOpenSettings() }
        binding.utilityPlanningButton.setOnClickListener { onTogglePlanning() }
    }

    fun renderOverlayControls(state: MapOverlayControlsUiState) {
        binding.utilityCenterOperatorButton.isVisible = state.showMyLocationButton
        binding.utilityCenterDroneButton.isVisible = state.showDroneLocationButton
        binding.utilityDownloadButton.isVisible = state.showDownloadOfflineButton
    }

    fun renderInteraction(state: HomeMapInteractionUiState) {
        bottomActionBarViewProvider()?.let { legacyBottomBar ->
            legacyBottomBar.isVisible = state.isLegacyBottomActionBarVisible
            if (state.isLegacyBottomActionBarVisible) {
                legacyBottomBar.bringToFront()
                legacyBottomBar.requestLayout()
            }
        }
        binding.homeBottomUtilityDock.isVisible = state.isBottomActionBarVisible
        binding.homeBottomPlanningLabel.isVisible = state.isBottomActionBarVisible
    }

    fun renderShell(state: HomeMapShellUiState) {
        binding.homeBottomUtilityDock.isVisible = state.isBottomUtilityBarVisible
        binding.homeBottomPlanningLabel.isVisible = state.isBottomUtilityBarVisible
        binding.utilityPlanningButton.isSelected = state.isRightPanelVisible
        binding.utilityPlanningButton.setTextColor(
            ContextCompat.getColor(
                binding.root.context,
                if (state.isRightPanelVisible) android.R.color.black else android.R.color.white
            )
        )
        binding.utilityPlanningButton.alpha = 1.0f
    }
}
