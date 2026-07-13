package com.example.droneservicesapp.data.mavlink

import android.location.Location
import android.util.Log
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.RouteWaypoint
import com.google.android.gms.maps.model.LatLng
import io.dronefleet.mavlink.common.MavCmd
import io.dronefleet.mavlink.common.MavFrame
import io.dronefleet.mavlink.common.MavMissionType
import io.dronefleet.mavlink.common.MissionItemInt

/**
 * Pure mission construction logic (no MAVLink connection, no sockets, no ViewModels).
 * This is a behavior-preserving extract of MavLinkComm.setupMission().
 */
object MissionBuilder {

    fun buildSurveyMission(
        waypoints: ArrayList<LatLng>,
        currentPos: Location,
        alt: Float,
        sprayerIntensity: Int,
        flightSpeed: Float,
        angleProgress: Float,
        targetSystemId: Int,
        targetComponentId: Int,
        altitudeReferenceMode: AltitudeReferenceMode = AltitudeReferenceMode.RELATIVE
    ): ArrayList<MissionItemInt> = buildSprayAreaMission(
        waypoints = waypoints,
        currentPos = currentPos,
        alt = alt,
        sprayerIntensity = sprayerIntensity,
        flightSpeed = flightSpeed,
        angleProgress = angleProgress,
        targetSystemId = targetSystemId,
        targetComponentId = targetComponentId,
        altitudeReferenceMode = altitudeReferenceMode
    )

    fun buildSprayAreaMission(
        waypoints: ArrayList<LatLng>,
        currentPos: Location,
        alt: Float,
        sprayerIntensity: Int,
        flightSpeed: Float,
        angleProgress: Float,
        targetSystemId: Int,
        targetComponentId: Int,
        altitudeReferenceMode: AltitudeReferenceMode = AltitudeReferenceMode.RELATIVE
    ): ArrayList<MissionItemInt> {

        val sprayerIntensityPWM = servo5PwmForSprayerIntensity(sprayerIntensity)

        val missionItems = ArrayList<MissionItemInt>()
        val waypointFrame = missionWaypointFrameFor(altitudeReferenceMode)
        val commandFrame = MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT
        val missionYawDegrees = 90.0f - angleProgress
        var seq = 0

        logInfo(
            "MissionUpload",
            "Uploading survey mission: altitudeReference=$altitudeReferenceMode, " +
                "frame=${waypointFrame.name}, altitude=${alt}m"
        )

        fun nextSeq() = seq++

        fun buildItem(
            frame: MavFrame,
            command: MavCmd,
            currentFlag: Int,
            p1: Float, p2: Float, p3: Float, p4: Float,
            x: Int, y: Int, z: Float
        ): MissionItemInt =
            MissionItemInt.builder().apply {
                seq(nextSeq())
                frame(frame)
                command(command)
                current(currentFlag)
                autocontinue(1)
                param1(p1)
                param2(p2)
                param3(p3)
                param4(p4)
                x(x)
                y(y)
                z(z)
                missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                targetSystem(targetSystemId)
                targetComponent(targetComponentId)
            }.build()

        // seq=0: TAKEOFF for generated Copter ground-start AUTO missions.
        missionItems.add(
            buildItem(
                frame = MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT,
                command = MavCmd.MAV_CMD_NAV_TAKEOFF,
                currentFlag = 1,
                p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = 0.0f,
                x = currentPos.latitude.toE7(),
                y = currentPos.longitude.toE7(),
                z = alt
            )
        )

        waypoints.forEachIndexed { i, wp ->

            // Before the first waypoint is added: set speed
            if (i == 0) {
                missionItems.add(
                    buildItem(
                        frame = commandFrame,
                        command = MavCmd.MAV_CMD_DO_CHANGE_SPEED,
                        currentFlag = 0,
                        p1 = 1.0f, p2 = flightSpeed, p3 = 0.0f, p4 = 0.0f,
                        x = 0, y = 0, z = 0.0f
                    )
                )
            }

            // Add waypoint using the selected altitude reference frame. Desired survey
            // yaw is encoded in param4 to avoid alternating CONDITION_YAW items.
            missionItems.add(
                buildItem(
                    frame = waypointFrame,
                    command = MavCmd.MAV_CMD_NAV_WAYPOINT,
                    currentFlag = 0,
                    p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = missionYawDegrees,
                    x = wp.latitude.toE7(),
                    y = wp.longitude.toE7(),
                    z = alt
                )
            )

            // After first mission waypoint added: enable sprayer on Servo 5.
            if (i == 0) {
                missionItems.add(
                    buildItem(
                        frame = commandFrame,
                        command = MavCmd.MAV_CMD_DO_SET_SERVO,
                        currentFlag = 0,
                        p1 = 5.0f, p2 = sprayerIntensityPWM, p3 = 0.0f, p4 = 0.0f,
                        x = 0, y = 0, z = 0.0f
                    )
                )
            }
        }

        // Disable sprayer on Servo 5.
        missionItems.add(
            buildItem(
                frame = commandFrame,
                command = MavCmd.MAV_CMD_DO_SET_SERVO,
                currentFlag = 0,
                p1 = 5.0f, p2 = 1000.0f, p3 = 0.0f, p4 = 0.0f,
                x = 0, y = 0, z = 0.0f
            )
        )

        // RTL - NAV commands MUST use a positional frame, not MISSION
        missionItems.add(
            buildItem(
                frame = MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT,
                command = MavCmd.MAV_CMD_NAV_RETURN_TO_LAUNCH,
                currentFlag = 0,
                p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = 0.0f,
                x = 0, y = 0, z = 0.0f
            )
        )

        missionItems.forEach { item ->
            logInfo(
                "setupMission",
                "seq: ${item.seq()}  frame: ${item.frame()}  command: ${item.command()}  " +
                        "current: ${item.current()}  auto continue: ${item.autocontinue()}  " +
                        "param1: ${item.param1()}  param2: ${item.param2()}  param3: ${item.param3()}  " +
                        "param4: ${item.param4()}  x: ${item.x()}  y: ${item.y()}  z: ${item.z()}  " +
                        "missionType: ${item.missionType()}  "
            )
        }

        return missionItems
    }

