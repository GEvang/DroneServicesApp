package com.example.droneservicesapp.ui.compose.screens.datasets

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.ortho.SimpleTiffDecoder
import com.example.droneservicesapp.data.ortho.WorldFileParser
import com.example.droneservicesapp.data.pointcloud.PointCloudDetailLevel
import com.example.droneservicesapp.data.pointcloud.PointCloudImportCache
import com.example.droneservicesapp.data.preview.PreviewDatasetRecord
import com.example.droneservicesapp.data.preview.PreviewDatasetStore
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.preview.PreviewMapFocus
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DatasetsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val store = PreviewDatasetStore(application)
    private val tiffDecoder = SimpleTiffDecoder()
    private val worldFileParser = WorldFileParser()
    private val pointCloudCache = PointCloudImportCache(application)
    private val cancelRequested = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(DatasetsUiState())
    private val effectChannel = Channel<DatasetEffect>(Channel.BUFFERED)
    private var loadJob: Job? = null
    private var loadRequestId = 0

    val state = mutableState.asStateFlow()
    val effects = effectChannel.receiveAsFlow()

    init {
        refresh()
    }

    fun createDataset(preview: PreviewAssetsViewModel) {
        cleanupEmptyActiveDataset()
        unloadPreviewAssets(preview)
        val record = PreviewDatasetRecord(
            id = System.currentTimeMillis().toString(),
            name = app.getString(R.string.dataset_default_name, store.loadDatasets().size + 1),
        )
        store.upsert(record)
        store.setActiveDatasetId(record.id)
        applySettings(preview, record)
        refresh()
        message(R.string.dataset_created)
    }

    fun selectDataset(id: String, preview: PreviewAssetsViewModel) {
        if (store.activeDatasetId() == id) return
        cleanupEmptyActiveDataset()
        unloadPreviewAssets(preview)
        val record = store.loadDatasets().firstOrNull { it.id == id } ?: return refresh()
        store.setActiveDatasetId(record.id)
        applySettings(preview, record)
        refresh()
    }

    fun loadActiveDataset(preview: PreviewAssetsViewModel) {
        store.activeDataset()?.let { loadDataset(it, preview) }
    }

    fun unload(preview: PreviewAssetsViewModel) {
        unloadPreviewAssets(preview)
        message(R.string.dataset_unloaded)
        refresh()
    }

    fun updateOrthoBackground(value: Boolean, preview: PreviewAssetsViewModel) =
        updateActive(preview) { copy(orthoBackgroundEnabled = value) }

    fun updateOrthoOpacity(value: Float, preview: PreviewAssetsViewModel) =
        updateActive(preview) { copy(orthoOpacity = value.coerceIn(0f, 1f)) }

    fun updatePointSize(value: Float, preview: PreviewAssetsViewModel) =
        updateActive(preview) { copy(pointCloudPointSize = value.coerceIn(1f, 10f)) }

    fun updateDetail(value: PointCloudDetailLevel, preview: PreviewAssetsViewModel) =
        updateActive(preview) { copy(pointCloudDetailLevel = value.name) }

    fun updateHeightColors(value: Boolean, preview: PreviewAssetsViewModel) =
        updateActive(preview) { copy(heightColorModeEnabled = value) }

    fun showRename() {
        val record = store.activeDataset() ?: return
        mutableState.value = mutableState.value.copy(
            dialog = DatasetDialog.Rename,
            renameValue = record.name,
            renameError = false,
        )
    }

    fun updateRename(value: String) {
        mutableState.value = mutableState.value.copy(renameValue = value, renameError = false)
    }

    fun confirmRename() {
        val name = mutableState.value.renameValue.trim()
        if (name.isBlank()) {
            mutableState.value = mutableState.value.copy(renameError = true)
            return
        }
        val record = store.activeDataset() ?: return dismissDialog()
        store.upsert(record.copy(name = name))
        dismissDialog()
        refresh()
        message(R.string.dataset_renamed)
    }

    fun showDeleteActive() {
        if (store.activeDataset() != null) mutableState.value = mutableState.value.copy(dialog = DatasetDialog.DeleteActive)
    }

    fun showDeleteAll() {
        if (store.loadDatasets().isNotEmpty()) mutableState.value = mutableState.value.copy(dialog = DatasetDialog.DeleteAll)
    }

    fun dismissDialog() {
        mutableState.value = mutableState.value.copy(dialog = null, renameError = false)
    }

    fun deleteActive(preview: PreviewAssetsViewModel) {
        val record = store.activeDataset() ?: return dismissDialog()
        cancelLoad()
        unloadPreviewAssets(preview)
        store.delete(record.id)
        normalizeActiveDataset()?.let { applySettings(preview, it) }
        dismissDialog()
        refresh()
        message(R.string.dataset_deleted)
    }

    fun deleteAll(preview: PreviewAssetsViewModel) {
        cancelLoad()
        unloadPreviewAssets(preview)
        store.deleteAll()
        dismissDialog()
        refresh()
        message(R.string.dataset_all_deleted)
    }

    fun cancelLoad() {
        cancelRequested.set(true)
        loadJob?.cancel()
        loadRequestId++
        mutableState.value = mutableState.value.copy(loading = false, loadingMessage = "", loadingPercent = null)
    }

    fun attachOrthoImage(uri: Uri, preview: PreviewAssetsViewModel) {
        persistReadPermission(uri)
        val fileName = queryDisplayName(uri) ?: app.getString(R.string.ortho_unknown_image)
        if (!isSupportedOrthoImage(fileName)) return message(R.string.ortho_select_tif)
        if (store.activeDataset() == null) return
        preview.clearOrtho()
        launchLoad(app.getString(R.string.ortho_loading_image, fileName)) {
            val decoded = withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(uri)?.use { tiffDecoder.decodePreview(it, MAX_ORTHO_PREVIEW_DIMENSION_PX) }
                    ?: error("Could not open image file.")
            }
            val updated = activeDataset().copy(
                orthoImageUri = uri,
                orthoImageName = fileName,
                orthoSourceWidth = decoded.sourceWidth,
                orthoSourceHeight = decoded.sourceHeight,
                orthoWorldUri = null,
                orthoWorldName = null,
            )
            store.upsert(updated)
            preview.setOrthoImage(decoded.bitmap, fileName, uri, decoded.sourceWidth, decoded.sourceHeight, true)
            applySettings(preview, updated)
            saveLegacyPreviewReferences(updated)
            refresh()
            message(R.string.ortho_load_world_next)
        }
    }

    fun attachWorldFile(uri: Uri, preview: PreviewAssetsViewModel) {
        persistReadPermission(uri)
        val fileName = queryDisplayName(uri) ?: app.getString(R.string.ortho_unknown_world)
        if (!isSupportedWorldFile(fileName)) return message(R.string.ortho_select_world)
        val record = store.activeDataset() ?: return
        val sourceWidth = preview.orthoAsset?.sourceWidth ?: record.orthoSourceWidth
        val sourceHeight = preview.orthoAsset?.sourceHeight ?: record.orthoSourceHeight
        if (sourceWidth == null || sourceHeight == null) return message(R.string.ortho_load_image_first)
        launchLoad(app.getString(R.string.ortho_loading_world, fileName)) {
            val bounds = withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(uri)?.use { worldFileParser.parse(it, sourceWidth, sourceHeight) }
                    ?: error("Could not open world file.")
            }
            val updated = activeDataset().copy(orthoWorldUri = uri, orthoWorldName = fileName)
            store.upsert(updated)
            preview.requestMapFocus(PreviewMapFocus.ORTHO)
            preview.setOrthoBounds(bounds, fileName, uri)
            applySettings(preview, updated)
            saveLegacyPreviewReferences(updated)
            refresh()
            message(R.string.dataset_loaded)
        }
    }

    fun attachPointCloud(uri: Uri, preview: PreviewAssetsViewModel) {
        persistReadPermission(uri)
        val fileName = queryDisplayName(uri) ?: app.getString(R.string.point_cloud_unknown_file)
        if (!isSupportedPointCloud(fileName)) return message(R.string.point_cloud_select_ply)
        val record = store.activeDataset() ?: return
        preview.clearPointCloud()
        cancelRequested.set(false)
        val detail = PointCloudDetailLevel.fromStored(record.pointCloudDetailLevel)
        launchLoad(app.getString(R.string.point_cloud_loading, fileName)) {
            val cloud = withContext(Dispatchers.IO) {
                pointCloudCache.load(uri, fileName, detail, ::updateProgress, cancelRequested::get)
            }
            val updated = activeDataset().copy(pointCloudUri = uri, pointCloudName = fileName)
            store.upsert(updated)
            preview.requestMapFocus(PreviewMapFocus.POINT_CLOUD)
            preview.setPointCloud(cloud, fileName, uri)
            applySettings(preview, updated)
            saveLegacyPreviewReferences(updated)
            refresh()
            message(R.string.dataset_loaded)
        }
    }

    private fun loadDataset(record: PreviewDatasetRecord, preview: PreviewAssetsViewModel) {
        launchLoad(app.getString(R.string.dataset_loading, record.name)) {
            unloadPreviewAssets(preview)
            applySettings(preview, record)
            cancelRequested.set(false)
            val loaded = withContext(Dispatchers.IO) {
                val decoded = record.orthoImageUri?.let { uri ->
                    app.contentResolver.openInputStream(uri)?.use { tiffDecoder.decodePreview(it, MAX_ORTHO_PREVIEW_DIMENSION_PX) }
                        ?: error("Could not open image file.")
                }
                val bounds = if (decoded != null && record.orthoWorldUri != null) {
                    app.contentResolver.openInputStream(record.orthoWorldUri)?.use {
                        worldFileParser.parse(it, decoded.sourceWidth, decoded.sourceHeight)
                    }
                } else null
                val cloud = record.pointCloudUri?.let { uri ->
                    pointCloudCache.load(
                        uri,
                        record.pointCloudName ?: app.getString(R.string.point_cloud_unknown_file),
                        PointCloudDetailLevel.fromStored(record.pointCloudDetailLevel),
                        ::updateProgress,
                        cancelRequested::get,
                    )
                }
                Triple(decoded, bounds, cloud)
            }
            val (decoded, bounds, cloud) = loaded
            if (decoded != null && record.orthoImageUri != null) {
                preview.setOrthoImage(
                    decoded.bitmap,
                    record.orthoImageName ?: app.getString(R.string.ortho_unknown_image),
                    record.orthoImageUri,
                    decoded.sourceWidth,
                    decoded.sourceHeight,
                    bounds == null,
                )
                if (bounds != null && record.orthoWorldUri != null) {
                    preview.requestMapFocus(PreviewMapFocus.ORTHO)
                    preview.setOrthoBounds(
                        bounds,
                        record.orthoWorldName ?: app.getString(R.string.ortho_unknown_world),
                        record.orthoWorldUri,
                    )
                }
            }
            if (cloud != null && record.pointCloudUri != null) {
                preview.requestMapFocus(PreviewMapFocus.POINT_CLOUD)
                preview.setPointCloud(
                    cloud,
                    record.pointCloudName ?: app.getString(R.string.point_cloud_unknown_file),
                    record.pointCloudUri,
                )
            }
            store.setActiveDatasetId(record.id)
            saveLegacyPreviewReferences(record)
            refresh()
            message(R.string.dataset_loaded)
        }
    }

    private fun launchLoad(status: String, operation: suspend () -> Unit) {
        loadJob?.cancel()
        cancelRequested.set(false)
        val requestId = ++loadRequestId
        mutableState.value = mutableState.value.copy(loading = true, loadingMessage = status, loadingPercent = null)
        loadJob = viewModelScope.launch {
            try {
                operation()
            } catch (_: CancellationException) {
                // Cancellation is an explicit user action or a replacement request.
            } catch (error: Throwable) {
                effectChannel.send(
                    DatasetEffect.Message(
                        app.getString(R.string.ortho_load_failed, error.message ?: error.javaClass.simpleName)
                    )
                )
            } finally {
                if (requestId == loadRequestId) {
                    mutableState.value = mutableState.value.copy(loading = false, loadingMessage = "", loadingPercent = null)
                }
            }
        }
    }

    private fun updateProgress(progress: com.example.droneservicesapp.data.pointcloud.PointCloudLoadProgress) {
        mutableState.value = mutableState.value.copy(
            loadingMessage = progress.message,
            loadingPercent = progress.percent,
        )
    }

    private fun updateActive(
        preview: PreviewAssetsViewModel,
        update: PreviewDatasetRecord.() -> PreviewDatasetRecord,
    ) {
        val current = store.activeDataset() ?: return
        val updated = current.update()
        store.upsert(updated)
        applySettings(preview, updated)
        refresh()
    }

    private fun refresh() {
        val active = normalizeActiveDataset()
        mutableState.value = mutableState.value.copy(
            datasets = store.loadDatasets(),
            activeDatasetId = active?.id,
        )
    }

    private fun normalizeActiveDataset(): PreviewDatasetRecord? {
        store.activeDataset()?.let { return it }
        val existing = store.loadDatasets().firstOrNull()
        store.setActiveDatasetId(existing?.id)
        return existing
    }

    private fun activeDataset(): PreviewDatasetRecord =
        checkNotNull(normalizeActiveDataset()) { "No active preview dataset" }

    private fun cleanupEmptyActiveDataset() {
        val record = store.activeDataset() ?: return
        if (!record.hasAttachedFiles()) {
            store.delete(record.id)
            normalizeActiveDataset()
        }
    }

    private fun applySettings(preview: PreviewAssetsViewModel, record: PreviewDatasetRecord) {
        preview.updateSettings {
            copy(
                orthoOpacity = record.orthoOpacity,
                orthoBackgroundEnabled = record.orthoBackgroundEnabled,
                pointCloudPointSize = record.pointCloudPointSize,
                heightColorModeEnabled = record.heightColorModeEnabled,
            )
        }
    }

    private fun unloadPreviewAssets(preview: PreviewAssetsViewModel) {
        preview.clearAssets()
        app.getSharedPreferences(PREVIEW_PREFS, Context.MODE_PRIVATE).edit { clear() }
    }

    private fun saveLegacyPreviewReferences(record: PreviewDatasetRecord) {
        app.getSharedPreferences(PREVIEW_PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_ORTHO_IMAGE_URI, record.orthoImageUri?.toString())
            putString(KEY_ORTHO_IMAGE_NAME, record.orthoImageName)
            putString(KEY_ORTHO_WORLD_URI, record.orthoWorldUri?.toString())
            putString(KEY_ORTHO_WORLD_NAME, record.orthoWorldName)
            putString(KEY_POINT_CLOUD_URI, record.pointCloudUri?.toString())
            putString(KEY_POINT_CLOUD_NAME, record.pointCloudName)
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun message(resource: Int) {
        viewModelScope.launch { effectChannel.send(DatasetEffect.Message(app.getString(resource))) }
    }

    override fun onCleared() {
        cancelRequested.set(true)
        loadJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val MAX_ORTHO_PREVIEW_DIMENSION_PX = 2048
        private const val PREVIEW_PREFS = "preview_assets"
        private const val KEY_ORTHO_IMAGE_URI = "ortho_image_uri"
        private const val KEY_ORTHO_IMAGE_NAME = "ortho_image_name"
        private const val KEY_ORTHO_WORLD_URI = "ortho_world_uri"
        private const val KEY_ORTHO_WORLD_NAME = "ortho_world_name"
        private const val KEY_POINT_CLOUD_URI = "point_cloud_uri"
        private const val KEY_POINT_CLOUD_NAME = "point_cloud_name"
    }
}
