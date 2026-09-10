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

class DroneArmControllerTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val client = RecordingMavlinkClient()

    @After fun tearDown() = scope.cancel()

    @Test fun `arm command uses guarded standard MAVLink command and waits for heartbeat`() {
        val controller = controller(connected = true, armed = false)

        assertEquals(ArmRequestResult.SENT, controller.requestArm())
        val command = client.sent.single() as CommandLong
        assertEquals(MavCmd.MAV_CMD_COMPONENT_ARM_DISARM, command.command().entry())
        assertEquals(1f, command.param1())
        assertTrue(controller.state.value is ArmCommandState.Pending)

        controller.onCommandAck(
            CommandAck.builder().command(MavCmd.MAV_CMD_COMPONENT_ARM_DISARM)
                .result(MavResult.MAV_RESULT_ACCEPTED).build()
        )
        assertTrue(controller.state.value is ArmCommandState.Pending)
        controller.onHeartbeatArmed(true)
        assertEquals(ArmCommandState.Succeeded, controller.state.value)
    }

    @Test fun `disconnected aircraft never receives an arm command`() {
        val controller = controller(connected = false, armed = false)

        assertEquals(ArmRequestResult.DISCONNECTED, controller.requestArm())
        assertTrue(client.sent.isEmpty())
    }

    @Test fun `already armed aircraft never receives a second arm command`() {
        val controller = controller(connected = true, armed = true)

        assertEquals(ArmRequestResult.ALREADY_ARMED, controller.requestArm())
        assertTrue(client.sent.isEmpty())
    }

    private fun controller(connected: Boolean, armed: Boolean) = DroneArmController(
        mavlinkClient = client,
        scope = scope,
        isConnected = { connected },
        isArmed = { armed },
        targetSystemId = { 1 },
        targetComponentId = { 1 },
        commandTimeoutMs = 60_000,
    )

    private class RecordingMavlinkClient : MavlinkClient {
        val sent = mutableListOf<Any>()
        override fun start(config: MavlinkConfig) = Unit
        override fun stop() = Unit
        override fun <T : Any> waitFor(clazz: Class<T>, timeoutMs: Long, filter: (MavlinkMessage<*>) -> Boolean): MavlinkMessage<T>? = null
        override fun send2(systemId: Int, componentId: Int, payload: Any) { sent += payload }
        override fun sendGpsRtcmData(targetSystemId: Int, targetComponentId: Int, rtcmPayload: ByteArray, rtcmMessageType: Int?) = Unit
        override fun currentRtcmQueueDepth(): Int = 0
        override fun messages(): Observable<MavlinkMessage<*>> = Observable.never()
        override val lastHeartbeatMs: Long = 0L
    }
}
