package com.example.droneservicesapp.domain.survey

import kotlin.math.roundToInt

data class SprayFlowSetting(
    val litersPerMinute: Double,
    val servo5Pwm: Float,
)

/** Calibrated pump settings. Values between these points must never be commanded. */
object SprayFlowCalibration {
    const val MAX_LITERS_PER_MINUTE = 4.0
    const val CLOSED_PWM = 1000.0f

    val settings: List<SprayFlowSetting> = listOf(
        SprayFlowSetting(2.5, 1350.0f),
        SprayFlowSetting(3.0, 1400.0f),
        SprayFlowSetting(3.5, 1600.0f),
        SprayFlowSetting(3.8, 1800.0f),
        SprayFlowSetting(3.9, 1800.0f),
        // The pump is saturated at 4 L/min in the 2000-2200 PWM range. Use the
        // lower edge so the mission does not command more pump drive than needed.
        SprayFlowSetting(4.0, 2000.0f),
    )

    fun nearestSetting(litersPerMinute: Double): SprayFlowSetting =
        settings.minByOrNull { kotlin.math.abs(it.litersPerMinute - litersPerMinute) }
            ?: settings.first()

    fun settingAt(index: Int): SprayFlowSetting = settings[index.coerceIn(settings.indices)]

    fun indexForFlow(litersPerMinute: Double): Int {
        val setting = nearestSetting(litersPerMinute)
        return settings.indexOf(setting).coerceAtLeast(0)
    }

    fun flowForIntensityPercent(intensityPercent: Double): Double {
        if (intensityPercent <= 0.0) return 0.0
        val impliedFlow = intensityPercent.coerceIn(0.0, 100.0) / 100.0 * MAX_LITERS_PER_MINUTE
        return nearestSetting(impliedFlow).litersPerMinute
    }

    fun intensityPercentForFlow(litersPerMinute: Double): Int {
        if (litersPerMinute <= 0.0) return 0
        return (nearestSetting(litersPerMinute).litersPerMinute / MAX_LITERS_PER_MINUTE * 100.0)
            .roundToInt()
    }

    fun servo5PwmForIntensityPercent(intensityPercent: Int): Float {
        if (intensityPercent <= 0) return CLOSED_PWM
        return nearestSetting(flowForIntensityPercent(intensityPercent.toDouble())).servo5Pwm
    }
}
