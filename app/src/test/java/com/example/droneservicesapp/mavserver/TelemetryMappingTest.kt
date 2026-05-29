package com.example.droneservicesapp.mavserver

import io.dronefleet.mavlink.common.GpsFixType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelemetryMappingTest {
    @Test
    fun mapsGpsFixTypesWithoutDowngradingRtk() {
        assertEquals("No GPS", TelemetryMapping.gpsFixLabel(GpsFixType.GPS_FIX_TYPE_NO_GPS))
        assertEquals("No GPS", TelemetryMapping.gpsFixLabel(GpsFixType.GPS_FIX_TYPE_NO_FIX))
        assertEquals("2D Lock", TelemetryMapping.gpsFixLabel(GpsFixType.GPS_FIX_TYPE_2D_FIX))
        assertEquals("3D Lock", TelemetryMapping.gpsFixLabel(GpsFixType.GPS_FIX_TYPE_3D_FIX))
        assertEquals("DGPS", TelemetryMapping.gpsFixLabel(GpsFixType.GPS_FIX_TYPE_DGPS))
        assertEquals("RTK Float", TelemetryMapping.gpsFixLabel(GpsFixType.GPS_FIX_TYPE_RTK_FLOAT))
        assertEquals("RTK Fixed", TelemetryMapping.gpsFixLabel(GpsFixType.GPS_FIX_TYPE_RTK_FIXED))
    }

    @Test
    fun mapsGpsFixQualityForColors() {
        assertEquals(
            GpsFixQuality.DISCONNECTED,
            TelemetryMapping.gpsFixQuality(GpsFixType.GPS_FIX_TYPE_RTK_FIXED, isConnected = false)
        )
        assertEquals(
            GpsFixQuality.FIX_3D,
            TelemetryMapping.gpsFixQuality(GpsFixType.GPS_FIX_TYPE_3D_FIX, isConnected = true)
        )
        assertEquals(
            GpsFixQuality.RTK_FLOAT,
            TelemetryMapping.gpsFixQuality(GpsFixType.GPS_FIX_TYPE_RTK_FLOAT, isConnected = true)
        )
        assertEquals(
            GpsFixQuality.RTK_FIXED,
            TelemetryMapping.gpsFixQuality(GpsFixType.GPS_FIX_TYPE_RTK_FIXED, isConnected = true)
        )
    }

    @Test
    fun formatsBatteryPercentAsClampedInteger() {
        assertEquals(56, TelemetryMapping.displayPercentFromFraction(0.5599f))
        assertEquals(55, TelemetryMapping.displayPercentFromFraction(0.552f))
        assertEquals(100, TelemetryMapping.displayPercentFromFraction(1.2f))
        assertNull(TelemetryMapping.displayPercentFromFraction(-1.0f))
        assertNull(TelemetryMapping.displayPercentFromFraction(Float.NaN))
    }

    @Test
    fun calculatesGlobalHorizontalSpeed() {
        assertEquals(
            5.0f,
            TelemetryMapping.globalHorizontalSpeedMetersPerSecond(300, 400) ?: -1f,
            0.001f
        )
    }

    @Test
    fun filtersInvalidSprayerSentinels() {
        assertEquals(56, TelemetryMapping.displayPercentFromRaw(55.99f))
        assertNull(TelemetryMapping.displayPercentFromRaw(65535f))
        assertNull(TelemetryMapping.displayPercentFromRaw(-1f))
        assertNull(TelemetryMapping.displayPercentFromRaw(12000f))
        assertNull(TelemetryMapping.displayPercentFromRaw(Float.NaN))
    }

    @Test
    fun calculatesHaversineDistance() {
        val distance = TelemetryMapping.haversineDistanceMeters(
            35.3387,
            25.1442,
            35.3397,
            25.1442
        )
        assertEquals(111.0, distance, 2.0)
    }
}
