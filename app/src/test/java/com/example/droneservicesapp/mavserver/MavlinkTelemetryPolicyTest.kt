package com.example.droneservicesapp.mavserver

import com.example.droneservicesapp.data.mavlink.MavlinkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MavlinkTelemetryPolicyTest {
    @Test
    fun udpKeepsExistingTelemetryBehavior() {
        assertTrue(
            MavlinkTelemetryPolicy.additionalRequests(MavlinkConfig.InterfaceType.UDP).isEmpty()
        )
    }

    @Test
    fun tcpRequestsGpsPositionAndHudStreams() {
        val requests = MavlinkTelemetryPolicy.additionalRequests(MavlinkConfig.InterfaceType.TCP)

        assertEquals(listOf(24, 33, 74), requests.map { it.messageId })
        assertEquals(listOf(500_000f, 200_000f, 200_000f), requests.map { it.intervalUs })
    }
}
