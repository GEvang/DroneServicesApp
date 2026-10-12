package com.example.droneservicesapp.ui.home.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.ortho.OrthoBounds
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.ui.home.MissionDialogQueue
import com.example.droneservicesapp.ui.map.model.MapCamera
import com.example.droneservicesapp.ui.map.model.MapCameraCommand
import com.example.droneservicesapp.ui.map.model.MapCoordinate
import com.example.droneservicesapp.ui.map.model.MapGeometry
import com.example.droneservicesapp.ui.map.model.MapSurfaceHandle
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng

/** Stable marker ids shared by the scene builder and interaction routing. */
object MissionMapMarkerIds {
    const val AIRCRAFT = "aircraft"
    const val SIMULATION = "simulation-aircraft"
    const val HOME = "home"
    const val AREA_VERTEX = "area-vertex-"
    const val ROUTE_WAYPOINT = "route-waypoint-"
    const val ROUTE_TERRAIN = "route-terrain-"
    const val SURVEY_WAYPOINT = "survey-waypoint-"

    fun indexOf(prefix: String, id: String): Int? =
        if (id.startsWith(prefix)) id.removePrefix(prefix).toIntOrNull() else null
}

/**
 * All map content of the mission screen, held as Compose snapshot state, plus interaction
 * routing. This replaces the stack of osmdroid editors whose relative overlay order used to
 * decide which one received a tap.
 */
class MissionMapLayers(
    private val context: Context,
    viewModel: MainActivityViewModel,
    dialogs: MissionDialogQueue,
    private val showMessage: (Int) -> Unit,
) {
    data class OrthoRaster(val bitmap: Bitmap, val bounds: OrthoBounds, val opacity: Float)

    val aircraft = AircraftLayerState()
    val survey = SurveyPathLayerState()
    val polygon = PolygonEditorState(viewModel)
    val route = RouteEditorState(viewModel)
    val obstacles = ObstacleEditorState(viewModel, showMessage)
    val geoZones = GeoZoneLayerState(context, dialogs)
    val terrainCoverage = TerrainCoverageLayerState()

    var homePlacementActive by mutableStateOf(false)
        private set
    var ortho by mutableStateOf<OrthoRaster?>(null)
    var tilesEnabled by mutableStateOf(true)
    var cameraCommand by mutableStateOf<MapCameraCommand?>(null)
        private set

    /** Live surface, available while the map is composed. */
    var surface: MapSurfaceHandle? = null

    private var onHomePlacementSelected: ((LatLng) -> Unit)? = null
    private var commandToken = 0L

    fun setHomePlacementEnabled(enabled: Boolean, onSelected: ((LatLng) -> Unit)? = null) {
        homePlacementActive = enabled
        onHomePlacementSelected = if (enabled) onSelected else null
    }

    /** Clears the path, its service stops and the simulation marker, like the old controller. */
    fun clearSurveyPath() {
        survey.clear()
        aircraft.clearSimulationDrone()
    }

    // Camera

    fun pan(center: LatLon) {
        val coordinate = MapGeometry.coordinateOrNull(center.lat, center.lon) ?: return
        cameraCommand = MapCameraCommand.Pan(++commandToken, coordinate)
    }

    fun jump(center: MapCoordinate, zoom: Double) {
        cameraCommand = MapCameraCommand.Jump(++commandToken, center, zoom)
    }

    fun move(camera: MapCamera) {
        cameraCommand = MapCameraCommand.Move(++commandToken, camera)
    }

    /** Centers on the displayed aircraft; returns false (after telling the operator) when unknown. */
    fun centerOnDrone(): Boolean {
        val position = aircraft.displayedPosition
        if (position == null || !MapGeometry.isUsable(position.lat, position.lon)) {
            showMessage(R.string.drone_location_not_available_yet)
            return false
        }
        pan(position)
        return true
    }

    fun centerOnUserIfPermitted(showErrors: Boolean = true): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!(fine || coarse)) {
            if (showErrors) showMessage(R.string.invalid_location_permissions)
            return false
        }
        val location = surface?.userLocation()
        return if (location != null && MapGeometry.isUsable(location.latitude, location.longitude)) {
            pan(LatLon(location.latitude, location.longitude))
            true
        } else {
            if (showErrors) showMessage(R.string.waiting_for_gps_fix)
            false
        }
    }

    // Interaction routing

    /**
     * Single tap on the map background. Priority mirrors the previous overlay stack: home
     * placement, obstacle placement, route editing, survey-waypoint moves, polygon drawing, and
     * finally geo-zone details.
     */
    fun onMapTap(coordinate: MapCoordinate) {
        val point = LatLng(coordinate.latitude, coordinate.longitude)
        if (homePlacementActive) {
            val callback = onHomePlacementSelected
            setHomePlacementEnabled(false)
            callback?.invoke(point)
            return
        }
        if (obstacles.onMapTapped(point)) return
        if (route.onMapTapped(point)) return
        if (survey.onMapTapped(point)) return
        if (polygon.onMapTapped(point)) return
        geoZones.onMapTapped(LatLon(coordinate.latitude, coordinate.longitude))
    }

    fun onMarkerClick(id: String): Boolean {
        MissionMapMarkerIds.indexOf(MissionMapMarkerIds.AREA_VERTEX, id)?.let { return polygon.onVertexClicked(it) }
        MissionMapMarkerIds.indexOf(MissionMapMarkerIds.ROUTE_WAYPOINT, id)?.let { return route.onWaypointClicked(it) }
        MissionMapMarkerIds.indexOf(MissionMapMarkerIds.ROUTE_TERRAIN, id)?.let { return route.onTerrainWaypointClicked(it) }
        MissionMapMarkerIds.indexOf(MissionMapMarkerIds.SURVEY_WAYPOINT, id)?.let { return survey.onWaypointClicked(it) }
        return false
    }

    fun onMarkerDoubleTap(id: String): Boolean {
        MissionMapMarkerIds.indexOf(MissionMapMarkerIds.ROUTE_WAYPOINT, id)?.let { return route.onWaypointDoubleTapped(it) }
        return false
    }

    fun onMarkerDrag(id: String, coordinate: MapCoordinate) {
        MissionMapMarkerIds.indexOf(MissionMapMarkerIds.ROUTE_WAYPOINT, id)?.let { route.onWaypointDragged(it, coordinate) }
    }

    fun onMarkerMoved(id: String, coordinate: MapCoordinate) {
        when {
            id == MissionMapMarkerIds.AIRCRAFT -> aircraft.completeDroneOffsetDrag(coordinate)
            MissionMapMarkerIds.indexOf(MissionMapMarkerIds.AREA_VERTEX, id) != null -> polygon.onVertexMoved(
                MissionMapMarkerIds.indexOf(MissionMapMarkerIds.AREA_VERTEX, id) ?: return,
                LatLng(coordinate.latitude, coordinate.longitude),
            )
            MissionMapMarkerIds.indexOf(MissionMapMarkerIds.ROUTE_WAYPOINT, id) != null -> route.onWaypointDropped(
                MissionMapMarkerIds.indexOf(MissionMapMarkerIds.ROUTE_WAYPOINT, id) ?: return,
                coordinate,
            )
        }
    }
}
