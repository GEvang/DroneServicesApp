package com.example.droneservicesapp.mavlink

import android.util.Log
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.MavFrame
import io.dronefleet.mavlink.common.MavMissionResult
import io.dronefleet.mavlink.common.MavMissionType
import io.dronefleet.mavlink.common.MissionAck
import io.dronefleet.mavlink.common.MissionClearAll
import io.dronefleet.mavlink.common.MissionCount
import io.dronefleet.mavlink.common.MissionItem
import io.dronefleet.mavlink.common.MissionItemInt
import io.dronefleet.mavlink.common.MissionRequest
import io.dronefleet.mavlink.common.MissionRequestInt
import io.dronefleet.mavlink.common.MissionRequestList
import io.dronefleet.mavlink.util.EnumValue

/**
 * Robust mission protocol implementation.
 *
 * Goal: Upload missions reliably even when QGroundControl is connected.
 *
 * Strategy:
 *  - Only react to mission REQUESTS that are explicitly targeted to *this GCS* (gcsSystemId/gcsComponentId).
 *    This prevents "two GCS writers" when QGC is on the same MAVLink link.
 *  - Respond with the *matching* item type:
 *      - MissionRequestInt -> MissionItemInt
 *      - MissionRequest    -> MissionItem
 *  - For final completion, accept MissionAck by sender header (autopilot sysid/compid), because payload target fields
 *    are not always consistent.
 */
