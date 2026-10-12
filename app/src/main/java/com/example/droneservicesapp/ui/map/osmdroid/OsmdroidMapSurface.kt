package com.example.droneservicesapp.ui.map.osmdroid

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.droneservicesapp.ui.home.components.EsriMapLayers
import com.example.droneservicesapp.ui.map.model.MapCamera
import com.example.droneservicesapp.ui.map.model.MapCameraCommand
import com.example.droneservicesapp.ui.map.model.MapCoordinate
import com.example.droneservicesapp.ui.map.model.MapInteractionHandlers
import com.example.droneservicesapp.ui.map.model.MapScene
import com.example.droneservicesapp.ui.map.model.MapOverlayLayer
import com.example.droneservicesapp.ui.ortho.OrthoImageOverlay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

/** The sole production osmdroid/Compose interop boundary. */
@Composable
fun OsmdroidMapSurface(
    scene: MapScene,
    rasterBitmaps: Map<String, Bitmap>,
    initialCamera: MapCamera,
    cameraCommand: MapCameraCommand? = null,
    backgroundEnabled: Boolean = true,
    interactions: MapInteractionHandlers = MapInteractionHandlers(),
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var host by remember { mutableStateOf<OsmdroidSceneHost?>(null) }

    DisposableEffect(lifecycleOwner, host) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> host?.resume()
                Lifecycle.Event.ON_PAUSE -> host?.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) host?.resume()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).also { map ->
                host = OsmdroidSceneHost(map, initialCamera, interactions)
            }
        },
        update = { map ->
            val current = host ?: OsmdroidSceneHost(map, initialCamera, interactions).also { host = it }
            current.interactions = interactions
            current.render(scene, rasterBitmaps, backgroundEnabled)
            current.apply(cameraCommand)
        },
        onRelease = { map ->
            host?.release()
            host = null
            map.onDetach()
        },
    )
}

private class OsmdroidSceneHost(
    private val map: MapView,
    initialCamera: MapCamera,
    var interactions: MapInteractionHandlers,
) {
    private val layers = EsriMapLayers.install(map.context, map)
    private var dynamicOverlays: List<Overlay> = emptyList()
    private var renderedScene: MapScene? = null
    private var renderedRasterIdentities: Map<String, Int> = emptyMap()
    private var lastCommandToken: Long? = null

    init {
        map.setBuiltInZoomControls(false)
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true
        map.maxZoomLevel = 22.0
        map.controller.setZoom(initialCamera.zoom)
        map.controller.setCenter(initialCamera.center.geoPoint())
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                interactions.onTap(point.model())
                return false
            }

            override fun longPressHelper(point: GeoPoint): Boolean {
                interactions.onLongPress(point.model())
                return true
            }
        }))
        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { publishCamera(); return false }
            override fun onZoom(event: ZoomEvent?): Boolean { publishCamera(); return false }
        })
    }

    fun render(scene: MapScene, rasterBitmaps: Map<String, Bitmap>, backgroundEnabled: Boolean) {
        map.overlayManager.tilesOverlay?.isEnabled = backgroundEnabled
        layers.refreshLabelsEnabled()
        val identities = rasterBitmaps.mapValues { System.identityHashCode(it.value) }
        if (scene == renderedScene && identities == renderedRasterIdentities) return
        map.overlays.removeAll(dynamicOverlays.toSet())
        dynamicOverlays = buildList<Pair<MapOverlayLayer, Overlay>> {
            scene.rasters.forEach { raster ->
                rasterBitmaps[raster.id]?.let { bitmap ->
                    add(raster.layer to OrthoImageOverlay(bitmap, raster.bounds.toOrthoBounds()).apply { opacity = raster.opacity })
                }
            }
            scene.polygons.forEach { model ->
                add(model.layer to Polygon(map).apply {
                    infoWindow = null
                    points = model.points.map(MapCoordinate::geoPoint)
                    outlinePaint.color = model.strokeColorArgb
                    outlinePaint.strokeWidth = model.strokeWidthPx
                    fillPaint.color = model.fillColorArgb
                })
            }
            scene.polylines.forEach { model ->
                add(model.layer to Polyline(map).apply {
                    infoWindow = null
                    setPoints(model.points.map(MapCoordinate::geoPoint))
                    outlinePaint.color = model.colorArgb
                    outlinePaint.strokeWidth = model.widthPx
                })
            }
            scene.markers.forEach { model ->
                add(model.layer to Marker(map).apply {
                    position = model.position.geoPoint()
                    title = model.title
                    rotation = model.rotationDegrees
                    isDraggable = model.draggable
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    if (model.draggable) {
                        setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                            override fun onMarkerDrag(marker: Marker) = Unit
                            override fun onMarkerDragStart(marker: Marker) = Unit
                            override fun onMarkerDragEnd(marker: Marker) {
                                interactions.onMarkerMoved(model.id, marker.position.model())
                            }
                        })
                    }
                })
            }
        }.sortedBy { it.first.drawOrder }.map { it.second }
        map.overlays.addAll(dynamicOverlays)
        layers.bringAttributionToFront()
        renderedScene = scene
        renderedRasterIdentities = identities
        map.invalidate()
    }

    fun apply(command: MapCameraCommand?) {
        if (command == null || command.token == lastCommandToken) return
        lastCommandToken = command.token
        when (command) {
            is MapCameraCommand.Move -> {
                map.controller.setZoom(command.camera.zoom)
                map.controller.animateTo(command.camera.center.geoPoint())
            }
            is MapCameraCommand.Fit -> {
                val box = BoundingBox(
                    command.bounds.northEast.latitude,
                    command.bounds.northEast.longitude,
                    command.bounds.southWest.latitude,
                    command.bounds.southWest.longitude,
                )
                map.post { map.zoomToBoundingBox(box, true, command.paddingPx) }
            }
        }
    }

    fun resume() { map.onResume(); layers.refreshLabelsEnabled() }
    fun pause() = map.onPause()
    fun release() { dynamicOverlays = emptyList() }

    private fun publishCamera() {
        val center = map.mapCenter
        interactions.onCameraChanged(MapCamera(MapCoordinate(center.latitude, center.longitude), map.zoomLevelDouble))
    }
}

private fun MapCoordinate.geoPoint() = GeoPoint(latitude, longitude)
private fun GeoPoint.model() = MapCoordinate(latitude, longitude)
private fun com.example.droneservicesapp.ui.map.model.MapBounds.toOrthoBounds() =
    com.example.droneservicesapp.data.ortho.OrthoBounds(
        minLon = southWest.longitude,
        minLat = southWest.latitude,
        maxLon = northEast.longitude,
        maxLat = northEast.latitude,
    )
