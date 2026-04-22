package com.example.droneservicesapp.data.mavlink

import android.os.SystemClock as AndroidSystemClock
import android.util.Log
import com.example.droneservicesapp.core.util.Clock
import com.example.droneservicesapp.core.util.SystemClock
import io.dronefleet.mavlink.MavlinkConnection
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.GpsRtcmData
import io.dronefleet.mavlink.minimal.Heartbeat
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages a MAVLink session over provided InputStream/OutputStream.
 * Owns the MavlinkConnection and coordinates message reading/sending.
 */
class MavlinkSession(
    input: InputStream,
    output: OutputStream,
    private val clock: Clock = SystemClock
) {
    private companion object {
        private const val TAG = "MavlinkSession"
        private const val RTCM_TAG = "MavlinkRtcm"
        private const val GCS_SYSTEM_ID = 255
        private const val GCS_COMPONENT_ID = 190
        private const val RTCM_FRAGMENT_SIZE = 180
        private const val MAX_RTCM_MESSAGE_SIZE = RTCM_FRAGMENT_SIZE * 4
        private const val RTCM_SEQUENCE_MASK = 0x1F
        private const val RTCM_FRAGMENT_PACING_DELAY_MS = 5L
        private const val RTCM_STATS_LOG_INTERVAL_MS = 5000L
        private const val RTCM_QUEUE_MAX_FRAMES = 30
        private const val RTCM_QUEUE_POLL_TIMEOUT_MS = 250L
    }

    private val mavCon: MavlinkConnection = MavlinkConnection.create(input, output)

    private var readerDisposable: Disposable? = null
    private var rtcmSenderThread: Thread? = null

    private val running = AtomicBoolean(false)

    private val msgSubject: Subject<MavlinkMessage<*>> =
        PublishSubject.create<MavlinkMessage<*>>().toSerialized()
    private val sendLock = Any()
    private val rtcmQueue = LinkedBlockingDeque<QueuedRtcmFrame>()
    private val rtcmSequence = AtomicInteger(0)
    private val totalRtcmMessagesSent = AtomicInteger(0)
    private val totalRtcmChunksSent = AtomicInteger(0)
    private val firstHeartbeatLogged = AtomicBoolean(false)
    private val totalRtcmFramesSent = AtomicInteger(0)

    @Volatile
    private var _lastHeartbeatMs: Long = 0L
    @Volatile
    private var lastRtcmStatsLogMs: Long = 0L

    val lastHeartbeatMs: Long
        get() = _lastHeartbeatMs

    /**
     * Start the session reader (idempotent; if already started, does nothing).
     */
    fun start() {
        if (running.getAndSet(true)) {
            Log.i(TAG, "start skipped: session reader already running")
            return
        }
        Log.i(TAG, "session start: reader creating")
        startReader()
        startRtcmSender()
        Log.i(TAG, "Started")
    }

    /**
     * Stop the session reader and dispose resources.
     * Note: Closing streams is handled by the transport layer.
     */
    fun stop() {
        if (!running.getAndSet(false)) {
            Log.i(TAG, "stop skipped: session reader already stopped")
            return
        }
        Log.i(TAG, "session stop: disposing reader")
        readerDisposable?.dispose()
        readerDisposable = null
        stopRtcmSender()
        Log.i(TAG, "Stopped")
    }

    /**
     * Send a MAVLink message with the specified system and component IDs.
     */
    fun send2(systemId: Int, componentId: Int, payload: Any) {
        synchronized(sendLock) {
            mavCon.send2(systemId, componentId, payload)
        }
    }

    /**
     * GPS_RTCM_DATA has no target fields. The target IDs are accepted here so callers only send
     * corrections once the autopilot is known, while the actual MAVLink sender identity remains the GCS.
     */
    fun sendGpsRtcmData(targetSystemId: Int, targetComponentId: Int, rtcmPayload: ByteArray) {
        require(targetSystemId >= 0 && targetComponentId >= 0) {
            "Autopilot target IDs are not known."
        }
        require(rtcmPayload.isNotEmpty()) {
            "RTCM payload must not be empty."
        }
        require(rtcmPayload.size <= MAX_RTCM_MESSAGE_SIZE) {
            "RTCM payload exceeds GPS_RTCM_DATA fragmentation capacity."
        }

        val frameCount = totalRtcmFramesSent.incrementAndGet().toLong()
        val chunkCount = totalRtcmChunksSent.incrementAndGet()
        val totalPackagedBytes = estimateRtcmPacketizedBytes(rtcmPayload.size)
        val enqueueMonoNs = AndroidSystemClock.elapsedRealtimeNanos()
        val queued = QueuedRtcmFrame(
            frameId = frameCount,
            chunkId = chunkCount,
            targetSystemId = targetSystemId,
            targetComponentId = targetComponentId,
            payload = rtcmPayload.copyOf(),
            enqueueMonoNs = enqueueMonoNs
        )
        val discarded = discardOldestFramesForCapacity()
        if (discarded > 0) {
            Log.w(
                RTCM_TAG,
                "queue backlog trimmed discardedOldest=$discarded queueDepthBeforeEnqueue=${rtcmQueue.size}"
            )
        }
        rtcmQueue.putLast(queued)
        val queueDepth = rtcmQueue.size
        Log.i(
            RTCM_TAG,
            "mavlink: frame enqueued frame=$frameCount enqueueMonoNs=$enqueueMonoNs chunkSize=${rtcmPayload.size} targetSys=$targetSystemId targetComp=$targetComponentId totalBytesIn=${rtcmPayload.size} totalBytesPackaged=$totalPackagedBytes queueDepth=$queueDepth"
        )
    }

    private fun maybeLogRtcmStats(totalPackets: Int, totalFrames: Int) {
        val now = clock.nowMs()
        if (totalFrames == 1 || totalFrames % 25 == 0 || now - lastRtcmStatsLogMs >= RTCM_STATS_LOG_INTERVAL_MS) {
            lastRtcmStatsLogMs = now
            Log.i(
                RTCM_TAG,
                "stats totalFrames=$totalFrames totalPackets=$totalPackets totalChunks=${totalRtcmChunksSent.get()}"
            )
        }
    }

    private fun startRtcmSender() {
        if (rtcmSenderThread?.isAlive == true) {
            Log.i(RTCM_TAG, "sender thread already running")
            return
        }
        rtcmSenderThread = Thread(
            {
                Log.i(RTCM_TAG, "sender thread started")
                while (running.get() || rtcmQueue.isNotEmpty()) {
                    val queuedFrame = try {
                        rtcmQueue.poll(RTCM_QUEUE_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    } catch (_: InterruptedException) {
                        break
                    } ?: continue

                    sendQueuedRtcmFrame(queuedFrame)
                }
                Log.i(RTCM_TAG, "sender thread stopped")
            },
            "MavlinkRtcmSender"
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun stopRtcmSender() {
        rtcmQueue.clear()
        rtcmSenderThread?.interrupt()
        rtcmSenderThread = null
    }

    private fun sendQueuedRtcmFrame(queuedFrame: QueuedRtcmFrame) {
        val sendStartMonoNs = AndroidSystemClock.elapsedRealtimeNanos()
        val queueLatencyMs = nanosToMillis(sendStartMonoNs - queuedFrame.enqueueMonoNs)
        val queueDepth = rtcmQueue.size
        val fragments = buildGpsRtcmMessages(queuedFrame.payload)
        Log.i(
            RTCM_TAG,
            "mavlink: send start frame=${queuedFrame.frameId} chunk=${queuedFrame.chunkId} enqueueMonoNs=${queuedFrame.enqueueMonoNs} sendStartMonoNs=$sendStartMonoNs queueLatencyMs=$queueLatencyMs queueDepth=$queueDepth generatedPackets=${fragments.size}"
        )
        Log.i(
            RTCM_TAG,
            "mavlink: sender sys=$GCS_SYSTEM_ID comp=$GCS_COMPONENT_ID for GPS_RTCM_DATA"
        )

        synchronized(sendLock) {
            try {
                var nextFragmentDeadlineNs = sendStartMonoNs
                var previousFragmentSentNs = sendStartMonoNs
                fragments.forEachIndexed { index, fragment ->
                    if (index > 0) {
                        nextFragmentDeadlineNs += TimeUnit.MILLISECONDS.toNanos(RTCM_FRAGMENT_PACING_DELAY_MS)
                        waitUntilMonoNs(nextFragmentDeadlineNs)
                    }

                    val fragmentSendMonoNs = AndroidSystemClock.elapsedRealtimeNanos()
                    val actualDelayMs = if (index == 0) {
                        0L
                    } else {
                        nanosToMillis(fragmentSendMonoNs - previousFragmentSentNs)
                    }
                    previousFragmentSentNs = fragmentSendMonoNs

                    val flags = fragment.flags()
                    val seq = (flags.toInt() shr 3) and RTCM_SEQUENCE_MASK
                    val fragmentIndex = (flags.toInt() shr 1) and 0x03
                    Log.i(
                        RTCM_TAG,
                        "mavlink: packet frame=${queuedFrame.frameId} chunk=${queuedFrame.chunkId} packet=${index + 1}/${fragments.size} seq=$seq flags=$flags len=${fragment.len()} fragmentIndex=$fragmentIndex actualInterFragmentDelayMs=$actualDelayMs"
                    )
                    mavCon.send2(GCS_SYSTEM_ID, GCS_COMPONENT_ID, fragment)
                }
                val totalPackets = totalRtcmMessagesSent.addAndGet(fragments.size)
                maybeLogRtcmStats(totalPackets, queuedFrame.frameId.toInt())
                Log.i(
                    RTCM_TAG,
                    "mavlink: send success frame=${queuedFrame.frameId} generatedPackets=${fragments.size} totalPackets=$totalPackets endToEndQueueLatencyMs=$queueLatencyMs queueDepthAfterSend=${rtcmQueue.size}"
                )
            } catch (e: Exception) {
                Log.e(
                    RTCM_TAG,
                    "mavlink: send failure frame=${queuedFrame.frameId} type=${e.javaClass.simpleName} message=${e.message}",
                    e
                )
            }
        }
    }

    private fun discardOldestFramesForCapacity(): Int {
        var discarded = 0
        while (rtcmQueue.size >= RTCM_QUEUE_MAX_FRAMES) {
            val staleFrame = rtcmQueue.pollFirst() ?: break
            discarded++
            Log.w(
                RTCM_TAG,
                "mavlink: dropping stale queued frame frame=${staleFrame.frameId} queueLatencyMs=${nanosToMillis(AndroidSystemClock.elapsedRealtimeNanos() - staleFrame.enqueueMonoNs)} queueDepth=${rtcmQueue.size}"
            )
        }
        return discarded
    }

    private fun estimateRtcmPacketizedBytes(payloadSize: Int): Int {
        val dataFragmentCount = (payloadSize + RTCM_FRAGMENT_SIZE - 1) / RTCM_FRAGMENT_SIZE
        val requiresTerminalEmptyFragment =
            payloadSize < MAX_RTCM_MESSAGE_SIZE && payloadSize % RTCM_FRAGMENT_SIZE == 0 && payloadSize > RTCM_FRAGMENT_SIZE
        val totalFragmentCount = if (requiresTerminalEmptyFragment) dataFragmentCount + 1 else dataFragmentCount.coerceAtLeast(1)
        return totalFragmentCount * RTCM_FRAGMENT_SIZE
    }

    private fun waitUntilMonoNs(targetMonoNs: Long) {
        while (true) {
            val remainingNs = targetMonoNs - AndroidSystemClock.elapsedRealtimeNanos()
            if (remainingNs <= 0L) return
            LockSupport.parkNanos(remainingNs.coerceAtMost(TimeUnit.MILLISECONDS.toNanos(1)))
        }
    }

    private fun nanosToMillis(durationNs: Long): Long {
        return TimeUnit.NANOSECONDS.toMillis(durationNs.coerceAtLeast(0L))
    }

    /**
     * Get the observable stream of all received MAVLink messages.
     */
    fun messages(): Observable<MavlinkMessage<*>> = msgSubject.hide()

    /**
     * Wait for a specific message type with optional filtering.
     * Returns the first matching message or null if timeout is exceeded.
     */
    fun <T : Any> waitFor(
        clazz: Class<T>,
        timeoutMs: Long,
        filter: (MavlinkMessage<*>) -> Boolean = { true }
    ): MavlinkMessage<T>? {
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            messages()
                .filter { clazz.isInstance(it.payload) && filter(it) }
                .timeout(timeoutMs, TimeUnit.MILLISECONDS)
                .blockingFirst() as MavlinkMessage<T>
        }.getOrNull()
    }

    private fun startReader() {
        Log.i(TAG, "reader start requested")
        readerDisposable = Observable.create<MavlinkMessage<*>> { emitter ->
            try {
                while (!emitter.isDisposed) {
                    val msg = mavCon.next() ?: break
                    emitter.onNext(msg)
                }
                if (!emitter.isDisposed) emitter.onComplete()
            } catch (e: Exception) {
                if (running.get() && !emitter.isDisposed) {
                    emitter.onError(e)
                }
            }
        }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { msg ->
                    msgSubject.onNext(msg)
                    if (msg.payload is Heartbeat) {
                        _lastHeartbeatMs = clock.nowMs()
                        if (firstHeartbeatLogged.compareAndSet(false, true)) {
                            Log.i(TAG, "heartbeat first seen system=${msg.originSystemId} component=${msg.originComponentId}")
                        }
                    }
                },
                { err ->
                    Log.e(TAG, "Reader error: ${err.message}", err)
                }
            )
    }

    private fun buildGpsRtcmMessages(rtcmPayload: ByteArray): List<GpsRtcmData> {
        if (rtcmPayload.size <= RTCM_FRAGMENT_SIZE) {
            return listOf(
                GpsRtcmData.builder()
                    .flags(0)
                    .len(rtcmPayload.size)
                    .data(rtcmPayload.toFixedLengthByteArray(RTCM_FRAGMENT_SIZE))
                    .build()
            )
        }

        val sequenceId = rtcmSequence.getAndUpdate { (it + 1) and RTCM_SEQUENCE_MASK }
        val requiresTerminalEmptyFragment =
            rtcmPayload.size < MAX_RTCM_MESSAGE_SIZE && rtcmPayload.size % RTCM_FRAGMENT_SIZE == 0
        val dataFragmentCount = (rtcmPayload.size + RTCM_FRAGMENT_SIZE - 1) / RTCM_FRAGMENT_SIZE
        val totalFragmentCount = if (requiresTerminalEmptyFragment) {
            dataFragmentCount + 1
        } else {
            dataFragmentCount
        }

        require(totalFragmentCount <= 4) {
            "RTCM payload requires more than 4 GPS_RTCM_DATA fragments."
        }

        return (0 until totalFragmentCount).map { fragmentId ->
            val start = fragmentId * RTCM_FRAGMENT_SIZE
            val end = minOf(start + RTCM_FRAGMENT_SIZE, rtcmPayload.size)
            val fragment = if (start < rtcmPayload.size) {
                rtcmPayload.copyOfRange(start, end)
            } else {
                ByteArray(0)
            }
            val flags = 1 or (fragmentId shl 1) or (sequenceId shl 3)

            GpsRtcmData.builder()
                .flags(flags)
                .len(fragment.size)
                .data(fragment.toFixedLengthByteArray(RTCM_FRAGMENT_SIZE))
                .build()
        }
    }

    private fun ByteArray.toFixedLengthByteArray(size: Int): ByteArray {
        val out = ByteArray(size)
        copyInto(out, endIndex = minOf(size, this.size))
        return out
    }

    private data class QueuedRtcmFrame(
        val frameId: Long,
        val chunkId: Int,
        val targetSystemId: Int,
        val targetComponentId: Int,
        val payload: ByteArray,
        val enqueueMonoNs: Long
    )
}
