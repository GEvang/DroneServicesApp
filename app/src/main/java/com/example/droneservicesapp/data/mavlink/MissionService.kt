package com.example.droneservicesapp.data.mavlink

import android.util.Log
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.MavCmd
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
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

sealed class MissionUploadResult {
    object Success : MissionUploadResult()
    data class Failure(val reason: String) : MissionUploadResult()
}

/**
 * Robust mission protocol implementation.
 *
 * Cancellation support:
 *  - Pass a cancel token to uploadMission().
 *  - If token is set, upload exits quickly and returns false.
 */
class MissionService(
    private val client: MavlinkClient,
    private val gcsSystemId: Int = 254,
    private val gcsComponentId: Int = 99
) {
    @Volatile var targetSystemId: Int = 1
    @Volatile var targetComponentId: Int = 1

    private val uploadSessionCounter = AtomicLong(0)

    private fun isTargetedToThisGcs(targetSys: Int, targetComp: Int): Boolean {
        return targetSys == gcsSystemId && targetComp == gcsComponentId
    }

    private fun cancelled(cancel: AtomicBoolean?): Boolean = cancel?.get() == true

    private fun itemWithTargetsAndSeq(src: MissionItemInt, seq: Int): MissionItemInt {
        val isNavCommand = src.command().entry().name.startsWith("MAV_CMD_NAV")

        val frame = if (isNavCommand) {
            src.frame()
        } else {
            EnumValue.of(MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT)
        }

        return MissionItemInt.builder()
            .targetSystem(targetSystemId)
            .targetComponent(targetComponentId)
            .seq(seq)
            .frame(frame)
            .command(src.command())
            .current(src.current())
            .autocontinue(src.autocontinue())  // ← Preserve from source
            .param1(src.param1())
            .param2(src.param2())
            .param3(src.param3())
            .param4(src.param4())
            .x(if (isNavCommand) src.x() else 0)
            .y(if (isNavCommand) src.y() else 0)
            .z(if (isNavCommand) src.z() else 0f)
            .missionType(src.missionType())  // ← Preserve from source
            .build()
    }

    private fun itemIntToItemWithTargetsAndSeq(src: MissionItemInt, seq: Int): MissionItem {
        val coordinateNavCommand = isCoordinateNavCommand(src.command().entry())

        val frame = EnumValue.of(normalizedLegacyFrameForUpload(src))

        // MISSION_ITEM uses float lat/lon degrees. Coordinate NAV items encode in INT as degrees*1e7.
        val x = if (coordinateNavCommand) src.x() / 1e7f else 0f
        val y = if (coordinateNavCommand) src.y() / 1e7f else 0f
        val z = if (coordinateNavCommand) src.z() else 0f

        return MissionItem.builder()
            .targetSystem(targetSystemId)
            .targetComponent(targetComponentId)
            .seq(seq)
            .frame(frame)
            .command(src.command())
            .current(src.current())
            .autocontinue(src.autocontinue())  // ← Preserve from source
            .param1(src.param1())
            .param2(src.param2())
            .param3(src.param3())
            .param4(src.param4())
            .x(x)
            .y(y)
            .z(z)
            .missionType(src.missionType())  // ← Preserve from source
            .build()
    }

    fun clearMission(timeoutMs: Long = 1200L): Boolean {
        val clear = MissionClearAll.builder()
            .targetSystem(targetSystemId)
            .targetComponent(targetComponentId)
            .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
            .build()

        client.send2(gcsSystemId, gcsComponentId, clear)
        Log.i("MissionUpload", "TX MissionClearAll")

        val ack = waitForMissionAckFromAutopilot(timeoutMs, null) ?: return false
        val res = ack.payload.type().entry()
        Log.i("MissionUpload", "RX MissionAck type=$res")
        return res == MavMissionResult.MAV_MISSION_ACCEPTED
    }

    fun downloadMission(timeoutMs: Long = 1500L, cancel: AtomicBoolean? = null): ArrayList<MissionItemInt> {
        if (cancelled(cancel)) return ArrayList()
        val reqList = MissionRequestList.builder()
            .targetSystem(targetSystemId)
            .targetComponent(targetComponentId)
            .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
            .build()

        var countMsg: MavlinkMessage<MissionCount>? = null
        repeat(5) {
            if (cancelled(cancel)) return ArrayList()
            client.send2(gcsSystemId, gcsComponentId, reqList)

            countMsg = client.waitFor(MissionCount::class.java, timeoutMs) { m ->
                val p = m.payload as MissionCount
                m.originSystemId == targetSystemId &&
                        m.originComponentId == targetComponentId &&
                        p.missionType().entry() == MavMissionType.MAV_MISSION_TYPE_MISSION
            }
            if (countMsg != null) return@repeat
        }

        if (countMsg == null) {
            Log.e("MissionService", "No MissionCount received after retries")
            return ArrayList()
        }

        if (cancelled(cancel)) return ArrayList()

        val count = countMsg!!.payload.count()
        if (count <= 0) return ArrayList()

        val items = ArrayList<MissionItemInt>(count)

        for (seq in 0 until count) {
            if (cancelled(cancel)) return ArrayList()
            var item: MissionItemInt? = null

            repeat(5) { attempt ->
                if (cancelled(cancel)) return ArrayList()
                // Prefer the modern request first, but accept either MISSION_ITEM_INT or
                // legacy MISSION_ITEM because some ArduPilot builds stay on the legacy
                // mission-transfer path.
                val reqInt = MissionRequestInt.builder()
                    .targetSystem(targetSystemId)
                    .targetComponent(targetComponentId)
                    .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                    .seq(seq)
                    .build()

                client.send2(gcsSystemId, gcsComponentId, reqInt)
                item = waitForDownloadedMissionItem(seq, timeoutMs, cancel)
                if (item != null) return@repeat

                val reqLegacy = MissionRequest.builder()
                    .targetSystem(targetSystemId)
                    .targetComponent(targetComponentId)
                    .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                    .seq(seq)
                    .build()

                Log.w(
                    "MissionService",
                    "Missing MissionItemInt seq=$seq after request-int attempt=${attempt + 1}; trying legacy MissionRequest"
                )
                client.send2(gcsSystemId, gcsComponentId, reqLegacy)
                item = waitForDownloadedMissionItem(seq, timeoutMs, cancel)
                if (item != null) return@repeat
            }

            if (cancelled(cancel)) return ArrayList()

            if (item == null) {
                Log.e("MissionService", "Missing mission item seq=$seq after INT and legacy retries")
                return ArrayList()
            }

            items.add(item!!)
        }

        return items
    }

    /**
     * Upload mission (event-driven).
     * If [cancel] becomes true, upload will stop and return false.
     *
     * Key behavior:
     *  - Send MissionCount once at start (and possibly retries from watchdog).
     *  - Immediately respond to MissionRequestInt/MissionRequest with the requested item.
     *  - Complete on MissionAck (from targetSystemId).
     *  - Call onProgress with (sentSeq, total, percent) after each item sent.
     *
     * De-duplication behavior:
     *  - Tracks the last processed sequence number to prevent duplicate sends.
     *  - If a request arrives for a sequence <= last processed, it's skipped.
     *  - Legitimate out-of-order requests (seq > last processed) are always handled.
     */
    fun uploadMission(
        items: ArrayList<MissionItemInt>,
        timeoutMs: Long = 4000L,
        cancel: AtomicBoolean? = null,
        onProgress: ((sentSeq: Int, total: Int, percent: Int) -> Unit)? = null
    ): MissionUploadResult {

        if (items.isEmpty()) {
            Log.w("MissionUpload", "uploadMission called with 0 items; treating as success")
            return MissionUploadResult.Success
        }

        validateMissionForUpload(items)?.let { reason ->
            Log.e("MissionUpload", "Mission validation failed: $reason")
            return MissionUploadResult.Failure("Mission validation failed: $reason")
        }

        // Capture target IDs ONCE at start to prevent mid-upload changes
        val uploadTargetSystemId = targetSystemId
        val uploadTargetComponentId = targetComponentId
        val uploadSessionId = uploadSessionCounter.incrementAndGet()
        Log.i(
            "MissionUpload",
            "session=$uploadSessionId start missionCount=${items.size} targetSystem=$uploadTargetSystemId targetComponent=$uploadTargetComponentId"
        )

        val lastSeq = items.size - 1

        val done = AtomicBoolean(false)
        val success = AtomicBoolean(false)
        val failureReason = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)
        val disposables = CompositeDisposable()

        val lastProgressMs = AtomicLong(System.currentTimeMillis())
        val lastSentSeq = AtomicInteger(-1)
        val lastSentItemSummary = AtomicReference<String?>(null)
        // Track progress, but still answer duplicate requests because ArduPilot may re-request
        // an item when the previous response was lost on a noisy link.
        val lastProcessedSeq = AtomicInteger(-1)

        val resendCountAttempts = AtomicInteger(0)
        var requestsStarted = AtomicBoolean(false)

        var lastPercent = -1

        val countMsg = MissionCount.builder()
            .targetSystem(uploadTargetSystemId)
            .targetComponent(uploadTargetComponentId)
            .count(items.size)
            .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
            .build()

        val startMs = System.currentTimeMillis()
        val totalTimeoutMs = maxOf(90_000L, items.size * 1_200L)

        try {
            // ---- MissionRequestInt ----
            disposables.add(
                client.messages()
                    .filter { it.payload is MissionRequestInt }
                    .map {
                        @Suppress("UNCHECKED_CAST")
                        it as MavlinkMessage<MissionRequestInt>
                    }
                    .filter { req ->
                        val p = req.payload
                        p.targetSystem() == gcsSystemId && p.targetComponent() == gcsComponentId &&
                                p.missionType().entry() == MavMissionType.MAV_MISSION_TYPE_MISSION
                    }
                    .subscribe({ req ->
                        if (cancelled(cancel) || done.get()) return@subscribe

                        requestsStarted.set(true)
                        val seq = req.payload.seq()
                        Log.i(
                            "MissionUpload",
                            "RX MISSION_REQUEST_INT seq=$seq from sys=${req.originSystemId} comp=${req.originComponentId}"
                        )

                        if (seq <= lastProcessedSeq.get()) {
                            Log.d(
                                "MissionUpload",
                                "RX duplicate/older MISSION_REQUEST_INT seq=$seq; resending item"
                            )
                        }

                        if (seq in items.indices) {
                            val out = itemWithTargetsAndSeq(items[seq], seq, uploadTargetSystemId, uploadTargetComponentId)
                            client.send2(gcsSystemId, gcsComponentId, out)
                            lastSentItemSummary.set(describeMissionItemInt(out))
                            lastSentSeq.set(seq)
                            lastProcessedSeq.updateAndGet { previous -> maxOf(previous, seq) }
                            // CRITICAL: Reset progress timer after successful send so watchdog doesn't timeout
                            lastProgressMs.set(System.currentTimeMillis())
                            logMissionItemInt(uploadSessionId, "MISSION_REQUEST_INT", seq, "MISSION_ITEM_INT", out)

                            val percent = (((seq + 1).toDouble() / items.size.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                Log.i("MissionUpload", "Progress: seq=$seq/${items.size} ($percent%)")
                                onProgress?.invoke(seq, items.size, percent)
                            }
                        } else {
                            val reason = "MISSION_REQUEST_INT requested seq out of range: seq=$seq size=${items.size}"
                            failureReason.compareAndSet(null, reason)
                            Log.e("MissionUpload", reason)
                        }
                    }, { err ->
                        Log.e("MissionUpload", "MissionRequestInt subscription error: ${err.message}", err)
                    })
            )

            // ---- Legacy MissionRequest ----
            disposables.add(
                client.messages()
                    .filter { it.payload is MissionRequest }
                    .map {
                        @Suppress("UNCHECKED_CAST")
                        it as MavlinkMessage<MissionRequest>
                    }
                    .filter { req ->
                        val p = req.payload
                        p.targetSystem() == gcsSystemId && p.targetComponent() == gcsComponentId &&
                                p.missionType().entry() == MavMissionType.MAV_MISSION_TYPE_MISSION
                    }
                    .subscribe({ req ->
                        if (cancelled(cancel) || done.get()) return@subscribe

                        requestsStarted.set(true)
                        val seq = req.payload.seq()
                        Log.w(
                            "MissionUpload",
                            "RX MISSION_REQUEST seq=$seq from sys=${req.originSystemId} comp=${req.originComponentId}"
                        )

                        if (seq <= lastProcessedSeq.get()) {
                            Log.d(
                                "MissionUpload",
                                "RX duplicate/older MISSION_REQUEST seq=$seq; resending item"
                            )
                        }

                        if (seq in items.indices) {
                            if (SEND_MISSION_ITEM_INT_FOR_LEGACY_REQUEST) {
                                val out = itemWithTargetsAndSeq(items[seq], seq, uploadTargetSystemId, uploadTargetComponentId)
                                client.send2(gcsSystemId, gcsComponentId, out)
                                lastSentItemSummary.set(describeMissionItemInt(out))
                                logMissionItemInt(uploadSessionId, "MISSION_REQUEST", seq, "MISSION_ITEM_INT", out)
                            } else {
                                val out = itemIntToItemWithTargetsAndSeq(items[seq], seq, uploadTargetSystemId, uploadTargetComponentId)
                                client.send2(gcsSystemId, gcsComponentId, out)
                                lastSentItemSummary.set(describeMissionItem(out))
                                Log.i(
                                    "MissionUpload",
                                    "session=$uploadSessionId requestType=MISSION_REQUEST requestedSeq=$seq outgoing=MISSION_ITEM " +
                                            "encodedSeq=${out.seq()} cmd=${out.command().entry().name} frame=${out.frame().entry().name} " +
                                            "current=${out.current()} autocontinue=${out.autocontinue()} missionType=${out.missionType().entry().name} " +
                                            "targetSystem=${out.targetSystem()} targetComponent=${out.targetComponent()} " +
                                            "p1=${out.param1()} p2=${out.param2()} p3=${out.param3()} p4=${out.param4()} " +
                                            "x=${out.x()} y=${out.y()} z=${out.z()}"
                                )
                            }
                            lastSentSeq.set(seq)
                            lastProcessedSeq.updateAndGet { previous -> maxOf(previous, seq) }
                            // CRITICAL: Reset progress timer after successful send so watchdog doesn't timeout
                            lastProgressMs.set(System.currentTimeMillis())

                            val percent = (((seq + 1).toDouble() / items.size.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                Log.i("MissionUpload", "Progress: seq=$seq/${items.size} ($percent%)")
                                onProgress?.invoke(seq, items.size, percent)
                            }
                        } else {
                            val reason = "MISSION_REQUEST requested seq out of range: seq=$seq size=${items.size}"
                            failureReason.compareAndSet(null, reason)
                            Log.e("MissionUpload", reason)
                        }
                    }, { err ->
                        Log.e("MissionUpload", "MissionRequest subscription error: ${err.message}", err)
                    })
            )

            // ---- MissionAck completes upload ----
            disposables.add(
                client.messages()
                    .filter { it.payload is MissionAck }
                    .map {
                        @Suppress("UNCHECKED_CAST")
                        it as MavlinkMessage<MissionAck>
                    }
                    .filter { ack ->
                        // Sender sysid is the most reliable discriminator on ArduPilot links.
                        ack.originSystemId == uploadTargetSystemId
                    }
                    .subscribe({ ack ->
                        if (done.getAndSet(true)) return@subscribe
                        lastProgressMs.set(System.currentTimeMillis())

                        val res = ack.payload.type().entry()
                        Log.i(
                            "MissionUpload",
                            "RX MISSION_ACK type=${ack.payload.type()} from sys=${ack.originSystemId} comp=${ack.originComponentId}"
                        )

                        success.set(res == MavMissionResult.MAV_MISSION_ACCEPTED)
                        if (!success.get()) {
                            val lastItemSummary = lastSentItemSummary.get()
                            if (lastItemSummary != null) {
                                Log.e(
                                    "MissionUpload",
                                    "session=$uploadSessionId lastSentBeforeAck type=${res.name} $lastItemSummary"
                                )
                            } else {
                                Log.e(
                                    "MissionUpload",
                                    "session=$uploadSessionId ACK ${res.name} received before any mission item was recorded as sent"
                                )
                            }
                            failureReason.compareAndSet(null, "Mission rejected by autopilot: ${res.name}")
                        }
                        if (success.get()) {
                            onProgress?.invoke(items.size - 1, items.size, 100)
                        }
                        latch.countDown()
                    }, { err ->
                        Log.e("MissionUpload", "MissionAck subscription error: ${err.message}", err)
                    })
            )

            // ---- Watchdog ----
            disposables.add(
                Observable.interval(timeoutMs, timeoutMs, TimeUnit.MILLISECONDS)
                    .subscribe({
                        if (done.get() || cancelled(cancel)) return@subscribe

                        val now = System.currentTimeMillis()
                        val stalled = (now - lastProgressMs.get()) >= timeoutMs
                        if (!stalled) return@subscribe

                        val sent = lastSentSeq.get()

                        // If we haven't started requests yet AND we haven't sent all items -> resend MissionCount
                        if (!requestsStarted.get() && sent < lastSeq) {
                            val attempts = resendCountAttempts.incrementAndGet()
                            if (attempts <= 6) {
                                Log.w(
                                    "MissionUpload",
                                    "Watchdog: stalled before completion, resending MissionCount (attempt=$attempts/6)"
                                )
                                client.send2(gcsSystemId, gcsComponentId, countMsg)
                                Log.i("MissionUpload", "TX MissionCount count=${items.size} (watchdog resend)")
                                lastProgressMs.set(now)
                            } else {
                                val reason = "Watchdog failed: autopilot did not request mission items after MissionCount"
                                failureReason.compareAndSet(null, reason)
                                Log.e("MissionUpload", reason)
                                if (!done.getAndSet(true)) {
                                    success.set(false)
                                    latch.countDown()
                                }
                            }
                            return@subscribe
                        }

                        // Upload is request-driven: after ArduPilot starts requesting items, do not
                        // resend MissionCount or push mission items without a fresh request. Some links
                        // legitimately pause/re-request; let the total upload timeout make the final call.
                        if (requestsStarted.get() && sent >= 0 && sent < lastSeq) {
                            Log.w(
                                "MissionUpload",
                                "session=$uploadSessionId watchdog: waiting for next autopilot request after seq=$sent; " +
                                        "not sending unsolicited mission data"
                            )
                            lastProgressMs.set(now)
                            return@subscribe
                        }

                        // If we've sent all items but ACK is missing, allow extra time for final ACK,
                        // especially over UDP/Android tethering where delayed packets are common.
                        if (sent == lastSeq) {
                            val elapsedSinceFinalItem = now - lastProgressMs.get()
                            val finalAckTimeoutMs = maxOf(timeoutMs * 3L, 12_000L)
                            if (elapsedSinceFinalItem < finalAckTimeoutMs) {
                                Log.w(
                                    "MissionUpload",
                                    "session=$uploadSessionId watchdog: waiting for final ACK after seq=$lastSeq " +
                                            "elapsed=${elapsedSinceFinalItem}ms/$finalAckTimeoutMs ms"
                                )
                                return@subscribe
                            }

                            val reason = "Watchdog failed: no ACK after final mission item seq=$lastSeq"
                            failureReason.compareAndSet(null, reason)
                            Log.e("MissionUpload", "session=$uploadSessionId $reason")
                            if (!done.getAndSet(true)) {
                                success.set(false)
                                latch.countDown()
                            }
                        }
                    }, { err ->
                        Log.e("MissionUpload", "Watchdog error: ${err.message}", err)
                    })
            )

            Log.i("MissionUpload", "session=$uploadSessionId TX MissionCount count=${items.size}")
            client.send2(gcsSystemId, gcsComponentId, countMsg)
            lastProgressMs.set(System.currentTimeMillis())

            // ---- Wait loop (cancel-friendly) ----
            while (!done.get()) {
                if (cancelled(cancel)) {
                    val reason = "Mission upload cancelled before completion"
                    failureReason.compareAndSet(null, reason)
                    Log.w("MissionUpload", reason)
                    done.set(true)
                    success.set(false)
                    break
                }

                try {
                    latch.await(250, TimeUnit.MILLISECONDS)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    val reason = "Mission upload interrupted while waiting for autopilot response"
                    failureReason.compareAndSet(null, reason)
                    Log.w("MissionUpload", reason)
                    done.set(true)
                    success.set(false)
                    break
                }

                if (System.currentTimeMillis() - startMs > totalTimeoutMs) {
                    val reason = "Total mission upload timeout exceeded (${totalTimeoutMs}ms)"
                    failureReason.compareAndSet(null, reason)
                    Log.e("MissionUpload", reason)
                    done.set(true)
                    success.set(false)
                    break
                }
            }
        } finally {
            disposables.clear()
        }

        return if (success.get()) {
            MissionUploadResult.Success
        } else {
            MissionUploadResult.Failure(failureReason.get() ?: "Mission upload failed without a reported MAVLink reason")
        }
    }

    private fun itemWithTargetsAndSeq(
        src: MissionItemInt,
        seq: Int,
        targetSysId: Int,
        targetCompId: Int
    ): MissionItemInt {
        val coordinateNavCommand = isCoordinateNavCommand(src.command().entry())
        val frame = EnumValue.of(normalizedFrameForUpload(src))

        return MissionItemInt.builder()
            .targetSystem(targetSysId)
            .targetComponent(targetCompId)
            .seq(seq)
            .frame(frame)
            .command(src.command())
            .current(src.current())
            .autocontinue(src.autocontinue())
            .param1(src.param1())
            .param2(src.param2())
            .param3(src.param3())
            .param4(src.param4())
            .x(if (coordinateNavCommand) src.x() else 0)
            .y(if (coordinateNavCommand) src.y() else 0)
            .z(if (coordinateNavCommand) src.z() else 0f)
            .missionType(src.missionType())
            .build()
    }

    private fun itemIntToItemWithTargetsAndSeq(
        src: MissionItemInt,
        seq: Int,
        targetSysId: Int,
        targetCompId: Int
    ): MissionItem {
        val coordinateNavCommand = isCoordinateNavCommand(src.command().entry())
        val frame = EnumValue.of(normalizedLegacyFrameForUpload(src))

        // MISSION_ITEM uses float lat/lon degrees. Coordinate NAV items encode in INT as degrees*1e7.
        val x = if (coordinateNavCommand) src.x() / 1e7f else 0f
        val y = if (coordinateNavCommand) src.y() / 1e7f else 0f
        val z = if (coordinateNavCommand) src.z() else 0f

        return MissionItem.builder()
            .targetSystem(targetSysId)
            .targetComponent(targetCompId)
            .seq(seq)
            .frame(frame)
            .command(src.command())
            .current(src.current())
            .autocontinue(src.autocontinue())
            .param1(src.param1())
            .param2(src.param2())
            .param3(src.param3())
            .param4(src.param4())
            .x(x)
            .y(y)
            .z(z)
            .missionType(src.missionType())
            .build()
    }

    private fun isCoordinateNavCommand(command: MavCmd): Boolean {
        return command == MavCmd.MAV_CMD_NAV_TAKEOFF ||
                command == MavCmd.MAV_CMD_NAV_WAYPOINT ||
                command == MavCmd.MAV_CMD_NAV_SPLINE_WAYPOINT ||
                command == MavCmd.MAV_CMD_NAV_LAND
    }

    private fun normalizedFrameForUpload(src: MissionItemInt): MavFrame {
        val command = src.command().entry()
        val sourceFrame = src.frame().entry()

        return when {
            isCoordinateNavCommand(command) -> {
                when (sourceFrame) {
                    MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT,
                    MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT_INT -> {
                        if (ENABLE_TERRAIN_MISSION_FRAMES) {
                            MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT_INT
                        } else {
                            Log.w(
                                "MissionUpload",
                                "Terrain mission frame requested for seq=${src.seq()} cmd=${command.name}; " +
                                        "falling back to MAV_FRAME_GLOBAL_RELATIVE_ALT_INT"
                            )
                            MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT
                        }
                    }
                    else -> MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT
                }
            }
            else -> MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT
        }
    }

    private fun normalizedLegacyFrameForUpload(src: MissionItemInt): MavFrame {
        val command = src.command().entry()
        val sourceFrame = src.frame().entry()

        return when {
            isCoordinateNavCommand(command) -> {
                when (sourceFrame) {
                    MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT,
                    MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT_INT -> {
                        if (ENABLE_TERRAIN_MISSION_FRAMES) {
                            MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT
                        } else {
                            MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT
                        }
                    }
                    else -> MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT
                }
            }
            else -> MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT
        }
    }

    private fun validateMissionForUpload(items: List<MissionItemInt>): String? {
        if (items.isEmpty()) return "mission item list is empty"

        val currentItems = items.filter { it.current() == 1 }
        if (currentItems.size != 1) return "expected exactly one current=1 item, found ${currentItems.size}"
        if (items.first().current() != 1) return "current=1 item must be seq/index 0"
        if (items.first().command().entry() != MavCmd.MAV_CMD_NAV_TAKEOFF) {
            return "seq/index 0 must be MAV_CMD_NAV_TAKEOFF for generated Copter missions"
        }

        items.forEachIndexed { index, item ->
            if (item.seq() != index) return "mission seq values must be contiguous; index=$index item.seq=${item.seq()}"
            if (item.missionType().entry() != MavMissionType.MAV_MISSION_TYPE_MISSION) {
                return "seq=$index mission_type must be MAV_MISSION_TYPE_MISSION"
            }

            val command = item.command().entry()
            val frame = item.frame().entry()
            val coordinateNav = isCoordinateNavCommand(command)
            if (coordinateNav) {
                if (
                    frame != MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT &&
                    frame != MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT &&
                    frame != MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT &&
                    frame != MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT_INT
                ) {
                    return "seq=$index $command uses unsupported coordinate frame $frame"
                }
            } else if (item.x() != 0 || item.y() != 0 || item.z() != 0f) {
                return "seq=$index $command is non-coordinate but has x/y/z=${item.x()}/${item.y()}/${item.z()}"
            }
        }

        return null
    }

    private fun logMissionItemInt(
        uploadSessionId: Long,
        requestType: String,
        requestedSeq: Int,
        outgoingType: String,
        item: MissionItemInt
    ) {
        val command = item.command().entry()
        val coordinateNav = isCoordinateNavCommand(command)
        val decodedPosition = if (coordinateNav) {
            " lat=${item.x() / 1e7} lon=${item.y() / 1e7}"
        } else {
            ""
        }
        if (item.seq() != requestedSeq) {
            Log.w(
                "MissionUpload",
                "session=$uploadSessionId encoded item.seq=${item.seq()} does not match requestedSeq=$requestedSeq"
            )
        }
        if (!coordinateNav && item.frame().entry().name.endsWith("_INT")) {
            Log.w(
                "MissionUpload",
                "session=$uploadSessionId seq=${item.seq()} non-coordinate command ${command.name} uses INT frame ${item.frame().entry().name}"
            )
        }

        Log.i(
            "MissionUpload",
            "session=$uploadSessionId requestType=$requestType requestedSeq=$requestedSeq outgoing=$outgoingType " +
                    "encodedSeq=${item.seq()} cmd=${command.name} frame=${item.frame().entry().name} " +
                    "current=${item.current()} autocontinue=${item.autocontinue()} missionType=${item.missionType().entry().name} " +
                    "targetSystem=${item.targetSystem()} targetComponent=${item.targetComponent()} " +
                    "p1=${item.param1()} p2=${item.param2()} p3=${item.param3()} p4=${item.param4()} " +
                    "x=${item.x()} y=${item.y()} z=${item.z()}$decodedPosition"
        )
    }


    private fun waitForDownloadedMissionItem(seq: Int, timeoutMs: Long, cancel: AtomicBoolean?): MissionItemInt? {
        if (cancelled(cancel)) return null
        val ref = AtomicReference<MissionItemInt?>(null)
        val latch = CountDownLatch(1)

        val disposable = client.messages()
            .filter { msg ->
                val payload = msg.payload
                val matchingOrigin = msg.originSystemId == targetSystemId && msg.originComponentId == targetComponentId
                if (!matchingOrigin) return@filter false

                when (payload) {
                    is MissionItemInt -> payload.seq() == seq &&
                            payload.missionType().entry() == MavMissionType.MAV_MISSION_TYPE_MISSION
                    is MissionItem -> payload.seq() == seq &&
                            payload.missionType().entry() == MavMissionType.MAV_MISSION_TYPE_MISSION
                    else -> false
                }
            }
            .subscribe({ msg ->
                val payload = msg.payload
                val converted = when (payload) {
                    is MissionItemInt -> payload
                    is MissionItem -> missionItemToMissionItemInt(payload)
                    else -> null
                }
                if (converted != null && ref.compareAndSet(null, converted)) {
                    val sourceType = if (payload is MissionItem) "MISSION_ITEM" else "MISSION_ITEM_INT"
                    Log.i("MissionService", "RX $sourceType seq=$seq during mission download")
                    latch.countDown()
                }
            }, { err ->
                Log.e("MissionService", "Mission download item listener failed: ${err.message}", err)
                latch.countDown()
            })

        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            if (cancelled(cancel)) return null
            return ref.get()
        } catch (interrupted: InterruptedException) {
            // Disposing the Rx download job can interrupt this blocking wait. Treat it
            // as cooperative cancellation; do not let it escape as an RxJava
            // UndeliverableException on the scheduler thread.
            Log.d("MissionService", "Mission download wait interrupted for seq=$seq; returning cancelled result")
            Thread.currentThread().interrupt()
            return null
        } finally {
            disposable.dispose()
        }
    }

    private fun missionItemToMissionItemInt(src: MissionItem): MissionItemInt {
        val command = src.command().entry()
        val coordinateNavCommand = isCoordinateNavCommand(command)
        val convertedFrame = when (src.frame().entry()) {
            MavFrame.MAV_FRAME_GLOBAL,
            MavFrame.MAV_FRAME_GLOBAL_INT -> MavFrame.MAV_FRAME_GLOBAL_INT
            MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT,
            MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT -> MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT
            MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT,
            MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT_INT -> MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT_INT
            else -> src.frame().entry()
        }

        val x = if (coordinateNavCommand) (src.x().toDouble() * 1e7).roundToInt() else 0
        val y = if (coordinateNavCommand) (src.y().toDouble() * 1e7).roundToInt() else 0
        val z = if (coordinateNavCommand) src.z() else 0f

        return MissionItemInt.builder()
            .targetSystem(src.targetSystem())
            .targetComponent(src.targetComponent())
            .seq(src.seq())
            .frame(EnumValue.of(convertedFrame))
            .command(src.command())
            .current(src.current())
            .autocontinue(src.autocontinue())
            .param1(src.param1())
            .param2(src.param2())
            .param3(src.param3())
            .param4(src.param4())
            .x(x)
            .y(y)
            .z(z)
            .missionType(src.missionType())
            .build()
    }

    private fun describeMissionItemInt(item: MissionItemInt): String {
        return "outgoing=MISSION_ITEM_INT seq=${item.seq()} cmd=${item.command().entry().name} " +
                "frame=${item.frame().entry().name} x=${item.x()} y=${item.y()} z=${item.z()} " +
                "p1=${item.param1()} p2=${item.param2()} p3=${item.param3()} p4=${item.param4()}"
    }

    private fun describeMissionItem(item: MissionItem): String {
        return "outgoing=MISSION_ITEM seq=${item.seq()} cmd=${item.command().entry().name} " +
                "frame=${item.frame().entry().name} x=${item.x()} y=${item.y()} z=${item.z()} " +
                "p1=${item.param1()} p2=${item.param2()} p3=${item.param3()} p4=${item.param4()}"
    }

    private companion object {
        private const val SEND_MISSION_ITEM_INT_FOR_LEGACY_REQUEST = false
        private const val ENABLE_TERRAIN_MISSION_FRAMES = false
    }

    private fun waitForMissionAckFromAutopilot(
        timeoutMs: Long,
        cancel: AtomicBoolean?
    ): MavlinkMessage<MissionAck>? {
        if (cancelled(cancel)) return null
        val ack = client.waitFor(MissionAck::class.java, timeoutMs) { m ->
            m.originSystemId == targetSystemId
        }
        if (cancelled(cancel)) return null
        return ack
    }
}