    fun buildSurveyAreaMission(
        waypoints: ArrayList<LatLng>,
        currentPos: Location,
        alt: Float,
        flightSpeed: Float,
        angleProgress: Float,
        targetSystemId: Int,
        targetComponentId: Int,
        altitudeReferenceMode: AltitudeReferenceMode = AltitudeReferenceMode.RELATIVE
    ): ArrayList<MissionItemInt> {
        val missionItems = ArrayList<MissionItemInt>()
        val waypointFrame = missionWaypointFrameFor(altitudeReferenceMode)
        val commandFrame = MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT
        val missionYawDegrees = 90.0f - angleProgress
        var seq = 0

        fun nextSeq() = seq++

        fun buildItem(
            frame: MavFrame,
            command: MavCmd,
            currentFlag: Int,
            p1: Float, p2: Float, p3: Float, p4: Float,
            x: Int, y: Int, z: Float
        ): MissionItemInt =
            MissionItemInt.builder().apply {
                seq(nextSeq())
                frame(frame)
                command(command)
                current(currentFlag)
                autocontinue(1)
                param1(p1)
                param2(p2)
                param3(p3)
                param4(p4)
                x(x)
                y(y)
                z(z)
                missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                targetSystem(targetSystemId)
                targetComponent(targetComponentId)
            }.build()

        missionItems.add(
            buildItem(
                frame = MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT,
                command = MavCmd.MAV_CMD_NAV_TAKEOFF,
                currentFlag = 1,
                p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = 0.0f,
                x = currentPos.latitude.toE7(),
                y = currentPos.longitude.toE7(),
                z = alt
            )
        )

        if (waypoints.isNotEmpty()) {
            missionItems.add(
                buildItem(
                    frame = commandFrame,
                    command = MavCmd.MAV_CMD_DO_CHANGE_SPEED,
                    currentFlag = 0,
                    p1 = 1.0f, p2 = flightSpeed, p3 = 0.0f, p4 = 0.0f,
                    x = 0, y = 0, z = 0.0f
                )
            )
        }

        waypoints.forEach { wp ->
            missionItems.add(
                buildItem(
                    frame = waypointFrame,
                    command = MavCmd.MAV_CMD_NAV_WAYPOINT,
                    currentFlag = 0,
                    p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = missionYawDegrees,
                    x = wp.latitude.toE7(),
                    y = wp.longitude.toE7(),
                    z = alt
                )
            )
        }

        missionItems.add(
            buildItem(
                frame = MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT,
                command = MavCmd.MAV_CMD_NAV_RETURN_TO_LAUNCH,
                currentFlag = 0,
                p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = 0.0f,
                x = 0, y = 0, z = 0.0f
            )
        )

        return missionItems
    }

