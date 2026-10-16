package com.example.droneservicesapp.ui.map.osmdroid

import android.content.Context
import com.example.droneservicesapp.ui.map.model.OfflineDownloadProgress
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.views.MapView

data class OfflineRegionResult(val errors: Int) { val succeeded: Boolean get() = errors == 0 }

/**
 * Downloads imagery and the optional reference-label layer as one logical offline operation.
 *
 * Uses osmdroid's no-UI download so progress is shown by the Compose screen rather than osmdroid's
 * own View dialog. osmdroid delivers these callbacks on the main thread; a cancelled download
 * reports nothing further, so the caller resets its own state when it cancels.
 */
class OsmdroidOfflineRegionDownloader(
    private val context: Context,
    private val mapView: MapView,
    private val layers: EsriMapLayers,
) {
    /** Starts the download and returns an action that cancels it. */
    fun downloadVisibleRegion(
        minZoom: Int,
        maxZoom: Int,
        onProgress: (percent: Int) -> Unit,
        onComplete: (OfflineRegionResult) -> Unit,
    ): () -> Unit {
        require(minZoom <= maxZoom)
        val managers = mutableListOf(CacheManager(mapView))
        if (layers.labelsEnabled) {
            val provider = layers.labelsTileProvider
            managers += CacheManager(provider, provider.tileWriter, minZoom, maxZoom)
        }
        val tileProgress = OfflineDownloadProgress(managers.size)
        var pending = managers.size
        var errors = 0
        var lastPercent = -1

        fun publishProgress() {
            val percent = tileProgress.percent ?: return
            if (percent != lastPercent) {
                lastPercent = percent
                onProgress(percent)
            }
        }

        fun callback(index: Int) = object : CacheManager.CacheManagerCallback {
            private var finished = false
            override fun downloadStarted() = Unit
            override fun setPossibleTilesInArea(total: Int) {
                tileProgress.setTotal(index, total)
                publishProgress()
            }
            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                tileProgress.setProcessed(index, progress)
                publishProgress()
            }
            override fun onTaskComplete() = finish(0)
            override fun onTaskFailed(count: Int) = finish(count.coerceAtLeast(1))
            private fun finish(errorCount: Int) {
                if (finished) return
                finished = true
                tileProgress.complete(index)
                errors += errorCount
                pending -= 1
                if (pending == 0) mapView.post { onComplete(OfflineRegionResult(errors)) }
            }
        }

        val tasks = managers.mapIndexed { index, manager ->
            manager.downloadAreaAsyncNoUI(context, mapView.boundingBox, minZoom, maxZoom, callback(index))
        }
        return { tasks.forEach { it.cancel(true) } }
    }
}
