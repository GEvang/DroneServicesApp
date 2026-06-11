package com.example.droneservicesapp.domain.geoawareness

import android.util.Log
import com.example.droneservicesapp.domain.model.LatLon
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class LiveGeoAwarenessChecker {

    fun checkDronePosition(
        dronePosition: LatLon?,
        droneAltitudeMeters: Double?,
        zones: List<GeoZone>
    ): List<GeoZone> = checkDronePosition(
        dronePosition = dronePosition,
        altitudeContext = GeoAltitudeContext(aglMeters = droneAltitudeMeters),
        zones = zones
    )

    fun checkDronePosition(
        dronePosition: LatLon?,
        altitudeContext: GeoAltitudeContext?,
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
                        altitudeContext = altitudeContext
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
    ): LiveGeoAwarenessProximityResult? = findNearestZoneWithinThreshold(
        position = position,
        zones = zones,
        thresholdMeters = thresholdMeters,
        altitudeContext = GeoAltitudeContext(aglMeters = altitudeMeters)
    )

    fun findNearestZoneWithinThreshold(
        position: LatLon,
        zones: List<GeoZone>,
        thresholdMeters: Double = 100.0,
        altitudeContext: GeoAltitudeContext? = null,
        groundSpeedMetersPerSecond: Double? = null,
        headingDegrees: Double? = null,
        requiredWarningSeconds: Double = REQUIRED_WARNING_SECONDS
    ): LiveGeoAwarenessProximityResult? {
        if (zones.isEmpty()) {
            return null
        }

        val normalizedSpeed = groundSpeedMetersPerSecond
            ?.takeIf { it.isFinite() && it > 0.0 }
        val normalizedHeading = headingDegrees
            ?.takeIf { it.isFinite() }
            ?.let { ((it % 360.0) + 360.0) % 360.0 }
        val predictedPosition = if (normalizedSpeed != null && normalizedHeading != null) {
            projectPosition(position, normalizedHeading, normalizedSpeed * CLOSING_SPEED_LOOKAHEAD_SECONDS)
        } else {
            null
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
                        altitudeContext = altitudeContext
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
                        altitudeContext = altitudeContext,
                        zoneLowerMeters = geometry.lowerLimitMeters,
                        zoneUpperMeters = geometry.upperLimitMeters,
                        lowerReference = geometry.lowerVerticalReference,
                        upperReference = geometry.upperVerticalReference
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

            val predictedDistance = predictedPosition?.let { nextPosition ->
                zone.geometries
                    .filter { geometry ->
                        GeoAwarenessGeometryUtils.altitudeOverlaps(
                            altitudeContext = altitudeContext,
                            zoneLowerMeters = geometry.lowerLimitMeters,
                            zoneUpperMeters = geometry.upperLimitMeters,
                            lowerReference = geometry.lowerVerticalReference,
                            upperReference = geometry.upperVerticalReference
                        )
                    }
                    .mapNotNull { geometry ->
                        try {
                            GeoAwarenessGeometryUtils.distanceMetersToGeometry(nextPosition, geometry)
                        } catch (error: Exception) {
                            Log.w(TAG, "Skipping malformed geometry while checking predicted proximity for ${zone.id}", error)
                            null
                        }
                    }
                    .minOrNull()
            }
            val closingSpeed = predictedDistance
                ?.let { (nearestDistance - it) / CLOSING_SPEED_LOOKAHEAD_SECONDS }
                ?.takeIf { it.isFinite() && it > 0.0 }
            val timeToBoundarySeconds = closingSpeed?.let { nearestDistance / it }
            val minimumWarningDistanceMeters = closingSpeed?.let { it * requiredWarningSeconds }
            val effectiveThresholdMeters = maxOf(thresholdMeters, minimumWarningDistanceMeters ?: 0.0)
            val fixedDistanceTriggered = nearestDistance <= thresholdMeters
            val timeToBoundaryTriggered = timeToBoundarySeconds?.let { it <= requiredWarningSeconds } == true

            if (!fixedDistanceTriggered && !timeToBoundaryTriggered) {
                return@mapNotNull null
            }

            LiveGeoAwarenessProximityResult(
                nearestZone = zone,
                distanceMeters = nearestDistance,
                restriction = zone.restriction,
                configuredThresholdMeters = thresholdMeters,
                effectiveThresholdMeters = effectiveThresholdMeters,
                requiredWarningSeconds = requiredWarningSeconds,
                minimumWarningDistanceMeters = minimumWarningDistanceMeters,
                groundSpeedMetersPerSecond = normalizedSpeed,
                headingDegrees = normalizedHeading,
                closingSpeedMetersPerSecond = closingSpeed,
                timeToBoundarySeconds = timeToBoundarySeconds,
                warningMeetsRequiredTime = timeToBoundarySeconds?.let { it >= requiredWarningSeconds },
                warningMode = when {
                    timeToBoundaryTriggered -> "TIME_TO_BOUNDARY"
                    fixedDistanceTriggered -> "FIXED_DISTANCE_100M"
                    else -> "NONE"
                },
                verticalRelevance = true
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
        const val REQUIRED_WARNING_SECONDS = 3.0
        private const val CLOSING_SPEED_LOOKAHEAD_SECONDS = 1.0
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }

    private fun projectPosition(position: LatLon, headingDegrees: Double, distanceMeters: Double): LatLon {
        if (distanceMeters <= 0.0) return position
        val angularDistance = distanceMeters / EARTH_RADIUS_METERS
        val bearing = Math.toRadians(headingDegrees)
        val lat1 = Math.toRadians(position.lat)
        val lon1 = Math.toRadians(position.lon)
        val sinLat1 = sin(lat1)
        val cosLat1 = cos(lat1)
        val sinAngular = sin(angularDistance)
        val cosAngular = cos(angularDistance)
        val lat2 = asin(sinLat1 * cosAngular + cosLat1 * sinAngular * cos(bearing))
        val lon2 = lon1 + atan2(
            sin(bearing) * sinAngular * cosLat1,
            cosAngular - sinLat1 * sin(lat2)
        )
        return LatLon(
            lat = Math.toDegrees(lat2),
            lon = ((Math.toDegrees(lon2) + 540.0) % 360.0) - 180.0
        )
    }
}
