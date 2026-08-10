package com.example.droneservicesapp.ui.preview

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.droneservicesapp.data.ortho.OrthoBounds
import com.example.droneservicesapp.data.pointcloud.PointCloudData

data class OrthoPreviewAsset(
    val bitmap: Bitmap,
    val bitmapFileName: String,
    val bitmapUri: Uri,
    val bounds: OrthoBounds? = null,
    val worldFileName: String? = null,
    val worldFileUri: Uri? = null,
)

data class PointCloudPreviewAsset(
    val pointCloud: PointCloudData,
    val fileName: String,
    val uri: Uri,
)

class PreviewAssetsViewModel : ViewModel() {
    var orthoAsset: OrthoPreviewAsset? = null
        private set

    var pointCloudAsset: PointCloudPreviewAsset? = null
        private set

    fun setOrthoImage(bitmap: Bitmap, bitmapFileName: String, bitmapUri: Uri) {
        orthoAsset = OrthoPreviewAsset(
            bitmap = bitmap,
            bitmapFileName = bitmapFileName,
            bitmapUri = bitmapUri,
        )
    }

    fun setOrthoBounds(bounds: OrthoBounds, worldFileName: String, worldFileUri: Uri) {
        val current = orthoAsset ?: return
        orthoAsset = current.copy(
            bounds = bounds,
            worldFileName = worldFileName,
            worldFileUri = worldFileUri,
        )
    }

    fun clearOrtho() {
        orthoAsset = null
    }

    fun setPointCloud(pointCloud: PointCloudData, fileName: String, uri: Uri) {
        pointCloudAsset = PointCloudPreviewAsset(
            pointCloud = pointCloud,
            fileName = fileName,
            uri = uri,
        )
    }
}
