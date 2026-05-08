package com.example.droneservicesapp.mavserver

import io.dronefleet.mavlink.common.GpsFixType
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal object TelemetryMapping {
    const val UNKNOWN_PERCENT = -1
    const val UINT16_MAX = 65535

    fun gpsFixLabel(fixType: GpsFixType?): String {
        return when (fixType) {
            GpsFixType.GPS_FIX_TYPE_2D_FIX -> "2D Lock"
            GpsFixType.GPS_FIX_TYPE_3D_FIX -> "3D Lock"
            GpsFixType.GPS_FIX_TYPE_DGPS -> "DGPS"
            GpsFixType.GPS_FIX_TYPE_RTK_FLOAT -> "RTK Float"
            GpsFixType.GPS_FIX_TYPE_RTK_FIXED -> "RTK Fixed"
            else -> "No GPS"
        }
    }

    fun batteryFractionFromRaw(rawPercent: Int): Float {
        return if (rawPercent in 0..100) rawPercent / 100.0f else -1.0f
    }

    fun displayPercentFromFraction(fraction: Float?): Int? {
        if (fraction == null || !fraction.isFinite() || fraction < 0f) return null
        return (fraction * 100.0f).roundToInt().coerceIn(0, 100)
    }

    fun displayPercentFromRaw(rawPercent: Float?): Int? {
        if (rawPercent == null || !rawPercent.isFinite()) return null
        if (rawPercent < 0f || rawPercent >= UINT16_MAX.toFloat()) return null
        return rawPercent.roundToInt().takeIf { it in 0..100 }?.coerceIn(0, 100)
    }

    fun globalHorizontalSpeedMetersPerSecond(vxCentimetersPerSecond: Int, vyCentimetersPerSecond: Int): Float? {
        if (!isValidSignedCentimetersPerSecond(vxCentimetersPerSecond) ||
            !isValidSignedCentimetersPerSecond(vyCentimetersPerSecond)
        ) {
            return null
        }
        val speed = sqrt(
            vxCentimetersPerSecond.toDouble() * vxCentimetersPerSecond.toDouble() +
                vyCentimetersPerSecond.toDouble() * vyCentimetersPerSecond.toDouble()
        ) / 100.0
        return speed.toFloat().takeIf { isValidGroundSpeedMetersPerSecond(it) }
    }

    fun gpsRawSpeedMetersPerSecond(velCentimetersPerSecond: Int): Float? {
        if (velCentimetersPerSecond < 0 || velCentimetersPerSecond >= UINT16_MAX) return null
        return (velCentimetersPerSecond / 100.0f).takeIf { isValidGroundSpeedMetersPerSecond(it) }
    }

    fun isValidGroundSpeedMetersPerSecond(speed: Float): Boolean {
        return speed.isFinite() && speed >= 0f && speed < 150f
    }

    private fun isValidSignedCentimetersPerSecond(value: Int): Boolean {
        return value != Short.MAX_VALUE.toInt() && value != Short.MIN_VALUE.toInt()
    }
}
