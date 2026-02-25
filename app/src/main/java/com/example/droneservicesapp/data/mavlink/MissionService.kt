package com.example.droneservicesapp.data.mavlink

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
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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

    private fun isTargetedToThisGcs(targetSys: Int, targetComp: Int): Boolean {
        return targetSys == gcsSystemId && targetComp == gcsComponentId
    }

    private fun cancelled(cancel: AtomicBoolean?): Boolean = cancel?.get() == true

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

    fun downloadMission(timeoutMs: Long = 1500L): ArrayList<MissionItemInt> {
        val reqList = MissionRequestList.builder()
            .targetSystem(targetSystemId)
            .targetComponent(targetComponentId)
            .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
            .build()

        var countMsg: MavlinkMessage<MissionCount>? = null
        repeat(5) {
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
                client.send2(gcsSystemId, gcsComponentId, reqItem)

                itemMsg = client.waitFor(MissionItemInt::class.java, timeoutMs) { m ->
                    val p = m.payload as MissionItemInt
                    m.originSystemId == targetSystemId &&
                            m.originComponentId == targetComponentId &&
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
    ): Boolean {

        if (items.isEmpty()) {
            Log.w("MissionUpload", "uploadMission called with 0 items; treating as success")
            return true
        }

        // Capture target IDs ONCE at start to prevent mid-upload changes
        val uploadTargetSystemId = targetSystemId
        val uploadTargetComponentId = targetComponentId
        Log.i("MissionUpload", "Using targetSystem=$uploadTargetSystemId targetComponent=$uploadTargetComponentId for this upload")

        val lastSeq = items.size - 1

        val done = AtomicBoolean(false)
        val success = AtomicBoolean(false)
        val latch = CountDownLatch(1)
        val disposables = CompositeDisposable()

        val lastProgressMs = AtomicLong(System.currentTimeMillis())
        val lastSentSeq = AtomicInteger(-1)
        // De-duplication: track the last processed sequence number
        val lastProcessedSeq = AtomicInteger(-1)

        val resendCountAttempts = AtomicInteger(0)
        val resendLastItemAttempts = AtomicInteger(0)
        var requestsStarted = AtomicBoolean(false)

        var lastPercent = -1

        val countMsg = MissionCount.builder()
            .targetSystem(uploadTargetSystemId)
            .targetComponent(uploadTargetComponentId)
            .count(items.size)
            .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
            .build()

        Log.i("MissionUpload", "TX MissionCount count=${items.size}")
        client.send2(gcsSystemId, gcsComponentId, countMsg)
        lastProgressMs.set(System.currentTimeMillis())

        val startMs = System.currentTimeMillis()
        // More forgiving than the previous 15s/500ms heuristic.
        val totalTimeoutMs = maxOf(45_000L, items.size * 750L)

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

                        // De-duplication check: skip if we've already processed this seq or earlier
                        if (seq <= lastProcessedSeq.get()) {
                            Log.d(
                                "MissionUpload",
                                "SKIP duplicate MISSION_REQUEST_INT seq=$seq (already processed seq=${lastProcessedSeq.get()})"
                            )
                            return@subscribe
                        }

                        if (seq in items.indices) {
                            val out = itemWithTargetsAndSeq(items[seq], seq, uploadTargetSystemId, uploadTargetComponentId)
                            client.send2(gcsSystemId, gcsComponentId, out)
                            lastSentSeq.set(seq)
                            // Mark this sequence as processed immediately after sending
                            lastProcessedSeq.set(seq)
                            // CRITICAL: Reset progress timer after successful send so watchdog doesn't timeout
                            lastProgressMs.set(System.currentTimeMillis())
                            Log.i(
                                "MissionUpload",
                                "TX MISSION_ITEM_INT seq=$seq cmd=${out.command().entry().name} frame=${out.frame().entry().name}"
                            )

                            val percent = (((seq + 1).toDouble() / items.size.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                Log.i("MissionUpload", "Progress: seq=$seq/${items.size} ($percent%)")
                                onProgress?.invoke(seq, items.size, percent)
                            }
                        } else {
                            Log.e("MissionUpload", "Requested seq out of range: $seq size=${items.size}")
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

                        // De-duplication check: skip if we've already processed this seq or earlier
                        if (seq <= lastProcessedSeq.get()) {
                            Log.d(
                                "MissionUpload",
                                "SKIP duplicate MISSION_REQUEST seq=$seq (already processed seq=${lastProcessedSeq.get()})"
                            )
                            return@subscribe
                        }

                        if (seq in items.indices) {
                            val out = itemIntToItemWithTargetsAndSeq(items[seq], seq, uploadTargetSystemId, uploadTargetComponentId)
                            client.send2(gcsSystemId, gcsComponentId, out)
                            lastSentSeq.set(seq)
                            // Mark this sequence as processed immediately after sending
                            lastProcessedSeq.set(seq)
                            // CRITICAL: Reset progress timer after successful send so watchdog doesn't timeout
                            lastProgressMs.set(System.currentTimeMillis())
                            Log.i(
                                "MissionUpload",
                                "TX MISSION_ITEM seq=$seq cmd=${out.command().entry().name} frame=${out.frame().entry().name}"
                            )

                            val percent = (((seq + 1).toDouble() / items.size.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                Log.i("MissionUpload", "Progress: seq=$seq/${items.size} ($percent%)")
                                onProgress?.invoke(seq, items.size, percent)
                            }
                        } else {
                            Log.e("MissionUpload", "Legacy requested seq out of range: $seq size=${items.size}")
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
                                Log.e("MissionUpload", "Watchdog: too many MissionCount resends, failing upload")
                                if (!done.getAndSet(true)) {
                                    success.set(false)
                                    latch.countDown()
                                }
                            }
                            return@subscribe
                        }

                        // If requests have started but we haven't finished -> resend the last sent item
                        if (requestsStarted.get() && sent >= 0 && sent < lastSeq) {
                            val attempts = resendLastItemAttempts.incrementAndGet()
                            if (attempts <= 10) {
                                val seq = sent
                                val out = itemWithTargetsAndSeq(items[seq], seq, uploadTargetSystemId, uploadTargetComponentId)
                                Log.w(
                                    "MissionUpload",
                                    "Watchdog: stalled after item sent, resending seq=$seq (attempt=$attempts/10)"
                                )
                                client.send2(gcsSystemId, gcsComponentId, out)
                                lastProgressMs.set(now)
                            } else {
                                Log.e("MissionUpload", "Watchdog: too many resends of seq=$sent, failing upload")
                                if (!done.getAndSet(true)) {
                                    success.set(false)
                                    latch.countDown()
                                }
                            }
                            return@subscribe
                        }

                        // If we've sent all items but ACK is missing -> resend last item
                        if (sent == lastSeq) {
                            val attempts = resendLastItemAttempts.incrementAndGet()
                            if (attempts <= 10) {
                                val seq = lastSeq
                                val out = itemWithTargetsAndSeq(items[seq], seq, uploadTargetSystemId, uploadTargetComponentId)
                                Log.w(
                                    "MissionUpload",
                                    "Watchdog: stalled waiting for ACK, resending last item seq=$seq (attempt=$attempts/10)"
                                )
                                client.send2(gcsSystemId, gcsComponentId, out)
                                lastProgressMs.set(now)
                            } else {
                                Log.e("MissionUpload", "Watchdog: no ACK after resending last item; failing upload")
                                if (!done.getAndSet(true)) {
                                    success.set(false)
                                    latch.countDown()
                                }
                            }
                        }
                    }, { err ->
                        Log.e("MissionUpload", "Watchdog error: ${err.message}", err)
                    })
            )

            // ---- Wait loop (cancel-friendly) ----
            while (!done.get()) {
                if (cancelled(cancel)) {
                    Log.w("MissionUpload", "Upload cancelled by token")
                    done.set(true)
                    success.set(false)
                    break
                }

                latch.await(250, TimeUnit.MILLISECONDS)

                if (System.currentTimeMillis() - startMs > totalTimeoutMs) {
                    Log.e("MissionUpload", "Total timeout exceeded (${totalTimeoutMs}ms); failing upload")
                    done.set(true)
                    success.set(false)
                    break
                }
            }
        } finally {
            disposables.clear()
        }

        return success.get()
    }

    private fun itemWithTargetsAndSeq(
        src: MissionItemInt,
        seq: Int,
        targetSysId: Int,
        targetCompId: Int
    ): MissionItemInt {
        val isNavCommand = src.command().entry().name.startsWith("MAV_CMD_NAV")

        val frame = if (isNavCommand) {
            src.frame()
        } else {
            EnumValue.of(MavFrame.MAV_FRAME_MISSION)
        }

        return MissionItemInt.builder()
            .targetSystem(targetSysId)
            .targetComponent(targetCompId)
            .seq(seq)
            .frame(frame)
            .command(src.command())
            .current(if (seq == 0) 1 else 0)
            .autocontinue(src.autocontinue())
            .param1(src.param1())
            .param2(src.param2())
            .param3(src.param3())
            .param4(src.param4())
            .x(if (isNavCommand) src.x() else 0)
            .y(if (isNavCommand) src.y() else 0)
            .z(if (isNavCommand) src.z() else 0f)
            .missionType(src.missionType())
            .build()
    }

    private fun itemIntToItemWithTargetsAndSeq(
        src: MissionItemInt,
        seq: Int,
        targetSysId: Int,
        targetCompId: Int
    ): MissionItem {
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
            .targetSystem(targetSysId)
            .targetComponent(targetCompId)
            .seq(seq)
            .frame(frame)
            .command(src.command())
            .current(if (seq == 0) 1 else 0)
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