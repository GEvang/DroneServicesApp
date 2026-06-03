package com.example.droneservicesapp.data.mavlink

import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.RouteWaypoint
import io.dronefleet.mavlink.common.MavCmd
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

    @Test
    fun buildsPointRouteMissionFromRouteWaypoints() {
        val route = listOf(
            RouteWaypoint(
                id = "one",
                index = 1,
                latitude = 35.1,
                longitude = 24.1,
                altitudeMeters = 8.0,
                speedMetersPerSecond = 2.0,
                sprayEnabled = true,
                sprayerIntensityPercent = 50
            ),
            RouteWaypoint(
                id = "two",
                index = 2,
                latitude = 35.2,
                longitude = 24.2,
                altitudeMeters = 9.0,
                speedMetersPerSecond = 2.0,
                sprayEnabled = true,
                sprayerIntensityPercent = 50
            )
        )

        val missionItems = MissionBuilder.buildPointRouteMission(
            routeWaypoints = route,
            currentLatitude = 35.0,
            currentLongitude = 24.0,
            targetSystemId = 1,
            targetComponentId = 1,
            altitudeReferenceMode = AltitudeReferenceMode.TERRAIN
        )
        val navWaypoints = missionItems.filter {
            it.command().entry() == MavCmd.MAV_CMD_NAV_WAYPOINT
        }
        val servoCommands = missionItems.filter {
            it.command().entry() == MavCmd.MAV_CMD_DO_SET_SERVO
        }

        assertEquals(3, navWaypoints.size)
        assertEquals(MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT, navWaypoints[1].frame().entry())
        assertEquals((35.1 * 1e7).toInt(), navWaypoints[1].x())
        assertEquals((24.1 * 1e7).toInt(), navWaypoints[1].y())
        assertEquals(8.0f, navWaypoints[1].z(), 0.001f)
        assertEquals(2, servoCommands.size)
        assertEquals(1600.0f, servoCommands.first().param2(), 0.001f)
        assertEquals(1000.0f, servoCommands.last().param2(), 0.001f)
    }
}
