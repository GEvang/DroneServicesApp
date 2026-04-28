package com.example.droneservicesapp.ui.home.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
    }

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var droneMarker: Marker? = null
    private var surveyPolyline: Polyline? = null

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
        return if (point != null) {
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
        return if (marker != null && marker.isEnabled) {
            mapView.controller.animateTo(marker.position)
            true
        } else {
            Toast.makeText(context, context.getString(R.string.drone_location_not_available_yet), Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun hasDronePosition(): Boolean = droneMarker?.isEnabled == true

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
        requestMapRedraw()
    }

    fun clearSurveyPath() {
        surveyPolyline?.setPoints(emptyList())
        requestMapRedraw()
    }

    private fun requestMapRedraw() {
        mapView.postInvalidateOnAnimation()
    }
}
