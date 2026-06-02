package com.example.droneservicesapp.data.mavlink

import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import io.dronefleet.mavlink.common.MavFrame
import org.junit.Assert.assertEquals
import org.junit.Test

class MissionBuilderTest {
    @Test
    fun mapsRelativeAltitudeReferenceToRelativeMavlinkFrame() {
        assertEquals(
            MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT,
            MissionBuilder.missionWaypointFrameFor(AltitudeReferenceMode.RELATIVE)
        )
    }

    @Test
    fun mapsTerrainAltitudeReferenceToTerrainMavlinkFrame() {
        assertEquals(
            MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT,
            MissionBuilder.missionWaypointFrameFor(AltitudeReferenceMode.TERRAIN)
        )
    }

    @Test
    fun defaultsMissingSavedAltitudeReferenceToRelative() {
        assertEquals(
            AltitudeReferenceMode.RELATIVE,
            AltitudeReferenceMode.fromStorageValue(null)
        )
    }

    @Test
    fun mapsSprayerIntensityToServo5PwmRange() {
        assertEquals(1000.0f, MissionBuilder.servo5PwmForSprayerIntensity(0), 0.001f)
        assertEquals(1600.0f, MissionBuilder.servo5PwmForSprayerIntensity(50), 0.001f)
        assertEquals(2200.0f, MissionBuilder.servo5PwmForSprayerIntensity(100), 0.001f)
    }

    @Test
    fun clampsSprayerIntensityBeforeMappingServo5Pwm() {
        assertEquals(1000.0f, MissionBuilder.servo5PwmForSprayerIntensity(-10), 0.001f)
        assertEquals(2200.0f, MissionBuilder.servo5PwmForSprayerIntensity(120), 0.001f)
    }
}
