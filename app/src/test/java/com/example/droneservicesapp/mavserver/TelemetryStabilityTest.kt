package com.example.droneservicesapp.mavserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryStabilityTest {
    @Test
    fun batteryPercentageDoesNotOscillateAtRoundingBoundary() {
        val stabilizer = BatteryPercentageStabilizer()

        assertEquals(67, stabilizer.update(0.666f))
        assertEquals(67, stabilizer.update(0.664f))
        assertEquals(67, stabilizer.update(0.667f))
        assertEquals(67, stabilizer.update(0.662f))
        assertEquals(67, stabilizer.update(0.662f))
        assertEquals(66, stabilizer.update(0.662f))
        assertEquals(66, stabilizer.update(0.668f))
        assertEquals(66, stabilizer.update(0.668f))
        assertEquals(67, stabilizer.update(0.668f))
    }

    @Test
    fun alternatingWholePercentReportsDoNotFlicker() {
        val stabilizer = BatteryPercentageStabilizer()

        assertEquals(66, stabilizer.update(0.66f))
        repeat(5) {
            assertEquals(66, stabilizer.update(0.67f))
            assertEquals(66, stabilizer.update(0.66f))
        }
    }

    @Test
    fun batteryPercentageStillTracksLargeChangesAndCanReset() {
        val stabilizer = BatteryPercentageStabilizer()

        assertEquals(67, stabilizer.update(0.67f))
        assertEquals(61, stabilizer.update(0.61f))
        assertNull(stabilizer.update(Float.NaN))
        stabilizer.reset()
        assertEquals(66, stabilizer.update(0.664f))
    }

    @Test
    fun autopilotLinkRequiresARecentKnownHeartbeat() {
        assertFalse(isAutopilotLinkHealthy(0L, nowMs = 10_000L, staleAfterMs = 2_500L))
        assertTrue(isAutopilotLinkHealthy(8_000L, nowMs = 10_000L, staleAfterMs = 2_500L))
        assertFalse(isAutopilotLinkHealthy(7_500L, nowMs = 10_000L, staleAfterMs = 2_500L))
        assertFalse(isAutopilotLinkHealthy(11_000L, nowMs = 10_000L, staleAfterMs = 2_500L))
    }

    @Test
    fun onlyAircraftAutopilotHeartbeatsQualify() {
        assertTrue(isAircraftHeartbeat(isGcs = false, hasAutopilot = true))
        assertFalse(isAircraftHeartbeat(isGcs = true, hasAutopilot = true))
        assertFalse(isAircraftHeartbeat(isGcs = false, hasAutopilot = false))
    }
}
