package io.dronefleet.mavlink

import com.example.droneservicesapp.data.mavlink.MavlinkClient
import com.example.droneservicesapp.data.mavlink.MavlinkConfig
import com.example.droneservicesapp.data.mavlink.MissionService
import io.dronefleet.mavlink.common.MavCmd
import io.dronefleet.mavlink.common.MavFrame
import io.dronefleet.mavlink.common.MavMissionType
import io.dronefleet.mavlink.common.MissionCount
import io.dronefleet.mavlink.common.MissionItemInt
import io.dronefleet.mavlink.common.MissionRequestInt
import io.dronefleet.mavlink.common.MissionRequestList
import io.dronefleet.mavlink.protocol.MavlinkPacket
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.junit.Assert.assertEquals
import org.junit.Test

class MissionServiceImmediateResponseTest {

    @Test
    fun downloadCapturesMissionCountAndItemReturnedDuringSend() {
        val client = ImmediateMissionClient()
        val service = MissionService(client).apply {
            targetSystemId = 1
            targetComponentId = 1
        }

        val downloaded = service.downloadMission(timeoutMs = 25L)

        assertEquals(1, downloaded.size)
        assertEquals(0, downloaded.single().seq())
        assertEquals(350_000_000, downloaded.single().x())
        assertEquals(240_000_000, downloaded.single().y())
    }

    private class ImmediateMissionClient : BaseFakeMavlinkClient() {
        override fun send2(systemId: Int, componentId: Int, payload: Any) {
            when (payload) {
                is MissionRequestList -> emit(
                    MissionCount.builder()
                        .targetSystem(systemId)
                        .targetComponent(componentId)
                        .count(1)
                        .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                        .build()
                )
                is MissionRequestInt -> emit(
                    MissionItemInt.builder()
                        .targetSystem(systemId)
                        .targetComponent(componentId)
                        .seq(payload.seq())
                        .frame(MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT)
                        .command(MavCmd.MAV_CMD_NAV_WAYPOINT)
                        .current(0)
                        .autocontinue(1)
                        .param1(0f)
                        .param2(0f)
                        .param3(0f)
                        .param4(0f)
                        .x(350_000_000)
                        .y(240_000_000)
                        .z(10f)
                        .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                        .build()
                )
            }
        }
    }

    private abstract class BaseFakeMavlinkClient : MavlinkClient {
        private val subject = PublishSubject.create<MavlinkMessage<*>>().toSerialized()

        override fun start(config: MavlinkConfig) = Unit
        override fun stop() = Unit
        override fun sendGpsRtcmData(
            targetSystemId: Int,
            targetComponentId: Int,
            rtcmPayload: ByteArray,
            rtcmMessageType: Int?,
        ) = Unit
        override fun currentRtcmQueueDepth(): Int = 0
        override fun messages(): Observable<MavlinkMessage<*>> = subject.hide()
        override val lastHeartbeatMs: Long = 0L

        override fun <T : Any> waitFor(
            clazz: Class<T>,
            timeoutMs: Long,
            filter: (MavlinkMessage<*>) -> Boolean,
        ): MavlinkMessage<T>? = null

        protected fun emit(payload: Any) {
            val packet = MavlinkPacket.createUnsignedMavlink2Packet(
                0,
                1,
                1,
                0,
                0,
                byteArrayOf(),
            )
            subject.onNext(MavlinkMessage(packet, payload))
        }
    }
}
