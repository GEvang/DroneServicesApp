package com.example.droneservicesapp.domain.terrain

import com.example.droneservicesapp.data.pointcloud.PointCloudCoordinateFrame
import com.example.droneservicesapp.data.pointcloud.PointCloudData
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.SurveyGridParams
import kotlinx.coroutines.ensureActive
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.coroutines.coroutineContext

data class TerrainWaypoint(
    val latLon: LatLon,
    val displayAltitudeMeters: Double,
    val missionAltitudeMeters: Double
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

    private val terrainGrid: Map<CellKey, Float> by lazy { buildTerrainGrid() }
    private val fallbackTerrainZ: Double by lazy { medianZ(pointCloud.positions) }

    fun terrainGridSummary(): TerrainGridSummary {
        val grid = terrainGrid
        val minHeight = grid.values.minOrNull()?.toDouble() ?: fallbackTerrainZ
        val maxHeight = grid.values.maxOrNull()?.toDouble() ?: fallbackTerrainZ
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

    fun terrainHeightAt(
        xMeters: Double,
        yMeters: Double,
        searchRadiusMeters: Double = DEFAULT_SEARCH_RADIUS_METERS,
        canopyRadiusMeters: Double = 0.0,
        fallback: Double = fallbackTerrainZ
    ): Double {
        if (terrainGrid.isEmpty()) return fallback
        val radiusCells = ceil((searchRadiusMeters + canopyRadiusMeters) / cellSizeMeters)
            .toInt()
            .coerceAtLeast(1)
        val centerX = floor(xMeters / cellSizeMeters).toInt()
        val centerY = floor(yMeters / cellSizeMeters).toInt()
        var best: Float? = null

        for (dx in -radiusCells..radiusCells) {
            for (dy in -radiusCells..radiusCells) {
                val z = terrainGrid[CellKey(centerX + dx, centerY + dy)] ?: continue
                if (best == null || z > best) best = z
            }
        }

        return best?.toDouble() ?: fallback
    }

    private fun buildTerrainGrid(): Map<CellKey, Float> {
        val positions = pointCloud.positions
        val grid = LinkedHashMap<CellKey, Float>()
        var index = 0
        while (index + 2 < positions.size) {
            val x = positions[index].toDouble()
            val y = positions[index + 1].toDouble()
            val z = positions[index + 2]
            val key = CellKey(
                x = floor(x / cellSizeMeters).toInt(),
                y = floor(y / cellSizeMeters).toInt()
            )
            val current = grid[key]
            if (current == null || z > current) grid[key] = z
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

    private data class CellKey(val x: Int, val y: Int) {
        override fun hashCode(): Int = 31 * x + y
    }

    companion object {
        private const val VALUES_PER_POINT = 3
        private const val DEFAULT_CELL_SIZE_METERS = 1.0
        private const val DEFAULT_SEARCH_RADIUS_METERS = 2.0
        private const val MIN_SEGMENT_METERS = 0.5
        private const val MIN_STRIP_SPACING_METERS = 1.0
        private const val CANCELLATION_CHECK_INTERVAL = 4096
    }
}
