package com.example.droneservicesapp.ui.home.components

import org.junit.Assert.assertNull
import org.junit.Test

class MissionMapSessionStateTest {
    @Test
    fun `clear removes all transient telemetry and RTK state`() {
        val state = MissionMapSessionState().apply {
            droneAltitudeAglMeters = 42.0
            droneAltitudeAmslMeters = 315.0
            horizontalAccuracyMeters = 0.2f
            verticalAccuracyMeters = 0.4f
            groundSpeedMetersPerSecond = 5f
            verticalSpeedMetersPerSecond = -1f
            headingDegrees = 180.0
            lastRtkStreamingActive = true
        }

        state.clear()

        assertNull(state.droneAltitudeAglMeters)
        assertNull(state.droneAltitudeAmslMeters)
        assertNull(state.horizontalAccuracyMeters)
        assertNull(state.verticalAccuracyMeters)
        assertNull(state.groundSpeedMetersPerSecond)
        assertNull(state.verticalSpeedMetersPerSecond)
        assertNull(state.headingDegrees)
        assertNull(state.lastRtkStreamingActive)
    }
}
