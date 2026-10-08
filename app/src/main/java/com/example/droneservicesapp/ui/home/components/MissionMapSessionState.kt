package com.example.droneservicesapp.ui.home.components

import android.location.Location

/** Mutable values that belong to one MissionMapFragment view session, not to application state. */
class MissionMapSessionState {
    var droneAltitudeAglMeters: Double? = null
    var droneAltitudeAmslMeters: Double? = null
    var horizontalAccuracyMeters: Float? = null
    var verticalAccuracyMeters: Float? = null
    var groundSpeedMetersPerSecond: Float? = null
    var verticalSpeedMetersPerSecond: Float? = null
    var headingDegrees: Double? = null
    var lastRtkStreamingActive: Boolean? = null

    fun updateLocation(location: Location?) {
        droneAltitudeAglMeters = location?.altitude
        horizontalAccuracyMeters = location?.takeIf { it.hasAccuracy() }?.accuracy
        verticalAccuracyMeters = location?.takeIf { it.hasVerticalAccuracy() }?.verticalAccuracyMeters
    }

    fun clear() {
        droneAltitudeAglMeters = null
        droneAltitudeAmslMeters = null
        horizontalAccuracyMeters = null
        verticalAccuracyMeters = null
        groundSpeedMetersPerSecond = null
        verticalSpeedMetersPerSecond = null
        headingDegrees = null
        lastRtkStreamingActive = null
    }
}
