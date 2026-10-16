package com.example.droneservicesapp.ui.map.osmdroid

import android.graphics.Bitmap
import android.graphics.DashPathEffect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.droneservicesapp.ui.map.model.MapBounds
import com.example.droneservicesapp.ui.map.model.MapCamera
import com.example.droneservicesapp.ui.map.model.MapCameraCommand
import com.example.droneservicesapp.ui.map.model.MapCoordinate
import com.example.droneservicesapp.ui.map.model.MapInteractionHandlers
import com.example.droneservicesapp.ui.map.model.MapMarkerAnchor
import com.example.droneservicesapp.ui.map.model.MapMarkerDragMode
import com.example.droneservicesapp.ui.map.model.MapMarkerModel
import com.example.droneservicesapp.ui.map.model.MapOverlayLayer
import com.example.droneservicesapp.ui.map.model.MapPolygonModel
import com.example.droneservicesapp.ui.map.model.MapPolylineModel
import com.example.droneservicesapp.ui.map.model.MapRasterModel
import com.example.droneservicesapp.ui.map.model.MapScene
import com.example.droneservicesapp.ui.map.model.MapScreenPoint
import com.example.droneservicesapp.ui.map.model.MapSurfaceHandle
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
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * The sole production osmdroid/Compose interop boundary.
 *
 * The scene is reconciled by overlay id: unchanged overlays are left alone, changed ones are
 * updated in place, and only real additions/removals touch the overlay list. Taps that no
 * clickable marker consumes reach [MapInteractionHandlers.onTap].
 */