class MissionService(
    private val repo: MavlinkRepository,
    private val gcsSystemId: Int = 254,
    private val gcsComponentId: Int = 99
) {
    @Volatile var targetSystemId: Int = 1
    @Volatile var targetComponentId: Int = 1

    private fun isTargetedToThisGcs(targetSys: Int, targetComp: Int): Boolean {
        return targetSys == gcsSystemId && targetComp == gcsComponentId
    }

    private fun itemWithTargetsAndSeq(src: MissionItemInt, seq: Int): MissionItemInt {
        val isNavCommand = src.command().entry().name.startsWith("MAV_CMD_NAV")

        val frame = if (isNavCommand) {
            src.frame()
        } else {
            EnumValue.of(MavFrame.MAV_FRAME_MISSION)
        }

        return MissionItemInt.builder()
            .targetSystem(targetSystemId)
            .targetComponent(targetComponentId)
            .seq(seq)
            .frame(frame)
            .command(src.command())
            .current(if (seq == 0) 1 else 0)
            .autocontinue(1)
            .param1(src.param1())
            .param2(src.param2())
            .param3(src.param3())
            .param4(src.param4())
            .x(if (isNavCommand) src.x() else 0)
            .y(if (isNavCommand) src.y() else 0)
            .z(if (isNavCommand) src.z() else 0f)
            .missionType(EnumValue.of(MavMissionType.MAV_MISSION_TYPE_MISSION))
            .build()
    }

    private fun itemIntToItemWithTargetsAndSeq(src: MissionItemInt, seq: Int): MissionItem {
        val isNavCommand = src.command().entry().name.startsWith("MAV_CMD_NAV")

        val frame = if (isNavCommand) {
            src.frame()
        } else {
            EnumValue.of(MavFrame.MAV_FRAME_MISSION)
        }

        // MISSION_ITEM uses float lat/lon degrees. NAV items encode in INT as degrees*1e7.
        val x = if (isNavCommand) src.x() / 1e7f else 0f
        val y = if (isNavCommand) src.y() / 1e7f else 0f
        val z = if (isNavCommand) src.z() else 0f

        return MissionItem.builder()
            .targetSystem(targetSystemId)
            .targetComponent(targetComponentId)
            .seq(seq)
            .frame(frame)
            .command(src.command())
            .current(if (seq == 0) 1 else 0)
            .autocontinue(1)
            .param1(src.param1())
            .param2(src.param2())
            .param3(src.param3())
            .param4(src.param4())
            .x(x)
            .y(y)
            .z(z)
            .missionType(EnumValue.of(MavMissionType.MAV_MISSION_TYPE_MISSION))
            .build()
    }

    fun clearMission(timeoutMs: Long = 1200L): Boolean {
        val clear = MissionClearAll.builder()
            .targetSystem(targetSystemId)
            .targetComponent(targetComponentId)
            .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
            .build()

        repo.send2(gcsSystemId, gcsComponentId, clear)
        Log.i("MissionUpload", "TX MissionClearAll")

        val ack = waitForMissionAckFromAutopilot(timeoutMs) ?: return false
        val res = ack.payload.type().entry()
        Log.i("MissionUpload", "RX MissionAck type=$res")
        return res == MavMissionResult.MAV_MISSION_ACCEPTED
    }

    fun downloadMission(timeoutMs: Long = 1500L): ArrayList<MissionItemInt> {
        val reqList = MissionRequestList.builder()
            .targetSystem(targetSystemId)
            .targetComponent(targetComponentId)
            .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
            .build()

        var countMsg: MavlinkMessage<MissionCount>? = null
        repeat(5) {
            repo.send2(gcsSystemId, gcsComponentId, reqList)

            countMsg = repo.waitFor(MissionCount::class.java, timeoutMs) { m ->
                val p = m.payload as MissionCount
                // MissionCount targeting is not always strict; accept from autopilot sender.
                m.originSystemId == targetSystemId && m.originComponentId == targetComponentId &&
                        p.missionType().entry() == MavMissionType.MAV_MISSION_TYPE_MISSION
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

            var itemMsg: MavlinkMessage<MissionItemInt>? = null
            repeat(5) {
                repo.send2(gcsSystemId, gcsComponentId, reqItem)

                itemMsg = repo.waitFor(MissionItemInt::class.java, timeoutMs) { m ->
                    val p = m.payload as MissionItemInt
                    m.originSystemId == targetSystemId && m.originComponentId == targetComponentId &&
                            p.seq() == seq &&
                            p.missionType().entry() == MavMissionType.MAV_MISSION_TYPE_MISSION
                }
                if (itemMsg != null) return@repeat
            }

            if (itemMsg == null) {
                Log.e("MissionService", "Missing MissionItemInt seq=$seq")
                return ArrayList()
            }

            items.add(itemMsg!!.payload)
        }

        return items
    }

    /**
     * Upload mission items.
     *
     * Designed to work while QGC is connected:
     *  - We ONLY respond to MissionRequest(Int) targeted to our GCS IDs (254/99).
     *  - We respond with matching message type:
     *      - MissionRequestInt -> MissionItemInt
     *      - MissionRequest    -> MissionItem
     *  - After last item, we do NOT resend MissionCount; we only wait for MissionAck.
     */
    fun uploadMission(items: ArrayList<MissionItemInt>, timeoutMs: Long = 2000L): Boolean {
        if (items.isEmpty()) {
            Log.w("MissionUpload", "uploadMission called with 0 items; treating as success")
            return true
        }

        val lastSeq = items.size - 1
        var lastSentSeq: Int? = null

        val countMsg = MissionCount.builder()
            .targetSystem(targetSystemId)
            .targetComponent(targetComponentId)
            .count(items.size)
            .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
            .build()

        Log.i("MissionUpload", "TX MissionCount count=${items.size}")
        repo.send2(gcsSystemId, gcsComponentId, countMsg)

        var resendCountAttempts = 0
        var ackWaitAttempts = 0

        while (true) {

            if (lastSentSeq == lastSeq) {
                val ack = waitForMissionAckFromAutopilot(timeoutMs)
                if (ack != null) {
                    val res = ack.payload.type().entry()
                    Log.i(
                        "MissionUpload",
                        "RX MISSION_ACK from sys=${ack.originSystemId} comp=${ack.originComponentId} type=$res"
                    )
                    return res == MavMissionResult.MAV_MISSION_ACCEPTED
                }

                ackWaitAttempts++
                Log.w("MissionUpload", "TIMEOUT after last item sent; waiting for MissionAck (attempt=$ackWaitAttempts/12)")
                if (ackWaitAttempts >= 12) {
                    Log.e("MissionUpload", "Giving up waiting for MissionAck after sending last item")
                    return false
                }
                continue
            }


            // 1) Preferred: MissionRequestInt, but ONLY if targeted to our GCS IDs (prevents QGC interference)
            val reqInt = repo.waitFor(MissionRequestInt::class.java, timeoutMs) { m ->
                val p = m.payload as MissionRequestInt
                isTargetedToThisGcs(p.targetSystem(), p.targetComponent()) &&
                        p.missionType().entry() == MavMissionType.MAV_MISSION_TYPE_MISSION
            }

            if (reqInt != null) {
                val seq = reqInt.payload.seq()
                Log.i("MissionUpload", "RX MISSION_REQUEST_INT seq=$seq")

                if (seq in items.indices) {
                    val out = itemWithTargetsAndSeq(items[seq], seq)
                    Log.i(
                        "MissionUpload",
                        "TX MISSION_ITEM_INT seq=$seq cmd=${out.command().entry().name} frame=${out.frame().entry().name}"
                    )
                    repo.send2(gcsSystemId, gcsComponentId, out)
                    lastSentSeq = seq
                } else {
                    Log.e("MissionUpload", "Requested seq out of range: $seq size=${items.size}")
                }
                continue
            }

            // 2) Legacy: MissionRequest (MISSION_ITEM). Also require strict targeting to our GCS IDs.
            val reqLegacy = repo.waitFor(MissionRequest::class.java, timeoutMs) { m ->
                val p = m.payload as MissionRequest
                isTargetedToThisGcs(p.targetSystem(), p.targetComponent()) &&
                        p.missionType().entry() == MavMissionType.MAV_MISSION_TYPE_MISSION
            }

            if (reqLegacy != null) {
                val seq = reqLegacy.payload.seq()
                Log.w("MissionUpload", "RX MISSION_REQUEST seq=$seq")

                if (seq in items.indices) {
                    val out = itemIntToItemWithTargetsAndSeq(items[seq], seq)
                    Log.i(
                        "MissionUpload",
                        "TX MISSION_ITEM seq=$seq cmd=${out.command().entry().name} frame=${out.frame().entry().name}"
                    )
                    repo.send2(gcsSystemId, gcsComponentId, out)
                    lastSentSeq = seq

                    // ✅ NEW: if we just sent the last item, immediately wait for ACK
                    if (seq == lastSeq) {
                        val ack = waitForMissionAckFromAutopilot(timeoutMs)
                        if (ack != null) {
                            val res = ack.payload.type().entry()
                            Log.i(
                                "MissionUpload",
                                "RX MISSION_ACK from sys=${ack.originSystemId} comp=${ack.originComponentId} type=$res"
                            )
                            return res == MavMissionResult.MAV_MISSION_ACCEPTED
                        }
                        // If we didn’t see ACK here, fall through to the loop and retry waiting.
                        Log.w("MissionUpload", "No immediate MISSION_ACK after last item; will continue waiting")
                    }
                } else {
                    Log.e("MissionUpload", "Legacy requested seq out of range: $seq size=${items.size}")
                }
                continue
            }


            // 3) Check for ACK (match by sender header = autopilot ids)
            val ack = waitForMissionAckFromAutopilot(timeoutMs)
            if (ack != null) {
                val res = ack.payload.type().entry()
                //Log.i("MissionUpload", "RX MISSION_ACK type=$res")
                Log.i("MissionUpload", "RX MISSION_ACK from sys=${ack.originSystemId} comp=${ack.originComponentId} type=${ack.payload.type()}")
                return res == MavMissionResult.MAV_MISSION_ACCEPTED
            }

            // 4) Timeout handling
            if (lastSentSeq == lastSeq) {
                // We already sent the last item; do NOT reset with MissionCount.
                ackWaitAttempts++
                Log.w(
                    "MissionUpload",
                    "TIMEOUT after last item sent; waiting for MissionAck (attempt=$ackWaitAttempts/12)"
                )
                if (ackWaitAttempts >= 12) {
                    Log.e("MissionUpload", "Giving up waiting for MissionAck after sending last item")
                    return false
                }
                continue
            }

            // Not at last item yet; retry by resending MissionCount a few times.
            resendCountAttempts++
            Log.e("MissionUpload", "TIMEOUT waiting for request/ack before last item (attempt=$resendCountAttempts/6)")
            if (resendCountAttempts >= 6) return false

            Log.i("MissionUpload", "TX MissionCount retry -> sys=$targetSystemId comp=$targetComponentId count=${items.size}")
            repo.send2(gcsSystemId, gcsComponentId, countMsg)
        }
    }

    private fun waitForMissionAckFromAutopilot(timeoutMs: Long): MavlinkMessage<MissionAck>? {
        return repo.waitFor(MissionAck::class.java, timeoutMs) { m ->
            // ArduPilot ACK fields can be inconsistent; sender sysid is the reliable discriminator.
            m.originSystemId == targetSystemId
        }
    }

}
