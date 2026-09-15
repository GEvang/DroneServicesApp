package com.example.droneservicesapp.data.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class MavlinkUdpPeerTrackerTest {
    private val address = InetAddress.getByName("192.168.43.1")
    private val primary = UdpEndpoint(address, 54111)
    private val reflected = UdpEndpoint(address, 14550)

    @Test
    fun `does not elect a peer before an autopilot heartbeat`() {
        val tracker = MavlinkUdpPeerTracker()

        assertFalse(tracker.shouldAccept(primary, containsAutopilotHeartbeat = false, nowMs = 0L))
        assertNull(tracker.currentEndpoint(0L))
    }

    @Test
    fun `locks to heartbeat peer and rejects a second port on the same address`() {
        val tracker = MavlinkUdpPeerTracker()

        assertTrue(tracker.shouldAccept(primary, containsAutopilotHeartbeat = true, nowMs = 100L))
        assertTrue(tracker.shouldAccept(primary, containsAutopilotHeartbeat = false, nowMs = 200L))
        assertFalse(tracker.shouldAccept(reflected, containsAutopilotHeartbeat = true, nowMs = 300L))
        assertEquals(primary, tracker.currentEndpoint(300L))
    }

    @Test
    fun `relearns from a heartbeat after selected peer is silent`() {
        val tracker = MavlinkUdpPeerTracker(silenceTimeoutMs = 1_000L)
        tracker.shouldAccept(primary, containsAutopilotHeartbeat = true, nowMs = 100L)

        assertFalse(tracker.shouldAccept(reflected, containsAutopilotHeartbeat = false, nowMs = 1_101L))
        assertTrue(tracker.shouldAccept(reflected, containsAutopilotHeartbeat = true, nowMs = 1_102L))
        assertEquals(reflected, tracker.currentEndpoint(1_102L))
    }

    @Test
    fun `recognizes an autopilot heartbeat but not a GCS heartbeat`() {
        assertTrue(MavlinkDatagramInspector.containsAutopilotHeartbeat(heartbeatV2(type = 2, autopilot = 3), 0, 21))
        assertFalse(MavlinkDatagramInspector.containsAutopilotHeartbeat(heartbeatV2(type = 6, autopilot = 8), 0, 21))
    }

    @Test
    fun `recognizes MAVLink one heartbeat`() {
        val heartbeat = heartbeatV1(type = 2, autopilot = 3)

        assertTrue(MavlinkDatagramInspector.containsAutopilotHeartbeat(heartbeat, 0, heartbeat.size))
    }

    private fun heartbeatV2(type: Int, autopilot: Int): ByteArray = ByteArray(21).apply {
        this[0] = 0xFD.toByte()
        this[1] = 9
        this[5] = 1
        this[6] = 1
        this[7] = 0
        this[8] = 0
        this[9] = 0
        this[14] = type.toByte()
        this[15] = autopilot.toByte()
        this[18] = 3
    }

    private fun heartbeatV1(type: Int, autopilot: Int): ByteArray = ByteArray(17).apply {
        this[0] = 0xFE.toByte()
        this[1] = 9
        this[3] = 1
        this[4] = 1
        this[5] = 0
        this[10] = type.toByte()
        this[11] = autopilot.toByte()
        this[14] = 3
    }
}
