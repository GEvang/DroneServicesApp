package com.example.droneservicesapp.ui.home.geoawareness

import android.util.Log
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessResult
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction
import com.example.droneservicesapp.domain.model.LatLon

/** Applies the upload guard, records audit events, and owns current-flight authorization state. */
class GeoAwarenessUploadController(
    private val eventLogger: GeoAwarenessEventLogger,
    private val dialogController: GeoAwarenessDialogController,
    private val authorizationSession: GeoAuthorizationSession,
    private val evaluatePlanningResult: () -> GeoAwarenessResult,
    private val ensureHealth: () -> GeoAwarenessHealth,
    private val datasetInfo: () -> GeoZoneDatasetInfo?,
    private val telemetry: () -> Telemetry,
) {
    data class Telemetry(val position: LatLon?, val altitudeMeters: Double?)

    fun handleBeforeUpload(onAllowed: () -> Unit) {
        val result = try {
            evaluatePlanningResult()
        } catch (error: Exception) {
            Log.w(TAG, "Geo-awareness result unavailable; proceeding with existing unavailable policy", error)
            onAllowed()
            return
        }
        val health = ensureHealth()
        when (val decision = GeoUploadGuardPolicy.decide(result, authorizationSession)) {
            GeoUploadGuardPolicy.Decision.AllowClear -> {
                audit(GeoAwarenessEventType.UPLOAD_GUARD_CLEAR, "INFO", "Geo upload guard clear, proceeding", health)
                onAllowed()
            }
            GeoUploadGuardPolicy.Decision.Block -> {
                auditResult(GeoAwarenessEventType.UPLOAD_BLOCKED, "BLOCKED", "Geo upload blocked", health, result)
                dialogController.showBlocked(result)
            }
            GeoUploadGuardPolicy.Decision.RequireProhibitedAcknowledgement -> {
                auditResult(
                    GeoAwarenessEventType.UPLOAD_ACK_REQUIRED,
                    "WARNING",
                    "Geo upload requires prohibited zone acknowledgement",
                    health,
                    result,
                    GeoZoneRestriction.PROHIBITED,
                )
                dialogController.showProhibitedAcknowledgement(
                    result = result,
                    onAcknowledged = {
                        auditResult(
                            GeoAwarenessEventType.UPLOAD_ACKNOWLEDGED,
                            "INFO",
                            "User acknowledged prohibited geo-zone upload warning",
                            health,
                            result,
                            GeoZoneRestriction.PROHIBITED,
                            mapOf("pilotAcknowledgement" to "prohibited_zone_warning_seen"),
                        )
                        onAllowed()
                    },
                    onCancelled = { logCancellation(health, "User cancelled prohibited geo-zone upload warning") },
                )
            }
            is GeoUploadGuardPolicy.Decision.AllowPreviouslyAuthorized -> {
                auditZones(
                    GeoAwarenessEventType.UPLOAD_ACKNOWLEDGED,
                    "INFO",
                    "Geo upload authorization already confirmed for current flight",
                    health,
                    decision.zones,
                    GeoZoneRestriction.REQ_AUTHORISATION,
                    mapOf("authorizationScope" to "current_flight"),
                )
                onAllowed()
            }
            is GeoUploadGuardPolicy.Decision.RequireAuthorization -> {
                val details = mapOf("authorizationScope" to "current_flight")
                auditZones(
                    GeoAwarenessEventType.UPLOAD_ACK_REQUIRED,
                    "WARNING",
                    "Geo upload requires acknowledgement",
                    health,
                    decision.zones,
                    GeoZoneRestriction.REQ_AUTHORISATION,
                    details,
                    includeTelemetry = true,
                )
                auditZones(
                    GeoAwarenessEventType.UGZ_AUTHORIZATION_REQUIRED,
                    "WARNING",
                    "Geo upload requires UGZ authorization confirmation",
                    health,
                    decision.zones,
                    GeoZoneRestriction.REQ_AUTHORISATION,
                    includeTelemetry = true,
                )
                dialogController.showAuthorizationAcknowledgement(
                    result = result,
                    onAcknowledged = {
                        val confirmedZones = authorizationSession.requiredZones(result)
                        authorizationSession.confirm(confirmedZones)
                        auditZones(
                            GeoAwarenessEventType.UGZ_AUTHORIZATION_CONFIRMED,
                            "INFO",
                            "Pilot declared UGZ authorization completed",
                            health,
                            confirmedZones,
                            GeoZoneRestriction.REQ_AUTHORISATION,
                            mapOf(
                                "confirmationScope" to "current_flight",
                                "pilotDeclaration" to "authorization_or_notification_completed",
                                "resetCondition" to "disarm_or_end_of_flight",
                            ),
                            includeTelemetry = true,
                        )
                        auditZones(
                            GeoAwarenessEventType.UPLOAD_ACKNOWLEDGED,
                            "INFO",
                            "User acknowledged geo upload warning",
                            health,
                            confirmedZones,
                            GeoZoneRestriction.REQ_AUTHORISATION,
                            details,
                            includeTelemetry = true,
                        )
                        onAllowed()
                    },
                    onCancelled = { logCancellation(health, "User cancelled geo upload acknowledgement") },
                )
            }
            GeoUploadGuardPolicy.Decision.ShowNotice -> {
                auditResult(
                    GeoAwarenessEventType.UPLOAD_CONTINUED_WITH_WARNING,
                    "WARNING",
                    "Geo upload warning shown",
                    health,
                    result,
                )
                dialogController.showNotice(
                    result = result,
                    onContinue = {
                        audit(GeoAwarenessEventType.UPLOAD_ACKNOWLEDGED, "INFO", "User continued after geo upload warning", health)
                        onAllowed()
                    },
                    onCancelled = { logCancellation(health, "User cancelled geo upload warning") },
                )
            }
        }
    }

    fun resetCurrentFlightAuthorizations(reason: String) {
        val resetIds = authorizationSession.reset().toList()
        if (resetIds.isEmpty()) return
        val info = datasetInfo()
        eventLogger.logSimple(
            type = GeoAwarenessEventType.UGZ_AUTHORIZATION_RESET,
            severity = "INFO",
            message = "UGZ authorization confirmations reset",
            category = "GEO",
            datasetTitle = info?.title,
            datasetVersion = info?.version,
            healthState = runCatching { ensureHealth().state.name }.getOrNull(),
            zoneIds = resetIds,
            details = mapOf("reason" to reason, "resetScope" to "current_flight"),
        )
    }

    private fun auditResult(
        type: GeoAwarenessEventType,
        severity: String,
        message: String,
        health: GeoAwarenessHealth,
        result: GeoAwarenessResult,
        restriction: GeoZoneRestriction = result.highestRestriction,
        details: Map<String, String> = emptyMap(),
    ) = auditZones(
        type, severity, message, health,
        result.conflicts.map { it.zone }.distinctBy { it.id }, restriction, details,
        includeTelemetry = true,
    )

    private fun auditZones(
        type: GeoAwarenessEventType,
        severity: String,
        message: String,
        health: GeoAwarenessHealth,
        zones: List<GeoZone>,
        restriction: GeoZoneRestriction,
        details: Map<String, String> = emptyMap(),
        includeTelemetry: Boolean = false,
    ) {
        val info = datasetInfo()
        val live = telemetry()
        eventLogger.logSimple(
            type = type,
            severity = severity,
            message = message,
            category = "MISSION",
            datasetTitle = info?.title,
            datasetVersion = info?.version,
            healthState = health.state.name,
            zoneIds = zones.map { it.id },
            zoneNames = zones.map { it.name },
            restriction = restriction.name,
            latitude = live.position?.lat.takeIf { includeTelemetry },
            longitude = live.position?.lon.takeIf { includeTelemetry },
            altitudeMeters = live.altitudeMeters.takeIf { includeTelemetry },
            details = details,
        )
    }

    private fun audit(
        type: GeoAwarenessEventType,
        severity: String,
        message: String,
        health: GeoAwarenessHealth,
    ) {
        val info = datasetInfo()
        eventLogger.logSimple(
            type = type,
            severity = severity,
            message = message,
            datasetTitle = info?.title,
            datasetVersion = info?.version,
            healthState = health.state.name,
        )
    }

    private fun logCancellation(health: GeoAwarenessHealth, message: String) {
        audit(GeoAwarenessEventType.UPLOAD_CANCELLED, "INFO", message, health)
    }

    companion object {
        private const val TAG = "GeoUploadGuard"
    }
}
