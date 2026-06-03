package com.example.droneservicesapp.ui.home.components

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.survey.PolygonVertexOrderer
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/**
 * osmdroid equivalent of PolygonEditor:
 * - tap map to add draggable vertex marker
 * - tap marker to remove
 * - drag marker to move
 *
 * Writes polygon vertices into activityViewModel.area (MissionArea.vertices).
 */
class OsmdroidPolygonEditor(
    private val activity: Activity,
    private val activityViewModel: MainActivityViewModel,
    private val mapView: MapView
) {
    private val points = mutableListOf<GeoPoint>()
    private val vertexMarkers = mutableListOf<Marker>()

    private var polygon: Polygon? = null
    private var eventsOverlay: MapEventsOverlay? = null

    private var enabled: Boolean = false

    fun init() {
        polygon = Polygon(mapView).apply {
            outlinePaint.color = Color.rgb(80, 200, 255)
            outlinePaint.strokeWidth = 10f
            fillPaint.color = Color.argb(60, 80, 200, 255)
            isEnabled = false
            setVisible(false)
        }
        mapView.overlays.add(polygon)

        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (!enabled) return false
                addVertex(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                return false
            }
        }

        eventsOverlay = MapEventsOverlay(receiver)
        mapView.overlays.add(eventsOverlay)

        mapView.invalidate()
    }

    fun setEnabled(isEnabled: Boolean) {
        enabled = isEnabled
    }

    fun clear() {
        for (m in vertexMarkers) {
            m.setVisible(false)
        }
        mapView.overlays.removeAll(vertexMarkers)

        vertexMarkers.clear()
        points.clear()

        // Clear pure model too
        activityViewModel.missionArea.value?.clearAll()

        redrawPolygonAndSyncModel()
    }

    fun setVertices(vertices: List<LatLng>) {
        if (isSameVertices(vertices)) return

        mapView.overlays.removeAll(vertexMarkers)
        vertexMarkers.clear()
        points.clear()

        vertices.forEach { vertex ->
            val point = GeoPoint(vertex.latitude, vertex.longitude)
            points.add(point)

            val marker = createVertexMarker(point, vertexMarkers.size + 1)
            vertexMarkers.add(marker)
            mapView.overlays.add(marker)
        }

        orderVerticesIfNeeded()
        updateVertexMarkerIcons()
        redrawPolygon()
    }

    // ---------- Internals ----------

    private fun addVertex(p: GeoPoint) {
        points.add(p)

        val marker = createVertexMarker(p, vertexMarkers.size + 1)

        vertexMarkers.add(marker)
        mapView.overlays.add(marker)

        orderVerticesIfNeeded()
        updateVertexMarkerIcons()
        redrawPolygonAndSyncModel()
    }

    private fun removeVertex(marker: Marker) {
        val idx = vertexMarkers.indexOf(marker)
        if (idx < 0) return

        mapView.overlays.remove(marker)
        vertexMarkers.removeAt(idx)
        points.removeAt(idx)

        updateVertexMarkerIcons()
        redrawPolygonAndSyncModel()
    }

    private fun onVertexMoved(marker: Marker) {
        val idx = vertexMarkers.indexOf(marker)
        if (idx < 0) return

        points[idx] = marker.position as GeoPoint
        orderVerticesIfNeeded()
        updateVertexMarkerIcons()
        redrawPolygonAndSyncModel()
    }

    private fun redrawPolygonAndSyncModel() {
        redrawPolygon()

        // Build new vertices and sync via ViewModel
        val newVertices = points.map { LatLng(it.latitude, it.longitude) }
        activityViewModel.setPolygonVertices(newVertices)

        mapView.invalidate()
    }

    private fun redrawPolygon() {
        val poly = polygon ?: return

        if (points.size < 2) {
            poly.points = ArrayList(points)
            poly.isEnabled = false
            poly.setVisible(false)
        } else {
            poly.points = ArrayList(points)
            poly.isEnabled = true
            poly.setVisible(true)
        }

        mapView.invalidate()
    }

    private fun orderVerticesIfNeeded() {
        if (points.size < 4) return

        val latLngPoints = points.map { LatLng(it.latitude, it.longitude) }
        val order = PolygonVertexOrderer.orderNonCrossingIndices(latLngPoints)
        if (order == points.indices.toList()) return

        val orderedPoints = order.map(points::get)
        val orderedMarkers = order.map(vertexMarkers::get)

        points.clear()
        points.addAll(orderedPoints)

        vertexMarkers.clear()
        vertexMarkers.addAll(orderedMarkers)
    }

    private fun updateVertexMarkerIcons() {
        vertexMarkers.forEachIndexed { index, marker ->
            marker.icon = createNumberedIcon(index + 1)
            marker.title = "Area point ${index + 1}"
        }
    }

    private fun createVertexMarker(point: GeoPoint, number: Int): Marker =
        Marker(mapView).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            isDraggable = true
            icon = createNumberedIcon(number)
            title = "Area point $number"

            setOnMarkerClickListener { marker, _ ->
                if (!enabled) return@setOnMarkerClickListener false
                removeVertex(marker)
                true
            }

            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(marker: Marker?) {}
                override fun onMarkerDragStart(marker: Marker?) {}
                override fun onMarkerDragEnd(marker: Marker?) {
                    if (!enabled) return
                    marker ?: return
                    onVertexMoved(marker)
                }
            })
        }

    private fun createNumberedIcon(number: Int): BitmapDrawable {
        val density = activity.resources.displayMetrics.density
        val size = (34 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(16, 24, 32)
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(80, 200, 255)
            strokeWidth = 2.5f * density
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

        return BitmapDrawable(activity.resources, bitmap)
    }

    private fun isSameVertices(vertices: List<LatLng>): Boolean {
        if (vertices.size != points.size) return false
        return vertices.indices.all { index ->
            val point = points[index]
            val vertex = vertices[index]
            point.latitude == vertex.latitude && point.longitude == vertex.longitude
        }
    }
}
