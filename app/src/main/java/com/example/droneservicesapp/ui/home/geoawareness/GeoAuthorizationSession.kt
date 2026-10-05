package com.example.droneservicesapp.ui.home.geoawareness

import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessResult
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction

/** Tracks UGZ authorizations acknowledged for the current armed-flight session. */
class GeoAuthorizationSession {
    private val authorizedZoneIds = mutableSetOf<String>()

    fun requiredZones(result: GeoAwarenessResult): List<GeoZone> = result.conflicts
        .map { it.zone }
        .filter { it.restriction == GeoZoneRestriction.REQ_AUTHORISATION }
        .distinctBy { it.id }

    fun unconfirmedZones(result: GeoAwarenessResult): List<GeoZone> =
        requiredZones(result).filterNot { it.id in authorizedZoneIds }

    fun confirm(zones: Collection<GeoZone>) {
        authorizedZoneIds += zones.map { it.id }
    }

    fun reset(): Set<String> {
        val removed = authorizedZoneIds.toSet()
        authorizedZoneIds.clear()
        return removed
    }

    fun isEmpty(): Boolean = authorizedZoneIds.isEmpty()
}
