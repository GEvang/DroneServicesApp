package com.example.droneservicesapp.data.rtk

data class RtkConfig(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = 2101,
    val mountpoint: String = "",
    val username: String = "",
    val password: String = "",
    val useTls: Boolean = false
)
