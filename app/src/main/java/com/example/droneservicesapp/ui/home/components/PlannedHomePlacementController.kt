package com.example.droneservicesapp.ui.home.components

import com.example.droneservicesapp.domain.model.LatLon

/** Coordinates the mutually exclusive map-editing mode used to place a planned home. */
class PlannedHomePlacementController(
    private val mapController: OsmdroidMapController,
    private val polygonEditor: OsmdroidPolygonEditor,
    private val routeWaypointEditor: OsmdroidRouteWaypointEditor,
    private val beforePlacement: () -> Unit,
    private val onHomePlaced: (LatLon) -> Unit,
) {
    var isActive: Boolean = false
        private set

    fun start() {
        isActive = true
        beforePlacement()
        polygonEditor.setEnabled(false)
        routeWaypointEditor.setEnabled(false)
        mapController.setSurveyWaypointEditingEnabled(false)
        mapController.setHomePlacementEnabled(true) { selectedPoint ->
            place(LatLon(selectedPoint.latitude, selectedPoint.longitude))
        }
    }

    fun cancel() {
        if (!isActive) return
        isActive = false
        mapController.setHomePlacementEnabled(false)
    }

    private fun place(position: LatLon) {
        isActive = false
        mapController.setHomePlacementEnabled(false)
        if (DroneMapTrackingController.isValidPosition(position)) onHomePlaced(position)
    }
}
