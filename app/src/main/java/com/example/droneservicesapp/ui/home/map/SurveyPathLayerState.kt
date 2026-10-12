package com.example.droneservicesapp.ui.home.map

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.droneservicesapp.domain.model.LatLon
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import java.util.Locale

/**
 * Generated/downloaded mission path, its waypoint selection, and battery/tank service stops.
 *
 * Replaces the survey-path half of the former `OsmdroidMapController`.
 */
class SurveyPathLayerState {
    var path by mutableStateOf<List<LatLng>>(emptyList())
        private set
    var areaVertices by mutableStateOf<List<LatLng>>(emptyList())
        private set
    var segmentColors by mutableStateOf<List<Int>?>(null)
        private set
    var editingEnabled by mutableStateOf(false)
        private set
    var selectedIndex by mutableStateOf<Int?>(null)
        private set
    var batteryReturnPoints by mutableStateOf<List<LatLon>>(emptyList())
        private set
    var tankRefillPoints by mutableStateOf<List<LatLon>>(emptyList())
        private set

    private var onSelected: ((Int?) -> Unit)? = null
    private var onMoved: ((Int, LatLng) -> Unit)? = null

    fun setEditCallbacks(onSelected: (Int?) -> Unit, onMoved: (Int, LatLng) -> Unit) {
        this.onSelected = onSelected
        this.onMoved = onMoved
    }

    fun setSurveyPath(path: List<LatLng>, areaVertices: List<LatLng> = emptyList(), segmentColors: List<Int>? = null) {
        val selected = selectedIndex
        if (selected != null && selected !in path.indices) clearSelectedWaypoint()
        this.path = path.toList()
        this.areaVertices = areaVertices.toList()
        this.segmentColors = segmentColors?.takeIf { it.size >= path.size - 1 }
        logMissionMeasurements(areaVertices)
    }

    fun setServiceMarkers(batteryReturnPoints: List<LatLon>, tankRefillPoints: List<LatLon>) {
        this.batteryReturnPoints = batteryReturnPoints.toList()
        this.tankRefillPoints = tankRefillPoints.toList()
    }

    /** Clears the path, selection and service stops (the simulation marker is cleared by the caller). */
    fun clear() {
        clearSelectedWaypoint()
        path = emptyList()
        areaVertices = emptyList()
        segmentColors = null
        batteryReturnPoints = emptyList()
        tankRefillPoints = emptyList()
    }

    // The property editingEnabled already owns the JVM name setEditingEnabled.
    @JvmName("applyEditingEnabled")
    fun setEditingEnabled(enabled: Boolean) {
        if (editingEnabled == enabled) return
        editingEnabled = enabled
        if (!enabled) clearSelectedWaypoint()
    }

    fun clearSelectedWaypoint() {
        if (selectedIndex == null) return
        selectedIndex = null
        onSelected?.invoke(null)
    }

    /** Waypoint marker tapped. Returns false when editing is disabled so the tap falls through. */
    fun onWaypointClicked(index: Int): Boolean {
        if (!editingEnabled) return false
        selectedIndex = if (selectedIndex == index) null else index
        onSelected?.invoke(selectedIndex)
        return true
    }

    /** Map tapped: moves the selected waypoint. Returns true when the tap was consumed. */
    fun onMapTapped(point: LatLng): Boolean {
        val selected = selectedIndex ?: return false
        if (!editingEnabled) return false
        onMoved?.invoke(selected, point)
        return true
    }

    private fun logMissionMeasurements(areaVertices: List<LatLng>) {
        if (areaVertices.size < 2) return
        val segments = perimeterSegments(areaVertices)
        val sideLengths = segments.map { (from, to) -> SphericalUtil.computeDistanceBetween(from, to) }
        val areaSquareMeters = if (areaVertices.size >= 3) SphericalUtil.computeArea(areaVertices) else 0.0
        val verticesText = areaVertices.mapIndexed { index, vertex ->
            "${index + 1}=(${String.format(Locale.US, "%.8f", vertex.latitude)}, " +
                "${String.format(Locale.US, "%.8f", vertex.longitude)})"
        }.joinToString("; ")
        val sideText = sideLengths.mapIndexed { index, meters ->
            "${index + 1}=${String.format(Locale.US, "%.2f m", meters)}"
        }.joinToString("; ")
        Log.d(
            MISSION_MEASUREMENTS_TAG,
            "vertices=${areaVertices.size} [$verticesText] sides=[$sideText] " +
                "perimeter=${String.format(Locale.US, "%.2f m", sideLengths.sum())} " +
                "area=${String.format(Locale.US, "%.2f m2", areaSquareMeters)}",
        )
    }

    companion object {
        private const val MISSION_MEASUREMENTS_TAG = "MissionMeasurements"

        fun perimeterSegments(vertices: List<LatLng>): List<Pair<LatLng, LatLng>> {
            if (vertices.size < 2) return emptyList()
            val open = vertices.zipWithNext()
            return if (vertices.size >= 3) open + (vertices.last() to vertices.first()) else open
        }
    }
}
