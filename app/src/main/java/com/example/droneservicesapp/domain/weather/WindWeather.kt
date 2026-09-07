package com.example.droneservicesapp.domain.weather

import kotlin.math.roundToInt

data class WindWeather(
    val speedKilometersPerHour: Double,
    val directionFromDegrees: Double,
) {
    val directionFromCompass: String
        get() {
            val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            val normalized = ((directionFromDegrees % 360.0) + 360.0) % 360.0
            return directions[(normalized / 45.0).roundToInt() % directions.size]
        }

    /** Meteorological wind direction indicates where the wind comes from. */
    val directionToDegrees: Float
        get() = ((directionFromDegrees + 180.0) % 360.0).toFloat()
}
