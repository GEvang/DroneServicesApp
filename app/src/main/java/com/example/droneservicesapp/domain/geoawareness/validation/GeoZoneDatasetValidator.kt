package com.example.droneservicesapp.domain.geoawareness.validation

import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessGeometryUtils
import com.example.droneservicesapp.domain.geoawareness.GeoAltitudeUnit
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneGeometry
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction
import com.example.droneservicesapp.domain.geoawareness.GeoVerticalReference
import com.example.droneservicesapp.domain.model.LatLon
import org.json.JSONObject

object GeoZoneDatasetValidator {
    private const val SUSPICIOUS_EDGE_METERS = 500_000.0

    fun validate(
        rawJson: String,
        datasetInfo: GeoZoneDatasetInfo?,
        zones: List<GeoZone>
    ): GeoZoneValidationResult {
        val issues = mutableListOf<GeoZoneValidationIssue>()

        if (rawJson.isBlank()) {
            issues += issue(GeoZoneValidationSeverity.ERROR, "RAW_JSON_EMPTY", "Raw geo-zone JSON is empty.")
            return GeoZoneValidationResult.fromIssues(issues)
        }

        val root = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            issues += issue(GeoZoneValidationSeverity.ERROR, "ROOT_NOT_JSON_OBJECT", "Geo-zone root is not a JSON object.")
            return GeoZoneValidationResult.fromIssues(issues)
        }

        if (!root.has("features")) {
            issues += issue(GeoZoneValidationSeverity.ERROR, "FEATURES_MISSING", "Geo-zone dataset is missing root.features.")
        } else if (root.optJSONArray("features") == null) {
            issues += issue(GeoZoneValidationSeverity.ERROR, "FEATURES_NOT_ARRAY", "Geo-zone dataset root.features is not an array.")
        } else {
            val featureCount = root.optJSONArray("features")?.length() ?: 0
            if (featureCount != zones.size) {
                issues += issue(
                    GeoZoneValidationSeverity.WARNING,
                    "FEATURE_COUNT_MISMATCH",
                    "Parsed zone count does not match raw feature count.",
                    field = "features"
                )
            }
        }

        validateMetadata(datasetInfo, issues)
        validateZones(zones, issues)
        validateDuplicateIds(zones, issues)

