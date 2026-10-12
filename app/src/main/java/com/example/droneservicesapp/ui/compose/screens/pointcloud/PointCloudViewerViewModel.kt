package com.example.droneservicesapp.ui.compose.screens.pointcloud

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.droneservicesapp.R
import com.example.droneservicesapp.ui.preview.PointCloudPreviewAsset
import com.example.droneservicesapp.ui.preview.PreviewAssetLoader
import com.example.droneservicesapp.ui.preview.PreviewAssetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PointCloudViewerUiState(
    val asset: PointCloudPreviewAsset? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val pointSize: Float = 2.5f,
    val opacity: Float = .85f,
    val heightColors: Boolean = true,
)

class PointCloudViewerViewModel(application: Application) : AndroidViewModel(application) {
    private val loader = PreviewAssetLoader(application)
    private val store = PreviewAssetStore(application)
    private val _uiState = MutableStateFlow(PointCloudViewerUiState())
    val uiState: StateFlow<PointCloudViewerUiState> = _uiState.asStateFlow()
    private var initialized = false

    fun initialize(
        sharedAsset: PointCloudPreviewAsset?,
        pointSize: Float,
        opacity: Float,
        heightColors: Boolean,
    ) {
        if (initialized) return
        initialized = true
        _uiState.update {
            it.copy(
                asset = sharedAsset,
                pointSize = pointSize,
                opacity = opacity,
                heightColors = heightColors,
            )
        }
        if (sharedAsset == null) restorePersistedAsset()
    }

    fun load(uri: Uri) {
        val fileName = store.displayName(uri) ?: appString(R.string.point_cloud_unknown_file)
        if (!store.supportsPointCloud(fileName)) {
            _uiState.update { it.copy(error = appString(R.string.point_cloud_select_ply)) }
            return
        }
        store.persistReadPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        load(uri, fileName, persist = true)
    }

    fun setPointSize(value: Float) = _uiState.update { it.copy(pointSize = value.coerceIn(1f, 8f)) }
    fun setOpacity(value: Float) = _uiState.update { it.copy(opacity = value.coerceIn(.1f, 1f)) }
    fun setHeightColors(enabled: Boolean) = _uiState.update { it.copy(heightColors = enabled) }

    private fun restorePersistedAsset() {
        val persisted = store.readPersistedAssets()
        val uri = persisted.pointCloudUri ?: return
        load(uri, persisted.pointCloudName ?: appString(R.string.point_cloud_unknown_file), persist = false)
    }

    private fun load(uri: Uri, fileName: String, persist: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(asset = null, loading = true, error = null) }
            runCatching { withContext(Dispatchers.IO) { loader.loadPointCloud(uri, fileName) } }
                .onSuccess { cloud ->
                    if (persist) store.savePointCloud(uri, fileName)
                    _uiState.update { it.copy(asset = PointCloudPreviewAsset(cloud, fileName, uri)) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = appString(R.string.point_cloud_load_failed, error.message ?: error.javaClass.simpleName))
                    }
                }
            _uiState.update { it.copy(loading = false) }
        }
    }

    private fun appString(id: Int, vararg values: Any): String = getApplication<Application>().getString(id, *values)
}
