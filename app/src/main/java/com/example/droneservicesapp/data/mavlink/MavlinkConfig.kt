package com.example.droneservicesapp.data.mavlink

import android.net.Network

data class MavlinkConfig(
    val interfaceType: InterfaceType = InterfaceType.UDP,
    val port: Int = 14550,
    val targetHost: String? = null,
    val targetPort: Int = 14550,
    val network: Network? = null
) {
    enum class InterfaceType { UDP, TCP, SERIAL }
}