    fun buildPointRouteMission(
        routeWaypoints: List<RouteWaypoint>,
        currentPos: Location,
        targetSystemId: Int,
        targetComponentId: Int,
        altitudeReferenceMode: AltitudeReferenceMode = AltitudeReferenceMode.RELATIVE
    ): ArrayList<MissionItemInt> = buildPointRouteMission(
        routeWaypoints = routeWaypoints,
        currentLatitude = currentPos.latitude,
        currentLongitude = currentPos.longitude,
        targetSystemId = targetSystemId,
        targetComponentId = targetComponentId,
        altitudeReferenceMode = altitudeReferenceMode
    )

    fun buildPointRouteMission(
        routeWaypoints: List<RouteWaypoint>,
        currentLatitude: Double,
        currentLongitude: Double,
        targetSystemId: Int,
        targetComponentId: Int,
        altitudeReferenceMode: AltitudeReferenceMode = AltitudeReferenceMode.RELATIVE
    ): ArrayList<MissionItemInt> {
        val missionItems = ArrayList<MissionItemInt>()
        val waypointFrame = missionWaypointFrameFor(altitudeReferenceMode)
        val commandFrame = MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT
        var seq = 0

        logInfo(
            "MissionUpload",
            "Uploading point route mission: altitudeReference=$altitudeReferenceMode, " +
                "frame=${waypointFrame.name}, waypoints=${routeWaypoints.size}"
        )

        fun nextSeq() = seq++

        fun buildItem(
            frame: MavFrame,
            command: MavCmd,
            currentFlag: Int,
            p1: Float, p2: Float, p3: Float, p4: Float,
            x: Int, y: Int, z: Float
        ): MissionItemInt =
            MissionItemInt.builder().apply {
                seq(nextSeq())
                frame(frame)
                command(command)
                current(currentFlag)
                autocontinue(1)
                param1(p1)
                param2(p2)
                param3(p3)
                param4(p4)
                x(x)
                y(y)
                z(z)
                missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                targetSystem(targetSystemId)
                targetComponent(targetComponentId)
            }.build()

        val firstAltitude = routeWaypoints.firstOrNull()?.altitudeMeters?.toFloat() ?: 2.0f

        missionItems.add(
            buildItem(
                frame = MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT,
                command = MavCmd.MAV_CMD_NAV_TAKEOFF,
                currentFlag = 1,
                p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = 0.0f,
                x = currentLatitude.toE7(),
                y = currentLongitude.toE7(),
                z = firstAltitude
            )
        )

        var activeSpeed: Double? = null
        var sprayerOn = false
        var activeSprayerPwm: Float? = null
        routeWaypoints.forEach { waypoint ->
            if (activeSpeed == null || kotlin.math.abs(activeSpeed!! - waypoint.speedMetersPerSecond) > 0.01) {
                activeSpeed = waypoint.speedMetersPerSecond
                missionItems.add(
                    buildItem(
                        frame = commandFrame,
                        command = MavCmd.MAV_CMD_DO_CHANGE_SPEED,
                        currentFlag = 0,
                        p1 = 1.0f, p2 = waypoint.speedMetersPerSecond.toFloat(), p3 = 0.0f, p4 = 0.0f,
                        x = 0, y = 0, z = 0.0f
                    )
                )
            }

            val waypointSprayerPwm = servo5PwmForSprayerIntensity(waypoint.sprayerIntensityPercent)
            if (
                waypoint.sprayEnabled &&
                (!sprayerOn || activeSprayerPwm == null || kotlin.math.abs(activeSprayerPwm!! - waypointSprayerPwm) > 0.01f)
            ) {
                missionItems.add(
                    buildItem(
                        frame = commandFrame,
                        command = MavCmd.MAV_CMD_DO_SET_SERVO,
                        currentFlag = 0,
                        p1 = 5.0f,
                        p2 = waypointSprayerPwm,
                        p3 = 0.0f,
                        p4 = 0.0f,
                        x = 0,
                        y = 0,
                        z = 0.0f
                    )
                )
                sprayerOn = true
                activeSprayerPwm = waypointSprayerPwm
            } else if (!waypoint.sprayEnabled && sprayerOn) {
                missionItems.add(
                    buildItem(
                        frame = commandFrame,
                        command = MavCmd.MAV_CMD_DO_SET_SERVO,
                        currentFlag = 0,
                        p1 = 5.0f, p2 = 1000.0f, p3 = 0.0f, p4 = 0.0f,
                        x = 0, y = 0, z = 0.0f
                    )
                )
                sprayerOn = false
                activeSprayerPwm = null
            }

            missionItems.add(
                buildItem(
                    frame = waypointFrame,
                    command = MavCmd.MAV_CMD_NAV_WAYPOINT,
                    currentFlag = 0,
                    p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = 0.0f,
                    x = waypoint.latitude.toE7(),
                    y = waypoint.longitude.toE7(),
                    z = waypoint.altitudeMeters.toFloat()
                )
            )
        }

        if (sprayerOn) {
            missionItems.add(
                buildItem(
                    frame = commandFrame,
                    command = MavCmd.MAV_CMD_DO_SET_SERVO,
                    currentFlag = 0,
                    p1 = 5.0f, p2 = 1000.0f, p3 = 0.0f, p4 = 0.0f,
                    x = 0, y = 0, z = 0.0f
                )
            )
        }

        missionItems.add(
            buildItem(
                frame = MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT,
                command = MavCmd.MAV_CMD_NAV_RETURN_TO_LAUNCH,
                currentFlag = 0,
                p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = 0.0f,
                x = 0, y = 0, z = 0.0f
            )
        )

        return missionItems
    }

