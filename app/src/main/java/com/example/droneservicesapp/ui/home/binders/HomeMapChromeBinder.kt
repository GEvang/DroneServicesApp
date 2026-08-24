package com.example.droneservicesapp.ui.home.binders

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.droneservicesapp.databinding.FragmentHomeMapsBinding
import com.example.droneservicesapp.R
import com.example.droneservicesapp.ui.home.model.HomeMapInteractionUiState
import com.example.droneservicesapp.ui.home.model.HomeMapShellUiState
import com.example.droneservicesapp.ui.home.model.MapOverlayControlsUiState
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeMapChromeBinder(
    private val binding: FragmentHomeMapsBinding,
    private val bottomActionBarViewProvider: () -> View?
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
        bottomActionBarViewProvider()?.let { legacyBottomBar ->
            legacyBottomBar.isVisible = state.isLegacyBottomActionBarVisible
            (legacyBottomBar as? BottomNavigationView)?.apply {
                labelVisibilityMode =
                    if (state.isLegacyBottomActionBarVisible) BottomNavigationView.LABEL_VISIBILITY_LABELED
                    else BottomNavigationView.LABEL_VISIBILITY_SELECTED
                itemIconTintList =
                    if (state.isLegacyBottomActionBarVisible) null
                    else AppCompatResources.getColorStateList(context, R.color.bottom_nav_item_icon)
            }
            if (state.isLegacyBottomActionBarVisible) {
                legacyBottomBar.bringToFront()
                legacyBottomBar.requestLayout()
            }
        }
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
        renderPlanningDockSelection(state.isPlanningActive || state.isRightPanelVisible)
    }

    private fun renderPlanningDockSelection(selected: Boolean) {
        binding.utilityPlanningButton.isSelected = selected
        binding.utilityPlanningButton.alpha = 1.0f
        val color = ContextCompat.getColor(
            binding.root.context,
            if (selected) R.color.ds_color_shell_active else R.color.ds_color_shell_unselected
        )
        val button = binding.utilityPlanningButton
        when (button) {
            is ImageView -> button.setColorFilter(color)
            is ViewGroup -> {
                for (index in 0 until button.childCount) {
                    when (val child = button.getChildAt(index)) {
                        is ImageView -> child.setColorFilter(color)
                        is TextView -> child.setTextColor(color)
                    }
                }
            }
        }
    }
}
