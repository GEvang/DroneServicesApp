package com.example.droneservicesapp.ui.home.map

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessGeometryUtils
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneApplicability
import com.example.droneservicesapp.domain.geoawareness.GeoZoneApplicabilityEvaluator
import com.example.droneservicesapp.domain.geoawareness.GeoZoneGeometry
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.ui.home.MissionDialog
import com.example.droneservicesapp.ui.home.MissionDialogQueue

/**
 * Geo-zone overlay content and zone-detail interaction.
 *
 * Port of the former `GeoZoneOverlayController`: zones are exposed as state for the scene
 * builder, and a tap inside one or more zones opens the same details as before.
 */
class GeoZoneLayerState(
    private val context: Context,
    private val dialogs: MissionDialogQueue,
) {
    var zones by mutableStateOf<List<GeoZone>>(emptyList())
        private set
    var zoneDetailsEnabled by mutableStateOf(true)
        private set

    // The property zoneDetailsEnabled already owns the JVM name setZoneDetailsEnabled.
    @JvmName("applyZoneDetailsEnabled")
    fun setZoneDetailsEnabled(enabled: Boolean) {
        zoneDetailsEnabled = enabled
    }

    fun renderZones(zones: List<GeoZone>) {
        this.zones = zones.toList()
    }

    fun clear() {
        zones = emptyList()
    }

    /** Map tapped. Returns true when the tap landed inside at least one rendered zone. */
    fun onMapTapped(point: LatLon): Boolean {
        if (!zoneDetailsEnabled || zones.isEmpty()) return false
        val matches = findZonesAt(point)
        return when {
            matches.isEmpty() -> false
            matches.size == 1 -> {
                showZoneDetails(matches.first().zone, matches.first().geometry)
                true
            }
            else -> {
                showMultipleZoneMatches(point, matches)
                true
            }
        }
    }

    private fun findZonesAt(point: LatLon): List<ZoneTapMatch> = zones.mapNotNull { zone ->
        val selected = zone.geometries
            .filter { GeoAwarenessGeometryUtils.pointInZone(point = point, geometry = it, missionAltitudeMeters = null) }
            .minByOrNull(::approximateGeometryAreaSquareMeters)
            ?: return@mapNotNull null
        ZoneTapMatch(zone, selected, approximateGeometryAreaSquareMeters(selected))
    }.sortedWith(
        compareBy<ZoneTapMatch>(
            { restrictionPriority(it.zone.restriction) },
            { it.approximateAreaSquareMeters },
            { it.zone.name.lowercase() },
        ),
    )

    private fun showMultipleZoneMatches(point: LatLon, matches: List<ZoneTapMatch>) {
        dialogs.show(
            MissionDialog.ZoneList(
                title = context.getString(R.string.geo_zones_at_location),
                message = "Multiple overlapping geo-zones apply at this location.",
                coordinates = "Latitude: ${point.lat}, Longitude: ${point.lon}",
                rows = matches.map { match ->
                    MissionDialog.ZoneList.Row(
                        name = match.zone.name,
                        restriction = "Restriction: ${match.zone.restriction} | ${formatApplicabilityStatus(match.zone)}",
                        altitude = "Altitude: ${formatDialogAltitude(match.geometry)}",
                        message = match.zone.message?.takeIf { it.isNotBlank() }
                            ?.let { "Message: ${truncatePreview(it, 140)}" },
                    )
                },
                onSelected = { index ->
                    matches.getOrNull(index)?.let { showZoneDetails(it.zone, it.geometry) }
                },
            ),
        )
    }

    private fun showZoneDetails(zone: GeoZone, geometry: GeoZoneGeometry) {
        val message = buildString {
            appendLine("Restriction: ${zone.restriction}")
            appendLine("Applicability: ${formatApplicabilityStatus(zone)}")
            formatApplicabilityWindow(zone)?.let { appendLine("Window: $it") }
            appendLine("Message: ${zone.message ?: "No message"}")
            appendLine("Authority: ${zone.authorities.firstOrNull()?.name ?: "N/A"}")
            append("Altitude: ${formatAltitude(geometry)}")
        }
        dialogs.show(MissionDialog.Message(zone.name, message))
    }

    private fun restrictionPriority(restriction: GeoZoneRestriction): Int = when (restriction) {
        GeoZoneRestriction.PROHIBITED -> 0
        GeoZoneRestriction.REQ_AUTHORISATION -> 1
        GeoZoneRestriction.CONDITIONAL -> 2
        GeoZoneRestriction.INFORMATION -> 3
        GeoZoneRestriction.UNKNOWN -> 4
    }

    private fun formatAltitude(geometry: GeoZoneGeometry): String {
        val lower = geometry.lowerLimitMeters?.formatMeters()
        val upper = geometry.upperLimitMeters?.formatMeters()
        return when {
            lower != null && upper != null -> "$lower-$upper m AGL"
            upper != null -> "Up to $upper m AGL"
            lower != null -> "From $lower m AGL"
            else -> "Not specified"
        }
    }

    private fun formatDialogAltitude(geometry: GeoZoneGeometry): String {
        val lower = geometry.lowerLimitMeters?.formatMeters()
        val upper = geometry.upperLimitMeters?.formatMeters()
        return when {
            lower != null && upper != null -> "$lower-$upper m"
            lower != null -> "$lower m and above"
            upper != null -> "Up to $upper m"
            else -> "Unknown"
        }
    }

    private fun truncatePreview(text: String, maxLength: Int): String =
        if (text.length <= maxLength) text else text.take(maxLength).trimEnd() + "..."

    private fun approximateGeometryAreaSquareMeters(geometry: GeoZoneGeometry): Double = when (geometry) {
        is GeoZoneGeometry.Circle -> Math.PI * geometry.radiusMeters * geometry.radiusMeters
        is GeoZoneGeometry.Polygon -> approximatePolygonBoundingBoxAreaSquareMeters(geometry)
    }

    private fun approximatePolygonBoundingBoxAreaSquareMeters(geometry: GeoZoneGeometry.Polygon): Double {
        val outerRing = geometry.rings.firstOrNull().orEmpty()
        if (outerRing.size < 3) return Double.MAX_VALUE
        val minLat = outerRing.minOf { it.lat }
        val maxLat = outerRing.maxOf { it.lat }
        val minLon = outerRing.minOf { it.lon }
        val maxLon = outerRing.maxOf { it.lon }
        val centerLat = (minLat + maxLat) / 2.0
        val centerLon = (minLon + maxLon) / 2.0
        val widthMeters = GeoAwarenessGeometryUtils.distanceMeters(LatLon(centerLat, minLon), LatLon(centerLat, maxLon))
        val heightMeters = GeoAwarenessGeometryUtils.distanceMeters(LatLon(minLat, centerLon), LatLon(maxLat, centerLon))
        return widthMeters * heightMeters
    }

    private fun formatApplicabilityStatus(zone: GeoZone): String =
        if (GeoZoneApplicabilityEvaluator.isActiveNow(zone)) "Active now" else "Inactive by date"

    private fun formatApplicabilityWindow(zone: GeoZone): String? {
        if (zone.applicability.isEmpty()) return "No applicability window specified; treated as active"
        val permanentCount = zone.applicability.count { it.permanent }
        val start = zone.applicability.mapNotNull(GeoZoneApplicability::startDateTime).minOrNull()
        val end = zone.applicability.mapNotNull(GeoZoneApplicability::endDateTime).maxOrNull()
        return buildString {
            append(if (permanentCount > 0) "Permanent" else "Temporary")
            if (!start.isNullOrBlank() || !end.isNullOrBlank()) {
                append(" (")
                append(start ?: "open start")
                append(" to ")
                append(end ?: "open end")
                append(")")
            }
        }
    }

    private fun Double.formatMeters(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

    private data class ZoneTapMatch(
        val zone: GeoZone,
        val geometry: GeoZoneGeometry,
        val approximateAreaSquareMeters: Double,
    )
}
