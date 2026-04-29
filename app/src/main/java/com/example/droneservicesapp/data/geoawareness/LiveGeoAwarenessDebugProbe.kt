package com.example.droneservicesapp.data.geoawareness

import android.content.Context
import android.util.Log
import com.example.droneservicesapp.domain.geoawareness.LiveGeoAwarenessChecker
import com.example.droneservicesapp.domain.model.LatLon

object LiveGeoAwarenessDebugProbe {

    fun logLiveGeoValidation(context: Context) {
        try {
            val repository = GeoZoneRepository(
                GeoZoneAssetDataSource(context.applicationContext)
            )
            val zones = repository.loadDummyRethymnoZones()
            val checker = LiveGeoAwarenessChecker()

            val hospital = checker.checkDronePosition(
                dronePosition = LatLon(35.36505, 24.47135),
                droneAltitudeMeters = 50.0,
                zones = zones
            ).any { it.id == "GR-RTH-DUMMY-007" }

            val fortezza = checker.checkDronePosition(
                dronePosition = LatLon(35.37220, 24.47050),
                droneAltitudeMeters = 50.0,
                zones = zones
            ).any { it.id == "GR-RTH-DUMMY-001" }

            val clear = checker.checkDronePosition(
                dronePosition = LatLon(35.35000, 24.45000),
                droneAltitudeMeters = 50.0,
                zones = zones
            ).isEmpty()

            logFailureIfNeeded("hospital", expected = true, actual = hospital)
            logFailureIfNeeded("fortezza", expected = true, actual = fortezza)
            logFailureIfNeeded("clear", expected = true, actual = clear)

            Log.d(TAG, "Live geo validation: hospital=$hospital fortezza=$fortezza clear=$clear")
        } catch (error: Exception) {
            Log.e(TAG, "Live geo validation failed unexpectedly", error)
        }
    }

    private fun logFailureIfNeeded(name: String, expected: Boolean, actual: Boolean) {
        if (expected != actual) {
            Log.e(TAG, "Live geo validation failed: $name expected $expected actual $actual")
        }
    }

    private const val TAG = "LiveGeoDebug"
}
