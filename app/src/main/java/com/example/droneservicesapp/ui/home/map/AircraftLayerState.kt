package com.example.droneservicesapp.ui.home.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.ui.map.model.MapCoordinate
import com.example.droneservicesapp.ui.map.model.MapGeometry
import kotlin.math.abs

/**
 * Aircraft, planned/captured home, in-flight trace and simulation marker state.
 *
 * Replaces the drone/home/trace half of the former `OsmdroidMapController`; the behaviour
 * (validity checks, epsilon filtering, offset adjustment) is unchanged.
 */
class AircraftLayerState {
    /** Latest raw (uncorrected) aircraft position received from telemetry. */
    var rawPosition by mutableStateOf<LatLon?>(null)
        private set
    var visible by mutableStateOf(false)
        private set
    var headingDegrees by mutableStateOf(0f)
        private set
    var offsetAdjustmentActive by mutableStateOf(false)
        private set
    var homePosition by mutableStateOf<LatLon?>(null)
        private set
    var flightTrace by mutableStateOf<List<MapCoordinate>>(emptyList())
        private set
    var simulation by mutableStateOf<SimulatedAircraft?>(null)
        private set

    private var latitudeOffset by mutableStateOf(0.0)
    private var longitudeOffset by mutableStateOf(0.0)
    private var onOffsetApplied: ((Double, Double) -> Unit)? = null

    data class SimulatedAircraft(val position: LatLon, val headingDegrees: Float)

    /** Position drawn on the map: raw telemetry plus the operator's display offset. */
    val displayedPosition: LatLon?
        get() = rawPosition?.takeIf { visible }?.let { LatLon(it.lat + latitudeOffset, it.lon + longitudeOffset) }

    fun hasDronePosition(): Boolean = visible

    fun updateDronePosition(latitude: Double, longitude: Double) {
        if (!MapGeometry.isUsable(latitude, longitude)) return
        val previous = rawPosition
        val changed = previous == null ||
            abs(previous.lat - latitude) > POSITION_EPSILON ||
            abs(previous.lon - longitude) > POSITION_EPSILON
        if (changed) rawPosition = LatLon(latitude, longitude)
        visible = true
    }

    fun setDroneVisible(isVisible: Boolean) {
        visible = isVisible
        if (!isVisible) offsetAdjustmentActive = false
    }

    fun updateHeadingDegrees(heading: Float) {
        if (abs(headingDegrees - heading) < HEADING_EPSILON_DEGREES) return
        headingDegrees = heading
    }

    /** Returns false when no usable aircraft position is available yet. */
    fun startDroneOffsetAdjustment(onApplied: (latitudeOffset: Double, longitudeOffset: Double) -> Unit): Boolean {
        val raw = rawPosition
        if (!visible || raw == null || !MapGeometry.isUsable(raw.lat, raw.lon)) return false
        onOffsetApplied = onApplied
        offsetAdjustmentActive = true
        return true
    }

    /** Applies the drag result: the new display offset is relative to the latest raw position. */
    fun completeDroneOffsetDrag(dropped: MapCoordinate) {
        if (!offsetAdjustmentActive) return
        val raw = rawPosition
        offsetAdjustmentActive = false
        val callback = onOffsetApplied
        onOffsetApplied = null
        if (raw == null) return
        latitudeOffset = dropped.latitude - raw.lat
        longitudeOffset = dropped.longitude - raw.lon
        callback?.invoke(latitudeOffset, longitudeOffset)
    }

    fun cancelDroneOffsetAdjustment() {
        offsetAdjustmentActive = false
        onOffsetApplied = null
    }

    fun setHomeMarker(latitude: Double, longitude: Double): Boolean {
        if (homePosition != null || !MapGeometry.isUsable(latitude, longitude)) return false
        homePosition = LatLon(latitude, longitude)
        return true
    }

    fun setOrMoveHomeMarker(latitude: Double, longitude: Double): Boolean {
        if (!MapGeometry.isUsable(latitude, longitude)) return false
        homePosition = LatLon(latitude, longitude)
        return true
    }

    fun clearHomeMarker() {
        homePosition = null
    }

    fun appendFlightTracePoint(latitude: Double, longitude: Double) {
        val point = MapGeometry.coordinateOrNull(latitude, longitude) ?: return
        val next = flightTrace + point
        flightTrace = if (next.size > MAX_FLIGHT_TRACE_POINTS) next.drop(next.size - MAX_FLIGHT_TRACE_POINTS) else next
    }

    fun clearFlightTraceAndHome() {
        homePosition = null
        flightTrace = emptyList()
    }

    fun setSimulationDrone(position: LatLon, headingDegrees: Float) {
        if (!MapGeometry.isUsable(position.lat, position.lon)) return
        simulation = SimulatedAircraft(position, headingDegrees)
    }

    fun clearSimulationDrone() {
        simulation = null
    }

    private companion object {
        const val POSITION_EPSILON = 1e-7
        const val HEADING_EPSILON_DEGREES = 0.5f
        const val MAX_FLIGHT_TRACE_POINTS = 5000
    }
}
