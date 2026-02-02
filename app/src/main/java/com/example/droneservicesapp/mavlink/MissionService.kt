package com.example.droneservicesapp.mavlink

import android.util.Log
import io.dronefleet.mavlink.common.MavMissionResult
import io.dronefleet.mavlink.common.MavMissionType
import io.dronefleet.mavlink.common.MissionAck
import io.dronefleet.mavlink.common.MissionClearAll
import io.dronefleet.mavlink.common.MissionCount
import io.dronefleet.mavlink.common.MissionItemInt
import io.dronefleet.mavlink.common.MissionRequest
import io.dronefleet.mavlink.common.MissionRequestInt
import io.dronefleet.mavlink.common.MissionRequestList
import io.dronefleet.mavlink.util.EnumValue

class MissionService(
    private val repo: MavlinkRepository,
    private val gcsSystemId: Int = 254,
    private val gcsComponentId: Int = 99
) {

    // These will be set once we lock onto autopilot ids
    @Volatile
    var targetSystemId: Int = 1
    @Volatile
    var targetComponentId: Int = 1

    fun downloadMission(timeoutMs: Long = 1200L): ArrayList<MissionItemInt> {

        val reqList = MissionRequestList.builder()
            .targetSystem(targetSystemId)
            .targetComponent(targetComponentId)
            .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
            .build()

        var countMsg: io.dronefleet.mavlink.MavlinkMessage<MissionCount>? = null

        repeat(5) {
            repo.send2(gcsSystemId, gcsComponentId, reqList)

            countMsg = repo.waitFor(
                MissionCount::class.java,
                timeoutMs
            ) { m ->
                val p = m.payload as MissionCount
                // accept responses that are broadcast (0) or targeted to our GCS ids
                (p.targetSystem() == 0 || p.targetSystem() == gcsSystemId) &&
                        (p.targetComponent() == 0 || p.targetComponent() == gcsComponentId)
            }


            if (countMsg != null) return@repeat
        }

        if (countMsg == null) {
            Log.e("MissionService", "No MissionCount received after retries")
            return ArrayList()
        }

        val count = countMsg!!.payload.count()
        if (count <= 0) return ArrayList()

        val items = ArrayList<MissionItemInt>(count)

        for (seq in 0 until count) {

            val reqItem = MissionRequestInt.builder()
                .targetSystem(targetSystemId)
                .targetComponent(targetComponentId)
                .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                .seq(seq)
                .build()

            var itemMsg: io.dronefleet.mavlink.MavlinkMessage<MissionItemInt>? = null

            repeat(5) {
                repo.send2(gcsSystemId, gcsComponentId, reqItem)

                itemMsg = repo.waitFor(
                    MissionItemInt::class.java,
                    timeoutMs
                ) { m ->
                    val p = m.payload as MissionItemInt
                    (p.targetSystem() == 0 || p.targetSystem() == gcsSystemId) &&
                            (p.targetComponent() == 0 || p.targetComponent() == gcsComponentId) &&
                            p.seq() == seq
                }


                if (itemMsg != null) return@repeat
            }

            if (itemMsg == null) {
                Log.e("MissionService", "Missing MissionItemInt seq=$seq")
                return ArrayList()
            }

            items.add(itemMsg!!.payload)
        }

        val ack = MissionAck.builder()
            .targetSystem(targetSystemId)
            .targetComponent(targetComponentId)
            .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
            .type(EnumValue.of(MavMissionResult.MAV_MISSION_ACCEPTED))
            .build()

        repo.send2(gcsSystemId, gcsComponentId, ack)
        return items
    }


    fun uploadMission(items: ArrayList<MissionItemInt>, timeoutMs: Long = 1200L): Boolean {
        val count = MissionCount.builder()
            .targetSystem(targetSystemId)
            .targetComponent(targetComponentId)
            .count(items.size)
            .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
            .build()

        repo.send2(gcsSystemId, gcsComponentId, count)
        Log.i("MissionUpload", "TX MissionCount count=${items.size}")

        var ackRetries = 0

        while (true) {
            // Wait for MissionRequestInt
            val reqInt = repo.waitFor(MissionRequestInt::class.java, timeoutMs) { m ->
                val p = m.payload as MissionRequestInt
                (p.targetSystem() == 0 || p.targetSystem() == gcsSystemId) &&
                        (p.targetComponent() == 0 || p.targetComponent() == gcsComponentId)
            }

            // If we got a MissionRequestInt, handle it and loop (don't wait for ack yet)
            if (reqInt != null) {
                val seq = reqInt.payload.seq()
                Log.i("MissionUpload", "RX MissionRequestInt seq=$seq")
                if (seq in items.indices) {
                    repo.send2(gcsSystemId, gcsComponentId, items[seq])
                }
                continue
            }

            // Wait for MissionRequest (non-int variant)
            val req = repo.waitFor(MissionRequest::class.java, timeoutMs) { m ->
                val p = m.payload as MissionRequest
                (p.targetSystem() == 0 || p.targetSystem() == gcsSystemId) &&
                        (p.targetComponent() == 0 || p.targetComponent() == gcsComponentId)
            }

            // If we got a MissionRequest, handle it the same way using seq field
            if (req != null) {
                val seq = req.payload.seq()
                Log.i("MissionUpload", "RX MissionRequest seq=$seq")
                if (seq in items.indices) {
                    repo.send2(gcsSystemId, gcsComponentId, items[seq])
                }
                continue
            }

            // Timeout on both request types: check for MissionAck
            val ack = repo.waitFor(MissionAck::class.java, timeoutMs) { m ->
                val p = m.payload as MissionAck
                (p.targetSystem() == 0 || p.targetSystem() == gcsSystemId) &&
                        (p.targetComponent() == 0 || p.targetComponent() == gcsComponentId)
            }

            ack?.let {
                val type = it.payload.type().entry()
                Log.i("MissionUpload", "RX MissionAck type=$type")
                return type == MavMissionResult.MAV_MISSION_ACCEPTED
            }

            // timeout on both: resend count a few times
            Log.e("MissionUpload", "TIMEOUT waiting for request/ack (attempt=${ackRetries + 1})")
            if (ackRetries >= 5) return false
            ackRetries++
            repo.send2(gcsSystemId, gcsComponentId, count)
            Log.i("MissionUpload", "TX MissionCount -> sys=$targetSystemId comp=$targetComponentId count=${items.size}")
        }
    }

    fun clearMission(timeoutMs: Long = 1200L): Boolean {
        val clear = MissionClearAll.builder()
            .targetSystem(targetSystemId)
            .targetComponent(targetComponentId)
            .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
            .build()

        repo.send2(gcsSystemId, gcsComponentId, clear)
        Log.i("MissionUpload", "TX MissionClearAll")

        val ack = repo.waitFor(MissionAck::class.java, timeoutMs) { m ->
            val p = m.payload as MissionAck
            (p.targetSystem() == 0 || p.targetSystem() == gcsSystemId) &&
                    (p.targetComponent() == 0 || p.targetComponent() == gcsComponentId)
        } ?: return false
        
        val type = ack.payload.type().entry()
        Log.i("MissionUpload", "RX MissionAck type=$type")
        return type == MavMissionResult.MAV_MISSION_ACCEPTED
    }
}
