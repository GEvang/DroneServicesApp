package com.example.droneservicesapp.mavlink

import android.util.Log
import com.example.droneservicesapp.transport.MavTransport
import com.example.droneservicesapp.transport.UdpTransport
import io.dronefleet.mavlink.MavlinkConnection
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.minimal.Heartbeat
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.atomic.AtomicBoolean

class MavlinkRepository {

    private var transport: MavTransport? = null
    private var mavCon: MavlinkConnection? = null

    private var readerDisposable: Disposable? = null

    private val running = AtomicBoolean(false)

    @Volatile var lastHeartbeatMs: Long = 0L
        private set

    fun start(config: MavlinkConfig) {
        if (running.getAndSet(true)) return

        transport = when (config.interfaceType) {
            MavlinkConfig.InterfaceType.UDP -> UdpTransport(config.port)
            else -> throw IllegalArgumentException("Not implemented yet: ${config.interfaceType}")
        }.also { it.start() }

        val t = transport!!
        mavCon = MavlinkConnection.create(t.input, t.output)

        startReader()
        Log.i("MavlinkRepository", "Started with $config")
    }

    fun stop() {
        running.set(false)
        readerDisposable?.dispose()
        readerDisposable = null

        transport?.stop()
        transport = null
        mavCon = null

        Log.i("MavlinkRepository", "Stopped")
    }

    fun restart(config: MavlinkConfig) {
        stop()
        start(config)
    }

    private fun startReader() {
        val con = mavCon ?: return

        readerDisposable = Observable.create<MavlinkMessage<*>> { emitter ->
            try {
                while (!emitter.isDisposed) {
                    val msg = con.next() ?: break
                    emitter.onNext(msg)
                }
            } catch (e: Exception) {
                emitter.onError(e)
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { msg ->
                    if (msg.payload is Heartbeat) {
                        lastHeartbeatMs = System.currentTimeMillis()
                    }
                },
                { err ->
                    Log.e("MavlinkRepository", "Reader error: ${err.message}", err)
                }
            )
    }
}
