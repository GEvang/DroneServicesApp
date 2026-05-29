package com.example.droneservicesapp.data.rtk

data class RtkConfig(
    val ip: String = "",
    val port: Int = 2101,
    val username: String = "",
    val password: String = "",
    val mountpoint: String = "",
    val mountpointLatitude: Double? = null,
    val mountpointLongitude: Double? = null,
    val lastFetchSucceeded: Boolean = false,
    val lastStatusMessage: String = ""
) {
    val selectedMountpoint: RtkMountpoint?
        get() = mountpoint.trim().takeIf { it.isNotEmpty() }?.let { name ->
            RtkMountpoint(name, mountpointLatitude, mountpointLongitude)
        }
}
