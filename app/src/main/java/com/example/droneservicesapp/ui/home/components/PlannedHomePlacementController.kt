package com.example.droneservicesapp.ui.home.components

import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.ui.home.map.MissionMapLayers

/** Coordinates the mutually exclusive map-editing mode used to place a planned home. */
class PlannedHomePlacementController(
    private val layers: MissionMapLayers,
    private val beforePlacement: () -> Unit,
    private val onHomePlaced: (LatLon) -> Unit,
) {
    val isActive: Boolean
        get() = layers.homePlacementActive

    fun start() {
        beforePlacement()
        layers.polygon.setEnabled(false)
        layers.route.setEnabled(false)
        layers.survey.setEditingEnabled(false)
        layers.setHomePlacementEnabled(true) { selectedPoint ->
            place(LatLon(selectedPoint.latitude, selectedPoint.longitude))
        }
    }

    fun cancel() {
        if (!isActive) return
        layers.setHomePlacementEnabled(false)
    }

    private fun place(position: LatLon) {
        layers.setHomePlacementEnabled(false)
        if (DroneMapTrackingController.isValidPosition(position)) onHomePlaced(position)
    }
}
