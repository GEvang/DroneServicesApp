package com.example.droneservicesapp.data.mavlink

data class MavlinkConfig(
    val interfaceType: InterfaceType = InterfaceType.UDP,
    val port: Int = 14550
) {
    enum class InterfaceType { UDP, TCP, SERIAL }
}