        return GeoZoneValidationResult.fromIssues(issues)
    }

    private fun validateMetadata(
        datasetInfo: GeoZoneDatasetInfo?,
        issues: MutableList<GeoZoneValidationIssue>
    ) {
        if (datasetInfo == null) return

        if (datasetInfo.title.isBlank()) {
            issues += issue(GeoZoneValidationSeverity.WARNING, "DATASET_TITLE_MISSING", "Dataset title is missing.", field = "title")
        }
        if (datasetInfo.version.isNullOrBlank()) {
            issues += issue(GeoZoneValidationSeverity.WARNING, "DATASET_VERSION_MISSING", "Dataset version is missing.", field = "version")
        }
        if (datasetInfo.source.isNullOrBlank()) {
            issues += issue(GeoZoneValidationSeverity.INFO, "DATASET_SOURCE_MISSING", "Dataset source is missing.", field = "source")
        }
        if (datasetInfo.country.isNullOrBlank()) {
            issues += issue(GeoZoneValidationSeverity.WARNING, "DATASET_COUNTRY_MISSING", "Dataset country is missing.", field = "country")
        }
        if (datasetInfo.isDummy) {
            issues += issue(GeoZoneValidationSeverity.WARNING, "DATASET_IS_DUMMY", "Dataset is marked as dummy/test data.", field = "dummy")
        }
    }

    private fun validateZones(
        zones: List<GeoZone>,
        issues: MutableList<GeoZoneValidationIssue>
    ) {
        zones.forEach { zone ->
            if (zone.id.isBlank()) {
                issues += issue(GeoZoneValidationSeverity.ERROR, "ZONE_ID_MISSING", "Zone identifier is missing.", zoneId = zone.id, field = "id")
            }
            if (zone.name.isBlank()) {
                issues += issue(GeoZoneValidationSeverity.WARNING, "ZONE_NAME_MISSING", "Zone name is missing.", zoneId = zone.id, field = "name")
            }
            if (zone.country.isBlank()) {
                issues += issue(GeoZoneValidationSeverity.WARNING, "ZONE_COUNTRY_MISSING", "Zone country is missing.", zoneId = zone.id, field = "country")
            }
            if (zone.restriction == GeoZoneRestriction.UNKNOWN) {
                issues += issue(GeoZoneValidationSeverity.WARNING, "ZONE_RESTRICTION_UNKNOWN", "Zone restriction is unknown.", zoneId = zone.id, field = "restriction")
            }
            if (zone.geometries.isEmpty()) {
                issues += issue(GeoZoneValidationSeverity.ERROR, "ZONE_GEOMETRY_MISSING", "Zone has no geometries.", zoneId = zone.id, field = "geometry")
            }

            zone.geometries.forEach { geometry ->
                validateAltitude(zone.id, geometry, issues)
                when (geometry) {
                    is GeoZoneGeometry.Circle -> validateCircle(zone.id, geometry, issues)
                    is GeoZoneGeometry.Polygon -> validatePolygon(zone.id, geometry, issues)
                }
            }
        }
    }

    private fun validateDuplicateIds(
        zones: List<GeoZone>,
        issues: MutableList<GeoZoneValidationIssue>
    ) {
        zones.groupBy { it.id }
            .filter { (id, grouped) -> id.isNotBlank() && grouped.size > 1 }
            .keys
            .forEach { duplicateId ->
                issues += issue(
                    GeoZoneValidationSeverity.WARNING,
                    "ZONE_ID_DUPLICATE",
                    "Duplicate zone identifier detected. Import allowed, but conflicts/logs may reference non-unique IDs.",
                    zoneId = duplicateId,
                    field = "id"
                )
            }
    }

    private fun validateCircle(
        zoneId: String,
        geometry: GeoZoneGeometry.Circle,
        issues: MutableList<GeoZoneValidationIssue>
    ) {
        validateCoordinate(zoneId, geometry.center, issues)
        if (geometry.radiusMeters <= 0.0) {
            issues += issue(GeoZoneValidationSeverity.ERROR, "CIRCLE_RADIUS_INVALID", "Circle radius must be positive.", zoneId, "radiusMeters")
        }
        if (geometry.radiusMeters > 50_000.0) {
            issues += issue(GeoZoneValidationSeverity.WARNING, "CIRCLE_RADIUS_SUSPICIOUS", "Circle radius is suspiciously large.", zoneId, "radiusMeters")
        }
    }

    private fun validatePolygon(
        zoneId: String,
        geometry: GeoZoneGeometry.Polygon,
        issues: MutableList<GeoZoneValidationIssue>
    ) {
        if (geometry.rings.isEmpty()) {
            issues += issue(GeoZoneValidationSeverity.ERROR, "POLYGON_RINGS_EMPTY", "Polygon has no rings.", zoneId, "rings")
            return
        }

        val outerRing = geometry.rings.first()
        if (outerRing.distinct().size < 3) {
            issues += issue(GeoZoneValidationSeverity.ERROR, "POLYGON_OUTER_RING_INVALID", "Polygon outer ring has fewer than 3 distinct points.", zoneId, "rings[0]")
        }

        geometry.rings.forEachIndexed { ringIndex, ring ->
            ring.forEach { validateCoordinate(zoneId, it, issues) }
            if (ring.isNotEmpty() && ring.first() != ring.last()) {
                issues += issue(
                    GeoZoneValidationSeverity.WARNING,
                    "POLYGON_RING_NOT_CLOSED",
                    "Polygon ring is not closed.",
                    zoneId,
                    "rings[$ringIndex]"
                )
            }
            ring.zipWithNext().forEach { (a, b) ->
                if (GeoAwarenessGeometryUtils.distanceMeters(a, b) > SUSPICIOUS_EDGE_METERS) {
                    issues += issue(
                        GeoZoneValidationSeverity.WARNING,
                        "POLYGON_EDGE_SUSPICIOUS",
                        "Polygon edge jump is suspiciously large.",
                        zoneId,
                        "rings[$ringIndex]"
                    )
                    return@forEach
                }
            }
        }
    }

    private fun validateAltitude(
        zoneId: String,
        geometry: GeoZoneGeometry,
        issues: MutableList<GeoZoneValidationIssue>
    ) {
        val lower = geometry.lowerLimitMeters
        val upper = geometry.upperLimitMeters
        if (geometry.altitudeUnit == GeoAltitudeUnit.UNKNOWN && (lower != null || upper != null)) {
            issues += issue(GeoZoneValidationSeverity.WARNING, "ALTITUDE_UNIT_UNKNOWN", "Altitude unit is missing or unknown.", zoneId, "uomDimensions")
        }
        if (geometry.lowerVerticalReference == GeoVerticalReference.UNKNOWN && lower != null) {
            issues += issue(GeoZoneValidationSeverity.WARNING, "LOWER_VERTICAL_REFERENCE_UNKNOWN", "Lower vertical reference is missing or unknown.", zoneId, "lowerVerticalReference")
        }
        if (geometry.upperVerticalReference == GeoVerticalReference.UNKNOWN && upper != null) {
            issues += issue(GeoZoneValidationSeverity.WARNING, "UPPER_VERTICAL_REFERENCE_UNKNOWN", "Upper vertical reference is missing or unknown.", zoneId, "upperVerticalReference")
        }
        if ((lower != null && lower < -500.0) || (upper != null && upper < -500.0)) {
            issues += issue(GeoZoneValidationSeverity.WARNING, "ALTITUDE_NEGATIVE_SUSPICIOUS", "Altitude limit is suspiciously negative.", zoneId, "altitude")
        }
        if (lower != null && upper != null && lower > upper) {
            issues += issue(GeoZoneValidationSeverity.ERROR, "ALTITUDE_RANGE_INVALID", "Altitude lower limit exceeds upper limit.", zoneId, "altitude")
        }
        if (upper != null && upper > 20_000.0) {
            issues += issue(GeoZoneValidationSeverity.WARNING, "ALTITUDE_UPPER_SUSPICIOUS", "Altitude upper limit is suspiciously high.", zoneId, "upperLimitMeters")
        }
    }

    private fun validateCoordinate(
        zoneId: String,
        point: LatLon,
        issues: MutableList<GeoZoneValidationIssue>
    ) {
        if (point.lat !in -90.0..90.0) {
            issues += issue(GeoZoneValidationSeverity.ERROR, "LATITUDE_OUT_OF_RANGE", "Latitude is out of range.", zoneId, "latitude")
        }
        if (point.lon !in -180.0..180.0) {
            issues += issue(GeoZoneValidationSeverity.ERROR, "LONGITUDE_OUT_OF_RANGE", "Longitude is out of range.", zoneId, "longitude")
        }
    }

    private fun issue(
        severity: GeoZoneValidationSeverity,
        code: String,
        message: String,
        zoneId: String? = null,
        field: String? = null
    ): GeoZoneValidationIssue = GeoZoneValidationIssue(
        severity = severity,
        code = code,
        message = message,
        zoneId = zoneId,
        field = field
    )
}
