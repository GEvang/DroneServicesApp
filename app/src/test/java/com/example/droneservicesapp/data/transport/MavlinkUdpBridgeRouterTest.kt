package com.example.droneservicesapp.data.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class MavlinkUdpBridgeRouterTest {
    private val aircraft = InetAddress.getByName("192.168.199.26")
    private val qgc = InetAddress.getByName("192.168.199.33")

    @Test
    fun `aircraft traffic is delivered to app and forwarded to QGC`() {
        val router = router()
        val route = router.routeIncoming(aircraft)

        assertEquals(IncomingRoute.DeliverAndForward(qgc, 14550), route)
    }

    @Test
    fun `QGC traffic bypasses app parser and is forwarded to aircraft`() {
        val router = router()
        val route = router.routeIncoming(qgc)

        assertEquals(IncomingRoute.ForwardOnly(aircraft, 14550), route)
    }

    @Test
    fun `NAT forwarded aircraft traffic is delivered and forwarded`() {
        val route = router().routeIncoming(InetAddress.getByName("192.168.199.99"))

        assertEquals(IncomingRoute.DeliverAndForward(qgc, 14550), route)
    }

    @Test
    fun `disabled bridge preserves normal app delivery`() {
        val router = MavlinkUdpBridgeRouter(aircraft, 14550, qgc, 14550, enabled = false)

        assertTrue(router.routeIncoming(qgc) is IncomingRoute.DeliverToApp)
    }

    @Test
    fun `same aircraft and QGC address cannot activate bridge`() {
        val router = MavlinkUdpBridgeRouter(aircraft, 14550, aircraft, 14550, enabled = true)

        assertTrue(!router.isActive)
    }

    private fun router() = MavlinkUdpBridgeRouter(aircraft, 14550, qgc, 14550, enabled = true)
}