@Composable
fun OsmdroidMapSurface(
    scene: MapScene,
    rasterBitmaps: Map<String, Bitmap>,
    initialCamera: MapCamera,
    cameraCommand: MapCameraCommand? = null,
    backgroundEnabled: Boolean = true,
    interactions: MapInteractionHandlers = MapInteractionHandlers(),
    modifier: Modifier = Modifier,
    maxZoom: Double = 22.0,
    showUserLocation: Boolean = false,
    onSurfaceReady: (MapSurfaceHandle?) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var host by remember { mutableStateOf<OsmdroidSceneHost?>(null) }
    val currentOnSurfaceReady by rememberUpdatedState(onSurfaceReady)

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
        currentOnSurfaceReady(host)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentOnSurfaceReady(null)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).also { map ->
                host = OsmdroidSceneHost(map, initialCamera, interactions, maxZoom, showUserLocation)
            }
        },
        update = { map ->
            val current = host ?: OsmdroidSceneHost(map, initialCamera, interactions, maxZoom, showUserLocation)
                .also { host = it }
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
    maxZoom: Double,
    showUserLocation: Boolean,
) : MapSurfaceHandle {
    private val layers = EsriMapLayers.install(map.context, map)
    private val icons = OsmdroidMarkerIcons(map.context)
    private val density = map.context.resources.displayMetrics.density
    private val entries = LinkedHashMap<String, SceneEntry>()
    private var orderedIds: List<String> = emptyList()
    private var renderedScene: MapScene? = null
    private var renderedRasterIdentities: Map<String, Int> = emptyMap()
    private var lastCommandToken: Long? = null
    private var draggingMarkerId: String? = null
    private val myLocationOverlay: MyLocationNewOverlay? = if (showUserLocation) {
        MyLocationNewOverlay(GpsMyLocationProvider(map.context), map).apply {
            enableMyLocation()
            disableFollowLocation()
        }
    } else null
    private val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
        override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
            point.modelOrNull()?.let(interactions.onTap)
            return true
        }

        override fun longPressHelper(point: GeoPoint): Boolean {
            point.modelOrNull()?.let(interactions.onLongPress)
            return true
        }
    })
    private val gestureOverlay = MarkerGestureOverlay()
    private var lastRedrawUptimeMs = 0L
    private var redrawScheduled = false
    private val redrawRunnable = Runnable {
        redrawScheduled = false
        lastRedrawUptimeMs = SystemClock.uptimeMillis()
        map.postInvalidateOnAnimation()
    }

    init {
        map.setBuiltInZoomControls(false)
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true
        map.maxZoomLevel = maxZoom
        map.controller.setZoom(initialCamera.zoom)
        map.controller.setCenter(initialCamera.center.geoPoint())
        // The events overlay sits directly above the tile/label layers so that every scene
        // overlay (and clickable marker) gets the first chance to handle a tap.
        map.overlays.add(eventsOverlay)
        myLocationOverlay?.let { map.overlays.add(it) }
        map.overlays.add(gestureOverlay)
        layers.bringAttributionToFront()
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

        val wanted = LinkedHashMap<String, Pair<MapOverlayLayer, Any>>()
        scene.rasters.forEach { wanted[it.id] = it.layer to it }
        scene.polygons.forEach { wanted[it.id] = it.layer to it }
        scene.polylines.forEach { wanted[it.id] = it.layer to it }
        scene.markers.forEach { wanted[it.id] = it.layer to it }

        var structureChanged = false
        val removed = entries.keys.filterNot { it in wanted.keys }
        removed.forEach { id ->
            entries.remove(id)?.let { map.overlays.remove(it.overlay) }
            structureChanged = true
        }
        wanted.forEach { (id, value) ->
            val model = value.second
            val existing = entries[id]
            if (existing != null && existing.model::class == model::class && existing.compatible(model, rasterBitmaps)) {
                if (existing.model != model) existing.update(model)
            } else {
                existing?.let { map.overlays.remove(it.overlay) }
                val created = create(model, rasterBitmaps) ?: return@forEach
                entries[id] = created
                structureChanged = true
            }
        }
        val newOrder = wanted.entries
            .filter { entries.containsKey(it.key) }
            .sortedBy { it.value.first.drawOrder }
            .map { it.key }
        if (structureChanged || newOrder != orderedIds) {
            orderedIds = newOrder
            reorderOverlays()
        }
        renderedScene = scene
        renderedRasterIdentities = identities
        requestRedraw()
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
            is MapCameraCommand.Pan -> map.controller.animateTo(command.center.geoPoint())
            is MapCameraCommand.Jump -> {
                map.controller.setZoom(command.zoom)
                map.controller.setCenter(command.center.geoPoint())
                map.invalidate()
            }
        }
    }

    fun resume() {
        map.onResume()
        myLocationOverlay?.enableMyLocation()
        layers.refreshLabelsEnabled()
        requestRedraw()
    }

    fun pause() {
        myLocationOverlay?.disableMyLocation()
        map.removeCallbacks(redrawRunnable)
        redrawScheduled = false
        map.onPause()
    }

    fun release() {
        map.removeCallbacks(redrawRunnable)
        myLocationOverlay?.disableMyLocation()
        entries.values.forEach { map.overlays.remove(it.overlay) }
        entries.clear()
        orderedIds = emptyList()
    }

    // MapSurfaceHandle

    override val viewportWidthPx: Int get() = map.width
    override val viewportHeightPx: Int get() = map.height

    override fun currentCamera(): MapCamera {
        val center = map.mapCenter
        return MapCamera(MapCoordinate(center.latitude, center.longitude.coerceIn(-180.0, 180.0)), map.zoomLevelDouble)
    }

    override fun userLocation(): MapCoordinate? =
        myLocationOverlay?.myLocation?.let { location ->
            runCatching { MapCoordinate(location.latitude, location.longitude) }.getOrNull()
        }

    override fun toScreenPoint(coordinate: MapCoordinate): MapScreenPoint? {
        if (map.width == 0 || map.height == 0) return null
        val pixel = map.projection.toPixels(coordinate.geoPoint(), null) ?: return null
        return MapScreenPoint(pixel.x.toFloat(), pixel.y.toFloat())
    }

    override fun visibleBounds(): MapBounds? {
        if (map.width == 0 || map.height == 0) return null
        val box = map.boundingBox
        return runCatching {
            MapBounds(
                MapCoordinate(box.latSouth.coerceIn(-90.0, 90.0), box.lonWest.coerceIn(-180.0, 180.0)),
                MapCoordinate(box.latNorth.coerceIn(-90.0, 90.0), box.lonEast.coerceIn(-180.0, 180.0)),
            )
        }.getOrNull()
    }

    override fun downloadVisibleRegion(minZoom: Int, maxZoom: Int, onComplete: (errorCount: Int) -> Unit) {
        OsmdroidOfflineRegionDownloader(map.context, map, layers)
            .downloadVisibleRegion(minZoom, maxZoom) { result -> onComplete(result.errors) }
    }

    // Reconciliation

    private fun reorderOverlays() {
        val sceneOverlays = orderedIds.mapNotNull { entries[it]?.overlay }
        map.overlays.removeAll(sceneOverlays.toSet())
        map.overlays.remove(gestureOverlay)
        // Scene overlays go above the events overlay and below the gesture overlay, which must
        // see touches first to recognise immediate marker drags and marker double taps.
        val eventsIndex = map.overlays.indexOf(eventsOverlay).coerceAtLeast(0)
        val locationIndex = myLocationOverlay?.let { map.overlays.indexOf(it) }?.takeIf { it >= 0 }
        val insertAt = (locationIndex ?: eventsIndex) + 1
        map.overlays.addAll(insertAt.coerceAtMost(map.overlays.size), sceneOverlays)
        map.overlays.add(gestureOverlay)
        layers.bringAttributionToFront()
    }

    private fun create(model: Any, rasterBitmaps: Map<String, Bitmap>): SceneEntry? = when (model) {
        is MapRasterModel -> rasterBitmaps[model.id]?.let { bitmap ->
            RasterEntry(model, bitmap, OrthoImageOverlay(bitmap, model.bounds.toOrthoBounds()).apply { opacity = model.opacity })
        }
        is MapPolygonModel -> PolygonEntry(model, Polygon(map).apply {
            infoWindow = null
            // Shapes never consume taps; map-level tap routing decides what a tap means.
            setOnClickListener { _, _, _ -> false }
        }).also { it.update(model) }
        is MapPolylineModel -> PolylineEntry(model, Polyline(map).apply {
            infoWindow = null
            setOnClickListener { _, _, _ -> false }
        }).also { it.update(model) }
        is MapMarkerModel -> MarkerEntry(model, Marker(map).apply { infoWindow = null }).also { it.configure(); it.update(model) }
        else -> null
    }

    private fun requestRedraw() {
        if (draggingMarkerId != null) {
            map.invalidate()
            return
        }
        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastRedrawUptimeMs
        if (elapsed >= MIN_REDRAW_INTERVAL_MS && !redrawScheduled) {
            lastRedrawUptimeMs = now
            map.postInvalidateOnAnimation()
            return
        }
        if (redrawScheduled) return
        redrawScheduled = true
        map.postDelayed(redrawRunnable, (MIN_REDRAW_INTERVAL_MS - elapsed).coerceAtLeast(0L))
    }

    private fun publishCamera() {
        interactions.onCameraChanged(currentCamera())
    }

    private abstract inner class SceneEntry(var model: Any, val overlay: Overlay) {
        open fun compatible(next: Any, rasterBitmaps: Map<String, Bitmap>): Boolean = true
        abstract fun update(next: Any)
    }

    private inner class RasterEntry(model: MapRasterModel, val bitmap: Bitmap, val raster: OrthoImageOverlay) :
        SceneEntry(model, raster) {
        override fun compatible(next: Any, rasterBitmaps: Map<String, Bitmap>): Boolean {
            next as MapRasterModel
            return rasterBitmaps[next.id] === bitmap && next.bounds == (model as MapRasterModel).bounds
        }

        override fun update(next: Any) {
            next as MapRasterModel
            raster.opacity = next.opacity
            model = next
        }
    }

    private inner class PolygonEntry(model: MapPolygonModel, val polygon: Polygon) : SceneEntry(model, polygon) {
        override fun update(next: Any) {
            next as MapPolygonModel
            polygon.points = ArrayList(next.points.map(MapCoordinate::geoPoint))
            polygon.outlinePaint.color = next.strokeColorArgb
            polygon.outlinePaint.strokeWidth = next.strokeWidthPx
            polygon.outlinePaint.pathEffect = if (next.dashed) DASH_EFFECT else null
            polygon.fillPaint.color = next.fillColorArgb
            model = next
        }
    }

    private inner class PolylineEntry(model: MapPolylineModel, val polyline: Polyline) : SceneEntry(model, polyline) {
        override fun update(next: Any) {
            next as MapPolylineModel
            polyline.setPoints(next.points.map(MapCoordinate::geoPoint))
            polyline.outlinePaint.color = next.colorArgb
            polyline.outlinePaint.strokeWidth = next.widthPx
            model = next
        }
    }

    private inner class MarkerEntry(model: MapMarkerModel, val marker: Marker) : SceneEntry(model, marker) {
        val current: MapMarkerModel get() = model as MapMarkerModel
        private var initialized = false

        fun configure() {
            marker.setOnMarkerClickListener { _, _ ->
                current.clickable && interactions.onMarkerClick(current.id)
            }
            marker.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDragStart(dragged: Marker) {
                    draggingMarkerId = current.id
                    interactions.onMarkerDragStart(current.id)
                }

                override fun onMarkerDrag(dragged: Marker) {
                    dragged.position.modelOrNull()?.let { interactions.onMarkerDrag(current.id, it) }
                }

                override fun onMarkerDragEnd(dragged: Marker) {
                    draggingMarkerId = null
                    dragged.position.modelOrNull()?.let { interactions.onMarkerMoved(current.id, it) }
                }
            })
        }

        override fun update(next: Any) {
            next as MapMarkerModel
            val previous = model as MapMarkerModel
            // Never yank a marker out from under the operator's finger mid-drag.
            if (!initialized || (draggingMarkerId != next.id && previous.position != next.position)) {
                marker.position = next.position.geoPoint()
            }
            marker.title = next.title
            marker.rotation = next.rotationDegrees
            marker.isDraggable = next.dragMode == MapMarkerDragMode.LONG_PRESS
            if (!initialized || previous.icon != next.icon) {
                // A null drawable selects osmdroid's default pin.
                marker.icon = icons.drawableFor(next.icon)
            }
            when (next.anchor) {
                MapMarkerAnchor.CENTER -> marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                MapMarkerAnchor.BOTTOM_CENTER -> marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            initialized = true
            model = next
        }
    }

    /**
     * Recognises the two marker gestures osmdroid does not offer: an immediate press-and-drag
     * (no long press) and a double tap. Only markers that opt in are considered.
     *
     * An immediate-drag marker consumes its touch-down so the map does not pan; osmdroid's own
     * gesture detector then never sees that touch, so taps and double taps on such markers are
     * recognised here as well.
     */
    private inner class MarkerGestureOverlay : Overlay() {
        private var candidateId: String? = null
        private var startX = 0f
        private var startY = 0f
        private var dragging = false
        private var lastTapId: String? = null
        private var lastTapUptimeMs = 0L

        override fun onDoubleTap(event: MotionEvent, mapView: MapView): Boolean {
            val id = findMarkerAt(event.x, event.y, DOUBLE_TAP_RADIUS_DP) { it.doubleTappable } ?: return false
            clearDrag()
            return interactions.onMarkerDoubleTap(id)
        }

        override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    candidateId = findMarkerAt(event.x, event.y, DRAG_RADIUS_DP) {
                        it.dragMode == MapMarkerDragMode.IMMEDIATE
                    }
                    startX = event.x
                    startY = event.y
                    dragging = false
                    return candidateId != null
                }
                MotionEvent.ACTION_MOVE -> {
                    val id = candidateId ?: return false
                    val slop = DRAG_SLOP_DP * density
                    val dx = event.x - startX
                    val dy = event.y - startY
                    if (!dragging && dx * dx + dy * dy >= slop * slop) {
                        dragging = true
                        draggingMarkerId = id
                        interactions.onMarkerDragStart(id)
                    }
                    if (dragging) {
                        val point = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt())
                        val geoPoint = GeoPoint(point.latitude, point.longitude)
                        (entries[id] as? MarkerEntry)?.marker?.position = geoPoint
                        geoPoint.modelOrNull()?.let { interactions.onMarkerDrag(id, it) }
                        mapView.invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val id = candidateId ?: return false
                    if (dragging) {
                        val position = (entries[id] as? MarkerEntry)?.marker?.position
                        draggingMarkerId = null
                        lastTapId = null
                        position?.modelOrNull()?.let { interactions.onMarkerMoved(id, it) }
                    } else {
                        handleTap(id)
                    }
                    clearDrag()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    val handled = candidateId != null
                    if (dragging) draggingMarkerId = null
                    clearDrag()
                    return handled
                }
            }
            return false
        }

        private fun clearDrag() {
            candidateId = null
            dragging = false
        }

        private fun handleTap(id: String) {
            val model = (entries[id] as? MarkerEntry)?.current ?: return
            val now = SystemClock.uptimeMillis()
            val isDoubleTap = lastTapId == id &&
                now - lastTapUptimeMs <= ViewConfiguration.getDoubleTapTimeout()
            if (isDoubleTap && model.doubleTappable) {
                lastTapId = null
                interactions.onMarkerDoubleTap(id)
                return
            }
            lastTapId = id
            lastTapUptimeMs = now
            if (model.clickable) interactions.onMarkerClick(id)
        }

        private fun findMarkerAt(x: Float, y: Float, radiusDp: Float, accepts: (MapMarkerModel) -> Boolean): String? {
            val radius = radiusDp * density
            val radiusSquared = radius * radius
            val pixel = android.graphics.Point()
            // Topmost first, matching what the operator sees.
            return orderedIds.asReversed().firstOrNull { id ->
                val entry = entries[id] as? MarkerEntry ?: return@firstOrNull false
                if (!accepts(entry.current)) return@firstOrNull false
                map.projection.toPixels(entry.marker.position, pixel)
                val dx = pixel.x - x
                val dy = pixel.y - y
                dx * dx + dy * dy <= radiusSquared
            }
        }
    }

    private companion object {
        const val MIN_REDRAW_INTERVAL_MS = 200L
        const val DOUBLE_TAP_RADIUS_DP = 30f
        const val DRAG_RADIUS_DP = 26f
        const val DRAG_SLOP_DP = 4f
        val DASH_EFFECT = DashPathEffect(floatArrayOf(18f, 10f), 0f)
    }
}

private fun MapCoordinate.geoPoint() = GeoPoint(latitude, longitude)

private fun GeoPoint.modelOrNull(): MapCoordinate? =
    if (latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) {
        MapCoordinate(latitude, longitude)
    } else null

private fun com.example.droneservicesapp.ui.map.model.MapBounds.toOrthoBounds() =
    com.example.droneservicesapp.data.ortho.OrthoBounds(
        minLon = southWest.longitude,
        minLat = southWest.latitude,
        maxLon = northEast.longitude,
        maxLat = northEast.latitude,
    )
