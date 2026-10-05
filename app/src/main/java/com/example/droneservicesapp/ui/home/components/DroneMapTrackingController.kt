package com.example.droneservicesapp.ui.home.components

import android.location.Location
import android.util.Log
import com.example.droneservicesapp.domain.model.LatLon
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Owns corrected drone position, automatic home capture, and the in-flight map trace. */
class DroneMapTrackingController(
    private val mapController: OsmdroidMapController,
    private val onHomeCaptured: (LatLon) -> Unit,
    private val onDisarmed: () -> Unit,
) {
    var rawPosition: LatLon? = null
        private set

    var correctedPosition: LatLon? = null
        private set

    var homePosition: LatLon? = null
        private set

    private var latitudeOffset = 0.0
    private var longitudeOffset = 0.0
    private var wasArmed = false
    private var pendingHomeAfterArm = false
    private var lastTracePosition: LatLon? = null
    private var tracePointCount = 0

    fun updateOffset(latitude: Double, longitude: Double) {
        latitudeOffset = latitude
        longitudeOffset = longitude
    }

    fun syncLocation(location: Location?) {
        rawPosition = location?.let { LatLon(it.latitude, it.longitude) }
        correctedPosition = rawPosition?.let(::applyOffset)
    }

    fun offsetLocation(source: Location?): Location? = source?.let {
        Location(it).apply {
            latitude = it.latitude + latitudeOffset
            longitude = it.longitude + longitudeOffset
        }
    }

    fun renderHome(position: LatLon?) {
        homePosition = position
        if (position == null) mapController.clearHomeMarker()
        else mapController.setOrMoveHomeMarker(position.lat, position.lon)
    }

    fun handleArmedState(isArmed: Boolean) {
        if (!wasArmed && isArmed) {
            val position = correctedPosition
            if (position != null && isValidPosition(position)) captureHome(position)
            else pendingHomeAfterArm = true
        } else if (wasArmed && !isArmed) {
            onDisarmed()
        }
        wasArmed = isArmed
    }

    fun capturePendingHome(position: LatLon) {
        if (!pendingHomeAfterArm || !isValidPosition(position)) return
        captureHome(position)
        pendingHomeAfterArm = false
    }

    fun appendTraceIfFlying(position: LatLon, connected: Boolean, armed: Boolean) {
        if (!connected || !armed || !shouldAppendTrace(position)) return
        mapController.appendFlightTracePoint(position.lat, position.lon)
        lastTracePosition = position
        tracePointCount += 1
        when {
            tracePointCount == 1 -> Log.d(LOG_TAG, "trace started")
            tracePointCount % 50 == 0 -> Log.d(LOG_TAG, "trace point count=$tracePointCount")
        }
    }

    fun clearTraceAndHome() {
        mapController.clearFlightTraceAndHome()
        homePosition = null
        pendingHomeAfterArm = false
        lastTracePosition = null
        tracePointCount = 0
    }

    private fun captureHome(position: LatLon) {
        if (homePosition != null || !isValidPosition(position)) return
        if (mapController.setHomeMarker(position.lat, position.lon)) {
            homePosition = position
            onHomeCaptured(position)
            Log.d(LOG_TAG, "home marker set lat=${position.lat} lon=${position.lon}")
        }
    }

    private fun applyOffset(position: LatLon) = LatLon(
        lat = position.lat + latitudeOffset,
        lon = position.lon + longitudeOffset,
    )

    private fun shouldAppendTrace(position: LatLon): Boolean {
        if (!isValidPosition(position)) return false
        val previous = lastTracePosition ?: return true
        return distanceMeters(previous, position) >= MIN_TRACE_POINT_DISTANCE_METERS
    }

    companion object {
        private const val LOG_TAG = "MapFlightTrace"
        private const val MIN_VALID_ABS_COORDINATE = 1e-4
        private const val MIN_TRACE_POINT_DISTANCE_METERS = 2.0

        fun isValidPosition(position: LatLon): Boolean =
            position.lat.isFinite() &&
                position.lon.isFinite() &&
                position.lat in -90.0..90.0 &&
                position.lon in -180.0..180.0 &&
                (abs(position.lat) > MIN_VALID_ABS_COORDINATE || abs(position.lon) > MIN_VALID_ABS_COORDINATE)

        internal fun distanceMeters(start: LatLon, end: LatLon): Double {
            val earthRadiusMeters = 6_371_000.0
            val dLat = Math.toRadians(end.lat - start.lat)
            val dLon = Math.toRadians(end.lon - start.lon)
            val startLat = Math.toRadians(start.lat)
            val endLat = Math.toRadians(end.lat)
            val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
                cos(startLat) * cos(endLat) * sin(dLon / 2.0) * sin(dLon / 2.0)
            return earthRadiusMeters * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        }
    }
}
