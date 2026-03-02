package com.example.droneservicesapp.domain.model

data class LatLon(
    val lat: Double,
    val lon: Double
) {
    fun toE7Lat(): Int = (lat * 1e7).toInt()

    fun toE7Lon(): Int = (lon * 1e7).toInt()
}