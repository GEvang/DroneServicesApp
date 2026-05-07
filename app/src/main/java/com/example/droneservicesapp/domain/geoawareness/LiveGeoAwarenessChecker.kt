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

    fun findNearestZoneWithinThreshold(
        position: LatLon,
        zones: List<GeoZone>,
        thresholdMeters: Double = 100.0,
        altitudeMeters: Double? = null
    ): LiveGeoAwarenessProximityResult? {
        if (zones.isEmpty()) {
            return null
        }

        val candidates = zones.mapNotNull { zone ->
            if (!isNearWarningRestriction(zone.restriction)) {
                return@mapNotNull null
            }
            val isInside = zone.geometries.any { geometry ->
                try {
                    GeoAwarenessGeometryUtils.pointInZone(
                        point = position,
                        geometry = geometry,
                        missionAltitudeMeters = altitudeMeters
                    )
                } catch (error: Exception) {
                    Log.w(TAG, "Skipping malformed geometry while checking inside state for ${zone.id}", error)
                    false
                }
            }
            if (isInside) {
                return@mapNotNull null
            }

            val nearestDistance = zone.geometries
                .filter { geometry ->
                    GeoAwarenessGeometryUtils.altitudeOverlaps(
                        missionAltitudeMeters = altitudeMeters,
                        zoneLowerMeters = geometry.lowerLimitMeters,
                        zoneUpperMeters = geometry.upperLimitMeters
                    )
                }
                .mapNotNull { geometry ->
                    try {
                        GeoAwarenessGeometryUtils.distanceMetersToGeometry(position, geometry)
                    } catch (error: Exception) {
                        Log.w(TAG, "Skipping malformed geometry while checking proximity for ${zone.id}", error)
                        null
                    }
                }
                .minOrNull()
                ?: return@mapNotNull null

            if (nearestDistance > thresholdMeters) {
                return@mapNotNull null
            }

            LiveGeoAwarenessProximityResult(
                nearestZone = zone,
                distanceMeters = nearestDistance,
                restriction = zone.restriction
            )
        }

        return candidates.minWithOrNull(
            compareBy<LiveGeoAwarenessProximityResult> { restrictionRank(it.restriction) * -1 }
                .thenBy { it.distanceMeters }
                .thenBy { it.nearestZone.name }
        )
    }

    private fun isNearWarningRestriction(restriction: GeoZoneRestriction): Boolean {
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED,
            GeoZoneRestriction.REQ_AUTHORISATION,
            GeoZoneRestriction.CONDITIONAL,
            GeoZoneRestriction.UNKNOWN -> true
            GeoZoneRestriction.INFORMATION -> false
        }
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
