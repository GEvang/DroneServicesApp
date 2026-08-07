package com.example.droneservicesapp.data.ortho

data class OrthoBounds(
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double
) {
    val centerLon: Double get() = (minLon + maxLon) / 2.0
    val centerLat: Double get() = (minLat + maxLat) / 2.0
}
