package com.example.droneservicesapp.mavserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArduCopterFlightModeTest {
    @Test
    fun mapsEveryOperatorModeToItsArduCopterCustomMode() {
        ArduCopterFlightMode.values().forEach { mode ->
            assertEquals(mode, ArduCopterFlightMode.fromCustomMode(mode.customMode))
        }
        assertNull(ArduCopterFlightMode.fromCustomMode(999))
    }

    @Test
    fun identifiesModesThatRequireDeliberateConfirmation() {
        assertTrue(ArduCopterFlightMode.RTL.holdDurationMs >= 2_000L)
        assertTrue(ArduCopterFlightMode.LAND.holdDurationMs >= 2_000L)
        assertTrue(ArduCopterFlightMode.BRAKE.holdDurationMs > 0L)
        assertEquals(0L, ArduCopterFlightMode.LOITER.holdDurationMs)
    }

    @Test
    fun onlyAutoRequiresAnUploadedMission() {
        assertTrue(ArduCopterFlightMode.AUTO.requiresUploadedMission)
        ArduCopterFlightMode.values()
            .filterNot { it == ArduCopterFlightMode.AUTO }
            .forEach { mode -> assertFalse(mode.requiresUploadedMission) }
    }

    @Test
    fun formatsKnownAndUnknownHeartbeatModes() {
        assertEquals("LOITER", ArduCopterFlightMode.displayName(5))
        assertEquals("SMART RTL", ArduCopterFlightMode.displayName(21))
        assertEquals("MODE 99", ArduCopterFlightMode.displayName(99))
    }
}
