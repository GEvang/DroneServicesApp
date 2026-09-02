package com.example.droneservicesapp.data.mavlink

import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.RouteWaypoint
import io.dronefleet.mavlink.common.MavCmd
import io.dronefleet.mavlink.common.MavFrame
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Test

class MissionBuilderTest {
    @Test
    fun mapsRelativeAltitudeReferenceToRelativeMavlinkFrame() {
        assertEquals(
            MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT,
            MissionBuilder.missionWaypointFrameFor(AltitudeReferenceMode.RELATIVE)
        )
    }

    @Test
    fun mapsTerrainAltitudeReferenceToTerrainMavlinkFrame() {
        assertEquals(
            MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT_INT,
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
    fun sprayPathStartsAtEndpointClosestToHomeAndKeepsAltitudeAlignment() {
        val path = listOf(LatLng(35.02, 24.0), LatLng(35.01, 24.0))

        val ordered = MissionBuilder.orderAreaPath(
            waypoints = path,
            waypointAltitudes = listOf(20f, 10f),
            homeLatitude = 35.0,
            homeLongitude = 24.0,
            startClosestToHome = true
        )

        assertEquals(35.01, ordered.waypoints.first().latitude, 0.000001)
        assertEquals(10f, ordered.altitudes!!.first(), 0.001f)
    }

    @Test
    fun surveyPathStartsAtEndpointFarthestFromHome() {
        val path = listOf(LatLng(35.01, 24.0), LatLng(35.02, 24.0))

        val ordered = MissionBuilder.orderAreaPath(
            waypoints = path,
            waypointAltitudes = null,
            homeLatitude = 35.0,
            homeLongitude = 24.0,
            startClosestToHome = false
        )

        assertEquals(35.02, ordered.waypoints.first().latitude, 0.000001)
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
        val navWaypoints = missionItems.filter { it.command().entry() == MavCmd.MAV_CMD_NAV_WAYPOINT }
        val servoCommands = missionItems.filter {
            it.command().entry() == MavCmd.MAV_CMD_DO_SET_SERVO
        }
        val rtlCommand = missionItems.last()

        assertEquals(MavCmd.MAV_CMD_NAV_RETURN_TO_LAUNCH, rtlCommand.command().entry())
        assertEquals(MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT, rtlCommand.frame().entry())
        assertEquals(MavCmd.MAV_CMD_NAV_TAKEOFF, missionItems.first().command().entry())
        assertEquals(1, missionItems.first().current())
        assertEquals(MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT, missionItems.first().frame().entry())
        assertEquals(2, navWaypoints.size)
        assertEquals(MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT_INT, navWaypoints[0].frame().entry())
        assertEquals((35.1 * 1e7).toInt(), navWaypoints[0].x())
        assertEquals((24.1 * 1e7).toInt(), navWaypoints[0].y())
        assertEquals(8.0f, navWaypoints[0].z(), 0.001f)
        assertEquals(2, servoCommands.size)
        assertEquals(1600.0f, servoCommands.first().param2(), 0.001f)
        assertEquals(1000.0f, servoCommands.last().param2(), 0.001f)
    }

    @Test
    fun buildsMinimalTestMission() {
        val missionItems = MissionBuilder.buildMinimalTestMission(
            currentLatitude = 35.0,
            currentLongitude = 24.0,
            altitudeMeters = 10f,
            targetSystemId = 1,
            targetComponentId = 1
        )

        assertEquals(4, missionItems.size)
        assertEquals(MavCmd.MAV_CMD_NAV_TAKEOFF, missionItems[0].command().entry())
        assertEquals(1, missionItems[0].current())
        assertEquals(MavCmd.MAV_CMD_NAV_RETURN_TO_LAUNCH, missionItems.last().command().entry())
        missionItems.forEachIndexed { index, item ->
            assertEquals(index, item.seq())
        }
        assertEquals(MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT, missionItems[0].frame().entry())
        assertEquals(MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT, missionItems[1].frame().entry())
        assertEquals(MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT, missionItems[2].frame().entry())
        assertEquals(MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT, missionItems[3].frame().entry())
    }
}
