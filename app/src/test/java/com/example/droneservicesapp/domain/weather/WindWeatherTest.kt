package com.example.droneservicesapp.domain.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class WindWeatherTest {
    @Test
    fun mapsMeteorologicalDirectionAndFlightArrow() {
        val wind = WindWeather(speedKilometersPerHour = 18.0, directionFromDegrees = 315.0)

        assertEquals("NW", wind.directionFromCompass)
        assertEquals(135f, wind.directionToDegrees, 0.001f)
    }
}
