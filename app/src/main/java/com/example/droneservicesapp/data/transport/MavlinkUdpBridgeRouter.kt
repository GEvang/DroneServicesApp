package com.example.droneservicesapp.data.transport

import java.net.InetAddress

/**
 * Pure endpoint routing policy used by [UdpTransport]'s optional QGC bridge.
 * Aircraft packets may be NAT-forwarded by the radio, so the QGC endpoint is
 * identified explicitly and all other inbound packets stay on the aircraft path.
 */
internal class MavlinkUdpBridgeRouter(
    private val aircraftAddress: InetAddress?,
    private val aircraftPort: Int,
    private val qgcAddress: InetAddress?,
    private val qgcPort: Int,
    enabled: Boolean,
) {
    val isActive: Boolean = enabled &&
        aircraftAddress != null &&
        qgcAddress != null &&
        aircraftAddress != qgcAddress &&
        aircraftPort > 0 &&
        qgcPort > 0

    fun routeIncoming(sourceAddress: InetAddress): IncomingRoute {
        if (!isActive) return IncomingRoute.DeliverToApp
        return when (sourceAddress) {
            qgcAddress -> IncomingRoute.ForwardOnly(requireNotNull(aircraftAddress), aircraftPort)
            else -> IncomingRoute.DeliverAndForward(requireNotNull(qgcAddress), qgcPort)
        }
    }
}

internal sealed class IncomingRoute {
    object DeliverToApp : IncomingRoute()
    data class ForwardOnly(val address: InetAddress, val port: Int) : IncomingRoute()
    data class DeliverAndForward(val address: InetAddress, val port: Int) : IncomingRoute()
}
