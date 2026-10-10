package com.example.droneservicesapp.ui.preview

data class PreviewModeUiState(
    val mode: PreviewMode,
    val showMapSurface: Boolean,
    val showPointCloudSurface: Boolean,
    val showOrthoMode: Boolean,
    val showAssetControls: Boolean,
    val primaryAction: AssetAction?,
    val secondaryAction: AssetAction?,
    val nextMode: PreviewMode,
    val pointSize: Float,
    val opacity: Float,
    val heightColorModeEnabled: Boolean,
) {
    enum class AssetAction { LOAD_ORTHO_IMAGE, LOAD_WORLD_FILE, LOAD_POINT_CLOUD, RESET_POINT_CLOUD }
}

object PreviewModePresenter {
    fun present(
        mode: PreviewMode,
        surveyMode: Boolean,
        hasPointCloud: Boolean,
        settings: PreviewSettings,
    ): PreviewModeUiState = when (mode) {
        PreviewMode.MAP -> PreviewModeUiState(
            mode = mode,
            showMapSurface = true,
            showPointCloudSurface = false,
            showOrthoMode = !surveyMode,
            showAssetControls = false,
            primaryAction = null,
            secondaryAction = null,
            nextMode = if (surveyMode) PreviewMode.POINT_CLOUD else PreviewMode.ORTHO,
            pointSize = if (surveyMode) 4f else settings.pointCloudPointSize,
            opacity = settings.orthoOpacity,
            heightColorModeEnabled = surveyMode || settings.heightColorModeEnabled,
        )
        PreviewMode.ORTHO -> PreviewModeUiState(
            mode = mode,
            showMapSurface = true,
            showPointCloudSurface = false,
            showOrthoMode = !surveyMode,
            showAssetControls = true,
            primaryAction = PreviewModeUiState.AssetAction.LOAD_ORTHO_IMAGE,
            secondaryAction = PreviewModeUiState.AssetAction.LOAD_WORLD_FILE,
            nextMode = PreviewMode.POINT_CLOUD,
            pointSize = settings.pointCloudPointSize,
            opacity = settings.orthoOpacity,
            heightColorModeEnabled = settings.heightColorModeEnabled,
        )
        PreviewMode.POINT_CLOUD -> PreviewModeUiState(
            mode = mode,
            showMapSurface = false,
            showPointCloudSurface = true,
            showOrthoMode = !surveyMode,
            showAssetControls = true,
            primaryAction = PreviewModeUiState.AssetAction.LOAD_POINT_CLOUD,
            secondaryAction = PreviewModeUiState.AssetAction.RESET_POINT_CLOUD.takeIf { hasPointCloud },
            nextMode = PreviewMode.MAP,
            pointSize = if (surveyMode) 4f else settings.pointCloudPointSize,
            opacity = settings.orthoOpacity,
            heightColorModeEnabled = surveyMode || settings.heightColorModeEnabled,
        )
    }
}
