package com.example.droneservicesapp.data.rtk

data class RtkConfig(
    val ip: String = "",
    val port: Int = 2101,
    val username: String = "",
    val password: String = "",
    val mountpoint: String = "",
    val lastFetchSucceeded: Boolean = false,
    val lastStatusMessage: String = ""
)
