package com.example.droneservicesapp.ui.home_maps

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.droneservicesapp.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

/**
 * Owns map setup + camera helpers + drone marker.
 *
 * - Configures GoogleMap UI and location layer (if permitted)
 * - Creates a drone marker and exposes update methods
 * - Provides zoom helpers: current location / drone location
 *
 * This class deliberately does NOT:
 * - manage polygon editing (PolygonEditor does)
 * - manage mission params panel (MissionParamsController does)
 * - manage file save/load
 */
class MapController(
    private val activity: Activity,
    private val context: Context,
    private val iconFactory: (Context, Int) -> BitmapDescriptor,
    private val showToast: (String) -> Unit = { msg ->
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }
) {

    private var map: GoogleMap? = null
    private var droneMarker: Marker? = null

    /** Call once when you have the map instance (e.g., in onMapReady). */
    fun bind(googleMap: GoogleMap) {
        map = googleMap

        // Configure map UI
        googleMap.uiSettings.isMyLocationButtonEnabled = false

        // Enable user location layer (if permitted)
        enableUserLocationLayerIfPermitted(googleMap)

        // Create drone marker (hidden until connected)
        droneMarker = googleMap.addMarker(
            MarkerOptions()
                .visible(false)
                .position(LatLng(0.0, 0.0))
                .anchor(0.5f, 0.5f)
                .icon(iconFactory(context, R.drawable.drone_marker_36))
        )
    }

    fun unbind() {
        droneMarker = null
        map = null
    }

    fun setDroneMarkerVisible(visible: Boolean) {
        droneMarker?.isVisible = visible
    }

    fun updateDroneLocation(location: Location) {
        droneMarker?.position = LatLng(location.latitude, location.longitude)
    }

    fun updateDroneHeading(heading: Double) {
        droneMarker?.rotation = heading.toFloat()
    }

    fun zoomToDroneLocation(droneLocation: Location?) {
        val m = map ?: return
        val location = droneLocation ?: return

        val cameraPosition = CameraPosition.Builder()
            .target(LatLng(location.latitude, location.longitude))
            .zoom(19f)
            .build()

        m.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    fun zoomToCurrentLocation() {
        val m = map ?: return

        if (!hasFineOrCoarseLocationPermission()) {
            showToast(context.getString(R.string.no_permissions_msg))
            return
        }

        val location = getLastKnownLocation() ?: return

        val cameraPosition = CameraPosition.Builder()
            .target(LatLng(location.latitude, location.longitude))
            .zoom(19f)
            .build()

        m.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    @SuppressLint("MissingPermission")
    private fun enableUserLocationLayerIfPermitted(googleMap: GoogleMap) {
        if (!hasFineOrCoarseLocationPermission()) {
            showToast(context.getString(R.string.invalid_location_permissions))
            return
        }

        run {
            googleMap.isMyLocationEnabled = true
        }
    }

    private fun hasFineOrCoarseLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            activity.applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            activity.applicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(): Location? {
        val lm = context.getSystemService(LOCATION_SERVICE) as LocationManager
        val providers: List<String> = lm.getProviders(true)

        var best: Location? = null
        for (provider in providers) {
            val l: Location = lm.getLastKnownLocation(provider) ?: continue
            if (best == null || l.accuracy < best.accuracy) best = l
        }
        return best
    }
}
