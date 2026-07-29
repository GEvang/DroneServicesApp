package com.example.droneservicesapp.ui.home.components

import android.content.Context
import android.graphics.Color
import android.widget.Toast
import com.example.droneservicesapp.domain.model.MissionObstacle
import com.example.droneservicesapp.domain.model.MissionObstacleShape
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

class OsmdroidObstacleEditor(
    private val context: Context,
    private val activityViewModel: MainActivityViewModel,
    private val mapView: MapView
) {
    enum class Mode {
        IDLE,
        CIRCLE,
        POLYGON
    }

    private val obstacleOverlays = mutableMapOf<String, Polygon>()
    private val draftMarkers = mutableListOf<Marker>()
    private val draftPoints = mutableListOf<GeoPoint>()
    private var draftPolygon: Polygon? = null
    private var eventsOverlay: MapEventsOverlay? = null
    private var mode = Mode.IDLE
    private var defaultRadiusMeters = 5.0

    fun init() {
        draftPolygon = Polygon(mapView).apply {
            fillPaint.color = Color.argb(55, 220, 38, 38)
            outlinePaint.color = Color.rgb(248, 113, 113)
            outlinePaint.strokeWidth = 4f
            isEnabled = false
            setVisible(false)
        }
        mapView.overlays.add(draftPolygon)

        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                return when (mode) {
                    Mode.IDLE -> false
                    Mode.CIRCLE -> {
                        activityViewModel.addMissionObstacle(
                            latitude = p.latitude,
                            longitude = p.longitude,
                            radiusMeters = defaultRadiusMeters
                        )
                        mode = Mode.IDLE
                        Toast.makeText(context, "Forbidden circle added", Toast.LENGTH_SHORT).show()
                        true
                    }
                    Mode.POLYGON -> {
                        addDraftPoint(p)
                        true
                    }
                }
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        }
        eventsOverlay = MapEventsOverlay(receiver)
        mapView.overlays.add(eventsOverlay)
    }

    fun startCirclePlacement(radiusMeters: Double) {
        clearDraft()
        mode = Mode.CIRCLE
        defaultRadiusMeters = radiusMeters.coerceIn(2.0, 100.0)
    }

    fun startPolygonPlacement() {
        clearDraft()
        mode = Mode.POLYGON
    }

    fun cancelPlacement() {
        mode = Mode.IDLE
        clearDraft()
    }

    fun finishPolygon(): Boolean {
        if (draftPoints.size < 3) {
            Toast.makeText(context, "Place at least 3 forbidden-area points.", Toast.LENGTH_SHORT).show()
            return false
        }
        activityViewModel.addPolygonMissionObstacle(
            draftPoints.map { LatLng(it.latitude, it.longitude) }
        )
        mode = Mode.IDLE
        clearDraft()
        Toast.makeText(context, "Forbidden polygon added", Toast.LENGTH_SHORT).show()
        return true
    }

    fun renderObstacles(obstacles: List<MissionObstacle>) {
        val activeIds = obstacles.map { it.id }.toSet()
        val removedIds = obstacleOverlays.keys.filterNot { it in activeIds }
        removedIds.forEach { id ->
            obstacleOverlays.remove(id)?.let { mapView.overlays.remove(it) }
        }

        obstacles.filter { it.isValid() }.forEach { obstacle ->
            val overlay = obstacleOverlays[obstacle.id] ?: createObstacleOverlay(obstacle).also {
                obstacleOverlays[obstacle.id] = it
                mapView.overlays.add(it)
            }
            overlay.points = obstaclePoints(obstacle)
        }
        mapView.invalidate()
    }

    fun clear() {
        clearDraft()
        mapView.overlays.removeAll(obstacleOverlays.values)
        obstacleOverlays.clear()
        mapView.invalidate()
    }

    private fun addDraftPoint(point: GeoPoint) {
        draftPoints += point
        val marker = Marker(mapView).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "Forbidden point ${draftPoints.size}"
        }
        draftMarkers += marker
        mapView.overlays.add(marker)
        redrawDraftPolygon()
    }

    private fun redrawDraftPolygon() {
        val polygon = draftPolygon ?: return
        polygon.points = ArrayList(
            if (draftPoints.size >= 3) draftPoints + draftPoints.first() else draftPoints
        )
        polygon.isEnabled = draftPoints.size >= 2
        polygon.setVisible(draftPoints.size >= 2)
        mapView.invalidate()
    }

    private fun clearDraft() {
        mapView.overlays.removeAll(draftMarkers)
        draftMarkers.clear()
        draftPoints.clear()
        draftPolygon?.points = ArrayList()
        draftPolygon?.isEnabled = false
        draftPolygon?.setVisible(false)
        mapView.invalidate()
    }

    private fun createObstacleOverlay(obstacle: MissionObstacle): Polygon {
        return Polygon(mapView).apply {
            fillPaint.color = Color.argb(85, 220, 38, 38)
            outlinePaint.color = Color.rgb(220, 38, 38)
            outlinePaint.strokeWidth = 5f
            points = obstaclePoints(obstacle)
            setOnClickListener { _, _, _ ->
                activityViewModel.removeMissionObstacle(obstacle.id)
                true
            }
        }
    }

    private fun obstaclePoints(obstacle: MissionObstacle): ArrayList<GeoPoint> {
        return when (obstacle.shape) {
            MissionObstacleShape.CIRCLE -> obstacleCirclePoints(obstacle)
            MissionObstacleShape.POLYGON -> obstaclePolygonPoints(obstacle)
        }
    }

    private fun obstacleCirclePoints(obstacle: MissionObstacle): ArrayList<GeoPoint> {
        val center = obstacle.center ?: return ArrayList()
        val centerLatLng = LatLng(center.lat, center.lon)
        val points = ArrayList<GeoPoint>()
        for (bearing in 0 until 360 step 12) {
            val point = SphericalUtil.computeOffset(centerLatLng, obstacle.radiusMeters, bearing.toDouble())
            points.add(GeoPoint(point.latitude, point.longitude))
        }
        if (points.isNotEmpty()) points.add(points.first())
        return points
    }

    private fun obstaclePolygonPoints(obstacle: MissionObstacle): ArrayList<GeoPoint> {
        val points = ArrayList(obstacle.vertices.map { GeoPoint(it.lat, it.lon) })
        if (points.isNotEmpty()) points.add(points.first())
        return points
    }
}
