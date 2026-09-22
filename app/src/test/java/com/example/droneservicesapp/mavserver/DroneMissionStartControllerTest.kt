package com.example.droneservicesapp.mavserver

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.droneservicesapp.data.mavlink.MavlinkClient
import com.example.droneservicesapp.data.mavlink.MavlinkConfig
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.CommandAck
import io.dronefleet.mavlink.common.CommandLong
import io.dronefleet.mavlink.common.MavCmd
import io.dronefleet.mavlink.common.MavResult
import io.reactivex.Observable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DroneMissionStartControllerTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val client = RecordingMavlinkClient()

    @After fun tearDown() = scope.cancel()

    @Test fun `mission start sends the standard command and waits for acknowledgement`() {
        val controller = controller(connected = true, armed = true, hasMission = true)

        assertEquals(MissionStartRequestResult.SENT, controller.requestStart())
        val command = client.sent.single() as CommandLong
        assertEquals(MavCmd.MAV_CMD_MISSION_START, command.command().entry())
        assertEquals(0f, command.param1())
        assertEquals(0f, command.param2())
        assertEquals(MissionStartCommandState.Pending, controller.state.value)

        controller.onCommandAck(
            CommandAck.builder().command(MavCmd.MAV_CMD_MISSION_START)
                .result(MavResult.MAV_RESULT_ACCEPTED).build()
        )
        assertEquals(MissionStartCommandState.Succeeded, controller.state.value)
    }

    @Test fun `mission start is not sent while disarmed`() {
        val controller = controller(connected = true, armed = false, hasMission = true)

        assertEquals(MissionStartRequestResult.NOT_ARMED, controller.requestStart())
        assertTrue(client.sent.isEmpty())
    }

    @Test fun `mission start is not sent without a downloaded mission`() {
        val controller = controller(connected = true, armed = true, hasMission = false)

        assertEquals(MissionStartRequestResult.MISSION_UNAVAILABLE, controller.requestStart())
        assertTrue(client.sent.isEmpty())
    }

    private fun controller(connected: Boolean, armed: Boolean, hasMission: Boolean) =
        DroneMissionStartController(
            mavlinkClient = client,
            scope = scope,
            isConnected = { connected },
            isArmed = { armed },
            hasMission = { hasMission },
            targetSystemId = { 1 },
            targetComponentId = { 1 },
            commandTimeoutMs = 60_000,
        )

    private class RecordingMavlinkClient : MavlinkClient {
        val sent = mutableListOf<Any>()
        override fun start(config: MavlinkConfig) = Unit
        override fun stop() = Unit
        override fun <T : Any> waitFor(
            clazz: Class<T>,
            timeoutMs: Long,
            filter: (MavlinkMessage<*>) -> Boolean,
        ): MavlinkMessage<T>? = null
        override fun send2(systemId: Int, componentId: Int, payload: Any) { sent += payload }
        override fun sendGpsRtcmData(
            targetSystemId: Int,
            targetComponentId: Int,
            rtcmPayload: ByteArray,
            rtcmMessageType: Int?,
        ) = Unit
        override fun currentRtcmQueueDepth(): Int = 0
        override fun messages(): Observable<MavlinkMessage<*>> = Observable.never()
        override val lastHeartbeatMs: Long = 0L
    }
}
