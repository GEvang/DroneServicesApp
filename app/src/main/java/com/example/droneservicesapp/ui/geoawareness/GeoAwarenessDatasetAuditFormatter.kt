package com.example.droneservicesapp.ui.geoawareness

import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneGeometry
import com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult
import com.example.droneservicesapp.domain.model.LatLon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Produces view-independent dataset transaction metadata for audit events. */
class GeoAwarenessDatasetAuditFormatter {
    fun standardDetails(
        operation: String,
        loadResult: GeoZoneLoadResult,
        requestedUri: String,
        originalFileName: String?,
        storageFileName: String? = null,
    ): Map<String, String> {
        val activeRecords = loadResult.datasetRecords
        val countries = activeRecords.mapNotNull { it.datasetInfo.country?.takeIf(String::isNotBlank) }
            .distinct().joinToString(", ").ifBlank { "not_specified" }
        val applicability = loadResult.zones.flatMap { it.applicability }
        val timeWindowStart = applicability.mapNotNull { it.startDateTime?.takeIf(String::isNotBlank) }.minOrNull()
        val timeWindowEnd = applicability.mapNotNull { it.endDateTime?.takeIf(String::isNotBlank) }.maxOrNull()

        return buildMap {
            put("standardLogSchema", "prEN4709-003-5.4-transaction-scope-v1")
            put("operation", operation)
            put("retrievalMethod", "manual_file_import")
            put("retrievalRequestUri", requestedUri)
            put("retrievalRequestUtc", isoUtc(System.currentTimeMillis()))
            put("officialRetrievalService", "not_implemented")
            put("subscriptionId", "not_applicable_manual_import")
            put("changePublicationId", "not_applicable_manual_import")
            put("originalFileName", originalFileName ?: "unknown")
            storageFileName?.let { put("storageFileName", it) }
            put("datasetTitle", loadResult.datasetInfo.title)
            put("datasetVersion", loadResult.datasetInfo.version ?: "not_specified")
            put("datasetSource", loadResult.datasetInfo.source ?: "not_specified")
            put("datasetSourceUrl", loadResult.datasetInfo.sourceUrl ?: "not_specified")
            put("datasetCountries", countries)
            put("scopeArea", boundingBox(loadResult.zones) ?: countries)
            put("scopeRegionOfInterest", countries)
            put("scopeTimeWindowStart", timeWindowStart ?: "not_provided_by_dataset")
            put("scopeTimeWindowEnd", timeWindowEnd ?: "not_provided_by_dataset")
            put("permanentApplicabilityCount", applicability.count { it.permanent }.toString())
            put("verticalReferenceSummary", verticalReferenceSummary(loadResult.zones))
            put("altitudeUnitSummary", altitudeUnitSummary(loadResult.zones))
            put("activeDatasetCount", activeRecords.size.toString())
            put("activeDatasetIds", activeRecords.joinToString(",") { it.datasetId })
            put("activeDatasetTitles", activeRecords.joinToString(" | ") { it.displayName })
            put("totalZones", loadResult.datasetInfo.zoneCount.toString())
            put("errorCount", loadResult.validationResult.errorCount.toString())
            put("warningCount", loadResult.validationResult.warningCount.toString())
            put("infoCount", loadResult.validationResult.infoCount.toString())
        }
    }

    fun boundingBox(zones: List<GeoZone>): String? {
        val points = zones.flatMap { zone ->
            zone.geometries.flatMap { geometry ->
                when (geometry) {
                    is GeoZoneGeometry.Circle -> listOf(
                        LatLon(geometry.center.lat - latitudeDegrees(geometry.radiusMeters), geometry.center.lon),
                        LatLon(geometry.center.lat + latitudeDegrees(geometry.radiusMeters), geometry.center.lon),
                        LatLon(geometry.center.lat, geometry.center.lon - longitudeDegrees(geometry.radiusMeters, geometry.center.lat)),
                        LatLon(geometry.center.lat, geometry.center.lon + longitudeDegrees(geometry.radiusMeters, geometry.center.lat)),
                    )
                    is GeoZoneGeometry.Polygon -> geometry.rings.flatten()
                }
            }
        }
        if (points.isEmpty()) return null
        return "bbox=${points.minOf { it.lon }},${points.minOf { it.lat }},${points.maxOf { it.lon }},${points.maxOf { it.lat }}"
    }

    fun verticalReferenceSummary(zones: List<GeoZone>): String = zones.flatMap { zone ->
        zone.geometries.flatMap { listOf(it.lowerVerticalReference.name, it.upperVerticalReference.name) }
    }.groupingBy { it }.eachCount().entries.joinToString(",") { "${it.key}:${it.value}" }.ifBlank { "none" }

    fun altitudeUnitSummary(zones: List<GeoZone>): String = zones.flatMap { zone ->
        zone.geometries.map { it.altitudeUnit.name }
    }.groupingBy { it }.eachCount().entries.joinToString(",") { "${it.key}:${it.value}" }.ifBlank { "none" }

    fun isoUtc(timestampMillis: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(timestampMillis))

    private fun latitudeDegrees(meters: Double) = meters / 111_320.0

    private fun longitudeDegrees(meters: Double, latitude: Double): Double {
        val scale = kotlin.math.cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
        return meters / (111_320.0 * scale)
    }
}
