package com.example.droneservicesapp.ui.home.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.RouteWaypoint
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class OsmdroidRouteWaypointEditor(
    private val context: Context,
    private val activityViewModel: MainActivityViewModel,
    private val mapView: MapView
) {
    private val waypointMarkers = mutableListOf<Marker>()
    private var routePolyline: Polyline? = null
    private var eventsOverlay: MapEventsOverlay? = null
    private var enabled: Boolean = false

    fun init() {
        routePolyline = Polyline(mapView).apply {
            infoWindow = null
            outlinePaint.color = ContextCompat.getColor(context, R.color.ds_color_shell_active)
            outlinePaint.strokeWidth = 8f
            setPoints(emptyList())
        }
        mapView.overlays.add(routePolyline)

        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (!enabled) return false
                activityViewModel.addRouteWaypoint(p.latitude, p.longitude)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        }

        eventsOverlay = MapEventsOverlay(receiver)
        mapView.overlays.add(eventsOverlay)
        mapView.invalidate()
    }

    fun setEnabled(isEnabled: Boolean) {
        enabled = isEnabled
    }

    fun setWaypoints(waypoints: List<RouteWaypoint>) {
        mapView.overlays.removeAll(waypointMarkers)
        waypointMarkers.clear()

        val routePoints = waypoints.map { GeoPoint(it.latitude, it.longitude) }
        routePolyline?.setPoints(routePoints)

        waypoints.forEach { waypoint ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(waypoint.latitude, waypoint.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                isDraggable = false
                infoWindow = null
                icon = createNumberedIcon(waypoint.index)
            }
            waypointMarkers += marker
            mapView.overlays.add(marker)
        }

        mapView.invalidate()
    }

    fun clear() {
        mapView.overlays.removeAll(waypointMarkers)
        waypointMarkers.clear()
        routePolyline?.setPoints(emptyList())
        mapView.invalidate()
    }

    private fun createNumberedIcon(number: Int): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val size = (34 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(16, 24, 32)
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.ds_color_shell_active)
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

        return BitmapDrawable(context.resources, bitmap)
    }
}
