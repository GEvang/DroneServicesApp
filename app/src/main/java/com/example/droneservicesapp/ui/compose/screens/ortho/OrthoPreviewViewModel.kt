package com.example.droneservicesapp.ui.compose.screens.ortho

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.droneservicesapp.R
import com.example.droneservicesapp.ui.map.model.MapBounds
import com.example.droneservicesapp.ui.map.model.MapCameraCommand
import com.example.droneservicesapp.ui.map.model.MapCoordinate
import com.example.droneservicesapp.ui.preview.OrthoPreviewAsset
import com.example.droneservicesapp.ui.preview.PreviewAssetLoader
import com.example.droneservicesapp.ui.preview.PreviewAssetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class OrthoPreviewUiState(
    val asset: OrthoPreviewAsset? = null,
    val loading: Boolean = false,
    val statusOverride: String? = null,
    val opacity: Float = .85f,
    val backgroundEnabled: Boolean = true,
    val cameraCommand: MapCameraCommand? = null,
    val camera: com.example.droneservicesapp.ui.map.model.MapCamera = com.example.droneservicesapp.ui.map.model.MapCamera(
        MapCoordinate(35.35824717735152, 24.62254619945714), 18.0,
    ),
)

class OrthoPreviewViewModel(application: Application) : AndroidViewModel(application) {
    private val loader = PreviewAssetLoader(application)
    private val store = PreviewAssetStore(application)
    private val _uiState = MutableStateFlow(OrthoPreviewUiState())
    val uiState: StateFlow<OrthoPreviewUiState> = _uiState.asStateFlow()
    private var initialized = false
    private var commandToken = 0L

    fun initialize(sharedAsset: OrthoPreviewAsset?, opacity: Float, backgroundEnabled: Boolean) {
        if (initialized) return
        initialized = true
        _uiState.update { it.copy(asset = sharedAsset, opacity = opacity, backgroundEnabled = backgroundEnabled) }
        if (sharedAsset == null) restorePersistedAsset()
    }

    fun loadImage(uri: Uri) {
        val fileName = store.displayName(uri) ?: appString(R.string.ortho_unknown_image)
        if (!fileName.lowercase(Locale.US).let { it.endsWith(".tif") || it.endsWith(".tiff") }) {
            _uiState.update { it.copy(statusOverride = appString(R.string.ortho_select_tif)) }
            return
        }
        store.persistReadPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launchLoad(appString(R.string.ortho_loading_image, fileName)) {
            val decoded = loader.loadOrthoImage(uri)
            val asset = OrthoPreviewAsset(
                bitmap = decoded.bitmap,
                bitmapFileName = fileName,
                bitmapUri = uri,
                sourceWidth = decoded.sourceWidth,
                sourceHeight = decoded.sourceHeight,
            )
            store.saveOrthoImage(uri, fileName)
            _uiState.update { it.copy(asset = asset, statusOverride = null) }
        }
    }

    fun loadWorldFile(uri: Uri) {
        val current = _uiState.value.asset
        if (current == null) {
            _uiState.update { it.copy(statusOverride = appString(R.string.ortho_load_image_first)) }
            return
        }
        val fileName = store.displayName(uri) ?: appString(R.string.ortho_unknown_world)
        if (!fileName.lowercase(Locale.US).let { it.endsWith(".tfw") || it.endsWith(".wld") }) {
            _uiState.update { it.copy(statusOverride = appString(R.string.ortho_select_world)) }
            return
        }
        store.persistReadPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launchLoad(appString(R.string.ortho_loading_world, fileName)) {
            val bounds = loader.loadWorldFile(uri, current.sourceWidth, current.sourceHeight)
            store.saveOrthoWorld(uri, fileName)
            val updated = current.copy(bounds = bounds, worldFileName = fileName, worldFileUri = uri)
            _uiState.update {
                it.copy(
                    asset = updated,
                    statusOverride = null,
                    cameraCommand = MapCameraCommand.Fit(++commandToken, bounds.toMapBounds()),
                )
            }
        }
    }

    fun clear() {
        store.clearOrtho()
        _uiState.update { it.copy(asset = null, statusOverride = null, cameraCommand = null) }
    }

    fun zoomToAsset() {
        val bounds = _uiState.value.asset?.bounds ?: run {
            _uiState.update { it.copy(statusOverride = appString(R.string.ortho_load_world_next)) }
            return
        }
        _uiState.update { it.copy(cameraCommand = MapCameraCommand.Fit(++commandToken, bounds.toMapBounds())) }
    }

    fun setOpacity(value: Float) = _uiState.update { it.copy(opacity = value.coerceIn(0f, 1f)) }
    fun setBackgroundEnabled(enabled: Boolean) = _uiState.update { it.copy(backgroundEnabled = enabled) }
    fun rememberCamera(camera: com.example.droneservicesapp.ui.map.model.MapCamera) =
        _uiState.update { it.copy(camera = camera) }

    private fun restorePersistedAsset() {
        val persisted = store.readPersistedAssets()
        val imageUri = persisted.orthoImageUri ?: return
        val imageName = persisted.orthoImageName ?: appString(R.string.ortho_unknown_image)
        launchLoad(appString(R.string.ortho_loading_image, imageName)) {
            val decoded = loader.loadOrthoImage(imageUri)
            val bounds = persisted.orthoWorldUri?.let {
                loader.loadWorldFile(it, decoded.sourceWidth, decoded.sourceHeight)
            }
            val asset = OrthoPreviewAsset(
                bitmap = decoded.bitmap,
                bitmapFileName = imageName,
                bitmapUri = imageUri,
                sourceWidth = decoded.sourceWidth,
                sourceHeight = decoded.sourceHeight,
                bounds = bounds,
                worldFileName = persisted.orthoWorldName.takeIf { bounds != null },
                worldFileUri = persisted.orthoWorldUri.takeIf { bounds != null },
            )
            _uiState.update {
                it.copy(
                    asset = asset,
                    statusOverride = null,
                    cameraCommand = bounds?.let { value -> MapCameraCommand.Fit(++commandToken, value.toMapBounds()) },
                )
            }
        }
    }

    private fun launchLoad(status: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, statusOverride = status) }
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(statusOverride = appString(R.string.ortho_load_failed, error.message ?: error.javaClass.simpleName))
                    }
                }
            _uiState.update { it.copy(loading = false) }
        }
    }

    private fun appString(id: Int, vararg values: Any): String = getApplication<Application>().getString(id, *values)
}

private fun com.example.droneservicesapp.data.ortho.OrthoBounds.toMapBounds() = MapBounds(
    southWest = MapCoordinate(minLat, minLon),
    northEast = MapCoordinate(maxLat, maxLon),
)
