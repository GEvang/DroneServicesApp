package com.example.droneservicesapp.domain.model

data class MissionParams(
    val altitude: Double = 0.0,
    val lineDistance: Double = 5.0,
    val angle: Double = 90.0,
    val sprayer: Double = 75.0,
    val speed: Double = 5.0,
    val altitudeReferenceMode: AltitudeReferenceMode = AltitudeReferenceMode.TERRAIN
)
