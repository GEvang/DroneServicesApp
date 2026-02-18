package com.example.droneservicesapp.ui.home_maps

import android.app.Activity
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.example.droneservicesapp.R
import com.example.droneservicesapp.activities.MainActivityViewModel
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
 * IMPORTANT: keeps activityViewModel.area.value!!.polygonEdges (LatLng list) updated,
 * so the rest of your mission pipeline continues to work unchanged.
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
        // Polygon overlay
        polygon = Polygon(mapView).apply {
            outlinePaint.color = Color.BLACK
            outlinePaint.strokeWidth = 5f
            fillPaint.color = Color.argb(40, 0, 0, 0)
            isEnabled = false
            setVisible(false)
        }
        mapView.overlays.add(polygon)

        // Tap receiver overlay (adds vertices)
        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (!enabled) return false
                addVertex(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                // No-op (kept simple). We can add "remove last" later if you want.
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
        // Remove markers from map
        for (m in vertexMarkers) {
            m.setVisible(false)
        }
        mapView.overlays.removeAll(vertexMarkers)

        vertexMarkers.clear()
        points.clear()

        redrawPolygonAndSyncModel()
    }

    // ---------- Internals ----------

    private fun addVertex(p: GeoPoint) {
        points.add(p)

        val marker = Marker(mapView).apply {
            position = p
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            isDraggable = true
            icon = ContextCompat.getDrawable(activity, R.drawable.ic_baseline_mission_marker)

            setOnMarkerClickListener { m, _ ->
                if (!enabled) return@setOnMarkerClickListener false
                removeVertex(m)
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

        vertexMarkers.add(marker)
        mapView.overlays.add(marker)

        redrawPolygonAndSyncModel()
    }

    private fun removeVertex(marker: Marker) {
        val idx = vertexMarkers.indexOf(marker)
        if (idx < 0) return

        // Remove marker overlay
        mapView.overlays.remove(marker)
        vertexMarkers.removeAt(idx)

        // Remove corresponding point
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

        // Sync into your existing mission model (Google LatLng list)
        val area = activityViewModel.area.value
        if (area != null) {
            area.polygonEdges.clear()
            for (gp in points) {
                area.polygonEdges.add(LatLng(gp.latitude, gp.longitude))
            }

            // If there was a Google polygon from older sessions, ensure it doesn't interfere
            area.polygon?.remove()
            area.polygon = null
        }

        mapView.invalidate()
    }
}
