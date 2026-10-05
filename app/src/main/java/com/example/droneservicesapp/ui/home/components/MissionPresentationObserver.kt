package com.example.droneservicesapp.ui.home.components

import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.ui.home.binders.HomeMapTelemetryBinder
import com.example.droneservicesapp.ui.home.model.HomeMapUiState
import com.example.droneservicesapp.ui.home.model.HomeTelemetryViewModel
import com.example.droneservicesapp.ui.home.model.MissionMapViewModel
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.preview.PreviewSettings
import androidx.lifecycle.LiveData

/** Binds presentation-only state streams to their renderers and refresh callbacks. */
class MissionPresentationObserver(
    private val owner: LifecycleOwner,
) {
    private var lastGeoReloadToken = 0L
    fun bindHomeTelemetry(viewModel: HomeTelemetryViewModel, binder: HomeMapTelemetryBinder) {
        viewModel.homeTelemetryUiState.observe(owner, binder::render)
    }

    fun bindMapUi(viewModel: MissionMapViewModel, render: (HomeMapUiState) -> Unit) {
        viewModel.homeMapUiState.observe(owner, render)
    }

    fun bindPreviewAssets(
        viewModel: PreviewAssetsViewModel,
        onSettings: (PreviewSettings) -> Unit,
        onAssetVersion: () -> Unit,
    ) {
        viewModel.previewSettings.observe(owner, onSettings)
        viewModel.assetVersion.observe(owner) { onAssetVersion() }
    }

    fun <T> bind(source: LiveData<T>, render: (T) -> Unit) {
        source.observe(owner, render)
    }

    fun bindGeoState(
        layerVisible: LiveData<Boolean>,
        reloadToken: LiveData<Long>,
        renderLayer: () -> Unit,
        reload: () -> Unit,
    ) {
        layerVisible.observe(owner) { renderLayer() }
        reloadToken.observe(owner) { token ->
            if (token != null && token > 0L && token != lastGeoReloadToken) {
                lastGeoReloadToken = token
                reload()
            }
        }
    }
}
