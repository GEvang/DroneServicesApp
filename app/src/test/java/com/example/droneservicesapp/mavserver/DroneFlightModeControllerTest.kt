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

class DroneFlightModeControllerTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val client = RecordingMavlinkClient()

    @After fun tearDown() = scope.cancel()

    @Test fun `AUTO callback runs only after heartbeat confirms AUTO`() {
        val confirmedModes = mutableListOf<ArduCopterFlightMode>()
        val controller = DroneFlightModeController(
            mavlinkClient = client,
            scope = scope,
            isConnected = { true },
            targetSystemId = { 1 },
            targetComponentId = { 1 },
            onModeConfirmed = confirmedModes::add,
            commandTimeoutMs = 60_000,
        )

        assertEquals(FlightModeRequestResult.Sent, controller.requestMode(ArduCopterFlightMode.AUTO))
        assertTrue(confirmedModes.isEmpty())
        val command = client.sent.single() as CommandLong
        assertEquals(MavCmd.MAV_CMD_DO_SET_MODE, command.command().entry())

        controller.onCommandAck(
            CommandAck.builder().command(MavCmd.MAV_CMD_DO_SET_MODE)
                .result(MavResult.MAV_RESULT_ACCEPTED).build()
        )
        assertTrue(confirmedModes.isEmpty())

        controller.onHeartbeatMode(ArduCopterFlightMode.AUTO.customMode)
        assertEquals(listOf(ArduCopterFlightMode.AUTO), confirmedModes)
        assertEquals(
            FlightModeCommandState.Succeeded(ArduCopterFlightMode.AUTO),
            controller.state.value,
        )
    }

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
