package com.example.droneservicesapp.ui.preview

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.droneservicesapp.data.ortho.OrthoBounds
import com.example.droneservicesapp.data.pointcloud.PointCloudData
import com.example.droneservicesapp.domain.terrain.TerrainGridSummary
import com.example.droneservicesapp.domain.terrain.PointCloudTerrainModel

data class OrthoPreviewAsset(
    val bitmap: Bitmap,
    val bitmapFileName: String,
    val bitmapUri: Uri,
    val sourceWidth: Int = bitmap.width,
    val sourceHeight: Int = bitmap.height,
    val bounds: OrthoBounds? = null,
    val worldFileName: String? = null,
    val worldFileUri: Uri? = null,
)

data class PointCloudPreviewAsset(
    val pointCloud: PointCloudData,
    val fileName: String,
    val uri: Uri,
)

enum class PreviewMapFocus {
    ORTHO,
    POINT_CLOUD
}

class PreviewAssetsViewModel : ViewModel() {
    private val _previewSettings = MutableLiveData(PreviewSettings())
    val previewSettings: LiveData<PreviewSettings> = _previewSettings

    private val _assetVersion = MutableLiveData(0)
    val assetVersion: LiveData<Int> = _assetVersion

    var orthoAsset: OrthoPreviewAsset? = null
        private set

    var pointCloudAsset: PointCloudPreviewAsset? = null
        private set

    var pointCloudTerrainModel: PointCloudTerrainModel? = null
        private set

    var pointCloudTerrainSummary: TerrainGridSummary? = null
        private set

    private var pendingMapFocus: PreviewMapFocus? = null

    fun setOrthoImage(
        bitmap: Bitmap,
        bitmapFileName: String,
        bitmapUri: Uri,
        sourceWidth: Int = bitmap.width,
        sourceHeight: Int = bitmap.height,
        notifyChange: Boolean = true
    ) {
        orthoAsset = OrthoPreviewAsset(
            bitmap = bitmap,
            bitmapFileName = bitmapFileName,
            bitmapUri = bitmapUri,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
        )
        if (notifyChange) notifyAssetsChanged()
    }

    fun setOrthoBounds(bounds: OrthoBounds, worldFileName: String, worldFileUri: Uri) {
        val current = orthoAsset ?: return
        orthoAsset = current.copy(
            bounds = bounds,
            worldFileName = worldFileName,
            worldFileUri = worldFileUri,
        )
        notifyAssetsChanged()
    }

    fun clearOrtho() {
        orthoAsset = null
        notifyAssetsChanged()
    }

    fun setPointCloud(pointCloud: PointCloudData, fileName: String, uri: Uri) {
        pointCloudAsset = PointCloudPreviewAsset(
            pointCloud = pointCloud,
            fileName = fileName,
            uri = uri,
        )
        pointCloudTerrainModel = PointCloudTerrainModel(pointCloud)
        pointCloudTerrainSummary = null
        notifyAssetsChanged()
    }

    fun setPointCloudTerrainSummary(summary: TerrainGridSummary) {
        pointCloudTerrainSummary = summary
        notifyAssetsChanged()
    }

    fun clearPointCloud() {
        pointCloudAsset = null
        pointCloudTerrainModel = null
        pointCloudTerrainSummary = null
        notifyAssetsChanged()
    }

    fun clearAssets() {
        orthoAsset = null
        pointCloudAsset = null
        pointCloudTerrainModel = null
        pointCloudTerrainSummary = null
        notifyAssetsChanged()
    }

    fun updateSettings(update: PreviewSettings.() -> PreviewSettings) {
        _previewSettings.value = (_previewSettings.value ?: PreviewSettings()).update()
    }

    fun requestMapFocus(focus: PreviewMapFocus) {
        pendingMapFocus = focus
    }

    fun hasPendingMapFocusRequest(): Boolean = pendingMapFocus != null

    fun consumeMapFocusRequest(): PreviewMapFocus? {
        val focus = pendingMapFocus
        pendingMapFocus = null
        return focus
    }

    private fun notifyAssetsChanged() {
        _assetVersion.value = (_assetVersion.value ?: 0) + 1
    }
}

data class PreviewSettings(
    val orthoOpacity: Float = 0.85f,
    val orthoBackgroundEnabled: Boolean = true,
    val pointCloudPointSize: Float = 2.5f,
    val heightColorModeEnabled: Boolean = false,
)
