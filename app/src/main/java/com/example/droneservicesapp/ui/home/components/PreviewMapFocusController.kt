package com.example.droneservicesapp.ui.home.components

import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.preview.PreviewMapFocus
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/** Centers the map on imported preview assets without leaking fit calculations into the Fragment. */
class PreviewMapFocusController(
    private val mapView: MapView,
    private val assets: PreviewAssetsViewModel,
    private val onFocused: () -> Unit,
) {
    fun hasPendingRequest(): Boolean = assets.hasPendingMapFocusRequest()

    fun focusPendingAsset(): Boolean {
        val focus = assets.consumeMapFocusRequest() ?: return false
        val centered = when (focus) {
            PreviewMapFocus.POINT_CLOUD -> focusPointCloud() || focusOrtho()
            PreviewMapFocus.ORTHO -> focusOrtho() || focusPointCloud()
        }
        if (centered) onFocused()
        return centered
    }

    fun focusOrtho(): Boolean {
        val bounds = assets.orthoAsset?.bounds ?: return false
        focusBounds(bounds.minLat, bounds.maxLat, bounds.minLon, bounds.maxLon)
        return true
    }

    private fun focusPointCloud(): Boolean {
        val pointCloud = assets.pointCloudAsset?.pointCloud ?: return false
        val frame = pointCloud.coordinateFrame ?: return false
        val halfSpanX = (pointCloud.bounds.spanX / 2f).toDouble().coerceAtLeast(MIN_SPAN_METERS)
        val halfSpanY = (pointCloud.bounds.spanY / 2f).toDouble().coerceAtLeast(MIN_SPAN_METERS)
        val corners = listOf(
            frame.localToLatLon(-halfSpanX, -halfSpanY),
            frame.localToLatLon(halfSpanX, halfSpanY),
        )
        focusBounds(
            minLat = corners.minOf { it.first },
            maxLat = corners.maxOf { it.first },
            minLon = corners.minOf { it.second },
            maxLon = corners.maxOf { it.second },
        )
        return true
    }

    private fun focusBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        mapView.controller.setZoom(calculateSafeZoom(minLat, maxLat, minLon, maxLon, mapView.width, mapView.height))
        mapView.controller.setCenter(GeoPoint((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0))
        mapView.invalidate()
    }

    companion object {
        private const val FIT_PADDING_PX = 96
        private const val MIN_SPAN_METERS = 10.0
        private const val TILE_SIZE_PX = 256.0
        private const val MIN_VIEWPORT_PX = 320
        private const val MIN_BOUNDS_SPAN_DEGREES = 0.000001
        private const val MIN_MERCATOR_SPAN = 0.000001
        private const val MIN_MERCATOR_LATITUDE = -85.05112878
        private const val MAX_MERCATOR_LATITUDE = 85.05112878
        private const val MIN_ZOOM = 2.0
        private const val MAX_ZOOM = 21.0

        internal fun calculateSafeZoom(
            minLat: Double,
            maxLat: Double,
            minLon: Double,
            maxLon: Double,
            viewportWidth: Int,
            viewportHeight: Int,
        ): Double {
            val lonSpan = (maxLon - minLon).coerceAtLeast(MIN_BOUNDS_SPAN_DEGREES)
            val mercatorSpan = abs(mercatorY(maxLat) - mercatorY(minLat)).coerceAtLeast(MIN_MERCATOR_SPAN)
            val mapWidth = max(viewportWidth - FIT_PADDING_PX * 2, MIN_VIEWPORT_PX)
            val mapHeight = max(viewportHeight - FIT_PADDING_PX * 2, MIN_VIEWPORT_PX)
            val lonZoom = log2(mapWidth * 360.0 / (TILE_SIZE_PX * lonSpan))
            val latZoom = log2(mapHeight * 2.0 * PI / (TILE_SIZE_PX * mercatorSpan))
            return min(lonZoom, latZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
        }

        private fun mercatorY(latitude: Double): Double {
            val radians = Math.toRadians(latitude.coerceIn(MIN_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE))
            return ln(tan(PI / 4.0 + radians / 2.0))
        }

        private fun log2(value: Double): Double = ln(value) / ln(2.0)
    }
}
