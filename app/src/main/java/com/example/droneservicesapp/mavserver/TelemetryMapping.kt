package com.example.droneservicesapp.mavserver

import io.dronefleet.mavlink.common.GpsFixType
import kotlin.math.roundToInt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal object TelemetryMapping {
    const val UNKNOWN_PERCENT = -1
    const val UINT16_MAX = 65535

    fun gpsFixLabel(fixType: GpsFixType?): String {
        return when (gpsFixQuality(fixType, isConnected = true)) {
            GpsFixQuality.FIX_2D -> "2D Lock"
            GpsFixQuality.FIX_3D -> "3D Lock"
            GpsFixQuality.DGPS -> "DGPS"
            GpsFixQuality.RTK_FLOAT -> "RTK Float"
            GpsFixQuality.RTK_FIXED -> "RTK Fixed"
            else -> "No GPS"
        }
    }

    fun gpsFixQuality(fixType: GpsFixType?, isConnected: Boolean): GpsFixQuality {
        if (!isConnected) return GpsFixQuality.DISCONNECTED
        return when (fixType) {
            GpsFixType.GPS_FIX_TYPE_2D_FIX -> GpsFixQuality.FIX_2D
            GpsFixType.GPS_FIX_TYPE_3D_FIX -> GpsFixQuality.FIX_3D
            GpsFixType.GPS_FIX_TYPE_DGPS -> GpsFixQuality.DGPS
            GpsFixType.GPS_FIX_TYPE_RTK_FLOAT -> GpsFixQuality.RTK_FLOAT
            GpsFixType.GPS_FIX_TYPE_RTK_FIXED -> GpsFixQuality.RTK_FIXED
            GpsFixType.GPS_FIX_TYPE_NO_GPS,
            GpsFixType.GPS_FIX_TYPE_NO_FIX -> GpsFixQuality.NO_GPS
            null -> GpsFixQuality.NO_GPS
            else -> GpsFixQuality.UNKNOWN
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

    fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2.0) * sin(dLon / 2.0)
        return earthRadiusMeters * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun isValidSignedCentimetersPerSecond(value: Int): Boolean {
        return value != Short.MAX_VALUE.toInt() && value != Short.MIN_VALUE.toInt()
    }
}
