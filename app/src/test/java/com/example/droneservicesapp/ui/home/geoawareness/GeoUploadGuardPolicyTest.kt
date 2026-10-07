package com.example.droneservicesapp.ui.home.geoawareness

import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessResult
import com.example.droneservicesapp.domain.geoawareness.GeoConflictType
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneConflict
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUploadGuardPolicyTest {
    private val session = GeoAuthorizationSession()

    @Test
    fun clearResultIsAllowedImmediately() {
        assertTrue(GeoUploadGuardPolicy.decide(GeoAwarenessResult.clear(), session) is GeoUploadGuardPolicy.Decision.AllowClear)
    }

    @Test
    fun nonUploadableResultIsBlocked() {
        assertTrue(GeoUploadGuardPolicy.decide(result(zone(GeoZoneRestriction.PROHIBITED), canUpload = false), session) is GeoUploadGuardPolicy.Decision.Block)
    }

    @Test
    fun prohibitedResultRequiresExplicitAcknowledgement() {
        assertTrue(GeoUploadGuardPolicy.decide(result(zone(GeoZoneRestriction.PROHIBITED)), session) is GeoUploadGuardPolicy.Decision.RequireProhibitedAcknowledgement)
    }

    @Test
    fun authorizationIsRememberedOnlyUntilSessionReset() {
        val zone = zone(GeoZoneRestriction.REQ_AUTHORISATION)
        val result = result(zone)
        val first = GeoUploadGuardPolicy.decide(result, session)
        assertTrue(first is GeoUploadGuardPolicy.Decision.RequireAuthorization)
        session.confirm(listOf(zone))
        assertTrue(GeoUploadGuardPolicy.decide(result, session) is GeoUploadGuardPolicy.Decision.AllowPreviouslyAuthorized)
        assertEquals(setOf(zone.id), session.reset())
        assertTrue(GeoUploadGuardPolicy.decide(result, session) is GeoUploadGuardPolicy.Decision.RequireAuthorization)
    }

    @Test
    fun informationalConflictShowsNotice() {
        assertTrue(GeoUploadGuardPolicy.decide(result(zone(GeoZoneRestriction.INFORMATION), acknowledgement = false), session) is GeoUploadGuardPolicy.Decision.ShowNotice)
    }

    private fun result(
        zone: GeoZone,
        canUpload: Boolean = true,
        acknowledgement: Boolean = true,
    ) = GeoAwarenessResult(
        conflicts = listOf(GeoZoneConflict(zone, GeoConflictType.WAYPOINT_INSIDE_ZONE, zone.restriction, null, 1)),
        highestRestriction = zone.restriction,
        canUpload = canUpload,
        requiresAcknowledgement = acknowledgement,
    )

    private fun zone(restriction: GeoZoneRestriction) = GeoZone(
        id = "zone-${restriction.name}",
        country = "GR",
        name = "Test zone",
        type = null,
        restriction = restriction,
        reason = emptyList(),
        otherReasonInfo = null,
        message = null,
        applicability = emptyList(),
        authorities = emptyList(),
        geometries = emptyList(),
        colorHex = null,
        arc = null,
        isDummy = false,
    )
}
