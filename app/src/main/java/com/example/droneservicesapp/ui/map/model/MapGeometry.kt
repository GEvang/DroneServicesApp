package com.example.droneservicesapp.ui.map.model

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Pure geometry used to build map scenes without a map engine projection. */
object MapGeometry {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val MAX_MERCATOR_LATITUDE = 85.05112878
    private const val MIN_VALID_ABS_COORDINATE = 1e-4

    /**
     * Marker rotation that makes a right-pointing arrow follow the on-screen direction from
     * [from] to [to] on an unrotated Web-Mercator map. This is the exact equivalent of the
     * previous projection-based calculation, without needing a live map view.
     */
    fun screenArrowRotationDegrees(from: MapCoordinate, to: MapCoordinate): Float {
        val dx = Math.toRadians(to.longitude - from.longitude)
        val dy = -(mercatorY(to.latitude) - mercatorY(from.latitude))
        if (dx == 0.0 && dy == 0.0) return 0f
        return ((-Math.toDegrees(atan2(dy, dx)) + 360.0) % 360.0).toFloat()
    }

    /**
     * Unit vector, in screen orientation (x right, y down), of the segment [from] -> [to] on an
     * unrotated Web-Mercator map, or null for a zero-length segment.
     */
    fun screenDirection(from: MapCoordinate, to: MapCoordinate): Pair<Double, Double>? {
        val dx = Math.toRadians(to.longitude - from.longitude)
        val dy = -(mercatorY(to.latitude) - mercatorY(from.latitude))
        val length = sqrt(dx * dx + dy * dy)
        if (length == 0.0) return null
        return (dx / length) to (dy / length)
    }

    /** Initial geodesic bearing in degrees, -180..180 (clockwise from north). */
    fun bearingDegrees(from: MapCoordinate, to: MapCoordinate): Float {
        val fromLat = Math.toRadians(from.latitude)
        val toLat = Math.toRadians(to.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(deltaLon) * cos(toLat)
        val x = cos(fromLat) * sin(toLat) - sin(fromLat) * cos(toLat) * cos(deltaLon)
        return Math.toDegrees(atan2(y, x)).toFloat()
    }

    fun midpoint(from: MapCoordinate, to: MapCoordinate) = MapCoordinate(
        (from.latitude + to.latitude) / 2.0,
        (from.longitude + to.longitude) / 2.0,
    )

    /**
     * Closed great-circle ring of [segments] points (plus the closing point) around [center].
     * Returns an empty list for a non-positive radius.
     */
    fun circle(center: MapCoordinate, radiusMeters: Double, segments: Int): List<MapCoordinate> {
        if (radiusMeters <= 0.0 || segments < 3) return emptyList()
        val angularDistance = radiusMeters / EARTH_RADIUS_METERS
        val lat1 = Math.toRadians(center.latitude)
        val lon1 = Math.toRadians(center.longitude)
        val points = ArrayList<MapCoordinate>(segments + 1)
        for (index in 0 until segments) {
            val bearing = 2.0 * PI * index / segments
            val lat2 = asin(sin(lat1) * cos(angularDistance) + cos(lat1) * sin(angularDistance) * cos(bearing))
            val lon2 = lon1 + atan2(
                sin(bearing) * sin(angularDistance) * cos(lat1),
                cos(angularDistance) - sin(lat1) * sin(lat2),
            )
            points += MapCoordinate(Math.toDegrees(lat2), normalizeLongitude(Math.toDegrees(lon2)))
        }
        points += points.first()
        return points
    }

    /** Appends the first point when the ring is not already closed. */
    fun closeRing(points: List<MapCoordinate>): List<MapCoordinate> {
        if (points.isEmpty()) return points
        return if (points.first() == points.last()) points else points + points.first()
    }

    /** True for finite, in-range coordinates that are not the (0, 0) "no fix" placeholder. */
    fun isUsable(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            (kotlin.math.abs(latitude) > MIN_VALID_ABS_COORDINATE ||
                kotlin.math.abs(longitude) > MIN_VALID_ABS_COORDINATE)

    fun coordinateOrNull(latitude: Double, longitude: Double): MapCoordinate? =
        if (isUsable(latitude, longitude)) MapCoordinate(latitude, longitude) else null

    private fun mercatorY(latitude: Double): Double {
        val radians = Math.toRadians(latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE))
        return ln(tan(PI / 4.0 + radians / 2.0))
    }

    private fun normalizeLongitude(longitude: Double): Double {
        var value = longitude
        while (value > 180.0) value -= 360.0
        while (value < -180.0) value += 360.0
        return value
    }
}
