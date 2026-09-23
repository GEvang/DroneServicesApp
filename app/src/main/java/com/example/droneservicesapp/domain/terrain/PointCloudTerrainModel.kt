package com.example.droneservicesapp.domain.terrain

import com.example.droneservicesapp.data.pointcloud.PointCloudCoordinateFrame
import com.example.droneservicesapp.data.pointcloud.PointCloudData
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.SurveyGridParams
import kotlinx.coroutines.ensureActive
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.coroutines.coroutineContext

data class TerrainWaypoint(
    val latLon: LatLon,
    val displayAltitudeMeters: Double,
    val missionAltitudeMeters: Double
)

enum class TerrainPathFailure {
    NONE,
    VALIDATION_PENDING,
    NO_GEOREFERENCE,
    PATH_TOO_SHORT,
    HOME_UNCOVERED,
    PATH_UNCOVERED,
}

data class TerrainPathResult(
    val waypoints: List<TerrainWaypoint> = emptyList(),
    val failure: TerrainPathFailure = TerrainPathFailure.NONE,
    val firstUncoveredPoint: LatLon? = null,
    val homeTerrainZMeters: Double? = null,
) {
    val isValid: Boolean
        get() = failure == TerrainPathFailure.NONE && waypoints.size >= 2
}

data class TerrainServiceCorridor(
    val pathDistanceMeters: Double,
    val outboundWaypoints: List<TerrainWaypoint>,
    val returnWaypoints: List<TerrainWaypoint>,
)

data class TerrainGridSummary(
    val cellSizeMeters: Double,
    val cellCount: Int,
    val pointCount: Int,
    val minHeightMeters: Double,
    val maxHeightMeters: Double,
    val fallbackHeightMeters: Double,
    val isGeoreferenced: Boolean
)

