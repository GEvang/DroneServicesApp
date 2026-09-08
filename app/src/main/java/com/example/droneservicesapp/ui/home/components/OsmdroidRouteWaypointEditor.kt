package com.example.droneservicesapp.ui.home.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.RouteWaypoint
import com.example.droneservicesapp.domain.terrain.TerrainWaypoint
import com.example.droneservicesapp.ui.preview.buildSurveyDirectionSegments
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

class OsmdroidRouteWaypointEditor(
    private val context: Context,
    private val activityViewModel: MainActivityViewModel,
    private val mapView: MapView
) {
    private val waypointMarkers = mutableListOf<Marker>()
    private val terrainMarkers = mutableListOf<Marker>()
    private val directionMarkers = mutableListOf<Marker>()
    private val distanceMarkers = mutableListOf<Marker>()
    private var routePolyline: Polyline? = null
    private var eventsOverlay: MapEventsOverlay? = null
    private var waypointGestureOverlay: Overlay? = null
    private var enabled = false
    private var addingEnabled = false
    private var dragCandidateIndex: Int? = null
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var draggingWaypoint = false
    private var onTerrainWaypointSelected: ((Int?) -> Unit)? = null
    private var selectedTerrainWaypointIndex: Int? = null
    private var selectedControlWaypointIndex: Int? = null
    private var directionArrowIcon: BitmapDrawable? = null
    private var terrainWaypointIcon: BitmapDrawable? = null
    private var selectedTerrainWaypointIcon: BitmapDrawable? = null

    fun init() {
        routePolyline = Polyline(mapView).apply {
            infoWindow = null
            outlinePaint.color = ContextCompat.getColor(context, R.color.ds_color_shell_active)
            outlinePaint.strokeWidth = ROUTE_LINE_WIDTH_PX
            setPoints(emptyList())
        }
        mapView.overlays.add(routePolyline)

        eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (!enabled) return false
                val selectedIndex = selectedControlWaypointIndex
                if (selectedIndex != null) {
                    selectedControlWaypointIndex = null
                    activityViewModel.updateRouteWaypoint(selectedIndex, p.latitude, p.longitude)
                } else if (addingEnabled) {
                    activityViewModel.addRouteWaypoint(p.latitude, p.longitude)
                } else {
                    return false
                }
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
        mapView.overlays.add(eventsOverlay)

        waypointGestureOverlay = object : Overlay() {
            override fun onDoubleTap(event: MotionEvent, mapView: MapView): Boolean {
                if (!enabled) return false
                val waypointIndex = findWaypointAt(event.x, event.y, WAYPOINT_DOUBLE_TAP_RADIUS_DP)
                    ?: return false
                clearDragState()
                selectedControlWaypointIndex = null
                activityViewModel.removeRouteWaypoint(waypointIndex)
                return true
            }

            override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
                if (!enabled) return false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragCandidateIndex = findWaypointAt(event.x, event.y, WAYPOINT_DRAG_RADIUS_DP)
                        dragStartX = event.x
                        dragStartY = event.y
                        draggingWaypoint = false
                        return dragCandidateIndex != null
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val index = dragCandidateIndex ?: return false
                        val slop = WAYPOINT_DRAG_SLOP_DP * context.resources.displayMetrics.density
                        val dx = event.x - dragStartX
                        val dy = event.y - dragStartY
                        if (!draggingWaypoint && dx * dx + dy * dy >= slop * slop) {
                            draggingWaypoint = true
                        }
                        if (draggingWaypoint) {
                            val point = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt())
                            waypointMarkers.getOrNull(index)?.position = GeoPoint(point.latitude, point.longitude)
                            routePolyline?.setPoints(waypointMarkers.map { it.position })
                            mapView.invalidate()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val index = dragCandidateIndex ?: return false
                        if (draggingWaypoint) {
                            val point = waypointMarkers.getOrNull(index)?.position
                            if (point != null) {
                                selectedControlWaypointIndex = null
                                activityViewModel.updateRouteWaypoint(index, point.latitude, point.longitude)
                            }
                        }
                        clearDragState()
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        val handled = dragCandidateIndex != null
                        clearDragState()
                        return handled
                    }
                }
                return false
            }
        }
        mapView.overlays.add(waypointGestureOverlay)
        mapView.invalidate()
    }

    fun setEnabled(isEnabled: Boolean) {
        enabled = isEnabled
        if (!isEnabled) addingEnabled = false
        waypointMarkers.forEach { it.isDraggable = isEnabled }
        if (!isEnabled) clearTerrainWaypointSelection()
    }

    fun setAddingEnabled(isEnabled: Boolean) {
        addingEnabled = enabled && isEnabled
    }

    fun setTerrainWaypointSelectionCallback(callback: (Int?) -> Unit) {
        onTerrainWaypointSelected = callback
    }

    fun setWaypoints(
        waypoints: List<RouteWaypoint>,
        plannedPath: List<LatLng> = emptyList(),
        terrainPath: List<TerrainWaypoint> = emptyList(),
        reverseDirection: Boolean = false
    ) {
        clearMarkers(waypointMarkers)
        if (selectedControlWaypointIndex !in waypoints.indices) selectedControlWaypointIndex = null
        val baseDisplayPath = terrainPath.takeIf { it.size >= 2 }
            ?.map { GeoPoint(it.latLon.lat, it.latLon.lon) }
            ?: plannedPath.takeIf { it.size >= 2 }
                ?.map { GeoPoint(it.latitude, it.longitude) }
            ?: waypoints.map { GeoPoint(it.latitude, it.longitude) }
        val displayPath = if (reverseDirection) baseDisplayPath.reversed() else baseDisplayPath
        routePolyline?.setPoints(displayPath)

        waypoints.forEachIndexed { listIndex, waypoint ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(waypoint.latitude, waypoint.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                isDraggable = enabled
                infoWindow = null
                icon = createNumberedIcon(waypoint.index, listIndex == selectedControlWaypointIndex)
                setOnMarkerClickListener { _, _ ->
                    if (!enabled) return@setOnMarkerClickListener false
                    selectedControlWaypointIndex = listIndex
                    waypointMarkers.forEachIndexed { markerIndex, routeMarker ->
                        routeMarker.icon = createNumberedIcon(
                            waypoints[markerIndex].index,
                            markerIndex == selectedControlWaypointIndex
                        )
                    }
                    mapView.invalidate()
                    true
                }
                setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                    override fun onMarkerDrag(marker: Marker?) = Unit
                    override fun onMarkerDragStart(marker: Marker?) = Unit
                    override fun onMarkerDragEnd(marker: Marker?) {
                        marker ?: return
                        selectedControlWaypointIndex = null
                        activityViewModel.updateRouteWaypoint(
                            listIndex,
                            marker.position.latitude,
                            marker.position.longitude
                        )
                    }
                })
            }
            waypointMarkers += marker
            mapView.overlays.add(marker)
        }

        renderTerrainMarkers(terrainPath)
        renderDirectionMarkers(displayPath)
        renderDistanceMarkers(waypoints)
        bringControlPointsToFront()
        mapView.invalidate()
    }

    fun selectTerrainWaypoint(index: Int?) {
        selectedTerrainWaypointIndex = index?.takeIf { it in terrainMarkers.indices }
        terrainMarkers.forEachIndexed { markerIndex, marker ->
            marker.icon = terrainIcon(markerIndex == selectedTerrainWaypointIndex)
        }
        onTerrainWaypointSelected?.invoke(selectedTerrainWaypointIndex)
        mapView.invalidate()
    }

    fun clearTerrainWaypointSelection() {
        if (selectedTerrainWaypointIndex != null) selectTerrainWaypoint(null)
    }

    fun clear() {
        clearMarkers(waypointMarkers)
        clearMarkers(terrainMarkers)
        clearMarkers(directionMarkers)
        clearMarkers(distanceMarkers)
        selectedTerrainWaypointIndex = null
        selectedControlWaypointIndex = null
        routePolyline?.setPoints(emptyList())
        mapView.invalidate()
    }

    private fun renderTerrainMarkers(terrainPath: List<TerrainWaypoint>) {
        clearMarkers(terrainMarkers)
        if (selectedTerrainWaypointIndex !in terrainPath.indices) {
            selectedTerrainWaypointIndex = null
            onTerrainWaypointSelected?.invoke(null)
        }
        terrainPath.forEachIndexed { index, waypoint ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(waypoint.latLon.lat, waypoint.latLon.lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                infoWindow = null
                icon = terrainIcon(index == selectedTerrainWaypointIndex)
                setOnMarkerClickListener { _, _ ->
                    if (!enabled) return@setOnMarkerClickListener false
                    selectedControlWaypointIndex = null
                    selectTerrainWaypoint(if (selectedTerrainWaypointIndex == index) null else index)
                    true
                }
            }
            terrainMarkers += marker
            mapView.overlays.add(marker)
        }
    }

    private fun renderDirectionMarkers(path: List<GeoPoint>) {
        clearMarkers(directionMarkers)
        val latLngPath = path.map { LatLng(it.latitude, it.longitude) }
        buildSurveyDirectionSegments(latLngPath, MAX_DIRECTION_MARKERS).forEach { segment ->
            val from = GeoPoint(segment.from.latitude, segment.from.longitude)
            val to = GeoPoint(segment.to.latitude, segment.to.longitude)
            val marker = Marker(mapView).apply {
                position = GeoPoint(
                    (from.latitude + to.latitude) / 2.0,
                    (from.longitude + to.longitude) / 2.0
                )
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                infoWindow = null
                icon = directionArrowIcon ?: createDirectionArrowIcon().also { directionArrowIcon = it }
                rotation = bearingDegrees(from, to)
            }
            directionMarkers += marker
            mapView.overlays.add(marker)
        }
    }

    private fun renderDistanceMarkers(waypoints: List<RouteWaypoint>) {
        clearMarkers(distanceMarkers)
        val labelOffsetPx = DISTANCE_LABEL_OFFSET_DP * context.resources.displayMetrics.density
        waypoints.zipWithNext().forEach { (from, to) ->
            val fromPoint = LatLng(from.latitude, from.longitude)
            val toPoint = LatLng(to.latitude, to.longitude)
            val fromPixel = android.graphics.Point()
            val toPixel = android.graphics.Point()
            mapView.projection.toPixels(GeoPoint(from.latitude, from.longitude), fromPixel)
            mapView.projection.toPixels(GeoPoint(to.latitude, to.longitude), toPixel)
            val dx = (toPixel.x - fromPixel.x).toFloat()
            val dy = (toPixel.y - fromPixel.y).toFloat()
            val length = kotlin.math.sqrt(dx * dx + dy * dy)
            val midpointX = (fromPixel.x + toPixel.x) / 2f
            val midpointY = (fromPixel.y + toPixel.y) / 2f
            val labelPixelX = if (length > 0f) midpointX - dy / length * labelOffsetPx else midpointX
            val labelPixelY = if (length > 0f) midpointY + dx / length * labelOffsetPx else midpointY
            val labelPoint = mapView.projection.fromPixels(labelPixelX.toInt(), labelPixelY.toInt())
            val marker = Marker(mapView).apply {
                position = GeoPoint(labelPoint.latitude, labelPoint.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                infoWindow = null
                icon = createTextIcon(
                    String.format(Locale.US, "%.1f m", SphericalUtil.computeDistanceBetween(fromPoint, toPoint))
                )
            }
            distanceMarkers += marker
            mapView.overlays.add(marker)
        }
    }

    private fun bringControlPointsToFront() {
        eventsOverlay?.let {
            mapView.overlays.remove(it)
            mapView.overlays.add(it)
        }
        waypointGestureOverlay?.let {
            mapView.overlays.remove(it)
            mapView.overlays.add(it)
        }
        terrainMarkers.forEach {
            mapView.overlays.remove(it)
            mapView.overlays.add(it)
        }
        waypointMarkers.forEach {
            mapView.overlays.remove(it)
            mapView.overlays.add(it)
        }
    }

    private fun clearMarkers(markers: MutableList<Marker>) {
        mapView.overlays.removeAll(markers)
        markers.clear()
    }

    private fun findWaypointAt(x: Float, y: Float, radiusDp: Float): Int? {
        val radius = radiusDp * context.resources.displayMetrics.density
        val radiusSquared = radius * radius
        val point = android.graphics.Point()
        return waypointMarkers.indexOfFirst { marker ->
            mapView.projection.toPixels(marker.position, point)
            val dx = point.x - x
            val dy = point.y - y
            dx * dx + dy * dy <= radiusSquared
        }.takeIf { it >= 0 }
    }

    private fun clearDragState() {
        dragCandidateIndex = null
        draggingWaypoint = false
    }

    private fun terrainIcon(selected: Boolean): BitmapDrawable = if (selected) {
        selectedTerrainWaypointIcon ?: createTerrainIcon(true).also { selectedTerrainWaypointIcon = it }
    } else {
        terrainWaypointIcon ?: createTerrainIcon(false).also { terrainWaypointIcon = it }
    }

    private fun createTerrainIcon(selected: Boolean): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val size = ((if (selected) 28f else 18f) * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.ds_color_shell_warning)
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = (if (selected) 2.5f else 1.5f) * density
            style = Paint.Style.STROKE
        }
        canvas.drawCircle(center, center, center - 2f * density, fill)
        canvas.drawCircle(center, center, center - 2f * density, stroke)
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun createDirectionArrowIcon(): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val size = (26 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.ds_color_shell_active)
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 1.2f * density
            style = Paint.Style.STROKE
        }
        val path = Path().apply {
            moveTo(size - 3f * density, center)
            lineTo(5f * density, size - 5f * density)
            lineTo(10f * density, center)
            lineTo(5f * density, 5f * density)
            close()
        }
        canvas.drawPath(path, arrowPaint)
        canvas.drawPath(path, strokePaint)
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun createTextIcon(text: String): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val width = (textPaint.measureText(text) + 14f * density).toInt()
        val height = (24f * density).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 12, 22, 30) }
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 8f * density, 8f * density, background)
        val baseline = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, width / 2f, baseline, textPaint)
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun createNumberedIcon(number: Int, selected: Boolean): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val size = (38 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(16, 24, 32)
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (selected) Color.WHITE else ContextCompat.getColor(context, R.color.ds_color_shell_active)
            strokeWidth = (if (selected) 4f else 2.5f) * density
            style = Paint.Style.STROKE
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = 14f * density
        }
        canvas.drawCircle(center, center, center - strokePaint.strokeWidth, fillPaint)
        canvas.drawCircle(center, center, center - strokePaint.strokeWidth, strokePaint)
        val baseline = center - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(number.toString(), center, baseline, textPaint)
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun bearingDegrees(from: GeoPoint, to: GeoPoint): Float {
        val fromLat = Math.toRadians(from.latitude)
        val toLat = Math.toRadians(to.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)
        val y = kotlin.math.sin(deltaLon) * kotlin.math.cos(toLat)
        val x = kotlin.math.cos(fromLat) * kotlin.math.sin(toLat) -
            kotlin.math.sin(fromLat) * kotlin.math.cos(toLat) * kotlin.math.cos(deltaLon)
        return Math.toDegrees(kotlin.math.atan2(y, x)).toFloat()
    }

    companion object {
        private const val ROUTE_LINE_WIDTH_PX = 8f
        private const val MAX_DIRECTION_MARKERS = 80
        private const val WAYPOINT_DOUBLE_TAP_RADIUS_DP = 30f
        private const val WAYPOINT_DRAG_RADIUS_DP = 26f
        private const val WAYPOINT_DRAG_SLOP_DP = 4f
        private const val DISTANCE_LABEL_OFFSET_DP = 28f
    }
}
