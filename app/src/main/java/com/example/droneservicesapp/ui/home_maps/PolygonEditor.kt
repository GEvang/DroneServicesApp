package com.example.droneservicesapp.ui.home_maps

import android.app.Activity
import com.example.droneservicesapp.R
import com.example.droneservicesapp.activities.MainActivityViewModel
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

/**
 * Encapsulates polygon editing behavior for the survey area:
 * - tap map to add a draggable vertex marker
 * - tap marker to remove vertex
 * - drag marker to move vertex and update edges
 *
 * This class assumes:
 * - activityViewModel.area.value!!.latLngArrayListMarkers is the vertex marker list
 * - area model provides addEdgeToPolygon/removeEdgeFromPolygon/adjustEdgeToPolygon
 */
class PolygonEditor(
    private val activity: Activity,
    private val activityViewModel: MainActivityViewModel,
    private val iconFactory: (Activity, Int) -> com.google.android.gms.maps.model.BitmapDescriptor
) : GoogleMap.OnMapClickListener,
    GoogleMap.OnMarkerClickListener,
    GoogleMap.OnMarkerDragListener {

    private var map: GoogleMap? = null

    /** Call once you have a GoogleMap instance. */
    fun bind(googleMap: GoogleMap) {
        map = googleMap
        googleMap.setOnMapClickListener(this)
        googleMap.setOnMarkerClickListener(this)
        googleMap.setOnMarkerDragListener(this)
    }

    /** Optional if you ever want to detach/replace the map. */
    fun unbind() {
        map?.setOnMapClickListener(null)
        map?.setOnMarkerClickListener(null)
        map?.setOnMarkerDragListener(null)
        map = null
    }

    override fun onMapClick(point: LatLng) {
        if (activityViewModel.drawEnableLiveData.value != true) return

        val m = map ?: return
        val area = activityViewModel.area.value ?: return

        val marker = m.addMarker(
            MarkerOptions()
                .anchor(0.5f, 0.5f)
                .position(point)
                .icon(iconFactory(activity, R.drawable.ic_baseline_mission_marker))
                .draggable(true)
        ) ?: return

        // Add marker to vertex list and update polygon edges
        area.latLngArrayListMarkers.add(marker)
        area.addEdgeToPolygon(marker, m, activity)
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        if (activityViewModel.drawEnableLiveData.value != true) return false

        val m = map ?: return false
        val area = activityViewModel.area.value ?: return false

        val index = area.latLngArrayListMarkers.indexOf(marker)
        if (index == -1) return false

        area.removeEdgeFromPolygon(marker, m, activity)
        return true
    }

    override fun onMarkerDrag(marker: Marker) {}

    override fun onMarkerDragStart(marker: Marker) {}

    override fun onMarkerDragEnd(marker: Marker) {
        val m = map ?: return
        val area = activityViewModel.area.value ?: return

        // Note: original code used requireActivity(); context here is fine if adjustEdgeToPolygon only needs Context.
        // If it truly requires an Activity, we will change constructor to accept Activity in Step 1.2.
        area.adjustEdgeToPolygon(marker, m, activity)
    }
}
