package com.example.droneservicesapp.data.terrain

import com.example.droneservicesapp.data.pointcloud.PointCloudBounds
import com.example.droneservicesapp.data.pointcloud.PointCloudCoordinateFrame
import com.example.droneservicesapp.data.pointcloud.PointCloudData
import com.example.droneservicesapp.domain.terrain.TerrainCoveragePlanner
import kotlin.math.ceil

data class TerrainPreview(
    val pointCloud: PointCloudData,
    val fileNames: List<String>,
    val gridSpacingMeters: Int,
)

/** Builds the 3D representation of the same 30 m samples served through TERRAIN_DATA. */
object TerrainPreviewBuilder {
    fun build(
        source: TerrainElevationSource,
        spacingMeters: Int = TerrainCoveragePlanner.GRID_SPACING_METERS.toInt(),
    ): TerrainPreview {
        require(spacingMeters > 0) { "Terrain preview spacing must be positive" }
        val plan = source.plan
        val radius = plan.radiusMeters
        val steps = ceil(radius / spacingMeters).toInt()
        val samples = ArrayList<Sample>((steps * 2 + 1) * (steps * 2 + 1))
        for (northStep in -steps..steps) {
            val north = northStep * spacingMeters.toDouble()
            for (eastStep in -steps..steps) {
                val east = eastStep * spacingMeters.toDouble()
                if (north * north + east * east > radius * radius) continue
                val point = TerrainCoveragePlanner.offset(plan.center, north, east)
                val elevation = source.elevationMeters(point.lat, point.lon) ?: continue
                samples += Sample(east.toFloat(), north.toFloat(), elevation.toFloat())
            }
        }
        require(samples.isNotEmpty()) { "Terrain source contains no preview samples" }

        val minX = samples.minOf(Sample::x)
        val maxX = samples.maxOf(Sample::x)
        val minY = samples.minOf(Sample::y)
        val maxY = samples.maxOf(Sample::y)
        val minZ = samples.minOf(Sample::z)
        val maxZ = samples.maxOf(Sample::z)
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        val centerZ = (minZ + maxZ) / 2f
        val positions = FloatArray(samples.size * VALUES_PER_POINT)
        val colors = FloatArray(samples.size * VALUES_PER_POINT)
        samples.forEachIndexed { index, sample ->
            val offset = index * VALUES_PER_POINT
            positions[offset] = sample.x - centerX
            positions[offset + 1] = sample.y - centerY
            positions[offset + 2] = sample.z - centerZ
            colors[offset] = DEFAULT_COLOR[0]
            colors[offset + 1] = DEFAULT_COLOR[1]
            colors[offset + 2] = DEFAULT_COLOR[2]
        }
        val frameCenter = TerrainCoveragePlanner.offset(plan.center, centerY.toDouble(), centerX.toDouble())
        return TerrainPreview(
            pointCloud = PointCloudData(
                positions = positions,
                colors = colors,
                totalPointCount = samples.size,
                displayedPointCount = samples.size,
                bounds = PointCloudBounds(minX, maxX, minY, maxY, minZ, maxZ),
                hasRgb = false,
                coordinateFrame = PointCloudCoordinateFrame(
                    originLat = frameCenter.lat,
                    originLon = frameCenter.lon,
                    originAltMeters = centerZ.toDouble(),
                ),
            ),
            fileNames = plan.sourceTiles.map { "${it.baseName}.DAT" }.sorted(),
            gridSpacingMeters = spacingMeters,
        )
    }

    private data class Sample(val x: Float, val y: Float, val z: Float)

    private const val VALUES_PER_POINT = 3
    private val DEFAULT_COLOR = floatArrayOf(0.34f, 0.62f, 0.38f)
}
