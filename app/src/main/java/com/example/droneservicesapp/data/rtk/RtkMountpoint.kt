package com.example.droneservicesapp.data.rtk

data class RtkMountpoint(
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val hasCoordinates: Boolean
        get() = latitude != null &&
            longitude != null &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0
}
