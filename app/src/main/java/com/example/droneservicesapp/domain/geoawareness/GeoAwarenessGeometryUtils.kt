package com.example.droneservicesapp.domain.geoawareness

import com.example.droneservicesapp.domain.model.LatLon
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object GeoAwarenessGeometryUtils {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val SEGMENT_EPSILON = 1e-12

    fun distanceMeters(a: LatLon, b: LatLon): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(b.lon - a.lon)

        val haversine = sin(deltaLat / 2.0) * sin(deltaLat / 2.0) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2.0) * sin(deltaLon / 2.0)

        val centralAngle = 2.0 * atan2(sqrt(haversine), sqrt(max(0.0, 1.0 - haversine)))
        return EARTH_RADIUS_METERS * centralAngle
    }

    fun altitudeOverlaps(
        missionAltitudeMeters: Double?,
        zoneLowerMeters: Double?,
        zoneUpperMeters: Double?
    ): Boolean = altitudeOverlaps(
        altitudeContext = GeoAltitudeContext(aglMeters = missionAltitudeMeters),
        zoneLowerMeters = zoneLowerMeters,
        zoneUpperMeters = zoneUpperMeters,
        lowerReference = GeoVerticalReference.AGL,
        upperReference = GeoVerticalReference.AGL
    )

    fun altitudeOverlaps(
        altitudeContext: GeoAltitudeContext?,
        zoneLowerMeters: Double?,
        zoneUpperMeters: Double?,
        lowerReference: GeoVerticalReference,
        upperReference: GeoVerticalReference
    ): Boolean {
        val context = altitudeContext ?: return true
        if (zoneLowerMeters == null && zoneUpperMeters == null) {
            return true
        }

        val lowerAltitude = context.altitudeFor(lowerReference)
        val upperAltitude = context.altitudeFor(upperReference)
        val lowerOk = zoneLowerMeters?.let { lower -> lowerAltitude?.let { it >= lower } ?: true } ?: true
        val upperOk = zoneUpperMeters?.let { upper -> upperAltitude?.let { it <= upper } ?: true } ?: true
        return lowerOk && upperOk
    }

    private fun altitudeOverlapsGeometry(
        altitudeContext: GeoAltitudeContext?,
        geometry: GeoZoneGeometry
    ): Boolean {
        return altitudeOverlaps(
            altitudeContext = altitudeContext,
            zoneLowerMeters = geometry.lowerLimitMeters,
            zoneUpperMeters = geometry.upperLimitMeters,
            lowerReference = geometry.lowerVerticalReference,
            upperReference = geometry.upperVerticalReference
        )
    }

    fun pointInZone(
        point: LatLon,
        geometry: GeoZoneGeometry,
        missionAltitudeMeters: Double? = null
    ): Boolean = pointInZone(
        point = point,
        geometry = geometry,
        altitudeContext = GeoAltitudeContext(aglMeters = missionAltitudeMeters)
    )

    fun pointInZone(
        point: LatLon,
        geometry: GeoZoneGeometry,
        altitudeContext: GeoAltitudeContext?
    ): Boolean {
        if (!altitudeOverlapsGeometry(altitudeContext, geometry)) {
            return false
        }

        return when (geometry) {
            is GeoZoneGeometry.Circle -> distanceMeters(point, geometry.center) <= geometry.radiusMeters
            is GeoZoneGeometry.Polygon -> pointInPolygonRings(point, geometry.rings)
        }
    }

    fun distanceMetersToZone(position: LatLon, zone: GeoZone): Double? {
        return zone.geometries
            .mapNotNull { geometry -> distanceMetersToGeometry(position, geometry) }
            .minOrNull()
    }

    fun distanceMetersToGeometry(position: LatLon, geometry: GeoZoneGeometry): Double? {
        return when (geometry) {
            is GeoZoneGeometry.Circle -> {
                val centerDistance = distanceMeters(position, geometry.center)
                max(0.0, centerDistance - geometry.radiusMeters)
            }
            is GeoZoneGeometry.Polygon -> {
                if (pointInPolygonRings(position, geometry.rings)) {
                    0.0
                } else {
                    distanceMetersToPolygonRings(position, geometry.rings)
                }
            }
        }
    }

    fun pointInPolygonRings(point: LatLon, rings: List<List<LatLon>>): Boolean {
        val outerRing = rings.firstOrNull()?.let(::normalizeRing)?.takeIf { distinctPointCount(it) >= 3 } ?: return false
        if (!pointInRing(point, outerRing)) {
            return false
        }

        for (hole in rings.drop(1)) {
            val normalizedHole = normalizeRing(hole)
            if (distinctPointCount(normalizedHole) >= 3 && pointInRing(point, normalizedHole)) {
                return false
            }
        }

        return true
    }

    fun pointInRing(point: LatLon, ring: List<LatLon>): Boolean {
        val normalized = normalizeRing(ring)
        if (distinctPointCount(normalized) < 3) {
            return false
        }

        val closed = closeRing(normalized)
        for (index in 0 until closed.lastIndex) {
            if (onSegment(closed[index], point, closed[index + 1])) {
                return true
            }
        }

        var inside = false
        val x = point.lon
        val y = point.lat
        for (index in 0 until closed.lastIndex) {
            val a = closed[index]
            val b = closed[index + 1]
            val latDelta = b.lat - a.lat
            if (abs(latDelta) <= SEGMENT_EPSILON) {
                continue
            }
            val intersects = ((a.lat > y) != (b.lat > y)) &&
                (x <= ((b.lon - a.lon) * (y - a.lat) / latDelta + a.lon))
            if (intersects) {
                inside = !inside
            }
        }

        return inside
    }

    fun segmentIntersectsRing(
        a: LatLon,
        b: LatLon,
        ring: List<LatLon>
    ): Boolean {
        val normalized = normalizeRing(ring)
        if (distinctPointCount(normalized) < 3) {
            return false
        }

        if (pointInRing(a, normalized) || pointInRing(b, normalized)) {
            return true
        }

        val closed = closeRing(normalized)
        for (index in 0 until closed.lastIndex) {
            if (segmentsIntersect(a, b, closed[index], closed[index + 1])) {
                return true
            }
        }

        return false
    }

    fun segmentIntersectsPolygonRings(
        a: LatLon,
        b: LatLon,
        rings: List<List<LatLon>>
    ): Boolean {
        val outerRing = rings.firstOrNull()?.let(::normalizeRing)?.takeIf { distinctPointCount(it) >= 3 } ?: return false
        return segmentIntersectsRing(a, b, outerRing)
    }

    fun pathIntersectsGeometry(
        path: List<LatLon>,
        geometry: GeoZoneGeometry,
        missionAltitudeMeters: Double? = null
    ): Boolean = pathIntersectsGeometry(
        path = path,
        geometry = geometry,
        altitudeContext = GeoAltitudeContext(aglMeters = missionAltitudeMeters)
    )

    fun pathIntersectsGeometry(
        path: List<LatLon>,
        geometry: GeoZoneGeometry,
        altitudeContext: GeoAltitudeContext?
    ): Boolean {
        if (path.isEmpty()) {
            return false
        }

        if (!altitudeOverlapsGeometry(altitudeContext, geometry)) {
            return false
        }

        if (path.size == 1) {
            return pointInZone(path.first(), geometry, altitudeContext)
        }

        return when (geometry) {
            is GeoZoneGeometry.Circle -> {
                if (path.any { distanceMeters(it, geometry.center) <= geometry.radiusMeters }) {
                    return true
                }
                path.zipWithNext().any { (start, end) ->
                    distancePointToSegmentMeters(geometry.center, start, end) <= geometry.radiusMeters
                }
            }
            is GeoZoneGeometry.Polygon -> {
                if (path.any { pointInPolygonRings(it, geometry.rings) }) {
                    return true
                }
                path.zipWithNext().any { (start, end) ->
                    segmentIntersectsPolygonRings(start, end, geometry.rings)
                }
            }
        }
    }

    fun polygonIntersectsGeometry(
        polygon: List<LatLon>,
        geometry: GeoZoneGeometry,
        missionAltitudeMeters: Double? = null
    ): Boolean = polygonIntersectsGeometry(
        polygon = polygon,
        geometry = geometry,
        altitudeContext = GeoAltitudeContext(aglMeters = missionAltitudeMeters)
    )

    fun polygonIntersectsGeometry(
        polygon: List<LatLon>,
        geometry: GeoZoneGeometry,
        altitudeContext: GeoAltitudeContext?
    ): Boolean {
        val missionRing = normalizeRing(polygon)
        if (distinctPointCount(missionRing) < 3) {
            return false
        }

        if (!altitudeOverlapsGeometry(altitudeContext, geometry)) {
            return false
        }

        return when (geometry) {
            is GeoZoneGeometry.Circle -> {
                if (missionRing.any { distanceMeters(it, geometry.center) <= geometry.radiusMeters }) {
                    return true
                }
                if (pointInRing(geometry.center, missionRing)) {
                    return true
                }
                closeRing(missionRing).zipWithNext().any { (start, end) ->
                    distancePointToSegmentMeters(geometry.center, start, end) <= geometry.radiusMeters
                }
            }
            is GeoZoneGeometry.Polygon -> {
                val zoneOuterRing = geometry.rings.firstOrNull()?.let(::normalizeRing)?.takeIf { distinctPointCount(it) >= 3 } ?: return false
                if (missionRing.any { pointInPolygonRings(it, geometry.rings) }) {
                    return true
                }
                if (zoneOuterRing.any { pointInRing(it, missionRing) }) {
                    return true
                }
                val missionEdges = edgesOfRing(missionRing)
                val zoneEdges = edgesOfRing(zoneOuterRing)
                missionEdges.any { (missionStart, missionEnd) ->
                    zoneEdges.any { (zoneStart, zoneEnd) ->
                        segmentsIntersect(missionStart, missionEnd, zoneStart, zoneEnd)
                    }
                }
            }
        }
    }

    fun distancePointToSegmentMeters(
        point: LatLon,
        segmentStart: LatLon,
        segmentEnd: LatLon
    ): Double {
        if (segmentStart == segmentEnd) {
            return distanceMeters(point, segmentStart)
        }

        val midpointLat = Math.toRadians((segmentStart.lat + segmentEnd.lat) / 2.0)
        val pointXY = toLocalMeters(point, segmentStart, midpointLat)
        val startXY = toLocalMeters(segmentStart, segmentStart, midpointLat)
        val endXY = toLocalMeters(segmentEnd, segmentStart, midpointLat)

        val dx = endXY.first - startXY.first
        val dy = endXY.second - startXY.second
        val segmentLengthSquared = dx * dx + dy * dy
        if (segmentLengthSquared <= SEGMENT_EPSILON) {
            return distanceMeters(point, segmentStart)
        }

        val projection = ((pointXY.first - startXY.first) * dx + (pointXY.second - startXY.second) * dy) / segmentLengthSquared
        val clampedProjection = projection.coerceIn(0.0, 1.0)
        val closestX = startXY.first + clampedProjection * dx
        val closestY = startXY.second + clampedProjection * dy
        val deltaX = pointXY.first - closestX
        val deltaY = pointXY.second - closestY
        return sqrt(deltaX * deltaX + deltaY * deltaY)
    }

    private fun distanceMetersToPolygonRings(point: LatLon, rings: List<List<LatLon>>): Double? {
        var minimumDistance: Double? = null
        rings.forEach { ring ->
            val normalized = normalizeRing(ring)
            if (normalized.size < 2) {
                return@forEach
            }
            val closed = closeRing(normalized)
            for (index in 0 until closed.lastIndex) {
                val distance = pointToSegmentDistanceMeters(
                    point = point,
                    segmentStart = closed[index],
                    segmentEnd = closed[index + 1]
                )
                minimumDistance = if (minimumDistance == null) {
                    distance
                } else {
                    min(minimumDistance!!, distance)
                }
            }
        }
        return minimumDistance
    }

    private fun pointToSegmentDistanceMeters(
        point: LatLon,
        segmentStart: LatLon,
        segmentEnd: LatLon
    ): Double {
        val referenceLatRadians = Math.toRadians(point.lat)
        val (px, py) = toLocalMeters(point, point, referenceLatRadians)
        val (ax, ay) = toLocalMeters(segmentStart, point, referenceLatRadians)
        val (bx, by) = toLocalMeters(segmentEnd, point, referenceLatRadians)
        return pointToSegmentDistanceMeters(px, py, ax, ay, bx, by)
    }

    private fun pointToSegmentDistanceMeters(
        px: Double,
        py: Double,
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double
    ): Double {
        val dx = bx - ax
        val dy = by - ay
        val segmentLengthSquared = dx * dx + dy * dy
        if (segmentLengthSquared <= SEGMENT_EPSILON) {
            val deltaX = px - ax
            val deltaY = py - ay
            return sqrt(deltaX * deltaX + deltaY * deltaY)
        }

        val projection = ((px - ax) * dx + (py - ay) * dy) / segmentLengthSquared
        val clampedProjection = projection.coerceIn(0.0, 1.0)
        val closestX = ax + clampedProjection * dx
        val closestY = ay + clampedProjection * dy
        val deltaX = px - closestX
        val deltaY = py - closestY
        return sqrt(deltaX * deltaX + deltaY * deltaY)
    }

    fun normalizeRing(ring: List<LatLon>): List<LatLon> {
        if (ring.isEmpty()) {
            return emptyList()
        }

        val normalized = mutableListOf<LatLon>()
        for (point in ring) {
            if (normalized.lastOrNull() != point) {
                normalized += point
            }
        }

        if (normalized.size > 1 && normalized.first() == normalized.last()) {
            normalized.removeAt(normalized.lastIndex)
        }

        return normalized
    }

    fun closeRing(ring: List<LatLon>): List<LatLon> {
        if (ring.isEmpty()) {
            return emptyList()
        }

        return if (ring.first() == ring.last()) {
            ring
        } else {
            ring + ring.first()
        }
    }

    fun segmentsIntersect(
        a: LatLon,
        b: LatLon,
        c: LatLon,
        d: LatLon
    ): Boolean {
        val o1 = orientation(a, b, c)
        val o2 = orientation(a, b, d)
        val o3 = orientation(c, d, a)
        val o4 = orientation(c, d, b)

        if ((o1 > SEGMENT_EPSILON && o2 < -SEGMENT_EPSILON || o1 < -SEGMENT_EPSILON && o2 > SEGMENT_EPSILON) &&
            (o3 > SEGMENT_EPSILON && o4 < -SEGMENT_EPSILON || o3 < -SEGMENT_EPSILON && o4 > SEGMENT_EPSILON)
        ) {
            return true
        }

        return (abs(o1) <= SEGMENT_EPSILON && onSegment(a, c, b)) ||
            (abs(o2) <= SEGMENT_EPSILON && onSegment(a, d, b)) ||
            (abs(o3) <= SEGMENT_EPSILON && onSegment(c, a, d)) ||
            (abs(o4) <= SEGMENT_EPSILON && onSegment(c, b, d))
    }

    private fun orientation(a: LatLon, b: LatLon, c: LatLon): Double {
        return (b.lon - a.lon) * (c.lat - a.lat) - (b.lat - a.lat) * (c.lon - a.lon)
    }

    private fun onSegment(a: LatLon, b: LatLon, c: LatLon): Boolean {
        return abs(orientation(a, b, c)) <= SEGMENT_EPSILON &&
            b.lon <= max(a.lon, c.lon) + SEGMENT_EPSILON &&
            b.lon + SEGMENT_EPSILON >= min(a.lon, c.lon) &&
            b.lat <= max(a.lat, c.lat) + SEGMENT_EPSILON &&
            b.lat + SEGMENT_EPSILON >= min(a.lat, c.lat)
    }

    private fun distinctPointCount(ring: List<LatLon>): Int = ring.distinct().size

    private fun edgesOfRing(ring: List<LatLon>): List<Pair<LatLon, LatLon>> {
        val normalized = normalizeRing(ring)
        if (distinctPointCount(normalized) < 3) {
            return emptyList()
        }
        return closeRing(normalized).zipWithNext()
    }

    private fun toLocalMeters(point: LatLon, origin: LatLon, referenceLatRadians: Double): Pair<Double, Double> {
        val x = Math.toRadians(point.lon - origin.lon) * EARTH_RADIUS_METERS * cos(referenceLatRadians)
        val y = Math.toRadians(point.lat - origin.lat) * EARTH_RADIUS_METERS
        return x to y
    }
}
