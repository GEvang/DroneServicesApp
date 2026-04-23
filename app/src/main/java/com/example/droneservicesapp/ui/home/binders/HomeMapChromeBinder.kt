package com.example.droneservicesapp.ui.home.binders

import android.view.View
import androidx.core.view.isVisible
import com.example.droneservicesapp.databinding.FragmentHomeMapsBinding
import com.example.droneservicesapp.ui.home.model.HomeMapInteractionUiState
import com.example.droneservicesapp.ui.home.model.MapOverlayControlsUiState

class HomeMapChromeBinder(
    private val binding: FragmentHomeMapsBinding,
    private val bottomActionBarViewProvider: () -> View?,
) {
    fun bindActions(
        onDownloadOffline: () -> Unit,
        onCenterOnUser: () -> Unit,
        onCenterOnDrone: () -> Unit,
    ) {
        binding.downloadOfflineButton.setOnClickListener { onDownloadOffline() }
        binding.myLocationButton.setOnClickListener { onCenterOnUser() }
        binding.droneLocationButton.setOnClickListener { onCenterOnDrone() }
    }

    fun renderOverlayControls(state: MapOverlayControlsUiState) {
        binding.myLocationButton.isVisible = state.showMyLocationButton
        binding.droneLocationButton.isVisible = state.showDroneLocationButton
        binding.downloadOfflineButton.isVisible = state.showDownloadOfflineButton
    }

    fun renderInteraction(state: HomeMapInteractionUiState) {
        bottomActionBarViewProvider()?.isVisible = state.isBottomActionBarVisible
    }
}
