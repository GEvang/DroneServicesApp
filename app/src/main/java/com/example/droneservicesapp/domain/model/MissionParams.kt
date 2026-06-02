package com.example.droneservicesapp.domain.model

data class MissionParams(
    val altitude: Double = 2.0,
    val lineDistance: Double = 1.0,
    val angle: Double = 1.0,
    val sprayer: Double = 0.0,
    val speed: Double = 1.0,
    val altitudeReferenceMode: AltitudeReferenceMode = AltitudeReferenceMode.RELATIVE
)
