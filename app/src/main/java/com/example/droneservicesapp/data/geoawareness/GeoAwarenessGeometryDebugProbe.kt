package com.example.droneservicesapp.data.geoawareness

import android.content.Context
import android.util.Log
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessGeometryUtils
import com.example.droneservicesapp.domain.model.LatLon

object GeoAwarenessGeometryDebugProbe {

    fun logGeometryValidation(context: Context) {
        try {
            val repository = GeoZoneRepository(
                GeoZoneAssetDataSource(context.applicationContext)
            )
            val zones = repository.loadDummyRethymnoZones()
            val fortezza = zones.firstOrNull { it.id == "GR-RTH-DUMMY-001" }
            val beach = zones.firstOrNull { it.id == "GR-RTH-DUMMY-006" }
            val hospital = zones.firstOrNull { it.id == "GR-RTH-DUMMY-007" }

            if (fortezza == null || beach == null || hospital == null) {
                Log.e(TAG, "Geometry validation failed: required test zones missing")
                return
            }

            val hospitalGeometry = hospital.geometries.firstOrNull()
            val fortezzaGeometry = fortezza.geometries.firstOrNull()
            val beachGeometry = beach.geometries.firstOrNull()

            if (hospitalGeometry == null || fortezzaGeometry == null || beachGeometry == null) {
                Log.e(TAG, "Geometry validation failed: required test geometries missing")
                return
            }

            val hospitalInside = GeoAwarenessGeometryUtils.pointInZone(
                LatLon(35.36505, 24.47135),
                hospitalGeometry
            )
            logFailureIfNeeded("hospitalInside", expected = true, actual = hospitalInside)

            val hospitalOutside = GeoAwarenessGeometryUtils.pointInZone(
                LatLon(35.36390, 24.47135),
                hospitalGeometry
            )
            logFailureIfNeeded("hospitalOutside", expected = false, actual = hospitalOutside)

            val fortezzaInside = GeoAwarenessGeometryUtils.pointInZone(
                LatLon(35.37220, 24.47050),
                fortezzaGeometry
            )
            logFailureIfNeeded("fortezzaInside", expected = true, actual = fortezzaInside)

            val beachPathCrosses = GeoAwarenessGeometryUtils.pathIntersectsGeometry(
                listOf(
                    LatLon(35.36580, 24.48500),
                    LatLon(35.38100, 24.56000)
                ),
                beachGeometry
            )
            logFailureIfNeeded("beachPathCrosses", expected = true, actual = beachPathCrosses)

            val farPathClear = !GeoAwarenessGeometryUtils.pathIntersectsGeometry(
                listOf(
                    LatLon(35.35000, 24.45000),
                    LatLon(35.35100, 24.45100)
                ),
                hospitalGeometry
            ) && !GeoAwarenessGeometryUtils.pathIntersectsGeometry(
                listOf(
                    LatLon(35.35000, 24.45000),
                    LatLon(35.35100, 24.45100)
                ),
                fortezzaGeometry
            ) && !GeoAwarenessGeometryUtils.pathIntersectsGeometry(
                listOf(
                    LatLon(35.35000, 24.45000),
                    LatLon(35.35100, 24.45100)
                ),
                beachGeometry
            )
            logFailureIfNeeded("farPathClear", expected = true, actual = farPathClear)

            val altitude50 = GeoAwarenessGeometryUtils.altitudeOverlaps(50.0, 0.0, 120.0)
            logFailureIfNeeded("altitude50", expected = true, actual = altitude50)

            val altitude150 = GeoAwarenessGeometryUtils.altitudeOverlaps(150.0, 0.0, 120.0)
            logFailureIfNeeded("altitude150", expected = false, actual = altitude150)

            val altitudeUnknown = GeoAwarenessGeometryUtils.altitudeOverlaps(null, 0.0, 120.0)
            logFailureIfNeeded("altitudeUnknown", expected = true, actual = altitudeUnknown)

            Log.d(
                TAG,
                "Geometry validation: hospitalInside=$hospitalInside hospitalOutside=$hospitalOutside fortezzaInside=$fortezzaInside beachPathCrosses=$beachPathCrosses farPathClear=$farPathClear altitude50=$altitude50 altitude150=$altitude150 altitudeUnknown=$altitudeUnknown"
            )
        } catch (error: Exception) {
            Log.e(TAG, "Geometry validation failed unexpectedly", error)
        }
    }

    private fun logFailureIfNeeded(name: String, expected: Boolean, actual: Boolean) {
        if (expected != actual) {
            Log.e(TAG, "Geometry validation failed: $name expected $expected actual $actual")
        }
    }

    private const val TAG = "GeoGeometryDebug"
}
