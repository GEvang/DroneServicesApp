package com.example.droneservicesapp.domain.terrain

import com.example.droneservicesapp.domain.model.LatLon
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

data class TerrainCoveragePlan(
    val center: LatLon,
    val radiusMeters: Double,
    val requiredPathSamples: List<LatLon>,
    val sourceTiles: Set<SrtmTileId>,
)

class MissionOutsideTerrainCoverageException(
    val point: LatLon,
    val distanceMeters: Double,
) : IllegalArgumentException("Mission point is ${distanceMeters.toInt()} m from terrain center")

object TerrainCoveragePlanner {
    const val COVERAGE_RADIUS_METERS = 1_000.0
    const val GRID_SPACING_METERS = 30.0
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    fun createPlan(
        missionPath: List<LatLon>,
        home: LatLon,
        returnPaths: List<List<LatLon>> = emptyList(),
        radiusMeters: Double = COVERAGE_RADIUS_METERS,
        enforceCoverage: Boolean = true,
    ): TerrainCoveragePlan {
        require(missionPath.isNotEmpty()) { "Terrain mission path is empty" }
        require(radiusMeters > 0.0) { "Terrain coverage radius must be positive" }
        val authoredPoints = buildList {
            addAll(missionPath)
            returnPaths.forEach(::addAll)
        }
        val center = missionCenter(missionPath)
        val sampled = buildList {
            add(home)
            addAll(densify(missionPath, GRID_SPACING_METERS))
            returnPaths.forEach { addAll(densify(it, GRID_SPACING_METERS)) }
        }.distinct()

        sampled.forEach { point ->
            val distance = distanceMeters(center, point)
            if (enforceCoverage && distance > radiusMeters + 0.01) {
                throw MissionOutsideTerrainCoverageException(point, distance)
            }
        }

        // The debug bypass deliberately prepares only the normal local coverage area. Including
        // a distant home in the plan would turn a diagnostic test into a multi-tile corridor
        // download and would still fail when the local elevation source is queried later.
        val requiredSamples = if (enforceCoverage) {
            sampled
        } else {
            sampled.filter { distanceMeters(center, it) <= radiusMeters + 0.01 }
        }

        return TerrainCoveragePlan(
            center = center,
            radiusMeters = radiusMeters,
            requiredPathSamples = requiredSamples.ifEmpty { authoredPoints },
            // A TERRAIN_REQUEST covers a complete 32 x 28 sample block. Load a source margin so
            // an edge request can be answered even when its unused samples extend past 1 km.
            sourceTiles = tilesIntersectingCircle(center, radiusMeters + MAX_REQUEST_BLOCK_EXTENT_METERS),
        )
    }

    fun distanceMeters(first: LatLon, second: LatLon): Double {
        val lat1 = first.lat.toRadians()
        val lat2 = second.lat.toRadians()
        val deltaLat = (second.lat - first.lat).toRadians()
        val deltaLon = (second.lon - first.lon).toRadians()
        val a = sin(deltaLat / 2.0) * sin(deltaLat / 2.0) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2.0) * sin(deltaLon / 2.0)
        return 2.0 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt(1.0 - a))
    }

    fun missionCenter(missionPath: List<LatLon>): LatLon {
        require(missionPath.isNotEmpty()) { "Terrain mission path is empty" }
        return sphericalCentroid(missionPath)
    }

    /** Moves from [origin] by local north/east offsets, accurately enough for terrain grids. */
    fun offset(origin: LatLon, northMeters: Double, eastMeters: Double): LatLon {
        val distance = sqrt(northMeters * northMeters + eastMeters * eastMeters)
        if (distance == 0.0) return origin
        val bearing = atan2(eastMeters, northMeters)
        val angular = distance / EARTH_RADIUS_METERS
        val lat1 = origin.lat.toRadians()
        val lon1 = origin.lon.toRadians()
        val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2),
        )
        return LatLon(lat2.toDegrees(), normalizeLongitude(lon2.toDegrees()))
    }

    private fun densify(path: List<LatLon>, spacingMeters: Double): List<LatLon> {
        if (path.size < 2) return path
        return buildList {
            add(path.first())
            path.zipWithNext().forEach { (start, end) ->
                val distance = distanceMeters(start, end)
                val segments = ceil(distance / spacingMeters).toInt().coerceAtLeast(1)
                for (index in 1..segments) {
                    add(interpolateGreatCircle(start, end, index.toDouble() / segments))
                }
            }
        }
    }

    private fun sphericalCentroid(points: List<LatLon>): LatLon {
        var x = 0.0
        var y = 0.0
        var z = 0.0
        points.forEach { point ->
            val lat = point.lat.toRadians()
            val lon = point.lon.toRadians()
            x += cos(lat) * cos(lon)
            y += cos(lat) * sin(lon)
            z += sin(lat)
        }
        val lon = atan2(y, x)
        val horizontal = sqrt(x * x + y * y)
        return LatLon(atan2(z, horizontal).toDegrees(), normalizeLongitude(lon.toDegrees()))
    }

    private fun interpolateGreatCircle(start: LatLon, end: LatLon, fraction: Double): LatLon {
        val distance = distanceMeters(start, end)
        if (distance < 0.001) return end
        val angular = distance / EARTH_RADIUS_METERS
        val a = sin((1.0 - fraction) * angular) / sin(angular)
        val b = sin(fraction * angular) / sin(angular)
        val lat1 = start.lat.toRadians()
        val lon1 = start.lon.toRadians()
        val lat2 = end.lat.toRadians()
        val lon2 = end.lon.toRadians()
        val x = a * cos(lat1) * cos(lon1) + b * cos(lat2) * cos(lon2)
        val y = a * cos(lat1) * sin(lon1) + b * cos(lat2) * sin(lon2)
        val z = a * sin(lat1) + b * sin(lat2)
        return LatLon(atan2(z, sqrt(x * x + y * y)).toDegrees(), normalizeLongitude(atan2(y, x).toDegrees()))
    }

    private fun tilesIntersectingCircle(center: LatLon, radiusMeters: Double): Set<SrtmTileId> {
        val north = offset(center, radiusMeters, 0.0).lat
        val south = offset(center, -radiusMeters, 0.0).lat
        val east = offset(center, 0.0, radiusMeters).lon
        val west = offset(center, 0.0, -radiusMeters).lon
        require(west <= east) { "Terrain coverage across the antimeridian is not supported" }
        return buildSet {
            for (latitude in floor(south).toInt()..floor(north).toInt()) {
                for (longitude in floor(west).toInt()..floor(east).toInt()) {
                    add(SrtmTileId(latitude, longitude))
                }
            }
        }
    }

    private fun Double.toRadians(): Double = this * PI / 180.0
    private fun Double.toDegrees(): Double = this * 180.0 / PI
    private fun normalizeLongitude(value: Double): Double = ((value + 540.0) % 360.0) - 180.0

    private const val MAX_REQUEST_BLOCK_EXTENT_METERS = 32.0 * GRID_SPACING_METERS
}
