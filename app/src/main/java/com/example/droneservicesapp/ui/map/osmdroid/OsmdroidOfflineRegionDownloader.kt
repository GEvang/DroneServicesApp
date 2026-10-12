package com.example.droneservicesapp.ui.map.osmdroid

import android.content.Context
import com.example.droneservicesapp.ui.home.components.EsriMapLayers
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.views.MapView
import java.util.concurrent.atomic.AtomicInteger

data class OfflineRegionResult(val errors: Int) { val succeeded: Boolean get() = errors == 0 }

/** Downloads imagery and the optional reference-label layer as one logical offline operation. */
class OsmdroidOfflineRegionDownloader(
    private val context: Context,
    private val mapView: MapView,
    private val layers: EsriMapLayers,
) {
    fun downloadVisibleRegion(minZoom: Int, maxZoom: Int, onComplete: (OfflineRegionResult) -> Unit) {
        require(minZoom <= maxZoom)
        val managers = mutableListOf(CacheManager(mapView))
        if (layers.labelsEnabled) {
            val provider = layers.labelsTileProvider
            managers += CacheManager(provider, provider.tileWriter, minZoom, maxZoom)
        }
        val pending = AtomicInteger(managers.size)
        val errors = AtomicInteger(0)
        fun callback() = object : CacheManager.CacheManagerCallback {
            override fun downloadStarted() = Unit
            override fun setPossibleTilesInArea(total: Int) = Unit
            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) = Unit
            override fun onTaskComplete() = finish(0)
            override fun onTaskFailed(count: Int) = finish(count.coerceAtLeast(1))
            private fun finish(errorCount: Int) {
                errors.addAndGet(errorCount)
                if (pending.decrementAndGet() == 0) mapView.post { onComplete(OfflineRegionResult(errors.get())) }
            }
        }
        managers.forEach { it.downloadAreaAsync(context, mapView.boundingBox, minZoom, maxZoom, callback()) }
    }
}
