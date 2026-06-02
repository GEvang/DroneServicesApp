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
}
