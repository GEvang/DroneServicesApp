package com.example.droneservicesapp.ui.home.geoawareness

import android.content.Context
import android.graphics.Color
import androidx.appcompat.app.AlertDialog
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthState
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessResult
import com.example.droneservicesapp.domain.geoawareness.GeoConflictType
import com.example.droneservicesapp.domain.geoawareness.GeoZoneConflict
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction

/** Owns geo-awareness warning content and Android dialog presentation. */
class GeoAwarenessDialogController(
    private val context: Context,
    private val authorizationSession: GeoAuthorizationSession,
) {
    fun showBlocked(result: GeoAwarenessResult) = showMessage(
        context.getString(R.string.geo_dialog_upload_blocked),
        buildString {
            appendLine(context.getString(R.string.geo_dialog_blocked_body))
            appendLine()
            append(conflictSummary(result))
        },
    )

    fun showProhibitedAcknowledgement(
        result: GeoAwarenessResult,
        onAcknowledged: () -> Unit,
        onCancelled: () -> Unit,
    ) {
        val zones = result.conflicts.map { it.zone }
            .filter { it.restriction == GeoZoneRestriction.PROHIBITED }
            .distinctBy { it.id }
        showConfirmation(
            title = context.getString(R.string.geo_dialog_prohibited_title),
            message = buildString {
                appendLine(context.getString(R.string.geo_dialog_prohibited_body))
                appendLine()
                appendZoneAuthorities(zones)
                appendLine()
                append(conflictSummary(result))
            },
            positiveText = context.getString(R.string.geo_dialog_acknowledge_upload),
            onConfirmed = onAcknowledged,
            onCancelled = onCancelled,
        )
    }

    fun showAuthorizationAcknowledgement(
        result: GeoAwarenessResult,
        onAcknowledged: () -> Unit,
        onCancelled: () -> Unit,
    ) = showConfirmation(
        title = context.getString(R.string.geo_dialog_authorization_title),
        message = buildString {
            appendLine(context.getString(R.string.geo_dialog_authorization_body))
            appendLine()
            appendZoneAuthorities(authorizationSession.requiredZones(result))
            appendLine()
            append(conflictSummary(result))
        },
        positiveText = context.getString(R.string.geo_dialog_confirm_authorization),
        onConfirmed = onAcknowledged,
        onCancelled = onCancelled,
    )

    fun showNotice(
        result: GeoAwarenessResult,
        onContinue: () -> Unit,
        onCancelled: () -> Unit,
    ) = showConfirmation(
        title = context.getString(R.string.geo_dialog_notice_title),
        message = buildString {
            appendLine(context.getString(R.string.geo_dialog_notice_body))
            appendLine()
            append(conflictSummary(result))
        },
        positiveText = context.getString(R.string.continue_action),
        onConfirmed = onContinue,
        onCancelled = onCancelled,
    )

    fun showHealthAcknowledgement(
        health: GeoAwarenessHealth,
        onContinue: () -> Unit,
        onCancelled: () -> Unit,
    ) = showConfirmation(
        title = context.getString(R.string.geo_dialog_health_title),
        message = buildString {
            appendLine(context.getString(R.string.geo_dialog_current_state, health.state))
            appendLine(health.message)
            appendLine(healthNotice(health))
            appendLine(context.getString(R.string.geo_dialog_verify_dagr))
        },
        positiveText = context.getString(R.string.continue_action),
        onConfirmed = onContinue,
        onCancelled = onCancelled,
    )

    fun showMessage(title: String, message: String) {
        val dialog = AlertDialog.Builder(context, R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(BUTTON_TEXT_COLOR)
    }

    fun conflictSummary(result: GeoAwarenessResult, maxItems: Int = 5): String {
        val ordered = result.conflicts.sortedWith(
            compareByDescending<GeoZoneConflict> { restrictionRank(it.restriction) }
                .thenBy { it.zone.name }
                .thenBy { it.conflictType.name },
        )
        val visible = ordered.take(maxItems)
        val remaining = ordered.size - visible.size
        return buildString {
            appendLine(context.getString(R.string.geo_summary_highest_restriction, result.highestRestriction))
            appendLine(context.getString(R.string.geo_summary_upload_allowed, yesNo(result.canUpload)))
            appendLine(context.getString(R.string.geo_summary_ack_required, yesNo(result.requiresAcknowledgement)))
            appendLine()
            visible.forEach { conflict ->
                appendLine("- ${conflict.zone.name}")
                appendLine("  ${context.getString(R.string.geo_summary_restriction, conflict.restriction)}")
                appendLine("  ${context.getString(R.string.geo_summary_type, conflictType(conflict.conflictType))}")
                appendLine("  ${context.getString(R.string.geo_summary_message, conflict.message ?: context.getString(R.string.geo_summary_no_message))}")
            }
            if (remaining > 0) append(context.getString(R.string.geo_summary_more, remaining))
        }
    }

    private fun StringBuilder.appendZoneAuthorities(zones: List<com.example.droneservicesapp.domain.geoawareness.GeoZone>) {
        zones.forEach { zone ->
            appendLine("- ${zone.name}")
            appendLine("  ${context.getString(R.string.geo_dialog_zone_id, zone.id)}")
            zone.authorities.firstOrNull()?.let { authority ->
                appendLine("  ${context.getString(R.string.geo_dialog_authority, authority.name ?: context.getString(R.string.geo_dialog_not_specified))}")
                appendLine("  ${context.getString(R.string.geo_dialog_purpose, authority.purpose ?: context.getString(R.string.geo_dialog_not_specified))}")
            }
        }
    }

    private fun showConfirmation(
        title: String,
        message: String,
        positiveText: String,
        onConfirmed: () -> Unit,
        onCancelled: () -> Unit,
    ) {
        val dialog = AlertDialog.Builder(context, R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { _, _ -> onConfirmed() }
            .setNegativeButton(R.string.cancel) { _, _ -> onCancelled() }
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(BUTTON_TEXT_COLOR)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(BUTTON_TEXT_COLOR)
    }

    private fun healthNotice(health: GeoAwarenessHealth): String = context.getString(
        when (health.state) {
            GeoAwarenessHealthState.STALE -> R.string.geo_health_stale
            GeoAwarenessHealthState.DEGRADED -> R.string.geo_health_degraded
            GeoAwarenessHealthState.UNAVAILABLE -> R.string.geo_health_unavailable
            GeoAwarenessHealthState.AVAILABLE -> R.string.geo_health_available
        },
    )

    private fun yesNo(value: Boolean) = context.getString(if (value) R.string.answer_yes else R.string.answer_no)

    private fun conflictType(value: GeoConflictType): String = when (value) {
        GeoConflictType.MISSION_AREA_INTERSECTS_ZONE -> "Mission area intersects zone"
        GeoConflictType.SURVEY_PATH_INTERSECTS_ZONE -> "Survey path intersects zone"
        GeoConflictType.WAYPOINT_INSIDE_ZONE -> "Waypoint inside zone"
    }

    private fun restrictionRank(value: GeoZoneRestriction): Int = when (value) {
        GeoZoneRestriction.PROHIBITED -> 4
        GeoZoneRestriction.REQ_AUTHORISATION -> 3
        GeoZoneRestriction.CONDITIONAL -> 2
        GeoZoneRestriction.INFORMATION -> 1
        GeoZoneRestriction.UNKNOWN -> 0
    }

    companion object {
        private val BUTTON_TEXT_COLOR = Color.parseColor("#212121")
    }
}
