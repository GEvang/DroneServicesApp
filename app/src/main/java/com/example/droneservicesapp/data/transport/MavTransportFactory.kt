package com.example.droneservicesapp.data.transport

import com.example.droneservicesapp.data.mavlink.MavlinkConfig

interface MavTransportFactory {
    fun create(config: MavlinkConfig): MavTransport
}

class DefaultMavTransportFactory : MavTransportFactory {
    override fun create(config: MavlinkConfig): MavTransport {
        return when (config.interfaceType) {
            MavlinkConfig.InterfaceType.UDP -> UdpTransport(config.port, config.targetHost)
            else -> throw IllegalArgumentException("Not implemented yet: ${config.interfaceType}")
        }
    }
}
