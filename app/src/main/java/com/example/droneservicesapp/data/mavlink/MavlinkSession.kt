package com.example.droneservicesapp.data.mavlink

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
        private const val GCS_SYSTEM_ID = 254
        private const val GCS_COMPONENT_ID = 99
        private const val RTCM_FRAGMENT_SIZE = 180
        private const val MAX_RTCM_MESSAGE_SIZE = RTCM_FRAGMENT_SIZE * 4
        private const val RTCM_SEQUENCE_MASK = 0x1F
    }

    private val mavCon: MavlinkConnection = MavlinkConnection.create(input, output)

    private var readerDisposable: Disposable? = null

    private val running = AtomicBoolean(false)

    private val msgSubject: Subject<MavlinkMessage<*>> =
        PublishSubject.create<MavlinkMessage<*>>().toSerialized()
    private val sendLock = Any()
    private val rtcmSequence = AtomicInteger(0)

    @Volatile
    private var _lastHeartbeatMs: Long = 0L

    val lastHeartbeatMs: Long
        get() = _lastHeartbeatMs

    /**
     * Start the session reader (idempotent; if already started, does nothing).
     */
    fun start() {
        if (running.getAndSet(true)) return
        startReader()
        Log.i(TAG, "Started")
    }

    /**
     * Stop the session reader and dispose resources.
     * Note: Closing streams is handled by the transport layer.
     */
    fun stop() {
        if (!running.getAndSet(false)) return
        readerDisposable?.dispose()
        readerDisposable = null
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

        val fragments = buildGpsRtcmMessages(rtcmPayload)
        synchronized(sendLock) {
            for (fragment in fragments) {
                mavCon.send2(GCS_SYSTEM_ID, GCS_COMPONENT_ID, fragment)
            }
        }
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
        val fragmentCount = (rtcmPayload.size + RTCM_FRAGMENT_SIZE - 1) / RTCM_FRAGMENT_SIZE

        return (0 until fragmentCount).map { fragmentId ->
            val start = fragmentId * RTCM_FRAGMENT_SIZE
            val end = minOf(start + RTCM_FRAGMENT_SIZE, rtcmPayload.size)
            val fragment = rtcmPayload.copyOfRange(start, end)
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
}
