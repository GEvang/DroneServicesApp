package com.example.droneservicesapp.ui.home_maps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.example.droneservicesapp.R
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.Polyline
import com.google.android.gms.maps.model.LatLng



/**
 * osmdroid equivalent of the parts of MapController that are:
 * - user location (blue dot)
 * - drone marker (icon + position + heading + centering)
 *
 * Keeps HomeMapsFragment from becoming a god-class.
 */
class OsmdroidMapController(
    private val context: Context,
    private val mapView: MapView
) {
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var droneMarker: Marker? = null
    private var surveyPolyline: Polyline? = null

    fun initOverlays() {
        // ----- My location overlay -----
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
            // Do NOT follow by default; user can tap the button to center.
            disableFollowLocation()
        }
        mapView.overlays.add(myLocationOverlay)

        // ----- Drone marker -----
        droneMarker = Marker(mapView).apply {
            position = GeoPoint(0.0, 0.0) // placeholder; keep hidden until first update
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            isEnabled = false
            setVisible(false)
            icon = ContextCompat.getDrawable(context, R.drawable.drone_marker_48_black)
        }
        mapView.overlays.add(droneMarker)

        mapView.invalidate()
    }

    // --- Lifecycle ---
    fun onResume() {
        mapView.onResume()
        myLocationOverlay?.enableMyLocation()
    }

    fun onPause() {
        myLocationOverlay?.disableMyLocation()
        mapView.onPause()
    }

    // --- User location ---
    fun centerOnUserIfPermitted(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!(fineGranted || coarseGranted)) {
            Toast.makeText(
                context,
                context.getString(R.string.invalid_location_permissions),
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val p = myLocationOverlay?.myLocation
        return if (p != null) {
            mapView.controller.animateTo(p)
            true
        } else {
            Toast.makeText(context, "Waiting for GPS fix…", Toast.LENGTH_SHORT).show()
            false
        }
    }

    // --- Drone marker ---
    fun setDroneVisible(visible: Boolean) {
        droneMarker?.apply {
            isEnabled = visible
            setVisible(visible)
        }
        mapView.invalidate()
    }

    fun updateDronePosition(latitude: Double, longitude: Double) {
        droneMarker?.apply {
            position = GeoPoint(latitude, longitude)
            isEnabled = true
            setVisible(true)
        }
        mapView.invalidate()
    }

    fun updateDroneHeadingDegrees(headingDeg: Float) {
        droneMarker?.rotation = headingDeg
        mapView.invalidate()
    }

    fun centerOnDrone(): Boolean {
        val m = droneMarker
        return if (m != null && m.isEnabled) {
            mapView.controller.animateTo(m.position)
            true
        } else {
            Toast.makeText(context, "Drone location not available yet", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun setSurveyPath(path: List<LatLng>) {
        if (surveyPolyline == null) {
            surveyPolyline = Polyline(mapView).apply {
                outlinePaint.color = Color.RED
                outlinePaint.strokeWidth = 4f
            }
            mapView.overlays.add(surveyPolyline)
        }

        val geoPoints = path.map { GeoPoint(it.latitude, it.longitude) }
        surveyPolyline?.setPoints(geoPoints)
        mapView.invalidate()
    }

    fun clearSurveyPath() {
        surveyPolyline?.setPoints(emptyList())
        mapView.invalidate()
    }

}