class PointCloudTerrainModel(
    private val pointCloud: PointCloudData,
    private val cellSizeMeters: Double = DEFAULT_CELL_SIZE_METERS
) {
    val coordinateFrame: PointCloudCoordinateFrame? = pointCloud.coordinateFrame
    val isGeoreferenced: Boolean get() = coordinateFrame != null
    val pointCount: Int get() = pointCloud.displayedPointCount

    private val terrainGrid: LongFloatMaxMap by lazy { buildTerrainGrid() }
    private val fallbackTerrainZ: Double by lazy { medianZ(pointCloud.positions) }

    fun terrainGridSummary(): TerrainGridSummary {
        val grid = terrainGrid
        val minHeight = grid.minValueOrNull()?.toDouble() ?: fallbackTerrainZ
        val maxHeight = grid.maxValueOrNull()?.toDouble() ?: fallbackTerrainZ
        return TerrainGridSummary(
            cellSizeMeters = cellSizeMeters,
            cellCount = grid.size,
            pointCount = pointCloud.displayedPointCount,
            minHeightMeters = minHeight,
            maxHeightMeters = maxHeight,
            fallbackHeightMeters = fallbackTerrainZ,
            isGeoreferenced = isGeoreferenced
        )
    }

    suspend fun buildTerrainSurveyPath(
        polygon: List<LatLon>,
        params: SurveyGridParams,
        homeTerrainZ: Double = 0.0
    ): List<TerrainWaypoint> {
        val frame = coordinateFrame ?: return emptyList()
        if (polygon.size < 3 || pointCloud.displayedPointCount == 0) return emptyList()

        val localPolygon = polygon.map { vertex ->
            val (x, y) = frame.latLonToLocal(vertex.lat, vertex.lon)
            LocalPoint(x, y)
        }
        val clippedPoints = clippedPoints(localPolygon)
        if (clippedPoints.isEmpty()) return emptyList()

        val overlapFraction = params.overlapPercent.coerceIn(0, 95) / 100.0
        val effectiveSpacing = (params.stripSpacingMeters * (1.0 - overlapFraction))
            .coerceAtLeast(MIN_STRIP_SPACING_METERS)
        val segmentMeters = params.terrainSegmentMeters.coerceAtLeast(MIN_SEGMENT_METERS)
        val canopyRadius = params.canopySmoothingMeters.coerceAtLeast(0).toDouble()
        val altitudeAgl = params.heightAboveTerrainMeters.toDouble()
        val angleRadians = Math.toRadians(params.gridAngleDegrees.toDouble())
        val cosA = cos(angleRadians)
        val sinA = sin(angleRadians)

        val rotated = clippedPoints.map { point ->
            LocalPoint(
                x = cosA * point.x + sinA * point.y,
                y = -sinA * point.x + cosA * point.y
            )
        }
        val xMin = rotated.minOf { it.x }
        val xMax = rotated.maxOf { it.x }
        val yMin = rotated.minOf { it.y }
        val yMax = rotated.maxOf { it.y }

        val waypoints = mutableListOf<TerrainWaypoint>()
        var stripIndex = 0
        var stripY = yMin
        while (stripY <= yMax + effectiveSpacing * 0.5) {
            coroutineContext.ensureActive()
            val forward = stripIndex % 2 == 0
            val xStart = if (forward) xMin else xMax
            val xEnd = if (forward) xMax else xMin
            val segmentCount = max(2, ceil(kotlin.math.abs(xEnd - xStart) / segmentMeters).toInt())

            for (segmentIndex in 0 until segmentCount) {
                if (segmentIndex % CANCELLATION_CHECK_INTERVAL == 0) {
                    coroutineContext.ensureActive()
                }
                val t = if (segmentCount == 1) 0.0 else segmentIndex.toDouble() / (segmentCount - 1)
                val rotatedX = xStart + (xEnd - xStart) * t
                val localX = cosA * rotatedX - sinA * stripY
                val localY = sinA * rotatedX + cosA * stripY
                val localPoint = LocalPoint(localX, localY)
                if (!pointInPolygon(localPoint, localPolygon)) continue

                val terrainZ = terrainHeightAt(
                    xMeters = localX,
                    yMeters = localY,
                    searchRadiusMeters = DEFAULT_SEARCH_RADIUS_METERS,
                    canopyRadiusMeters = canopyRadius,
                    fallback = fallbackTerrainZ
                )
                val displayZ = terrainZ + altitudeAgl
                val missionZ = terrainZ - homeTerrainZ + altitudeAgl
                val (lat, lon) = frame.localToLatLon(localX, localY)
                waypoints += TerrainWaypoint(
                    latLon = LatLon(lat, lon),
                    displayAltitudeMeters = displayZ,
                    missionAltitudeMeters = missionZ
                )
            }

            stripIndex++
            stripY += effectiveSpacing
        }

        return waypoints
    }

    suspend fun hasPointsInside(polygon: List<LatLon>): Boolean {
        val frame = coordinateFrame ?: return false
        if (polygon.size < 3 || pointCloud.displayedPointCount == 0) return false
        val localPolygon = polygon.map { vertex ->
            val (x, y) = frame.latLonToLocal(vertex.lat, vertex.lon)
            LocalPoint(x, y)
        }
        return hasPointInside(localPolygon)
    }

    /**
     * Classifies the accepted area, not only the generated flight lines. A cloud that overlaps
     * the polygon but leaves any boundary or interior sample unsupported is partial coverage.
     */
    suspend fun classifyAreaCoverage(polygon: List<LatLon>): PointCloudCoverage {
        val frame = coordinateFrame ?: return PointCloudCoverage.NONE
        if (polygon.size < 3 || pointCloud.displayedPointCount == 0) return PointCloudCoverage.NONE
        val localPolygon = polygon.map { vertex ->
            val (x, y) = frame.latLonToLocal(vertex.lat, vertex.lon)
            LocalPoint(x, y)
        }
        if (!hasPointInside(localPolygon)) return PointCloudCoverage.NONE

        val boundary = samplePath(localPolygon + localPolygon.first(), AREA_COVERAGE_SAMPLE_METERS)
        boundary.forEachIndexed { index, point ->
            if (index % CANCELLATION_CHECK_INTERVAL == 0) coroutineContext.ensureActive()
            if (!hasLocalCoverage(point.x, point.y)) return PointCloudCoverage.PARTIAL
        }

        val minX = localPolygon.minOf { it.x }
        val maxX = localPolygon.maxOf { it.x }
        val minY = localPolygon.minOf { it.y }
        val maxY = localPolygon.maxOf { it.y }
        var checkedSamples = 0
        var y = minY
        while (y <= maxY) {
            var x = minX
            while (x <= maxX) {
                if (checkedSamples++ % CANCELLATION_CHECK_INTERVAL == 0) {
                    coroutineContext.ensureActive()
                }
                val sample = LocalPoint(x, y)
                if (pointInPolygon(sample, localPolygon) && !hasLocalCoverage(x, y)) {
                    return PointCloudCoverage.PARTIAL
                }
                x += AREA_COVERAGE_SAMPLE_METERS
            }
            y += AREA_COVERAGE_SAMPLE_METERS
        }
        return PointCloudCoverage.COMPLETE
    }

    /** Samples an already obstacle-aware route against the point cloud at a fixed segment length. */
    suspend fun buildTerrainPath(
        path: List<LatLon>,
        heightAboveTerrainMeters: Double,
        segmentMeters: Double,
        canopySmoothingMeters: Double,
        homeTerrainZ: Double = 0.0
    ): List<TerrainWaypoint> {
        val frame = coordinateFrame ?: return emptyList()
        if (path.size < 2) return emptyList()
        val localPath = path.map { point ->
            val (x, y) = frame.latLonToLocal(point.lat, point.lon)
            LocalPoint(x, y)
        }
        val spacing = segmentMeters.coerceAtLeast(MIN_SEGMENT_METERS)
        val sampled = ArrayList<LocalPoint>()
        localPath.zipWithNext().forEach { (from, to) ->
            coroutineContext.ensureActive()
            val segmentCount = ceil(hypot(to.x - from.x, to.y - from.y) / spacing)
                .toInt()
                .coerceAtLeast(1)
            for (segmentIndex in 0 until segmentCount) {
                val t = segmentIndex.toDouble() / segmentCount
                sampled += LocalPoint(
                    x = from.x + (to.x - from.x) * t,
                    y = from.y + (to.y - from.y) * t
                )
            }
        }
        sampled += localPath.last()

        return sampled.mapIndexed { index, point ->
            if (index % CANCELLATION_CHECK_INTERVAL == 0) coroutineContext.ensureActive()
            val terrainZ = terrainHeightAt(
                xMeters = point.x,
                yMeters = point.y,
                canopyRadiusMeters = canopySmoothingMeters.coerceAtLeast(0.0),
                fallback = fallbackTerrainZ
            )
            val (lat, lon) = frame.localToLatLon(point.x, point.y)
            TerrainWaypoint(
                latLon = LatLon(lat, lon),
                displayAltitudeMeters = terrainZ + heightAboveTerrainMeters,
                missionAltitudeMeters = terrainZ - homeTerrainZ + heightAboveTerrainMeters
            )
        }
    }

    /**
     * Builds an upload-safe terrain path without substituting fallback heights for missing data.
     * Every metre of the path must have local point-cloud support and all mission altitudes share
     * the point-cloud surface at home as their relative-altitude zero.
     */
    suspend fun buildValidatedTerrainPath(
        path: List<LatLon>,
        home: LatLon,
        heightAboveTerrainMeters: Double,
        segmentMeters: Double,
        canopySmoothingMeters: Double,
        requireHomeCoverage: Boolean = true,
        homeAltitudeAmslMeters: Double? = null,
    ): TerrainPathResult {
        val frame = coordinateFrame ?: return TerrainPathResult(
            failure = TerrainPathFailure.NO_GEOREFERENCE
        )
        if (path.size < 2) return TerrainPathResult(failure = TerrainPathFailure.PATH_TOO_SHORT)

        val (homeX, homeY) = frame.latLonToLocal(home.lat, home.lon)
        val coveredHomeTerrainZ = nearestTerrainWithinRadius(
            xMeters = homeX,
            yMeters = homeY,
            radiusMeters = HOME_REFERENCE_RADIUS_METERS,
        )

        val localPath = path.map { point ->
            val (x, y) = frame.latLonToLocal(point.lat, point.lon)
            LocalPoint(x, y)
        }
        val missionSpacing = segmentMeters.coerceAtLeast(MIN_SEGMENT_METERS)
        val validationSpacing = missionSpacing.coerceAtMost(MAX_VALIDATED_SEGMENT_METERS)
        val validationSamples = samplePath(localPath, validationSpacing)
        val canopyRadius = canopySmoothingMeters.coerceAtLeast(0.0)

        val homeTerrainZ = coveredHomeTerrainZ
            ?: homeAltitudeAmslMeters
                ?.takeIf { it.isFinite() }
                ?.minus(frame.originAltMeters)
            ?: if (!requireHomeCoverage) {
                val first = validationSamples.first()
                highestTerrainWithinRadius(
                    xMeters = first.x,
                    yMeters = first.y,
                    radiusMeters = COVERAGE_RADIUS_METERS,
                )
            } else {
                null
            }
            ?: return TerrainPathResult(
                failure = TerrainPathFailure.HOME_UNCOVERED,
                firstUncoveredPoint = home,
            )

        validationSamples.forEachIndexed { index, point ->
            if (index % CANCELLATION_CHECK_INTERVAL == 0) coroutineContext.ensureActive()
            if (!hasLocalCoverage(point.x, point.y)) {
                val (lat, lon) = frame.localToLatLon(point.x, point.y)
                return TerrainPathResult(
                    failure = TerrainPathFailure.PATH_UNCOVERED,
                    firstUncoveredPoint = LatLon(lat, lon),
                    homeTerrainZMeters = homeTerrainZ,
                )
            }
        }

        val missionSamples = samplePath(localPath, missionSpacing)
        val waypoints = ArrayList<TerrainWaypoint>(missionSamples.size)
        val profileRadius = max(max(DEFAULT_SEARCH_RADIUS_METERS, canopyRadius), missionSpacing)
        missionSamples.forEachIndexed { index, point ->
            if (index % CANCELLATION_CHECK_INTERVAL == 0) coroutineContext.ensureActive()
            val (lat, lon) = frame.localToLatLon(point.x, point.y)
            val latLon = LatLon(lat, lon)
            val terrainZ = highestTerrainWithinRadius(
                xMeters = point.x,
                yMeters = point.y,
                // Include at least one full mission segment so linear interpolation between
                // adjacent uploaded waypoints cannot cut below an intervening sampled peak.
                radiusMeters = profileRadius,
            ) ?: return TerrainPathResult(
                failure = TerrainPathFailure.PATH_UNCOVERED,
                firstUncoveredPoint = latLon,
                homeTerrainZMeters = homeTerrainZ,
            )
            waypoints += TerrainWaypoint(
                latLon = latLon,
                displayAltitudeMeters = terrainZ + heightAboveTerrainMeters,
                missionAltitudeMeters = terrainZ - homeTerrainZ + heightAboveTerrainMeters,
            )
        }

        return TerrainPathResult(
            waypoints = waypoints,
            homeTerrainZMeters = homeTerrainZ,
        )
    }

    fun terrainHeightAt(
        xMeters: Double,
        yMeters: Double,
        searchRadiusMeters: Double = DEFAULT_SEARCH_RADIUS_METERS,
        canopyRadiusMeters: Double = 0.0,
        fallback: Double = fallbackTerrainZ
    ): Double {
        return highestTerrainWithinRadius(
            xMeters = xMeters,
            yMeters = yMeters,
            radiusMeters = max(searchRadiusMeters, canopyRadiusMeters),
        ) ?: fallback
    }

    private fun hasLocalCoverage(xMeters: Double, yMeters: Double): Boolean =
        highestTerrainWithinRadius(xMeters, yMeters, COVERAGE_RADIUS_METERS) != null

    private fun highestTerrainWithinRadius(
        xMeters: Double,
        yMeters: Double,
        radiusMeters: Double,
    ): Double? {
        if (terrainGrid.isEmpty()) return null
        val radiusCells = ceil(radiusMeters.coerceAtLeast(0.0) / cellSizeMeters)
            .toInt()
            .coerceAtLeast(0)
        val radiusCellsSquared = radiusCells * radiusCells
        val centerX = floor(xMeters / cellSizeMeters).toInt()
        val centerY = floor(yMeters / cellSizeMeters).toInt()
        var best: Float? = null

        for (dx in -radiusCells..radiusCells) {
            for (dy in -radiusCells..radiusCells) {
                if (dx * dx + dy * dy > radiusCellsSquared) continue
                val z = terrainGrid.getOrNaN(packCellKey(centerX + dx, centerY + dy))
                if (!z.isNaN() && (best == null || z > best)) best = z
            }
        }

        return best?.toDouble()
    }

    private fun nearestTerrainWithinRadius(
        xMeters: Double,
        yMeters: Double,
        radiusMeters: Double,
    ): Double? {
        if (terrainGrid.isEmpty()) return null
        val radiusCells = ceil(radiusMeters.coerceAtLeast(0.0) / cellSizeMeters)
            .toInt()
            .coerceAtLeast(0)
        val radiusCellsSquared = radiusCells * radiusCells
        val centerX = floor(xMeters / cellSizeMeters).toInt()
        val centerY = floor(yMeters / cellSizeMeters).toInt()
        var nearestDistanceSquared = Int.MAX_VALUE
        var nearest: Float? = null
        for (dx in -radiusCells..radiusCells) {
            for (dy in -radiusCells..radiusCells) {
                val distanceSquared = dx * dx + dy * dy
                if (distanceSquared > radiusCellsSquared || distanceSquared > nearestDistanceSquared) continue
                val z = terrainGrid.getOrNaN(packCellKey(centerX + dx, centerY + dy))
                if (!z.isNaN() &&
                    (distanceSquared < nearestDistanceSquared || nearest == null || z > nearest)
                ) {
                    nearestDistanceSquared = distanceSquared
                    nearest = z
                }
            }
        }
        return nearest?.toDouble()
    }

    private fun samplePath(path: List<LocalPoint>, spacingMeters: Double): List<LocalPoint> {
        val sampled = ArrayList<LocalPoint>()
        path.zipWithNext().forEach { (from, to) ->
            val segmentCount = ceil(hypot(to.x - from.x, to.y - from.y) / spacingMeters)
                .toInt()
                .coerceAtLeast(1)
            for (segmentIndex in 0 until segmentCount) {
                val t = segmentIndex.toDouble() / segmentCount
                sampled += LocalPoint(
                    x = from.x + (to.x - from.x) * t,
                    y = from.y + (to.y - from.y) * t,
                )
            }
        }
        sampled += path.last()
        return sampled
    }

    private fun buildTerrainGrid(): LongFloatMaxMap {
        val positions = pointCloud.positions
        val grid = LongFloatMaxMap(pointCloud.displayedPointCount)
        var index = 0
        while (index + 2 < positions.size) {
            val cellX = floor(positions[index] / cellSizeMeters).toInt()
            val cellY = floor(positions[index + 1] / cellSizeMeters).toInt()
            val z = positions[index + 2]
            grid.putMax(packCellKey(cellX, cellY), z)
            index += VALUES_PER_POINT
        }
        return grid
    }

    private suspend fun clippedPoints(localPolygon: List<LocalPoint>): List<LocalPoint> {
        val positions = pointCloud.positions
        val clipped = ArrayList<LocalPoint>(pointCloud.displayedPointCount)
        val minX = localPolygon.minOf { it.x }
        val maxX = localPolygon.maxOf { it.x }
        val minY = localPolygon.minOf { it.y }
        val maxY = localPolygon.maxOf { it.y }
        var index = 0
        while (index + 2 < positions.size) {
            if (index % (VALUES_PER_POINT * CANCELLATION_CHECK_INTERVAL) == 0) {
                coroutineContext.ensureActive()
            }
            val x = positions[index].toDouble()
            val y = positions[index + 1].toDouble()
            if (x >= minX && x <= maxX && y >= minY && y <= maxY) {
                val point = LocalPoint(x, y)
                if (pointInPolygon(point, localPolygon)) clipped += point
            }
            index += VALUES_PER_POINT
        }
        return clipped
    }

    private suspend fun hasPointInside(localPolygon: List<LocalPoint>): Boolean {
        val positions = pointCloud.positions
        val minX = localPolygon.minOf { it.x }
        val maxX = localPolygon.maxOf { it.x }
        val minY = localPolygon.minOf { it.y }
        val maxY = localPolygon.maxOf { it.y }
        var index = 0
        while (index + 2 < positions.size) {
            if (index % (VALUES_PER_POINT * CANCELLATION_CHECK_INTERVAL) == 0) {
                coroutineContext.ensureActive()
            }
            val x = positions[index].toDouble()
            val y = positions[index + 1].toDouble()
            if (x in minX..maxX && y in minY..maxY && pointInPolygon(LocalPoint(x, y), localPolygon)) {
                return true
            }
            index += VALUES_PER_POINT
        }
        return false
    }

    private fun medianZ(positions: FloatArray): Double {
        if (positions.size < VALUES_PER_POINT) return 0.0
        val zValues = FloatArray(positions.size / VALUES_PER_POINT)
        var zIndex = 0
        var positionIndex = 2
        while (positionIndex < positions.size) {
            zValues[zIndex++] = positions[positionIndex]
            positionIndex += VALUES_PER_POINT
        }
        zValues.sort()
        val mid = zValues.size / 2
        return if (zValues.size % 2 == 0) {
            (zValues[mid - 1] + zValues[mid]) / 2.0
        } else {
            zValues[mid].toDouble()
        }
    }

    private fun pointInPolygon(point: LocalPoint, polygon: List<LocalPoint>): Boolean {
        var inside = false
        var previous = polygon.last()
        polygon.forEach { current ->
            val crosses = (current.y > point.y) != (previous.y > point.y)
            if (crosses) {
                val intersectionX = (previous.x - current.x) *
                    (point.y - current.y) /
                    ((previous.y - current.y).takeIf { kotlin.math.abs(it) > 1e-9 } ?: 1e-9) +
                    current.x
                if (point.x < intersectionX) inside = !inside
            }
            previous = current
        }
        return inside
    }

    private data class LocalPoint(val x: Double, val y: Double)

    private fun packCellKey(x: Int, y: Int): Long {
        return (x.toLong() shl Int.SIZE_BITS) xor (y.toLong() and 0xFFFF_FFFFL)
    }

    private class LongFloatMaxMap(expectedSize: Int) {
        private var keys: LongArray
        private var values: FloatArray
        private var occupied: BooleanArray
        private var mask: Int
        private var resizeThreshold: Int
        var size: Int = 0
            private set

        init {
            var capacity = MIN_GRID_CAPACITY
            val requiredCapacity = (expectedSize / GRID_LOAD_FACTOR).toInt().coerceAtLeast(MIN_GRID_CAPACITY)
            while (capacity < requiredCapacity && capacity < MAX_GRID_CAPACITY) capacity = capacity shl 1
            keys = LongArray(capacity)
            values = FloatArray(capacity)
            occupied = BooleanArray(capacity)
            mask = capacity - 1
            resizeThreshold = (capacity * GRID_LOAD_FACTOR).toInt()
        }

        fun isEmpty(): Boolean = size == 0

        fun putMax(key: Long, value: Float) {
            if (size >= resizeThreshold) resize()
            var index = indexFor(key)
            while (occupied[index]) {
                if (keys[index] == key) {
                    if (value > values[index]) values[index] = value
                    return
                }
                index = (index + 1) and mask
            }
            occupied[index] = true
            keys[index] = key
            values[index] = value
            size++
        }

        fun getOrNaN(key: Long): Float {
            var index = indexFor(key)
            while (occupied[index]) {
                if (keys[index] == key) return values[index]
                index = (index + 1) and mask
            }
            return Float.NaN
        }

        fun minValueOrNull(): Float? {
            if (size == 0) return null
            var result = Float.POSITIVE_INFINITY
            for (index in values.indices) {
                if (occupied[index] && values[index] < result) result = values[index]
            }
            return result
        }

        fun maxValueOrNull(): Float? {
            if (size == 0) return null
            var result = Float.NEGATIVE_INFINITY
            for (index in values.indices) {
                if (occupied[index] && values[index] > result) result = values[index]
            }
            return result
        }

        private fun resize() {
            require(keys.size < MAX_GRID_CAPACITY) { "Terrain grid is too large." }
            val previousKeys = keys
            val previousValues = values
            val previousOccupied = occupied
            val newCapacity = (keys.size shl 1).coerceAtMost(MAX_GRID_CAPACITY)
            keys = LongArray(newCapacity)
            values = FloatArray(newCapacity)
            occupied = BooleanArray(newCapacity)
            mask = newCapacity - 1
            resizeThreshold = (newCapacity * GRID_LOAD_FACTOR).toInt()
            size = 0
            for (index in previousKeys.indices) {
                if (previousOccupied[index]) putMax(previousKeys[index], previousValues[index])
            }
        }

        private fun indexFor(key: Long): Int {
            var mixed = key
            mixed = mixed xor (mixed ushr 33)
            mixed *= -49064778989728563L
            mixed = mixed xor (mixed ushr 33)
            return mixed.toInt() and mask
        }
    }

    companion object {
        private const val VALUES_PER_POINT = 3
        private const val DEFAULT_CELL_SIZE_METERS = 1.0
        private const val DEFAULT_SEARCH_RADIUS_METERS = 2.0
        private const val COVERAGE_RADIUS_METERS = 2.0
        private const val AREA_COVERAGE_SAMPLE_METERS = 2.0
        private const val HOME_REFERENCE_RADIUS_METERS = 2.0
        private const val MAX_VALIDATED_SEGMENT_METERS = 1.0
        private const val MIN_SEGMENT_METERS = 0.5
        private const val MIN_STRIP_SPACING_METERS = 1.0
        private const val CANCELLATION_CHECK_INTERVAL = 4096
        private const val MIN_GRID_CAPACITY = 16
        private const val MAX_GRID_CAPACITY = 1 shl 27
        private const val GRID_LOAD_FACTOR = 0.7
    }
}
