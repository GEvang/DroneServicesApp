package com.example.droneservicesapp.data.mavlink

import android.util.Log
import io.dronefleet.mavlink.MavlinkConnection
import io.dronefleet.mavlink.MavlinkMessage
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

/**
 * Manages a MAVLink session over provided InputStream/OutputStream.
 * Owns the MavlinkConnection and coordinates message reading/sending.
 */
class MavlinkSession(
    input: InputStream,
    output: OutputStream
) {
    private companion object {
        private const val TAG = "MavlinkSession"
    }

    private val mavCon: MavlinkConnection = MavlinkConnection.create(input, output)

    private var readerDisposable: Disposable? = null

    private val running = AtomicBoolean(false)

    private val msgSubject: Subject<MavlinkMessage<*>> =
        PublishSubject.create<MavlinkMessage<*>>().toSerialized()

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
        mavCon.send2(systemId, componentId, payload)
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
                        _lastHeartbeatMs = System.currentTimeMillis()
                    }
                },
                { err ->
                    Log.e(TAG, "Reader error: ${err.message}", err)
                }
            )
    }
}