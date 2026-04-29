package com.example.droneservicesapp.domain.geoawareness

import android.util.Log
import com.example.droneservicesapp.domain.model.LatLon

class LiveGeoAwarenessChecker {

    fun checkDronePosition(
        dronePosition: LatLon?,
        droneAltitudeMeters: Double?,
        zones: List<GeoZone>
    ): List<GeoZone> {
        if (dronePosition == null || zones.isEmpty()) {
            return emptyList()
        }

        val insideZones = mutableListOf<GeoZone>()
        for (zone in zones) {
            val inside = zone.geometries.any { geometry ->
                try {
                    GeoAwarenessGeometryUtils.pointInZone(
                        point = dronePosition,
                        geometry = geometry,
                        missionAltitudeMeters = droneAltitudeMeters
                    )
                } catch (error: Exception) {
                    Log.w(TAG, "Skipping malformed geometry while checking live zone ${zone.id}", error)
                    false
                }
            }
            if (inside) {
                insideZones += zone
            }
        }

        return insideZones.sortedWith(
            compareByDescending<GeoZone> { restrictionRank(it.restriction) }
                .thenBy { it.name }
        )
    }

    private fun restrictionRank(restriction: GeoZoneRestriction): Int {
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED -> 4
            GeoZoneRestriction.REQ_AUTHORISATION -> 3
            GeoZoneRestriction.CONDITIONAL -> 2
            GeoZoneRestriction.INFORMATION -> 1
            GeoZoneRestriction.UNKNOWN -> 0
        }
    }

    companion object {
        private const val TAG = "LiveGeoAwarenessChecker"
    }
}
