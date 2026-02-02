package com.example.droneservicesapp.mavlink

import android.location.Location
import android.util.Log
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
        targetComponentId: Int
    ): ArrayList<MissionItemInt> {

        val min = 1000.0F
        val max = 2000.0F
        val sprayerIntensityPWM = ((max - min) * (sprayerIntensity / 100.0F)) + min

        val missionItems = ArrayList<MissionItemInt>()
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

        // TAKEOFF (placeholder item)
        missionItems.add(
            buildItem(
                frame = MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT,
                command = MavCmd.MAV_CMD_NAV_TAKEOFF,
                currentFlag = 1,
                p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = Float.NaN,
                x = 0, y = 0, z = alt
            )
        )

        // TAKEOFF to current position
        missionItems.add(
            buildItem(
                frame = MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT,
                command = MavCmd.MAV_CMD_NAV_TAKEOFF,
                currentFlag = 0,
                p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = Float.NaN,
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
                        frame = MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT,
                        command = MavCmd.MAV_CMD_DO_CHANGE_SPEED,
                        currentFlag = 0,
                        p1 = 1.0f, p2 = flightSpeed, p3 = 0.0f, p4 = 0.0f,
                        x = 0, y = 0, z = 0.0f
                    )
                )
            }

            // Set steady heading for each mission waypoint
            missionItems.add(
                buildItem(
                    frame = MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT,
                    command = MavCmd.MAV_CMD_CONDITION_YAW,
                    currentFlag = 0,
                    p1 = 90.0f - angleProgress, p2 = 0.0f, p3 = 0.0f, p4 = 0.0f,
                    x = 0, y = 0, z = 0.0f
                )
            )

            // Add relative waypoint to mission
            missionItems.add(
                buildItem(
                    frame = MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT,
                    command = MavCmd.MAV_CMD_NAV_WAYPOINT,
                    currentFlag = 0,
                    p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = Float.NaN,
                    x = wp.latitude.toE7(),
                    y = wp.longitude.toE7(),
                    z = alt
                )
            )

            // After first mission waypoint added: enable sprayer + servo
            if (i == 0) {
                // Sprayer ON
                missionItems.add(
                    buildItem(
                        frame = MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT,
                        command = MavCmd.MAV_CMD_DO_SPRAYER,
                        currentFlag = 0,
                        p1 = 1.0f, p2 = 0.0f, p3 = 0.0f, p4 = 0.0f,
                        x = 0, y = 0, z = 0.0f
                    )
                )

                // Servo ON
                missionItems.add(
                    buildItem(
                        frame = MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT,
                        command = MavCmd.MAV_CMD_DO_SET_SERVO,
                        currentFlag = 0,
                        p1 = 5.0f, p2 = sprayerIntensityPWM, p3 = 0.0f, p4 = 0.0f,
                        x = 0, y = 0, z = 0.0f
                    )
                )
            }
        }

        // Sprayer OFF
        missionItems.add(
            buildItem(
                frame = MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT,
                command = MavCmd.MAV_CMD_DO_SPRAYER,
                currentFlag = 0,
                p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = 0.0f,
                x = 0, y = 0, z = 0.0f
            )
        )

        // Servo OFF
        missionItems.add(
            buildItem(
                frame = MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT,
                command = MavCmd.MAV_CMD_DO_SET_SERVO,
                currentFlag = 0,
                p1 = 5.0f, p2 = 1000.0f, p3 = 0.0f, p4 = 0.0f,
                x = 0, y = 0, z = 0.0f
            )
        )

        // RTL
        missionItems.add(
            buildItem(
                frame = MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT,
                command = MavCmd.MAV_CMD_NAV_RETURN_TO_LAUNCH,
                currentFlag = 0,
                p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = 0.0f,
                x = 0, y = 0, z = 0.0f
            )
        )

        missionItems.forEach { item ->
            Log.i(
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

    private fun Double.toE7(): Int = (this * 1e7).toInt()
}
