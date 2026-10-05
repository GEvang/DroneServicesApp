package com.example.droneservicesapp.ui.home.geoawareness

import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessResult
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction

/** Pure upload-guard decision policy; dialogs and audit logging remain presentation concerns. */
object GeoUploadGuardPolicy {
    sealed interface Decision {
        object AllowClear : Decision
        data class AllowPreviouslyAuthorized(val zones: List<GeoZone>) : Decision
        object Block : Decision
        object RequireProhibitedAcknowledgement : Decision
        data class RequireAuthorization(val zones: List<GeoZone>) : Decision
        object ShowNotice : Decision
    }

    fun decide(
        result: GeoAwarenessResult,
        authorizationSession: GeoAuthorizationSession,
    ): Decision = when {
        !result.hasConflicts -> Decision.AllowClear
        !result.canUpload -> Decision.Block
        result.highestRestriction == GeoZoneRestriction.PROHIBITED ->
            Decision.RequireProhibitedAcknowledgement
        result.requiresAcknowledgement -> {
            val unconfirmed = authorizationSession.unconfirmedZones(result)
            if (unconfirmed.isEmpty()) {
                Decision.AllowPreviouslyAuthorized(authorizationSession.requiredZones(result))
            } else Decision.RequireAuthorization(unconfirmed)
        }
        else -> Decision.ShowNotice
    }
}
