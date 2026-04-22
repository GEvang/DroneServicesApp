package com.example.droneservicesapp.data.rtk

import android.location.Location
import android.util.Log
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

object NmeaGgaBuilder {

    private const val TAG = "NmeaGgaBuilder"
    private const val QUALITY_GPS_FIX = 1
    private const val QUALITY_DGPS_FIX = 2
    private const val QUALITY_RTK_FIXED = 4
    private const val QUALITY_RTK_FLOAT = 5
    private val utcFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmmss.00")

    fun build(data: GgaData): String {
        val latitude = data.location.latitude
        val longitude = data.location.longitude
        val altitude = data.location.altitude.takeUnless { it.isNaN() } ?: 0.0
        val quality = mapFixTypeToQuality(data.fixType)
        val satellites = data.satellites.coerceAtLeast(1)
        val hdop = String.format(java.util.Locale.US, "%.1f", data.hdop ?: 1.0)

        val latHemisphere = if (latitude >= 0.0) "N" else "S"
        val lonHemisphere = if (longitude >= 0.0) "E" else "W"
        val utcTime = LocalTime.now(ZoneOffset.UTC).format(utcFormatter)
        val body = buildString {
            append("GPGGA,")
            append(utcTime)
            append(',')
            append(toNmeaCoordinate(latitude, isLatitude = true))
            append(',')
            append(latHemisphere)
            append(',')
            append(toNmeaCoordinate(longitude, isLatitude = false))
            append(',')
            append(lonHemisphere)
            append(",$quality,")
            append(satellites)
            append(',')
            append(hdop)
            append(',')
            append(String.format(java.util.Locale.US, "%.1f", altitude))
            append(",M,0.0,M,,")
        }

        val checksum = body.fold(0) { acc, char -> acc xor char.code }
        Log.i(
            TAG,
            "gga built lat=${String.format(java.util.Locale.US, "%.6f", latitude)} lon=${String.format(java.util.Locale.US, "%.6f", longitude)} altitudeM=${String.format(java.util.Locale.US, "%.1f", altitude)} quality=$quality satellites=$satellites hdop=$hdop fixType=${data.fixType}"
        )
        return "\$$body*${checksum.toString(16).uppercase().padStart(2, '0')}\r\n"
    }

    fun build(
        location: Location,
        fixType: Int = -1,
        satellites: Int = 12,
        hdop: Double? = null
    ): String {
        return build(
            GgaData(
                location = location,
                fixType = fixType,
                satellites = satellites,
                hdop = hdop
            )
        )
    }

    private fun mapFixTypeToQuality(fixType: Int): Int {
        return when (fixType) {
            6 -> QUALITY_RTK_FIXED
            5 -> QUALITY_RTK_FLOAT
            4 -> QUALITY_DGPS_FIX
            else -> QUALITY_GPS_FIX
        }
    }

    private fun toNmeaCoordinate(value: Double, isLatitude: Boolean): String {
        val absolute = value.absoluteValue
        val degrees = absolute.toInt()
        val minutes = (absolute - degrees) * 60.0
        return if (isLatitude) {
            String.format(java.util.Locale.US, "%02d%07.4f", degrees, minutes)
        } else {
            String.format(java.util.Locale.US, "%03d%07.4f", degrees, minutes)
        }
    }

    data class GgaData(
        val location: Location,
        val fixType: Int = -1,
        val satellites: Int = 12,
        val hdop: Double? = null
    )
}
