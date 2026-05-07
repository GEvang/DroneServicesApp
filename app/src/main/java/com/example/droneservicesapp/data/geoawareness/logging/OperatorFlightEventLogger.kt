package com.example.droneservicesapp.data.geoawareness.logging

import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction
import com.example.droneservicesapp.domain.model.LatLon

class OperatorFlightEventLogger(
    private val eventLogger: GeoAwarenessEventLogger
) {

    fun logDroneConnected(details: Map<String, String> = emptyMap()) {
        eventLogger.logSimple(
            type = GeoAwarenessEventType.DRONE_CONNECTED,
            severity = "INFO",
            message = "Drone connected",
            category = "FLIGHT",
            connectionState = "CONNECTED",
            details = details
        )
    }

    fun logDroneDisconnected(reason: String? = null) {
        eventLogger.logSimple(
            type = GeoAwarenessEventType.DRONE_DISCONNECTED,
            severity = "WARNING",
            message = "Drone disconnected",
            category = "FLIGHT",
            connectionState = "DISCONNECTED",
            details = reason?.let { mapOf("reason" to it) } ?: emptyMap()
        )
    }

    fun logArmRequested() {
        eventLogger.logSimple(
            type = GeoAwarenessEventType.OPERATOR_ARM_REQUESTED,
            severity = "INFO",
            message = "Operator requested arm",
            category = "OPERATOR",
            operatorAction = "ARM"
        )
    }

    fun logDisarmRequested() {
        eventLogger.logSimple(
            type = GeoAwarenessEventType.OPERATOR_DISARM_REQUESTED,
            severity = "INFO",
            message = "Operator requested disarm",
            category = "OPERATOR",
            operatorAction = "DISARM"
        )
    }

    fun logDroneArmed(position: LatLon?, altitudeMeters: Double?, flightMode: String?) {
        eventLogger.logSimple(
            type = GeoAwarenessEventType.DRONE_ARMED,
            severity = "INFO",
            message = "Drone armed",
            category = "FLIGHT",
            flightState = "ARMED",
            flightMode = flightMode,
            latitude = position?.lat,
            longitude = position?.lon,
            altitudeMeters = altitudeMeters
        )
    }

    fun logDroneDisarmed(position: LatLon?, altitudeMeters: Double?, flightMode: String?) {
        eventLogger.logSimple(
            type = GeoAwarenessEventType.DRONE_DISARMED,
            severity = "INFO",
            message = "Drone disarmed",
            category = "FLIGHT",
            flightState = "DISARMED",
            flightMode = flightMode,
            latitude = position?.lat,
            longitude = position?.lon,
            altitudeMeters = altitudeMeters
        )
    }

    fun logTakeoffRequested() {
        eventLogger.logSimple(
            type = GeoAwarenessEventType.TAKEOFF_REQUESTED,
            severity = "INFO",
            message = "Takeoff requested",
            category = "OPERATOR",
            operatorAction = "TAKEOFF"
        )
    }

    fun logTakeoffDetected(position: LatLon?, altitudeMeters: Double?) {
        eventLogger.logSimple(
            type = GeoAwarenessEventType.TAKEOFF_DETECTED,
            severity = "INFO",
            message = "Takeoff detected",
            category = "FLIGHT",
            flightState = "AIRBORNE",
            latitude = position?.lat,
            longitude = position?.lon,
            altitudeMeters = altitudeMeters
        )
    }

    fun logLandingDetected(position: LatLon?, altitudeMeters: Double?) {
        eventLogger.logSimple(
            type = GeoAwarenessEventType.LANDING_DETECTED,
            severity = "INFO",
            message = "Landing detected",
            category = "FLIGHT",
            flightState = "LANDED",
            latitude = position?.lat,
            longitude = position?.lon,
            altitudeMeters = altitudeMeters
        )
    }

    fun logFlightModeChanged(previous: String?, current: String?) {
        eventLogger.logSimple(
            type = GeoAwarenessEventType.FLIGHT_MODE_CHANGED,
            severity = "INFO",
            message = "Flight mode changed",
            category = "FLIGHT",
            flightMode = current,
            details = buildMap {
                previous?.let { put("previous", it) }
                current?.let { put("current", it) }
            }
        )
    }

    fun logMissionUploadStarted(itemCount: Int?) {
        eventLogger.logSimple(
            type = GeoAwarenessEventType.MISSION_UPLOAD_STARTED,
            severity = "INFO",
            message = "Mission upload started",
            category = "MISSION",
            operatorAction = "UPLOAD",
            details = itemCount?.let { mapOf("itemCount" to it.toString()) } ?: emptyMap()
        )
    }

    fun logMissionUploadSucceeded(itemCount: Int?) {
        eventLogger.logSimple(
            type = GeoAwarenessEventType.MISSION_UPLOAD_SUCCEEDED,
            severity = "INFO",
            message = "Mission upload succeeded",
            category = "MISSION",
            details = itemCount?.let { mapOf("itemCount" to it.toString()) } ?: emptyMap()
        )
    }

    fun logMissionUploadFailed(reason: String?) {
        eventLogger.logSimple(
            type = GeoAwarenessEventType.MISSION_UPLOAD_FAILED,
            severity = "ERROR",
            message = "Mission upload failed",
            category = "MISSION",
            details = reason?.let { mapOf("reason" to it) } ?: emptyMap()
        )
    }

    fun logBatteryLow(percent: Int) {
        eventLogger.logSimple(
            type = GeoAwarenessEventType.BATTERY_LOW,
            severity = "WARNING",
            message = "Battery low",
            category = "TELEMETRY",
            batteryPercent = percent
        )
    }

    fun logFailsafeDetected(type: String, details: Map<String, String> = emptyMap()) {
        eventLogger.logSimple(
            type = GeoAwarenessEventType.FAILSAFE_DETECTED,
            severity = "ERROR",
            message = "Failsafe detected",
            category = "FLIGHT",
            details = details + mapOf("failsafeType" to type)
        )
    }

    fun logRtlDetected() {
        eventLogger.logSimple(
            type = GeoAwarenessEventType.RTL_DETECTED,
            severity = "WARNING",
            message = "RTL detected",
            category = "FLIGHT"
        )
    }

    fun logGeoZoneEntered(zones: List<GeoZone>, position: LatLon?, altitudeMeters: Double?) {
        if (zones.isEmpty()) return
        eventLogger.logSimple(
            type = GeoAwarenessEventType.GEO_ZONE_ENTERED,
            severity = severityForRestriction(zones.first().restriction),
            message = "Drone entered geo-zone(s)",
            category = "GEO",
            zoneIds = zones.map { it.id },
            zoneNames = zones.map { it.name },
            restriction = zones.first().restriction.name,
            latitude = position?.lat,
            longitude = position?.lon,
            altitudeMeters = altitudeMeters
        )
    }

    fun logGeoZoneExited(zones: List<GeoZone>, position: LatLon?, altitudeMeters: Double?) {
        if (zones.isEmpty()) return
        eventLogger.logSimple(
            type = GeoAwarenessEventType.GEO_ZONE_EXITED,
            severity = "INFO",
            message = "Drone exited geo-zone(s)",
            category = "GEO",
            zoneIds = zones.map { it.id },
            zoneNames = zones.map { it.name },
            restriction = zones.firstOrNull()?.restriction?.name,
            latitude = position?.lat,
            longitude = position?.lon,
            altitudeMeters = altitudeMeters
        )
    }

    private fun severityForRestriction(restriction: GeoZoneRestriction): String {
        return when (restriction) {
            GeoZoneRestriction.INFORMATION -> "INFO"
            GeoZoneRestriction.CONDITIONAL,
            GeoZoneRestriction.REQ_AUTHORISATION -> "WARNING"
            GeoZoneRestriction.PROHIBITED,
            GeoZoneRestriction.UNKNOWN -> "ERROR"
        }
    }
}
