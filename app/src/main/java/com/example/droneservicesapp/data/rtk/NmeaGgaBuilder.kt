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
    private val utcFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmmss.00")

    fun build(location: Location, satellites: Int = 12): String {
        val latitude = location.latitude
        val longitude = location.longitude
        val altitude = location.altitude.takeUnless { it.isNaN() } ?: 0.0
        val quality = QUALITY_GPS_FIX

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
            append(satellites.coerceAtLeast(1))
            append(",1.0,")
            append(String.format(java.util.Locale.US, "%.1f", altitude))
            append(",M,0.0,M,,")
        }

        val checksum = body.fold(0) { acc, char -> acc xor char.code }
        Log.i(
            TAG,
            "gga built lat=${String.format(java.util.Locale.US, "%.6f", latitude)} lon=${String.format(java.util.Locale.US, "%.6f", longitude)} quality=$quality satellites=${satellites.coerceAtLeast(1)} altitudeM=${String.format(java.util.Locale.US, "%.1f", altitude)}"
        )
        return "\$$body*${checksum.toString(16).uppercase().padStart(2, '0')}\r\n"
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
}
