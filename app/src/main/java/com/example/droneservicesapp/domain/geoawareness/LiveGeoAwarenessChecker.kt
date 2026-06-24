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
        zones: List<GeoZone>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<GeoZone> {
        if (dronePosition == null || zones.isEmpty()) {
            return emptyList()
        }

        val insideZones = mutableListOf<GeoZone>()
        for (zone in zones) {
            if (!GeoZoneApplicabilityEvaluator.isActiveNow(zone, nowMillis)) {
                continue
            }
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
        verticalSpeedMetersPerSecond: Double? = null,
        requiredWarningSeconds: Double = REQUIRED_WARNING_SECONDS,
        verticalWarningBufferMeters: Double = DEFAULT_VERTICAL_WARNING_BUFFER_METERS,
        nowMillis: Long = System.currentTimeMillis()
    ): LiveGeoAwarenessProximityResult? {
        return findZonesWithinThreshold(
            position = position,
            zones = zones,
            thresholdMeters = thresholdMeters,
            altitudeContext = altitudeContext,
            groundSpeedMetersPerSecond = groundSpeedMetersPerSecond,
            headingDegrees = headingDegrees,
            verticalSpeedMetersPerSecond = verticalSpeedMetersPerSecond,
            requiredWarningSeconds = requiredWarningSeconds,
            verticalWarningBufferMeters = verticalWarningBufferMeters,
            nowMillis = nowMillis
        ).firstOrNull()
    }

    fun findZonesWithinThreshold(
        position: LatLon,
        zones: List<GeoZone>,
        thresholdMeters: Double = 100.0,
        altitudeContext: GeoAltitudeContext? = null,
        groundSpeedMetersPerSecond: Double? = null,
        headingDegrees: Double? = null,
        verticalSpeedMetersPerSecond: Double? = null,
        requiredWarningSeconds: Double = REQUIRED_WARNING_SECONDS,
        verticalWarningBufferMeters: Double = DEFAULT_VERTICAL_WARNING_BUFFER_METERS,
        nowMillis: Long = System.currentTimeMillis()
    ): List<LiveGeoAwarenessProximityResult> {
        if (zones.isEmpty()) {
            return emptyList()
        }

        val normalizedSpeed = groundSpeedMetersPerSecond
            ?.takeIf { it.isFinite() && it > 0.0 }
        val normalizedHeading = headingDegrees
            ?.takeIf { it.isFinite() }
            ?.let { ((it % 360.0) + 360.0) % 360.0 }
        val normalizedVerticalSpeed = verticalSpeedMetersPerSecond
            ?.takeIf { it.isFinite() }
        val predictedPosition = if (normalizedSpeed != null && normalizedHeading != null) {
            projectPosition(position, normalizedHeading, normalizedSpeed * CLOSING_SPEED_LOOKAHEAD_SECONDS)
        } else {
            null
        }

        val candidates = zones.mapNotNull { zone ->
            if (!GeoZoneApplicabilityEvaluator.isActiveNow(zone, nowMillis)) {
                return@mapNotNull null
            }
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

            val geometryCandidates = zone.geometries.mapNotNull geometryCandidate@{ geometry ->
                val horizontalDistance = try {
                    GeoAwarenessGeometryUtils.distanceMetersToGeometry(position, geometry)
                } catch (error: Exception) {
                    Log.w(TAG, "Skipping malformed geometry while checking proximity for ${zone.id}", error)
                    null
                } ?: return@geometryCandidate null
                val verticalWarning = evaluateVerticalWarning(
                    altitudeContext = altitudeContext,
                    geometry = geometry,
                    verticalSpeedMetersPerSecond = normalizedVerticalSpeed,
                    requiredWarningSeconds = requiredWarningSeconds,
                    verticalWarningBufferMeters = verticalWarningBufferMeters
                )
                if (!verticalWarning.relevant) {
                    return@geometryCandidate null
                }
                GeometryProximityCandidate(
                    geometry = geometry,
                    horizontalDistanceMeters = horizontalDistance,
                    verticalWarning = verticalWarning
                )
            }

            if (geometryCandidates.isEmpty()) {
                return@mapNotNull null
            }

            val nearestDistance = geometryCandidates.minOf { it.horizontalDistanceMeters }

            val predictedDistance = predictedPosition?.let { nextPosition ->
                geometryCandidates
                    .mapNotNull { candidate ->
                        try {
                            GeoAwarenessGeometryUtils.distanceMetersToGeometry(nextPosition, candidate.geometry)
                        } catch (error: Exception) {
                            Log.w(TAG, "Skipping malformed geometry while checking predicted proximity for ${zone.id}", error)
                            null
                        }
                    }
                    .minOrNull()
            }
            val verticalWarning = geometryCandidates
                .map { it.verticalWarning }
                .minWithOrNull(
                    compareBy<VerticalWarningEvaluation> { if (it.triggered) 0 else 1 }
                        .thenBy { it.distanceMeters ?: Double.MAX_VALUE }
                ) ?: VerticalWarningEvaluation(relevant = true)
            val closingSpeed = predictedDistance
                ?.let { (nearestDistance - it) / CLOSING_SPEED_LOOKAHEAD_SECONDS }
                ?.takeIf { it.isFinite() && it > 0.0 }
            val timeToBoundarySeconds = closingSpeed?.let { nearestDistance / it }
            val minimumWarningDistanceMeters = closingSpeed?.let { it * requiredWarningSeconds }
            val effectiveThresholdMeters = maxOf(thresholdMeters, minimumWarningDistanceMeters ?: 0.0)
            val nearestGeometry = geometryCandidates.minBy { it.horizontalDistanceMeters }.geometry
            val horizontalFixedDistanceTriggered = nearestDistance <= thresholdMeters &&
                GeoAwarenessGeometryUtils.altitudeOverlaps(
                    altitudeContext = altitudeContext,
                    zoneLowerMeters = nearestGeometry.lowerLimitMeters,
                    zoneUpperMeters = nearestGeometry.upperLimitMeters,
                    lowerReference = nearestGeometry.lowerVerticalReference,
                    upperReference = nearestGeometry.upperVerticalReference
                )
            val timeToBoundaryTriggered = timeToBoundarySeconds?.let { it <= requiredWarningSeconds } == true
            val verticalWarningTriggered = verticalWarning.triggered && nearestDistance <= thresholdMeters

            if (!horizontalFixedDistanceTriggered && !timeToBoundaryTriggered && !verticalWarningTriggered) {
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
                verticalDistanceMeters = verticalWarning.distanceMeters,
                verticalClosingSpeedMetersPerSecond = verticalWarning.closingSpeedMetersPerSecond,
                verticalTimeToBoundarySeconds = verticalWarning.timeToBoundarySeconds,
                verticalBoundaryReference = verticalWarning.reference,
                warningMeetsRequiredTime = timeToBoundarySeconds?.let { it >= requiredWarningSeconds }
                    ?: verticalWarning.timeToBoundarySeconds?.let { it >= requiredWarningSeconds },
                warningMode = when {
                    verticalWarningTriggered && verticalWarning.timeToBoundarySeconds != null -> "VERTICAL_TIME_TO_BOUNDARY"
                    verticalWarningTriggered -> "VERTICAL_FIXED_DISTANCE"
                    timeToBoundaryTriggered -> "TIME_TO_BOUNDARY"
                    horizontalFixedDistanceTriggered -> "FIXED_DISTANCE_100M"
                    else -> "NONE"
                },
                verticalRelevance = verticalWarning.relevant
            )
        }

        return candidates.sortedWith(
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
        const val DEFAULT_VERTICAL_WARNING_BUFFER_METERS = 25.0
        private const val CLOSING_SPEED_LOOKAHEAD_SECONDS = 1.0
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }

    private data class GeometryProximityCandidate(
        val geometry: GeoZoneGeometry,
        val horizontalDistanceMeters: Double,
        val verticalWarning: VerticalWarningEvaluation
    )

    private data class VerticalWarningEvaluation(
        val relevant: Boolean,
        val triggered: Boolean = false,
        val distanceMeters: Double? = null,
        val closingSpeedMetersPerSecond: Double? = null,
        val timeToBoundarySeconds: Double? = null,
        val reference: GeoVerticalReference? = null
    )

    private fun evaluateVerticalWarning(
        altitudeContext: GeoAltitudeContext?,
        geometry: GeoZoneGeometry,
        verticalSpeedMetersPerSecond: Double?,
        requiredWarningSeconds: Double,
        verticalWarningBufferMeters: Double
    ): VerticalWarningEvaluation {
        val overlaps = GeoAwarenessGeometryUtils.altitudeOverlaps(
            altitudeContext = altitudeContext,
            zoneLowerMeters = geometry.lowerLimitMeters,
            zoneUpperMeters = geometry.upperLimitMeters,
            lowerReference = geometry.lowerVerticalReference,
            upperReference = geometry.upperVerticalReference
        )
        if (overlaps) {
            return VerticalWarningEvaluation(relevant = true)
        }

        val lowerGap = geometry.lowerLimitMeters?.let { lower ->
            altitudeContext?.altitudeFor(geometry.lowerVerticalReference)?.let { altitude ->
                if (altitude < lower) VerticalGap(
                    distanceMeters = lower - altitude,
                    closingSpeedMetersPerSecond = verticalSpeedMetersPerSecond?.takeIf { it > 0.0 },
                    reference = geometry.lowerVerticalReference
                ) else null
            }
        }
        val upperGap = geometry.upperLimitMeters?.let { upper ->
            altitudeContext?.altitudeFor(geometry.upperVerticalReference)?.let { altitude ->
                if (altitude > upper) VerticalGap(
                    distanceMeters = altitude - upper,
                    closingSpeedMetersPerSecond = verticalSpeedMetersPerSecond?.takeIf { it < 0.0 }?.let { -it },
                    reference = geometry.upperVerticalReference
                ) else null
            }
        }
        val nearestGap = listOfNotNull(lowerGap, upperGap).minByOrNull { it.distanceMeters }
            ?: return VerticalWarningEvaluation(relevant = false)
        val timeToBoundarySeconds = nearestGap.closingSpeedMetersPerSecond
            ?.let { nearestGap.distanceMeters / it }
            ?.takeIf { it.isFinite() }
        val timeTriggered = timeToBoundarySeconds?.let { it <= requiredWarningSeconds } == true
        val fixedBufferTriggered = nearestGap.distanceMeters <= verticalWarningBufferMeters
        return VerticalWarningEvaluation(
            relevant = fixedBufferTriggered || timeTriggered,
            triggered = fixedBufferTriggered || timeTriggered,
            distanceMeters = nearestGap.distanceMeters,
            closingSpeedMetersPerSecond = nearestGap.closingSpeedMetersPerSecond,
            timeToBoundarySeconds = timeToBoundarySeconds,
            reference = nearestGap.reference
        )
    }

    private data class VerticalGap(
        val distanceMeters: Double,
        val closingSpeedMetersPerSecond: Double?,
        val reference: GeoVerticalReference
    )

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
