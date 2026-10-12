package com.example.droneservicesapp.ui.home.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.droneservicesapp.domain.model.RouteWaypoint
import com.example.droneservicesapp.domain.terrain.TerrainWaypoint
import com.example.droneservicesapp.ui.map.model.MapCoordinate
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng

/**
 * Point-route editor: tap the map to add a waypoint, tap a waypoint to select it and then tap the
 * map to move it, drag a waypoint immediately, double-tap a waypoint to remove it, and select
 * terrain-sampled waypoints for height editing.
 *
 * Port of the former `OsmdroidRouteWaypointEditor`.
 */
class RouteEditorState(
    private val viewModel: MainActivityViewModel,
) {
    var waypoints by mutableStateOf<List<RouteWaypoint>>(emptyList())
        private set
    var displayPath by mutableStateOf<List<LatLng>>(emptyList())
        private set
    var terrainPath by mutableStateOf<List<TerrainWaypoint>>(emptyList())
        private set
    var enabled by mutableStateOf(false)
        private set
    var addingEnabled by mutableStateOf(false)
        private set
    var selectedControlIndex by mutableStateOf<Int?>(null)
        private set
    var selectedTerrainIndex by mutableStateOf<Int?>(null)
        private set

    /** Live position of the waypoint currently being dragged, before the model is updated. */
    var dragPreview by mutableStateOf<Pair<Int, MapCoordinate>?>(null)
        private set

    private var onTerrainSelected: ((Int?) -> Unit)? = null

    fun setTerrainWaypointSelectionCallback(callback: (Int?) -> Unit) {
        onTerrainSelected = callback
    }

    // The property enabled already owns the JVM name setEnabled.
    @JvmName("applyEnabled")
    fun setEnabled(isEnabled: Boolean) {
        enabled = isEnabled
        if (!isEnabled) {
            addingEnabled = false
            dragPreview = null
            clearTerrainWaypointSelection()
        }
    }

    // The property addingEnabled already owns the JVM name setAddingEnabled.
    @JvmName("applyAddingEnabled")
    fun setAddingEnabled(isEnabled: Boolean) {
        addingEnabled = enabled && isEnabled
    }

    fun setWaypoints(
        waypoints: List<RouteWaypoint>,
        plannedPath: List<LatLng> = emptyList(),
        terrainPath: List<TerrainWaypoint> = emptyList(),
        reverseDirection: Boolean = false,
    ) {
        if (selectedControlIndex !in waypoints.indices) selectedControlIndex = null
        val base = terrainPath.takeIf { it.size >= 2 }?.map { LatLng(it.latLon.lat, it.latLon.lon) }
            ?: plannedPath.takeIf { it.size >= 2 }
            ?: waypoints.map { LatLng(it.latitude, it.longitude) }
        this.waypoints = waypoints.toList()
        displayPath = if (reverseDirection) base.reversed() else base
        if (selectedTerrainIndex !in terrainPath.indices) {
            selectedTerrainIndex = null
            onTerrainSelected?.invoke(null)
        }
        this.terrainPath = terrainPath.toList()
        dragPreview = null
    }

    fun selectTerrainWaypoint(index: Int?) {
        selectedTerrainIndex = index?.takeIf { it in terrainPath.indices }
        onTerrainSelected?.invoke(selectedTerrainIndex)
    }

    fun clearTerrainWaypointSelection() {
        if (selectedTerrainIndex != null) selectTerrainWaypoint(null)
    }

    fun clear() {
        waypoints = emptyList()
        terrainPath = emptyList()
        displayPath = emptyList()
        selectedTerrainIndex = null
        selectedControlIndex = null
        dragPreview = null
    }

    /** Map tapped. Moves the selected waypoint or appends a new one. */
    fun onMapTapped(point: LatLng): Boolean {
        if (!enabled) return false
        val selected = selectedControlIndex
        when {
            selected != null -> {
                selectedControlIndex = null
                viewModel.updateRouteWaypoint(selected, point.latitude, point.longitude)
            }
            addingEnabled -> viewModel.addRouteWaypoint(point.latitude, point.longitude)
            else -> return false
        }
        return true
    }

    fun onWaypointClicked(index: Int): Boolean {
        if (!enabled || index !in waypoints.indices) return false
        selectedControlIndex = index
        return true
    }

    fun onWaypointDoubleTapped(index: Int): Boolean {
        if (!enabled || index !in waypoints.indices) return false
        dragPreview = null
        selectedControlIndex = null
        viewModel.removeRouteWaypoint(index)
        return true
    }

    fun onWaypointDragged(index: Int, position: MapCoordinate) {
        if (!enabled) return
        dragPreview = index to position
    }

    fun onWaypointDropped(index: Int, position: MapCoordinate) {
        dragPreview = null
        if (!enabled || index !in waypoints.indices) return
        selectedControlIndex = null
        viewModel.updateRouteWaypoint(index, position.latitude, position.longitude)
    }

    fun onTerrainWaypointClicked(index: Int): Boolean {
        if (!enabled) return false
        selectedControlIndex = null
        selectTerrainWaypoint(if (selectedTerrainIndex == index) null else index)
        return true
    }
}
