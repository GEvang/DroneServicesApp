package com.example.droneservicesapp.domain.survey

import org.junit.Assert.assertEquals
import org.junit.Test

class SprayFlowCalibrationTest {
    @Test
    fun exposesOnlyCalibratedSliderValues() {
        assertEquals(listOf(2.5, 3.0, 3.5, 3.8, 3.9, 4.0), SprayFlowCalibration.settings.map { it.litersPerMinute })
    }

    @Test
    fun snapsArbitraryFlowToNearestCalibratedValue() {
        assertEquals(3.0, SprayFlowCalibration.nearestSetting(3.2).litersPerMinute, 0.001)
        assertEquals(3.5, SprayFlowCalibration.nearestSetting(3.4).litersPerMinute, 0.001)
    }

    @Test
    fun mapsCalibratedFlowsToPumpPwm() {
        assertEquals(1350.0f, SprayFlowCalibration.nearestSetting(2.5).servo5Pwm, 0.001f)
        assertEquals(1400.0f, SprayFlowCalibration.nearestSetting(3.0).servo5Pwm, 0.001f)
        assertEquals(1600.0f, SprayFlowCalibration.nearestSetting(3.5).servo5Pwm, 0.001f)
        assertEquals(1800.0f, SprayFlowCalibration.nearestSetting(3.8).servo5Pwm, 0.001f)
        assertEquals(1800.0f, SprayFlowCalibration.nearestSetting(3.9).servo5Pwm, 0.001f)
        assertEquals(2000.0f, SprayFlowCalibration.nearestSetting(4.0).servo5Pwm, 0.001f)
    }
}
