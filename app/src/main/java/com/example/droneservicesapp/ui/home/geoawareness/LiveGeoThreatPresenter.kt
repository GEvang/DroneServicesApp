package com.example.droneservicesapp.ui.home.geoawareness

import android.content.Context
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.geoawareness.GeoVerticalReference
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneGeometry
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction
import com.example.droneservicesapp.domain.geoawareness.LiveGeoAwarenessProximityResult
import com.example.droneservicesapp.domain.model.LatLon
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Maps live geo-awareness domain results to the compact threat-panel models. */
class LiveGeoThreatPresenter(private val context: Context) {
    data class Altitudes(val aglMeters: Double?, val amslMeters: Double?)

    fun proximity(
        result: LiveGeoAwarenessProximityResult,
        dronePosition: LatLon,
        altitudes: Altitudes,
    ): LiveGeoThreatUiModel {
        val vertical = result.warningMode.startsWith("VERTICAL")
        val zoneIsAboveDrone = verticalRelation(result, altitudes) == VerticalRelation.ABOVE
        val ratio = (result.distanceMeters / result.effectiveThresholdMeters)
            .takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)?.toFloat() ?: 1f
        return LiveGeoThreatUiModel(
            label = restrictionLabel(result.restriction),
            colorHex = restrictionColor(result.restriction),
            directionText = directionLabel(dronePosition, result.nearestZone) ?: "--",
            distanceText = "H: ${formatDistance(result.distanceMeters)}",
            altitudeText = if (vertical) "V: ${formatDistance(result.verticalDistanceMeters)}" else "V: --",
            verticalArrowText = if (!vertical) "" else if (zoneIsAboveDrone) "\u2191" else "\u2193",
            radialDistanceRatio = 0.25f + ratio * 0.75f,
            bearingDegrees = representativePoint(result.nearestZone)?.let { bearing(dronePosition, it) },
            verticalIndicator = when {
                !vertical -> VerticalIndicator.NONE
                zoneIsAboveDrone -> VerticalIndicator.UP
                else -> VerticalIndicator.DOWN
            },
            showCompassMarker = true,
        )
    }

    fun inside(zone: GeoZone, dronePosition: LatLon): LiveGeoThreatUiModel = LiveGeoThreatUiModel(
        label = restrictionLabel(zone.restriction),
        colorHex = restrictionColor(zone.restriction),
        directionText = directionLabel(dronePosition, zone) ?: context.getString(R.string.live_geo_inside_short),
        distanceText = context.getString(R.string.live_geo_horizontal_inside),
        altitudeText = context.getString(R.string.live_geo_vertical_inside),
        radialDistanceRatio = 0.18f,
        bearingDegrees = null,
        showCompassMarker = false,
        isInsideZone = true,
    )

    fun restrictionLabel(restriction: GeoZoneRestriction): String = context.getString(
        when (restriction) {
            GeoZoneRestriction.PROHIBITED -> R.string.geo_restriction_prohibited
            GeoZoneRestriction.REQ_AUTHORISATION -> R.string.geo_restriction_authorization
            GeoZoneRestriction.CONDITIONAL -> R.string.geo_restriction_conditional
            GeoZoneRestriction.INFORMATION -> R.string.geo_restriction_information
            GeoZoneRestriction.UNKNOWN -> R.string.geo_restriction_unknown
        }
    )

    fun nearRestrictionLabel(restriction: GeoZoneRestriction): String = context.getString(
        when (restriction) {
            GeoZoneRestriction.PROHIBITED -> R.string.geo_restriction_near_prohibited
            GeoZoneRestriction.REQ_AUTHORISATION -> R.string.geo_restriction_near_authorization
            GeoZoneRestriction.CONDITIONAL -> R.string.geo_restriction_near_conditional
            GeoZoneRestriction.INFORMATION -> R.string.geo_restriction_information
            GeoZoneRestriction.UNKNOWN -> R.string.geo_restriction_near_unknown
        }
    )

    fun restrictionColor(restriction: GeoZoneRestriction): String = when (restriction) {
        GeoZoneRestriction.PROHIBITED -> "#FF4F45"
        GeoZoneRestriction.REQ_AUTHORISATION -> "#FF972E"
        GeoZoneRestriction.CONDITIONAL -> "#F4C73D"
        GeoZoneRestriction.INFORMATION -> "#4C9DFF"
        GeoZoneRestriction.UNKNOWN -> "#8D6E63"
    }

    fun restrictionPriority(restriction: GeoZoneRestriction): Int = when (restriction) {
        GeoZoneRestriction.PROHIBITED -> 4
        GeoZoneRestriction.REQ_AUTHORISATION -> 3
        GeoZoneRestriction.CONDITIONAL -> 2
        GeoZoneRestriction.INFORMATION -> 1
        GeoZoneRestriction.UNKNOWN -> 0
    }

    fun formatDistance(distanceMeters: Double?): String {
        val value = distanceMeters?.takeIf { it.isFinite() } ?: return "--"
        return if (value >= 1_000.0) String.format(Locale.US, "%.1f km", value / 1_000.0)
        else "${value.toInt().coerceAtLeast(0)} m"
    }

    private fun directionLabel(from: LatLon, zone: GeoZone): String? =
        representativePoint(zone)?.let { point -> compassDirection(bearing(from, point)) }

    private fun representativePoint(zone: GeoZone): LatLon? = when (val geometry = zone.geometries.firstOrNull()) {
        is GeoZoneGeometry.Circle -> geometry.center
        is GeoZoneGeometry.Polygon -> geometry.rings.firstOrNull().orEmpty().takeIf { it.isNotEmpty() }?.let { ring ->
            LatLon(ring.map { it.lat }.average(), ring.map { it.lon }.average())
        }
        null -> null
    }

    private fun bearing(from: LatLon, to: LatLon): Double {
        val startLat = Math.toRadians(from.lat)
        val endLat = Math.toRadians(to.lat)
        val deltaLon = Math.toRadians(to.lon - from.lon)
        val y = sin(deltaLon) * cos(endLat)
        val x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(deltaLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun compassDirection(bearing: Double): String =
        listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((bearing + 22.5) % 360.0) / 45.0).toInt()]

    private fun verticalRelation(
        proximity: LiveGeoAwarenessProximityResult,
        altitudes: Altitudes,
    ): VerticalRelation {
        val geometry = proximity.nearestZone.geometries.firstOrNull() ?: return VerticalRelation.ABOVE
        fun altitude(reference: GeoVerticalReference): Double? = when (reference) {
            GeoVerticalReference.AGL -> altitudes.aglMeters
            GeoVerticalReference.AMSL -> altitudes.amslMeters
            GeoVerticalReference.UNKNOWN -> altitudes.aglMeters ?: altitudes.amslMeters
        }
        val lowerLimit = geometry.lowerLimitMeters
        val upperLimit = geometry.upperLimitMeters
        return when {
            lowerLimit != null && altitude(geometry.lowerVerticalReference)?.let {
                it < lowerLimit
            } == true -> VerticalRelation.ABOVE
            upperLimit != null && altitude(geometry.upperVerticalReference)?.let {
                it > upperLimit
            } == true -> VerticalRelation.BELOW
            else -> VerticalRelation.ABOVE
        }
    }

    private enum class VerticalRelation { ABOVE, BELOW }
}
