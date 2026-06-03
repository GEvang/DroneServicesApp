package com.example.droneservicesapp.ui.home.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.droneservicesapp.R
import com.google.android.gms.maps.model.LatLng
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * osmdroid equivalent of the parts of MapController that are:
 * - user location (blue dot)
 * - drone marker (icon + position + heading + centering)
 *
 * Keeps MissionMapFragment from becoming a god-class.
 */
class OsmdroidMapController(
    private val context: Context,
    private val mapView: MapView
) {
    companion object {
        private const val POSITION_EPSILON = 1e-7
        private const val HEADING_EPSILON_DEGREES = 0.5f
        private const val MIN_VALID_ABS_COORDINATE = 1e-4
        private const val MAX_FLIGHT_TRACE_POINTS = 5000
    }

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var droneMarker: Marker? = null
    private var surveyPolyline: Polyline? = null
    private val surveyDirectionMarkers = mutableListOf<Marker>()
    private var homeMarker: Marker? = null
    private var flightTracePolyline: Polyline? = null
    private val flightTracePoints = mutableListOf<GeoPoint>()

    fun initOverlays() {
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
            disableFollowLocation()
        }
        mapView.overlays.add(myLocationOverlay)

        droneMarker = Marker(mapView).apply {
            position = GeoPoint(0.0, 0.0)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            isEnabled = false
            setVisible(false)
            icon = ContextCompat.getDrawable(context, R.drawable.drone_marker_36)
        }
        mapView.overlays.add(droneMarker)

        requestMapRedraw()
    }

    fun onResume() {
        mapView.onResume()
        myLocationOverlay?.enableMyLocation()
    }

    fun onPause() {
        myLocationOverlay?.disableMyLocation()
        mapView.onPause()
    }

    fun centerOnUserIfPermitted(showErrors: Boolean = true): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!(fineGranted || coarseGranted)) {
            if (showErrors) {
                Toast.makeText(
                    context,
                    context.getString(R.string.invalid_location_permissions),
                    Toast.LENGTH_LONG
                ).show()
            }
            return false
        }

        val point = myLocationOverlay?.myLocation
        return if (point != null && isValidMapPoint(point.latitude, point.longitude)) {
            mapView.controller.animateTo(point)
            true
        } else {
            if (showErrors) {
                Toast.makeText(context, context.getString(R.string.waiting_for_gps_fix), Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    fun setDroneVisible(visible: Boolean) {
        val changed = droneMarker?.let { marker ->
            val wasVisible = marker.isEnabled
            marker.isEnabled = visible
            marker.setVisible(visible)
            wasVisible != visible
        } ?: false
        if (changed) {
            requestMapRedraw()
        }
    }

    fun updateDronePosition(latitude: Double, longitude: Double) {
        if (!isValidMapPoint(latitude, longitude)) {
            return
        }

        val changed = droneMarker?.let { marker ->
            val current = marker.position
            val positionChanged =
                current == null ||
                    kotlin.math.abs(current.latitude - latitude) > POSITION_EPSILON ||
                    kotlin.math.abs(current.longitude - longitude) > POSITION_EPSILON
            if (!positionChanged && marker.isEnabled) {
                return@let false
            }
            marker.position = GeoPoint(latitude, longitude)
            val visibilityChanged = !marker.isEnabled
            marker.isEnabled = true
            marker.setVisible(true)
            positionChanged || visibilityChanged
        } ?: false
        if (changed) {
            requestMapRedraw()
        }
    }

    fun updateDroneHeadingDegrees(headingDeg: Float) {
        val changed = droneMarker?.let { marker ->
            if (kotlin.math.abs(marker.rotation - headingDeg) < HEADING_EPSILON_DEGREES) {
                false
            } else {
                marker.rotation = headingDeg
                true
            }
        } ?: false
        if (changed) {
            requestMapRedraw()
        }
    }

    fun centerOnDrone(): Boolean {
        val marker = droneMarker
        return if (marker != null && marker.isEnabled && isValidMapPoint(marker.position.latitude, marker.position.longitude)) {
            mapView.controller.animateTo(marker.position)
            true
        } else {
            Toast.makeText(context, context.getString(R.string.drone_location_not_available_yet), Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun hasDronePosition(): Boolean = droneMarker?.isEnabled == true

    fun setHomeMarker(latitude: Double, longitude: Double): Boolean {
        if (homeMarker != null || !isValidMapPoint(latitude, longitude)) {
            return false
        }

        homeMarker = Marker(mapView).apply {
            position = GeoPoint(latitude, longitude)
            title = "Home / Takeoff"
            snippet = "Drone takeoff position"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_home_takeoff_marker)
        }
        addOverlayBelowDrone(homeMarker!!)
        requestMapRedraw()
        return true
    }

    fun appendFlightTracePoint(latitude: Double, longitude: Double) {
        if (!isValidMapPoint(latitude, longitude)) {
            return
        }
        ensureFlightTracePolyline()
        flightTracePoints += GeoPoint(latitude, longitude)
        if (flightTracePoints.size > MAX_FLIGHT_TRACE_POINTS) {
            flightTracePoints.removeAt(0)
        }
        flightTracePolyline?.setPoints(flightTracePoints)
        requestMapRedraw()
    }

    fun clearFlightTraceAndHome() {
        homeMarker?.let { mapView.overlays.remove(it) }
        homeMarker = null
        flightTracePolyline?.let { mapView.overlays.remove(it) }
        flightTracePolyline = null
        flightTracePoints.clear()
        requestMapRedraw()
    }

    fun setSurveyPath(path: List<LatLng>) {
        if (surveyPolyline == null) {
            surveyPolyline = Polyline(mapView).apply {
                outlinePaint.color = ContextCompat.getColor(context, R.color.ds_color_shell_active)
                outlinePaint.strokeWidth = 6f
            }
            mapView.overlays.add(surveyPolyline)
        }

        val geoPoints = path.map { GeoPoint(it.latitude, it.longitude) }
        surveyPolyline?.setPoints(geoPoints)
        renderSurveyDirectionMarkers(geoPoints)
        requestMapRedraw()
    }

    fun clearSurveyPath() {
        surveyPolyline?.setPoints(emptyList())
        clearSurveyDirectionMarkers()
        requestMapRedraw()
    }

    private fun requestMapRedraw() {
        mapView.postInvalidateOnAnimation()
    }

    private fun ensureFlightTracePolyline() {
        if (flightTracePolyline != null) return
        flightTracePolyline = Polyline(mapView).apply {
            outlinePaint.color = Color.RED
            outlinePaint.strokeWidth = 5f
        }
        addOverlayBelowDrone(flightTracePolyline!!)
    }

    private fun addOverlayBelowDrone(overlay: org.osmdroid.views.overlay.Overlay) {
        val droneIndex = droneMarker?.let { mapView.overlays.indexOf(it) } ?: -1
        if (droneIndex >= 0) {
            mapView.overlays.add(droneIndex, overlay)
        } else {
            mapView.overlays.add(overlay)
        }
    }

    private fun renderSurveyDirectionMarkers(path: List<GeoPoint>) {
        clearSurveyDirectionMarkers()
        if (path.size < 2) return

        val segments = if (path.size % 2 == 0) {
            path.chunked(2).mapNotNull { pair -> pair.takeIf { it.size == 2 } }
        } else {
            path.zipWithNext().map { listOf(it.first, it.second) }
        }

        segments.forEach { segment ->
            val from = segment[0]
            val to = segment[1]
            if (!isValidMapPoint(from.latitude, from.longitude) || !isValidMapPoint(to.latitude, to.longitude)) {
                return@forEach
            }

            val marker = Marker(mapView).apply {
                position = GeoPoint(
                    (from.latitude + to.latitude) / 2.0,
                    (from.longitude + to.longitude) / 2.0
                )
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createDirectionArrowIcon()
                rotation = bearingDegrees(from, to)
            }
            surveyDirectionMarkers += marker
            mapView.overlays.add(marker)
        }
    }

    private fun clearSurveyDirectionMarkers() {
        mapView.overlays.removeAll(surveyDirectionMarkers)
        surveyDirectionMarkers.clear()
    }

    private fun createDirectionArrowIcon(): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val size = (26 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 0, 0, 0)
            style = Paint.Style.FILL
        }
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
            moveTo(center, 3f * density)
            lineTo(size - 5f * density, size - 5f * density)
            lineTo(center, size - 10f * density)
            lineTo(5f * density, size - 5f * density)
            close()
        }
        canvas.drawCircle(center, center, center - 2f * density, shadowPaint)
        canvas.drawPath(path, arrowPaint)
        canvas.drawPath(path, strokePaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    private fun bearingDegrees(from: GeoPoint, to: GeoPoint): Float {
        val fromLat = Math.toRadians(from.latitude)
        val toLat = Math.toRadians(to.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(deltaLon) * cos(toLat)
        val x = cos(fromLat) * sin(toLat) - sin(fromLat) * cos(toLat) * cos(deltaLon)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    private fun isValidMapPoint(latitude: Double, longitude: Double): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite()) {
            return false
        }

        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return false
        }

        return kotlin.math.abs(latitude) > MIN_VALID_ABS_COORDINATE ||
            kotlin.math.abs(longitude) > MIN_VALID_ABS_COORDINATE
    }
}
