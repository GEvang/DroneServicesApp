package com.example.droneservicesapp.ui.home.components

import android.app.Activity
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.example.droneservicesapp.R
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

            val marker = createVertexMarker(point)
            vertexMarkers.add(marker)
            mapView.overlays.add(marker)
        }

        redrawPolygon()
    }

    // ---------- Internals ----------

    private fun addVertex(p: GeoPoint) {
        points.add(p)

        val marker = createVertexMarker(p)

        vertexMarkers.add(marker)
        mapView.overlays.add(marker)

        redrawPolygonAndSyncModel()
    }

    private fun removeVertex(marker: Marker) {
        val idx = vertexMarkers.indexOf(marker)
        if (idx < 0) return

        mapView.overlays.remove(marker)
        vertexMarkers.removeAt(idx)
        points.removeAt(idx)

        redrawPolygonAndSyncModel()
    }

    private fun onVertexMoved(marker: Marker) {
        val idx = vertexMarkers.indexOf(marker)
        if (idx < 0) return

        points[idx] = marker.position as GeoPoint
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

    private fun createVertexMarker(point: GeoPoint): Marker =
        Marker(mapView).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            isDraggable = true
            icon = ContextCompat.getDrawable(activity, R.drawable.bg_mission_vertex_marker)

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

    private fun isSameVertices(vertices: List<LatLng>): Boolean {
        if (vertices.size != points.size) return false
        return vertices.indices.all { index ->
            val point = points[index]
            val vertex = vertices[index]
            point.latitude == vertex.latitude && point.longitude == vertex.longitude
        }
    }
}
