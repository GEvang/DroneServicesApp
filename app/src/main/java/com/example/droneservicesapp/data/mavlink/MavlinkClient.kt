package com.example.droneservicesapp.data.mavlink

import io.dronefleet.mavlink.MavlinkMessage
import io.reactivex.Observable

interface MavlinkClient {
    fun start(config: MavlinkConfig)
    fun stop()
    fun restart(config: MavlinkConfig) {
        stop()
        start(config)
    }

    fun <T : Any> waitFor(
        clazz: Class<T>,
        timeoutMs: Long,
        filter: (MavlinkMessage<*>) -> Boolean = { true }
    ): MavlinkMessage<T>?

    fun send2(systemId: Int, componentId: Int, payload: Any)
    fun sendGpsRtcmData(targetSystemId: Int, targetComponentId: Int, rtcmPayload: ByteArray)
    fun messages(): Observable<MavlinkMessage<*>>
    val lastHeartbeatMs: Long
}
