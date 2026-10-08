package com.example.droneservicesapp.ui.home.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.pointcloud.PointCloudData
import com.example.droneservicesapp.databinding.FragmentHomeMapsBinding
import com.example.droneservicesapp.ui.preview.OrthoOverlayController
import com.example.droneservicesapp.ui.preview.PreviewAssetLoader
import com.example.droneservicesapp.ui.preview.PreviewAssetStore
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.preview.PreviewMode
import com.example.droneservicesapp.ui.preview.PreviewModeRenderer
import com.example.droneservicesapp.ui.preview.PreviewSettings
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Coordinates preview mode selection, imported assets, restoration, and loading failures. */
class PreviewWorkflowController(
    private val context: Context,
    private val scope: LifecycleCoroutineScope,
    private val binding: FragmentHomeMapsBinding,
    private val assets: PreviewAssetsViewModel,
    private val store: PreviewAssetStore,
    private val loader: PreviewAssetLoader,
    private val orthoOverlay: OrthoOverlayController,
    private val modeRenderer: PreviewModeRenderer,
    private val terrainPreview: TerrainPreviewCoordinator,
    private val uiActions: MissionMapUiActionController,
    private val isSurveyMode: () -> Boolean,
    private val surveyPointCloud: () -> PointCloudData?,
    private val renderMapPath: () -> Unit,
    private val redrawMission: () -> Unit,
    private val generatePointRoute: () -> Unit,
    private val launchFilePicker: (Intent, Int) -> Unit,
    private val isActive: () -> Boolean,
) {
    var mode: PreviewMode = PreviewMode.MAP
        private set

    val heightColorsEnabled: Boolean
        get() = assets.previewSettings.value?.heightColorModeEnabled ?: false

    private var loadJob: Job? = null

    fun bindActions() {
        uiActions.bindPreview(
            MissionMapUiActionController.PreviewActions(
                selectMode = ::selectMode,
                primary = {
                    when (mode) {
                        PreviewMode.MAP -> Unit
                        PreviewMode.ORTHO -> openFilePicker(REQUEST_OPEN_TIFF)
                        PreviewMode.POINT_CLOUD -> openFilePicker(REQUEST_OPEN_POINT_CLOUD)
                    }
                },
                secondary = {
                    when (mode) {
                        PreviewMode.MAP -> Unit
                        PreviewMode.ORTHO -> openFilePicker(REQUEST_OPEN_WORLD)
                        PreviewMode.POINT_CLOUD -> binding.homePointCloudGlView.resetCamera()
                    }
                },
                toggleHeightColors = {
                    assets.updateSettings { copy(heightColorModeEnabled = !heightColorModeEnabled) }
                },
                backgroundChanged = { checked ->
                    if (mode == PreviewMode.ORTHO) {
                        binding.osmMap.overlayManager.tilesOverlay?.isEnabled = checked
                        binding.osmMap.invalidate()
                    }
                },
                opacityChanged = { opacity ->
                    orthoOverlay.setOpacity(opacity)
                    binding.homePointCloudGlView.setPointCloudOpacity(opacity)
                    binding.osmMap.invalidate()
                },
            ),
        )
    }

    fun selectMode(selected: PreviewMode) {
        mode = selected
        if (selected == PreviewMode.POINT_CLOUD && !isSurveyMode()) restorePointCloudOnDemand()
        render()
    }

    fun cycleMode() {
        mode = if (isSurveyMode()) {
            when (mode) {
                PreviewMode.MAP, PreviewMode.ORTHO -> PreviewMode.POINT_CLOUD
                PreviewMode.POINT_CLOUD -> PreviewMode.MAP
            }
        } else {
            when (mode) {
                PreviewMode.MAP -> PreviewMode.ORTHO
                PreviewMode.ORTHO -> PreviewMode.POINT_CLOUD
                PreviewMode.POINT_CLOUD -> PreviewMode.MAP
            }
        }
        if (mode == PreviewMode.POINT_CLOUD && !isSurveyMode()) restorePointCloudOnDemand()
        render()
    }

    fun render() {
        if (!isActive()) return
        if (isSurveyMode() && mode == PreviewMode.ORTHO) mode = PreviewMode.MAP
        modeRenderer.render(mode, isSurveyMode(), currentPointCloud())
    }

    fun refreshAssets() {
        if (!isActive()) return
        if (currentPointCloud() == null) binding.homePointCloudGlView.clearPointCloud()
        if (assets.orthoAsset == null) orthoOverlay.clear()
        render()
    }

    fun applySettings(settings: PreviewSettings) {
        if (!isActive()) return
        binding.previewBackgroundSwitch.isChecked = settings.orthoBackgroundEnabled
        if (binding.previewOpacitySlider.value != settings.orthoOpacity) {
            binding.previewOpacitySlider.value = settings.orthoOpacity
        }
        binding.homePointCloudGlView.setPointCloudOpacity(settings.orthoOpacity)
        binding.homePointCloudGlView.setPointSize(settings.pointCloudPointSize)
        binding.homePointCloudGlView.setHeightColorModeEnabled(settings.heightColorModeEnabled)
        orthoOverlay.setOpacity(settings.orthoOpacity)
        if (mode == PreviewMode.ORTHO) {
            binding.osmMap.overlayManager.tilesOverlay?.isEnabled = settings.orthoBackgroundEnabled
        }
        renderMapPath()
        terrainPreview.renderMissionOverlay()
        render()
    }

    fun onAssetsChanged() {
        refreshAssets()
        renderMapPath()
        redrawMission()
    }

    fun onTerrainPreviewChanged() {
        if (isSurveyMode()) render()
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode !in SUPPORTED_REQUESTS) return false
        if (resultCode != Activity.RESULT_OK) return true
        val uri = data?.data ?: return true
        store.persistReadPermission(uri, data.flags)
        when (requestCode) {
            REQUEST_OPEN_TIFF -> loadOrthoImage(uri)
            REQUEST_OPEN_WORLD -> loadOrthoWorldFile(uri)
            REQUEST_OPEN_POINT_CLOUD -> loadPointCloud(uri)
        }
        return true
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
                        val bounds = worldUri?.let {
                            loader.loadWorldFile(it, decoded.sourceWidth, decoded.sourceHeight)
                        }
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
                        if (mode == PreviewMode.ORTHO) render()
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

    private fun currentPointCloud(): PointCloudData? =
        if (isSurveyMode()) surveyPointCloud() else assets.pointCloudAsset?.pointCloud

    private fun openFilePicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        launchFilePicker(intent, requestCode)
    }

    private fun loadOrthoImage(uri: Uri) {
        val fileName = store.displayName(uri) ?: context.getString(R.string.ortho_unknown_image)
        val lowerName = fileName.lowercase(Locale.US)
        if (!lowerName.endsWith(".tif") && !lowerName.endsWith(".tiff")) {
            toast(R.string.ortho_select_tif, Toast.LENGTH_SHORT)
            return
        }
        assets.clearOrtho()
        orthoOverlay.clear()
        replaceLoadJob {
            runCatching { withContext(Dispatchers.IO) { loader.loadOrthoImage(uri) } }
                .onSuccess { decoded ->
                    if (!isActive()) return@onSuccess
                    assets.setOrthoImage(
                        bitmap = decoded.bitmap,
                        bitmapFileName = fileName,
                        bitmapUri = uri,
                        sourceWidth = decoded.sourceWidth,
                        sourceHeight = decoded.sourceHeight,
                        notifyChange = false,
                    )
                    store.saveOrthoImage(uri, fileName)
                    orthoOverlay.clear()
                    toast(R.string.ortho_load_world_next, Toast.LENGTH_SHORT)
                    render()
                }
                .onFailure(::showOrthoFailure)
        }
    }

    private fun loadOrthoWorldFile(uri: Uri) {
        val fileName = store.displayName(uri) ?: context.getString(R.string.ortho_unknown_world)
        val lowerName = fileName.lowercase(Locale.US)
        if (!lowerName.endsWith(".tfw") && !lowerName.endsWith(".wld")) {
            toast(R.string.ortho_select_world, Toast.LENGTH_SHORT)
            return
        }
        val asset = assets.orthoAsset
        if (asset == null) {
            toast(R.string.ortho_load_image_first, Toast.LENGTH_SHORT)
            return
        }
        replaceLoadJob {
            runCatching {
                withContext(Dispatchers.IO) { loader.loadWorldFile(uri, asset.sourceWidth, asset.sourceHeight) }
            }.onSuccess { bounds ->
                if (!isActive()) return@onSuccess
                assets.setOrthoBounds(bounds, fileName, uri)
                store.saveOrthoWorld(uri, fileName)
                mode = PreviewMode.ORTHO
                render()
            }.onFailure(::showOrthoFailure)
        }
    }

    private fun loadPointCloud(uri: Uri, activatePreview: Boolean = true) {
        val fileName = store.displayName(uri) ?: context.getString(R.string.point_cloud_unknown_file)
        if (!store.supportsPointCloud(fileName)) {
            toast(R.string.point_cloud_select_ply, Toast.LENGTH_SHORT)
            return
        }
        assets.clearPointCloud()
        binding.homePointCloudGlView.clearPointCloud()
        replaceLoadJob {
            runCatching { withContext(Dispatchers.IO) { loader.loadPointCloud(uri, fileName) } }
                .onSuccess { pointCloud ->
                    if (!isActive()) return@onSuccess
                    assets.setPointCloud(pointCloud, fileName, uri)
                    store.savePointCloud(uri, fileName)
                    if (activatePreview) {
                        mode = PreviewMode.POINT_CLOUD
                        binding.homePointCloudGlView.setPointCloud(pointCloud)
                        binding.homePointCloudGlView.setHeightColorModeEnabled(heightColorsEnabled)
                    }
                    terrainPreview.warmGrid(showToast = activatePreview)
                    generatePointRoute()
                    terrainPreview.renderMissionOverlay()
                    render()
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    if (isActive()) Toast.makeText(
                        context,
                        context.getString(
                            R.string.point_cloud_load_failed,
                            error.message ?: error.javaClass.simpleName,
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
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

    private fun showOrthoFailure(error: Throwable) {
        if (error is CancellationException) return
        if (!isActive()) return
        Toast.makeText(
            context,
            context.getString(R.string.ortho_load_failed, error.message ?: error.javaClass.simpleName),
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun toast(message: Int, duration: Int) {
        if (isActive()) Toast.makeText(context, message, duration).show()
    }

    companion object {
        const val REQUEST_OPEN_TIFF = 3301
        const val REQUEST_OPEN_WORLD = 3302
        const val REQUEST_OPEN_POINT_CLOUD = 3303
        private val SUPPORTED_REQUESTS = setOf(REQUEST_OPEN_TIFF, REQUEST_OPEN_WORLD, REQUEST_OPEN_POINT_CLOUD)
    }
}
