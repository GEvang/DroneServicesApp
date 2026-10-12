package com.example.droneservicesapp.ui.home.components

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.pointcloud.PointCloudData
import com.example.droneservicesapp.ui.home.PreviewPanelState
import com.example.droneservicesapp.ui.home.map.MissionMapLayers
import com.example.droneservicesapp.ui.preview.PreviewAssetLoader
import com.example.droneservicesapp.ui.preview.PreviewAssetStore
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.preview.PreviewMode
import com.example.droneservicesapp.ui.preview.PreviewSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinates Map/Ortho/3D preview selection, persisted-asset restoration, and the inputs of the
 * ortho raster layer and embedded point-cloud renderer.
 *
 * Replaces the former `PreviewWorkflowController` + `PreviewModeRenderer` pair. Asset import now
 * lives entirely in the Datasets screen, which the old mission screen also relied on: its own
 * import rows were never shown.
 */
class PreviewWorkflowController(
    private val context: Context,
    private val scope: LifecycleCoroutineScope,
    private val preview: PreviewPanelState,
    private val layers: MissionMapLayers,
    private val assets: PreviewAssetsViewModel,
    private val store: PreviewAssetStore,
    private val loader: PreviewAssetLoader,
    private val terrainPreview: TerrainPreviewCoordinator,
    private val isSurveyMode: () -> Boolean,
    private val surveyPointCloud: () -> PointCloudData?,
    private val renderMapPath: () -> Unit,
    private val focusOrtho: () -> Unit,
    private val redrawMission: () -> Unit,
    private val generatePointRoute: () -> Unit,
    private val isActive: () -> Boolean,
    private val showMessage: (String) -> Unit,
) {
    val mode: PreviewMode
        get() = preview.mode

    val heightColorsEnabled: Boolean
        get() = assets.previewSettings.value?.heightColorModeEnabled ?: false

    private var loadJob: Job? = null

    fun selectMode(selected: PreviewMode) {
        preview.mode = selected
        if (selected == PreviewMode.POINT_CLOUD && !isSurveyMode()) restorePointCloudOnDemand()
        render()
    }

    fun cycleMode() {
        val next = if (isSurveyMode()) {
            when (preview.mode) {
                PreviewMode.MAP, PreviewMode.ORTHO -> PreviewMode.POINT_CLOUD
                PreviewMode.POINT_CLOUD -> PreviewMode.MAP
            }
        } else {
            when (preview.mode) {
                PreviewMode.MAP -> PreviewMode.ORTHO
                PreviewMode.ORTHO -> PreviewMode.POINT_CLOUD
                PreviewMode.POINT_CLOUD -> PreviewMode.MAP
            }
        }
        selectMode(next)
    }

    fun render() {
        if (!isActive()) return
        val survey = isSurveyMode()
        preview.surveyMode = survey
        if (survey && preview.mode == PreviewMode.ORTHO) preview.mode = PreviewMode.MAP
        when (preview.mode) {
            PreviewMode.MAP -> {
                preview.terrainStatus = null
                layers.ortho = null
                layers.tilesEnabled = true
                renderMapPath()
            }
            PreviewMode.ORTHO -> {
                preview.terrainStatus = null
                renderOrtho()
                renderMapPath()
            }
            PreviewMode.POINT_CLOUD -> {
                val settings = assets.previewSettings.value ?: PreviewSettings()
                preview.pointCloud = currentPointCloud()
                preview.pointSize = if (survey) 4f else settings.pointCloudPointSize
                preview.heightColors = survey || settings.heightColorModeEnabled
                preview.pointOpacity = settings.orthoOpacity
                terrainPreview.renderStatus()
                terrainPreview.renderMissionOverlay()
            }
        }
    }

    fun refreshAssets() {
        if (!isActive()) return
        if (currentPointCloud() == null) preview.pointCloud = null
        if (assets.orthoAsset == null) layers.ortho = null
        render()
    }

    fun applySettings(settings: PreviewSettings) {
        if (!isActive()) return
        preview.pointOpacity = settings.orthoOpacity
        preview.pointSize = settings.pointCloudPointSize
        preview.heightColors = settings.heightColorModeEnabled
        layers.ortho?.let { layers.ortho = it.copy(opacity = settings.orthoOpacity) }
        if (preview.mode == PreviewMode.ORTHO) layers.tilesEnabled = settings.orthoBackgroundEnabled
        terrainPreview.renderMissionOverlay()
        render()
    }

    fun onAssetsChanged() {
        refreshAssets()
        redrawMission()
    }

    fun onTerrainPreviewChanged() {
        if (isSurveyMode()) render()
    }

    fun resetPointCloudCamera() {
        preview.cameraResetToken += 1
    }

    fun restorePersistedAssets() {
        val persisted = store.readPersistedAssets()
        val imageUri = persisted.orthoImageUri
        if (imageUri == null) {
            restorePointCloudOnDemand(activatePreview = false)
            return
        }
        val imageName = persisted.orthoImageName ?: context.getString(R.string.ortho_unknown_image)
        val worldUri = persisted.orthoWorldUri
        val worldName = persisted.orthoWorldName
        replaceLoadJob {
            if (assets.orthoAsset == null) {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val decoded = loader.loadOrthoImage(imageUri)
                        val bounds = worldUri?.let { loader.loadWorldFile(it, decoded.sourceWidth, decoded.sourceHeight) }
                        decoded to bounds
                    }
                }
                result.onSuccess { (decoded, bounds) ->
                    if (!isActive()) return@onSuccess
                    assets.setOrthoImage(
                        bitmap = decoded.bitmap,
                        bitmapFileName = imageName,
                        bitmapUri = imageUri,
                        sourceWidth = decoded.sourceWidth,
                        sourceHeight = decoded.sourceHeight,
                        notifyChange = bounds == null,
                    )
                    if (bounds != null && worldUri != null && worldName != null) {
                        assets.setOrthoBounds(bounds, worldName, worldUri)
                        if (preview.mode == PreviewMode.ORTHO) render()
                    }
                }
            }
            loadJob = null
            restorePointCloudOnDemand(activatePreview = false)
        }
    }

    fun renderMissionOverlay() = terrainPreview.renderMissionOverlay()

    fun dispose() {
        loadJob?.cancel()
        loadJob = null
        terrainPreview.dispose()
    }

    private fun renderOrtho() {
        val asset = assets.orthoAsset
        val bounds = asset?.bounds
        if (asset == null || bounds == null) {
            layers.ortho = null
            layers.tilesEnabled = true
            showMessage(context.getString(R.string.ortho_empty_state))
            return
        }
        val settings = assets.previewSettings.value
        layers.ortho = MissionMapLayers.OrthoRaster(asset.bitmap, bounds, settings?.orthoOpacity ?: 0.85f)
        layers.tilesEnabled = settings?.orthoBackgroundEnabled ?: true
        focusOrtho()
    }

    private fun currentPointCloud(): PointCloudData? =
        if (isSurveyMode()) surveyPointCloud() else assets.pointCloudAsset?.pointCloud

    private fun loadPointCloud(uri: Uri, activatePreview: Boolean) {
        val fileName = store.displayName(uri) ?: context.getString(R.string.point_cloud_unknown_file)
        if (!store.supportsPointCloud(fileName)) {
            if (isActive()) showMessage(context.getString(R.string.point_cloud_select_ply))
            return
        }
        assets.clearPointCloud()
        preview.pointCloud = null
        replaceLoadJob {
            runCatching { withContext(Dispatchers.IO) { loader.loadPointCloud(uri, fileName) } }
                .onSuccess { pointCloud ->
                    if (!isActive()) return@onSuccess
                    assets.setPointCloud(pointCloud, fileName, uri)
                    store.savePointCloud(uri, fileName)
                    if (activatePreview) {
                        preview.mode = PreviewMode.POINT_CLOUD
                        preview.pointCloud = pointCloud
                        preview.heightColors = heightColorsEnabled
                    }
                    terrainPreview.warmGrid(showToast = activatePreview)
                    generatePointRoute()
                    terrainPreview.renderMissionOverlay()
                    render()
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    if (isActive()) {
                        showMessage(
                            context.getString(R.string.point_cloud_load_failed, error.message ?: error.javaClass.simpleName),
                        )
                    }
                }
        }
    }

    private fun restorePointCloudOnDemand(activatePreview: Boolean = true) {
        if (assets.pointCloudAsset != null || loadJob?.isActive == true) return
        val uri = store.readPersistedAssets().pointCloudUri ?: return
        loadPointCloud(uri, activatePreview)
    }

    private fun replaceLoadJob(block: suspend () -> Unit) {
        loadJob?.cancel()
        loadJob = scope.launch { block() }
    }
}
