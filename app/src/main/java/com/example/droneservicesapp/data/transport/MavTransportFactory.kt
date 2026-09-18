package com.example.droneservicesapp.data.transport

import com.example.droneservicesapp.data.mavlink.MavlinkConfig

interface MavTransportFactory {
    fun create(config: MavlinkConfig): MavTransport
}

class DefaultMavTransportFactory : MavTransportFactory {
    override fun create(config: MavlinkConfig): MavTransport {
        return when (config.interfaceType) {
            MavlinkConfig.InterfaceType.UDP -> UdpTransport(
                listenPort = config.port,
                targetHost = config.targetHost,
                targetPort = config.targetPort,
                qgcBridgeEnabled = config.qgcBridgeEnabled,
                qgcBridgeHost = config.qgcBridgeHost,
                qgcBridgePort = config.qgcBridgePort,
                network = config.network
            )
            MavlinkConfig.InterfaceType.TCP -> TcpTransport(
                host = config.targetHost
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalArgumentException("A target IP address is required for TCP"),
                port = config.targetPort,
                network = config.network,
            )
            MavlinkConfig.InterfaceType.SERIAL ->
                throw IllegalArgumentException("Not implemented yet: ${config.interfaceType}")
        }
    }
}
