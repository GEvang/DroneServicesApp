package com.example.droneservicesapp.ui.preview

import android.content.Context
import android.net.Uri
import com.example.droneservicesapp.data.ortho.DecodedTiffBitmap
import com.example.droneservicesapp.data.ortho.OrthoBounds
import com.example.droneservicesapp.data.ortho.SimpleTiffDecoder
import com.example.droneservicesapp.data.ortho.WorldFileParser
import com.example.droneservicesapp.data.pointcloud.PointCloudData
import com.example.droneservicesapp.data.pointcloud.PointCloudImportCache

/** Performs blocking preview-file decoding. Callers choose the coroutine dispatcher. */
class PreviewAssetLoader(context: Context) {
    private val appContext = context.applicationContext
    private val tiffDecoder = SimpleTiffDecoder()
    private val worldFileParser = WorldFileParser()
    private val pointCloudImportCache = PointCloudImportCache(appContext)

    fun loadOrthoImage(uri: Uri): DecodedTiffBitmap =
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            tiffDecoder.decodePreview(stream, MAX_ORTHO_PREVIEW_DIMENSION_PX)
        } ?: error("Could not open image file.")

    fun loadWorldFile(uri: Uri, imageWidth: Int, imageHeight: Int): OrthoBounds =
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            worldFileParser.parse(stream, imageWidth, imageHeight)
        } ?: error("Could not open world file.")

    fun loadPointCloud(uri: Uri, fileName: String): PointCloudData =
        pointCloudImportCache.load(uri, fileName)

    companion object {
        private const val MAX_ORTHO_PREVIEW_DIMENSION_PX = 2048
    }
}
