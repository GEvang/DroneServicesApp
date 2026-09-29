package com.example.droneservicesapp.mavserver

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import com.example.droneservicesapp.data.mavlink.MavlinkClient
import com.example.droneservicesapp.data.mavlink.MavlinkConfig
import com.example.droneservicesapp.data.terrain.SrtmPreparationProgress
import com.example.droneservicesapp.data.terrain.TerrainElevationSource
import com.example.droneservicesapp.data.terrain.TerrainSourceRepository
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.terrain.SrtmTileId
import com.example.droneservicesapp.domain.terrain.TerrainCoveragePlan
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.TerrainData
import io.dronefleet.mavlink.common.TerrainRequest
import io.reactivex.Observable
import java.math.BigInteger
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DroneTerrainControllerTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val client = RecordingMavlinkClient()
    private val state = MutableLiveData<TerrainProvisioningState>(TerrainProvisioningState.Idle)

    @After fun tearDown() = scope.cancel()

    @Test fun `mask bits advance east and each response contains north rows with east first`() {
        val controller = controller(source())
        controller.prepare(plan()) {}
        val request = TerrainRequest.builder()
            .lat(350000000)
            .lon(240000000)
            .gridSpacing(30)
            .mask(BigInteger.valueOf(3L))
            .build()

        controller.handle(1, request)

        val responses = client.sent.filterIsInstance<TerrainData>()
        assertEquals(listOf(0, 1), responses.map { it.gridbit() })
        assertEquals(16, responses[0].data().size)
        assertTrue(responses[0].data()[1] > responses[0].data()[0])
        assertTrue(responses[0].data()[4] > responses[0].data()[0])
        assertTrue(responses[1].data()[0] > responses[0].data()[3])
    }

    @Test fun `duplicate requests produce identical terrain blocks`() {
        val controller = controller(source())
        controller.prepare(plan()) {}
        val request = TerrainRequest.builder().lat(350000000).lon(240000000)
            .gridSpacing(30).mask(BigInteger.ONE).build()

        controller.handle(1, request)
        controller.handle(1, request)

        val responses = client.sent.filterIsInstance<TerrainData>()
        assertEquals(2, responses.size)
        assertEquals(responses[0].data(), responses[1].data())
    }

    @Test fun `missing elevation fails instead of sending zero terrain`() {
        val controller = controller(source { _, _ -> null })
        controller.prepare(plan()) {}
        val request = TerrainRequest.builder().lat(350000000).lon(240000000)
            .gridSpacing(30).mask(BigInteger.ONE).build()

        controller.handle(1, request)

        assertTrue(client.sent.none { it is TerrainData })
        assertTrue(state.value is TerrainProvisioningState.Failed)
        assertEquals(TerrainFailure.MISSING_ELEVATION, (state.value as TerrainProvisioningState.Failed).failure)
    }

    @Test fun `tablet download succeeds while aircraft is disconnected`() {
        val controller = controller(source(), connected = false)

        controller.download(plan()) {}

        val result = state.value as TerrainProvisioningState.SourceReady
        assertEquals(1, result.tileCount)
        assertEquals(LatLon(35.0, 24.0), result.center)
        assertEquals(1_000.0, result.radiusMeters, 0.0)
    }

    @Test fun `disconnect preserves a completed tablet download`() {
        val controller = controller(source(), connected = false)
        controller.download(plan()) {}

        controller.onDisconnected()

        assertTrue(state.value is TerrainProvisioningState.SourceReady)
    }

    private fun controller(
        source: TerrainElevationSource,
        connected: Boolean = true,
    ) = DroneTerrainController(
        mavlinkClient = client,
        sourceRepository = object : TerrainSourceRepository {
            override suspend fun prepare(
                plan: TerrainCoveragePlan,
                onProgress: (SrtmPreparationProgress) -> Unit,
            ): TerrainElevationSource = source
        },
        scope = scope,
        state = state,
        isConnected = { connected },
        targetSystemId = { 1 },
    )

    private fun source(
        elevation: (Double, Double) -> Int? = { lat, lon ->
            (((lat - 35.0) + (lon - 24.0)) * 100_000.0).roundToInt()
        },
    ) = object : TerrainElevationSource {
        override val plan = plan()
        override fun elevationMeters(latitude: Double, longitude: Double): Int? = elevation(latitude, longitude)
        override fun contains(latitude: Double, longitude: Double): Boolean = true
        override fun close() = Unit
    }

    private fun plan() = TerrainCoveragePlan(
        center = LatLon(35.0, 24.0),
        radiusMeters = 1_000.0,
        requiredPathSamples = listOf(LatLon(35.0, 24.0)),
        sourceTiles = setOf(SrtmTileId(35, 24)),
    )

    private class RecordingMavlinkClient : MavlinkClient {
        val sent = mutableListOf<Any>()
        override fun start(config: MavlinkConfig) = Unit
        override fun stop() = Unit
        override fun <T : Any> waitFor(
            clazz: Class<T>, timeoutMs: Long, filter: (MavlinkMessage<*>) -> Boolean,
        ): MavlinkMessage<T>? = null
        override fun send2(systemId: Int, componentId: Int, payload: Any) { sent += payload }
        override fun sendGpsRtcmData(
            targetSystemId: Int, targetComponentId: Int, rtcmPayload: ByteArray, rtcmMessageType: Int?,
        ) = Unit
        override fun currentRtcmQueueDepth(): Int = 0
        override fun messages(): Observable<MavlinkMessage<*>> = Observable.never()
        override val lastHeartbeatMs: Long = 0L
    }
}
