package com.example.droneservicesapp.ui.home.geoawareness

import com.example.droneservicesapp.data.geoawareness.incident.GeoIncidentLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthState
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessResult
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult
import com.example.droneservicesapp.domain.geoawareness.LiveGeoAwarenessProximityResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult

/** Deduplicates and records geo-awareness dataset, planning, and live-flight evidence. */
class GeoAwarenessEventTracker(
    private val eventLogger: GeoAwarenessEventLogger,
    private val incidentLogger: GeoIncidentLogger,
    private val metadata: () -> Metadata,
    private val telemetry: () -> Telemetry,
) {
    data class Metadata(val datasetTitle: String?, val datasetVersion: String?, val healthState: String?)
    data class Telemetry(
        val altitudeAglMeters: Double?,
        val altitudeAmslMeters: Double?,
        val horizontalAccuracyMeters: Float?,
        val verticalAccuracyMeters: Float?,
        val groundSpeedMetersPerSecond: Float?,
        val verticalSpeedMetersPerSecond: Float?,
        val headingDegrees: Double?,
    )

    private var lastPlanningSignature: String? = null
    private var lastConflictSignature: String? = null
    private var lastHealthSignature: String? = null
    private var lastHealthState: GeoAwarenessHealthState? = null
    private var lastLiveZoneMap: Map<String, GeoZone> = emptyMap()
    private var lastProximityIdentity: String? = null

    fun datasetLoaded(info: GeoZoneDatasetInfo) = eventLogger.logSimple(
        type = GeoAwarenessEventType.DATASET_LOADED,
        severity = "INFO",
        message = "Geo-awareness dataset loaded",
        datasetTitle = info.title,
        datasetVersion = info.version,
        details = mapOf(
            "zoneCount" to info.zoneCount.toString(),
            "circleGeometryCount" to info.circleGeometryCount.toString(),
            "polygonGeometryCount" to info.polygonGeometryCount.toString(),
            "validNonDummyDataset" to (info.zoneCount > 0 && !info.isDummy).toString(),
            "dummy" to info.isDummy.toString(),
        ),
    )

    fun datasetLoadFailed(error: Throwable) = eventLogger.logSimple(
        type = GeoAwarenessEventType.DATASET_LOAD_FAILED,
        severity = "ERROR",
        message = "Geo-awareness dataset failed to load: ${error::class.java.simpleName}: ${error.message}",
        details = mapOf(
            "errorClass" to error::class.java.name,
            "errorMessage" to (error.message ?: "unknown"),
        ),
    )

    fun datasetValidation(result: GeoZoneValidationResult, info: GeoZoneDatasetInfo) {
        val meta = metadata()
        val details = mapOf(
            "errorCount" to result.errorCount.toString(),
            "warningCount" to result.warningCount.toString(),
            "infoCount" to result.infoCount.toString(),
            "issueCodes" to result.issues.take(10).joinToString(",") { it.code },
        )
        eventLogger.logSimple(
            type = if (result.hasErrors) GeoAwarenessEventType.DATASET_VALIDATION_FAILED else GeoAwarenessEventType.DATASET_VALIDATED,
            severity = when {
                result.hasErrors -> "ERROR"
                result.hasWarnings -> "WARNING"
                else -> "INFO"
            },
            message = when {
                result.hasErrors -> "Geo-awareness dataset validation failed"
                result.hasWarnings -> "Geo-awareness dataset validation completed with warnings"
                else -> "Geo-awareness dataset validation passed"
            },
            datasetTitle = info.title,
            datasetVersion = info.version,
            healthState = meta.healthState,
            details = details,
        )
    }

    fun multiDatasetLoaded(result: GeoZoneLoadResult) {
        if (result.datasetRecords.size <= 1) return
        eventLogger.logSimple(
            type = GeoAwarenessEventType.MULTI_DATASET_LOADED,
            severity = if (result.validationResult.hasWarnings) "WARNING" else "INFO",
            message = "Multiple geo-zone datasets loaded",
            datasetTitle = result.datasetInfo.title,
            datasetVersion = result.datasetInfo.version,
            healthState = metadata().healthState,
            details = mapOf(
                "datasetCount" to result.datasetRecords.size.toString(),
                "totalZones" to result.datasetInfo.zoneCount.toString(),
                "totalWarnings" to result.validationResult.warningCount.toString(),
                "totalErrors" to result.validationResult.errorCount.toString(),
            ),
        )
    }

    fun healthEvaluation(health: GeoAwarenessHealth) {
        val meta = metadata()
        val signature = "${health.state}|${health.message}"
        val severity = when (health.state) {
            GeoAwarenessHealthState.AVAILABLE -> "INFO"
            GeoAwarenessHealthState.UNAVAILABLE -> "ERROR"
            else -> "WARNING"
        }
        if (lastHealthSignature != signature) {
            eventLogger.logSimple(
                type = GeoAwarenessEventType.HEALTH_EVALUATED,
                severity = severity,
                message = health.message,
                datasetTitle = meta.datasetTitle,
                datasetVersion = meta.datasetVersion,
                healthState = health.state.name,
            )
            lastHealthSignature = signature
        }
        val previous = lastHealthState
        if (previous != null && previous != health.state) {
            eventLogger.logSimple(
                type = GeoAwarenessEventType.HEALTH_CHANGED,
                severity = severity,
                message = "Geo-awareness health changed to ${health.state}",
                datasetTitle = meta.datasetTitle,
                datasetVersion = meta.datasetVersion,
                healthState = health.state.name,
                details = mapOf("previousState" to previous.name, "newState" to health.state.name),
            )
        }
        lastHealthState = health.state
    }

    fun planningStatus(result: GeoAwarenessResult) {
        val meta = metadata()
        val zoneIds = result.conflicts.map { it.zone.id }.distinct().sorted()
        val signature = listOf(
            result.conflicts.size,
            result.highestRestriction.name,
            result.canUpload,
            result.requiresAcknowledgement,
            zoneIds.joinToString(","),
        ).joinToString("|")
        if (signature != lastPlanningSignature) {
            eventLogger.logSimple(
                type = GeoAwarenessEventType.PLANNING_CHECKED,
                severity = if (result.hasConflicts) "WARNING" else "INFO",
                message = if (result.hasConflicts) "Planning geo-awareness check found conflicts" else "Planning geo-awareness check clear",
                datasetTitle = meta.datasetTitle,
                datasetVersion = meta.datasetVersion,
                healthState = meta.healthState,
                zoneIds = zoneIds,
                zoneNames = result.conflicts.map { it.zone.name }.distinct().sorted(),
                restriction = result.highestRestriction.name,
                details = mapOf(
                    "conflicts" to result.conflicts.size.toString(),
                    "highestRestriction" to result.highestRestriction.name,
                    "canUpload" to result.canUpload.toString(),
                    "requiresAcknowledgement" to result.requiresAcknowledgement.toString(),
                ),
            )
            lastPlanningSignature = signature
        }
        if (result.hasConflicts && signature != lastConflictSignature) {
            eventLogger.logSimple(
                type = GeoAwarenessEventType.PLANNING_CONFLICT_DETECTED,
                severity = "WARNING",
                message = "Planning geo-awareness conflict detected",
                datasetTitle = meta.datasetTitle,
                datasetVersion = meta.datasetVersion,
                healthState = meta.healthState,
                zoneIds = zoneIds,
                zoneNames = result.conflicts.map { it.zone.name }.distinct().sorted(),
                restriction = result.highestRestriction.name,
            )
            lastConflictSignature = signature
        } else if (!result.hasConflicts) {
            lastConflictSignature = null
        }
    }

    fun liveStatus(zones: List<GeoZone>, latitude: Double?, longitude: Double?, altitudeMeters: Double?) {
        val current = zones.associateBy(::zoneIdentity)
        if (current.keys == lastLiveZoneMap.keys) return
        val entered = current.filterKeys { it !in lastLiveZoneMap }.values.toList()
        val exited = lastLiveZoneMap.filterKeys { it !in current }.values.toList()
        val meta = metadata()
        if (entered.isNotEmpty()) incidentLogger.logZoneEntered(
            zones = entered,
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            datasetTitle = meta.datasetTitle,
            datasetVersion = meta.datasetVersion,
            healthState = meta.healthState,
            source = "live_drone",
            details = verificationDetails("alert_active"),
        )
        if (exited.isNotEmpty()) incidentLogger.logZoneExited(
            zones = exited,
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            datasetTitle = meta.datasetTitle,
            datasetVersion = meta.datasetVersion,
            healthState = meta.healthState,
            source = "live_drone",
            details = verificationDetails("alert_cleared"),
        )
        lastLiveZoneMap = current
    }

    fun liveProximity(
        proximity: LiveGeoAwarenessProximityResult,
        latitude: Double?,
        longitude: Double?,
        altitudeMeters: Double?,
    ) {
        val identity = proximityIdentity(proximity)
        if (identity == lastProximityIdentity) return
        lastProximityIdentity = identity
        val meta = metadata()
        incidentLogger.logApproachWarning(
            zone = proximity.nearestZone,
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            datasetTitle = meta.datasetTitle,
            datasetVersion = meta.datasetVersion,
            healthState = meta.healthState,
            source = "live_drone",
            details = verificationDetails("approach_warning") + proximityDetails(proximity),
        )
    }

    private fun verificationDetails(alertStatus: String): Map<String, String> {
        val value = telemetry()
        return buildMap {
            put("verificationSchema", "prEN4709-003-7.1-live-behaviour-v1")
            put("utcTimeMillis", System.currentTimeMillis().toString())
            put("alertStatus", alertStatus)
            value.altitudeAglMeters?.let { put("heightAglOrRelativeMeters", it.toString()) }
            value.altitudeAmslMeters?.let { put("altitudeAmslMeters", it.toString()) }
            value.horizontalAccuracyMeters?.let { put("horizontalPositionAccuracyMeters", it.toString()) }
            value.verticalAccuracyMeters?.let { put("verticalPositionAccuracyMeters", it.toString()) }
            value.groundSpeedMetersPerSecond?.let { put("groundSpeedMetersPerSecond", it.toString()) }
            value.verticalSpeedMetersPerSecond?.let { put("verticalSpeedMetersPerSecond", it.toString()) }
            value.headingDegrees?.let { put("headingDegrees", it.toString()) }
            put("altitudeReferenceSupport", "AGL_from_relative_altitude_or_mission_height;AMSL_from_GLOBAL_POSITION_INT.alt")
        }
    }

    private fun proximityDetails(value: LiveGeoAwarenessProximityResult): Map<String, String> = buildMap {
        put("approachWarningSchema", "prEN4709-003-3-second-approach-warning-v1")
        put("nearestZoneId", value.nearestZone.id)
        put("nearestZoneName", value.nearestZone.name)
        put("nearestZoneRestriction", value.restriction.name)
        put("timeApplicabilityActive", "true")
        put("timeApplicabilityRule", "inactive future/expired non-permanent UGZ windows are excluded before warning evaluation")
        put("timeApplicabilityPermanent", value.nearestZone.applicability.any { it.permanent }.toString())
        value.nearestZone.applicability.mapNotNull { it.startDateTime }.minOrNull()?.let { put("timeApplicabilityStartUtc", it) }
        value.nearestZone.applicability.mapNotNull { it.endDateTime }.maxOrNull()?.let { put("timeApplicabilityEndUtc", it) }
        put("distanceToBoundaryMeters", value.distanceMeters.toString())
        put("configuredDistanceThresholdMeters", value.configuredThresholdMeters.toString())
        put("effectiveWarningThresholdMeters", value.effectiveThresholdMeters.toString())
        put("requiredWarningTimeSeconds", value.requiredWarningSeconds.toString())
        value.minimumWarningDistanceMeters?.let { put("minimumSpeedBasedWarningDistanceMeters", it.toString()) }
        value.groundSpeedMetersPerSecond?.let { put("groundSpeedMetersPerSecond", it.toString()) }
        value.headingDegrees?.let { put("headingDegrees", it.toString()) }
        value.closingSpeedMetersPerSecond?.let { put("closingSpeedMetersPerSecond", it.toString()) }
        value.timeToBoundarySeconds?.let { put("timeToBoundarySeconds", it.toString()) }
        value.verticalDistanceMeters?.let { put("verticalDistanceToBoundaryMeters", it.toString()) }
        value.verticalClosingSpeedMetersPerSecond?.let { put("verticalClosingSpeedMetersPerSecond", it.toString()) }
        value.verticalTimeToBoundarySeconds?.let { put("verticalTimeToBoundarySeconds", it.toString()) }
        value.verticalBoundaryReference?.let { put("verticalBoundaryReference", it.name) }
        value.warningMeetsRequiredTime?.let { put("warningMeetsRequiredTime", it.toString()) }
        put("warningMode", value.warningMode)
        put("verticalRelevance", value.verticalRelevance.toString())
        put("triggerRule", "horizontal distanceToBoundaryMeters <= configuredDistanceThresholdMeters OR horizontal timeToBoundarySeconds <= requiredWarningTimeSeconds when closingSpeedMetersPerSecond > 0 OR verticalDistanceToBoundaryMeters <= verticalWarningBufferMeters OR verticalTimeToBoundarySeconds <= requiredWarningTimeSeconds when verticalClosingSpeedMetersPerSecond > 0")
    }

    private fun zoneIdentity(zone: GeoZone) = "${zone.id}|${zone.name}|${zone.restriction.name}"
    private fun proximityIdentity(value: LiveGeoAwarenessProximityResult) =
        "${value.nearestZone.id}|${value.nearestZone.name}|${value.restriction.name}"
}
