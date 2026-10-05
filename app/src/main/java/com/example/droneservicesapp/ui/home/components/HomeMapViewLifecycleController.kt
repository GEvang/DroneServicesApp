package com.example.droneservicesapp.ui.home.components

import android.view.View
import android.app.Activity
import androidx.appcompat.widget.Toolbar
import com.example.droneservicesapp.R
import com.example.droneservicesapp.ui.pointcloud.PointCloudGlView
import org.osmdroid.views.MapView

/**
 * Owns the view-bound lifecycle effects for the mission map screen.
 *
 * Keeping this Android plumbing together makes it harder to resume one renderer without the
 * others, and leaves the Fragment responsible only for feature-specific refresh decisions.
 */
class HomeMapViewLifecycleController(
    private val activityProvider: () -> Activity?,
    private val mapView: MapView,
    private val mapController: OsmdroidMapController,
    private val pointCloudView: PointCloudGlView,
    private val refreshMapLabels: () -> Unit,
    private val isPointCloudVisible: () -> Boolean,
) {
    fun onResume(afterRenderersResumed: () -> Unit) {
        hideShellToolbar()
        refreshMapLabels()
        mapView.onResume()
        mapController.onResume()
        if (isPointCloudVisible()) pointCloudView.onResume()
        pointCloudView.post(afterRenderersResumed)
    }

    fun onPause(beforeRenderersPaused: () -> Unit) {
        beforeRenderersPaused()
        showShellToolbar()
        pointCloudView.onPause()
        mapController.onPause()
        mapView.onPause()
    }

    fun onDestroyView(beforeMapDetached: () -> Unit) {
        beforeMapDetached()
        showShellToolbar()
        mapView.onDetach()
    }

    private fun hideShellToolbar() {
        activityProvider()?.findViewById<Toolbar>(R.id.customToolbar)?.navigationIcon = null
        activityProvider()?.findViewById<View?>(R.id.appBarMain)?.visibility = View.GONE
    }

    private fun showShellToolbar() {
        activityProvider()?.findViewById<View?>(R.id.appBarMain)?.visibility = View.VISIBLE
    }
}
