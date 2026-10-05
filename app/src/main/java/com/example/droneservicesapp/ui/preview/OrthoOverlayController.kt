package com.example.droneservicesapp.ui.preview

import android.widget.Toast
import com.example.droneservicesapp.R
import com.example.droneservicesapp.ui.ortho.OrthoImageOverlay
import org.osmdroid.views.MapView

class OrthoOverlayController(
    private val mapView: MapView,
    private val assetsViewModel: PreviewAssetsViewModel,
    private val focusOverlay: () -> Unit,
) {
    private var overlay: OrthoImageOverlay? = null

    fun render() {
        val asset = assetsViewModel.orthoAsset
        val bounds = asset?.bounds
        if (asset == null || bounds == null) {
            clear()
            mapView.overlayManager.tilesOverlay?.isEnabled = true
            Toast.makeText(mapView.context, R.string.ortho_empty_state, Toast.LENGTH_SHORT).show()
            return
        }
        overlay?.let(mapView.overlays::remove)
        val settings = assetsViewModel.previewSettings.value
        overlay = OrthoImageOverlay(asset.bitmap, bounds).also {
            it.opacity = settings?.orthoOpacity ?: 0.85f
            mapView.overlays.add(0, it)
        }
        mapView.overlayManager.tilesOverlay?.isEnabled = settings?.orthoBackgroundEnabled ?: true
        focusOverlay()
        mapView.invalidate()
    }

    fun clear() {
        overlay?.let(mapView.overlays::remove)
        overlay = null
        mapView.invalidate()
    }

    fun setOpacity(value: Float) {
        overlay?.opacity = value
        mapView.invalidate()
    }
}