    fun buildMinimalTestMission(
        currentLatitude: Double,
        currentLongitude: Double,
        altitudeMeters: Float,
        targetSystemId: Int,
        targetComponentId: Int
    ): ArrayList<MissionItemInt> {
        val missionItems = ArrayList<MissionItemInt>()
        var seq = 0

        fun nextSeq() = seq++

        fun buildItem(
            command: MavCmd,
            currentFlag: Int,
            p1: Float, p2: Float, p3: Float, p4: Float,
            x: Int, y: Int, z: Float,
            frame: MavFrame = MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT
        ): MissionItemInt =
            MissionItemInt.builder().apply {
                seq(nextSeq())
                frame(frame)
                command(command)
                current(currentFlag)
                autocontinue(1)
                param1(p1)
                param2(p2)
                param3(p3)
                param4(p4)
                x(x)
                y(y)
                z(z)
                missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                targetSystem(targetSystemId)
                targetComponent(targetComponentId)
            }.build()

        missionItems.add(
            buildItem(
                command = MavCmd.MAV_CMD_NAV_TAKEOFF,
                currentFlag = 1,
                p1 = 0f, p2 = 0f, p3 = 0f, p4 = 0.0f,
                x = currentLatitude.toE7(),
                y = currentLongitude.toE7(),
                z = altitudeMeters
            )
        )
        missionItems.add(
            buildItem(
                command = MavCmd.MAV_CMD_NAV_WAYPOINT,
                currentFlag = 0,
                p1 = 0f, p2 = 0f, p3 = 0f, p4 = 0.0f,
                x = (currentLatitude + 0.0001).toE7(),
                y = currentLongitude.toE7(),
                z = altitudeMeters
            )
        )
        missionItems.add(
            buildItem(
                command = MavCmd.MAV_CMD_NAV_WAYPOINT,
                currentFlag = 0,
                p1 = 0f, p2 = 0f, p3 = 0f, p4 = 0.0f,
                x = (currentLatitude + 0.0001).toE7(),
                y = (currentLongitude + 0.0001).toE7(),
                z = altitudeMeters
            )
        )
        missionItems.add(
            buildItem(
                command = MavCmd.MAV_CMD_NAV_RETURN_TO_LAUNCH,
                currentFlag = 0,
                p1 = 0f, p2 = 0f, p3 = 0f, p4 = 0f,
                x = 0,
                y = 0,
                z = 0f,
                frame = MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT
            )
        )

        return missionItems
    }

    private fun Double.toE7(): Int = (this * 1e7).toInt()

    fun missionWaypointFrameFor(mode: AltitudeReferenceMode): MavFrame {
        return when (mode) {
            AltitudeReferenceMode.RELATIVE -> MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT
            AltitudeReferenceMode.TERRAIN -> MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT_INT
        }
    }

    fun servo5PwmForSprayerIntensity(sprayerIntensity: Int): Float {
        val closedPwm = 1000.0F
        val maxPwm = 2200.0F
        val percent = sprayerIntensity.coerceIn(0, 100) / 100.0F
        return closedPwm + ((maxPwm - closedPwm) * percent)
    }

    private fun logInfo(tag: String, message: String) {
        runCatching { Log.i(tag, message) }
    }
}
