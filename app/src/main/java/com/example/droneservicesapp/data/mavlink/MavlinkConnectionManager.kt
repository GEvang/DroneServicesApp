package com.example.droneservicesapp.data.mavlink

import android.util.Log
import com.example.droneservicesapp.data.transport.DefaultMavTransportFactory
import com.example.droneservicesapp.data.transport.MavTransport
import com.example.droneservicesapp.data.transport.MavTransportFactory
import io.dronefleet.mavlink.MavlinkMessage
import io.reactivex.Observable
import java.util.concurrent.atomic.AtomicBoolean


class MavlinkConnectionManager(
    private val transportFactory: MavTransportFactory = DefaultMavTransportFactory()
) : MavlinkClient {

    private companion object {
        private const val TAG = "MavlinkConnectionManager"
    }

    private var transport: MavTransport? = null
    private var session: MavlinkSession? = null

    private val running = AtomicBoolean(false)

    private val lifecycleLock = Any()

    override val lastHeartbeatMs: Long
        get() = session?.lastHeartbeatMs ?: 0L

    override fun start(config: MavlinkConfig) {
        synchronized(lifecycleLock) {
            if (running.getAndSet(true)) return

            transport = transportFactory.create(config).also { it.start() }

            val t = transport!!
            session = MavlinkSession(t.input, t.output)
            session?.start()

            Log.i(TAG, "Started with $config")
        }
    }


    override fun stop() {
        synchronized(lifecycleLock) {
            if (!running.getAndSet(false)) return

            session?.stop()
            session = null

            transport?.stop()
            transport = null

            Log.i(TAG, "Stopped")
        }
    }

    override fun restart(config: MavlinkConfig) {
        synchronized(lifecycleLock) {
            if (running.get()) {
                if (running.getAndSet(false)) {
                    session?.stop()
                    session = null

                    transport?.stop()
                    transport = null

                    Log.i(TAG, "Stopped")
                }
            }

            if (running.getAndSet(true)) return

            transport = transportFactory.create(config).also { it.start() }

            val t = transport!!
            session = MavlinkSession(t.input, t.output)
            session?.start()

            Log.i(TAG, "Started with $config")
        }
    }

    override fun send2(systemId: Int, componentId: Int, payload: Any) {
        session?.send2(systemId, componentId, payload)
    }

    override fun messages(): Observable<MavlinkMessage<*>> =
        session?.messages() ?: Observable.empty()

    override fun <T : Any> waitFor(
        clazz: Class<T>,
        timeoutMs: Long,
        filter: (MavlinkMessage<*>) -> Boolean
    ): MavlinkMessage<T>? =
        session?.waitFor(clazz, timeoutMs, filter)

}
