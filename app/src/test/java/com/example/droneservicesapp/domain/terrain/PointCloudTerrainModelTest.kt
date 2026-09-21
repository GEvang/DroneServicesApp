package com.example.droneservicesapp.domain.terrain

import com.example.droneservicesapp.data.pointcloud.PointCloudBounds
import com.example.droneservicesapp.data.pointcloud.PointCloudCoordinateFrame
import com.example.droneservicesapp.data.pointcloud.PointCloudData
import com.example.droneservicesapp.domain.model.LatLon
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointCloudTerrainModelTest {

    private val frame = PointCloudCoordinateFrame(originLat = 35.0, originLon = 24.0)
    private val model = PointCloudTerrainModel(
        PointCloudData(
            positions = floatArrayOf(0f, 0f, 10f, 5f, 0f, 12f),
            colors = floatArrayOf(),
            totalPointCount = 2,
            displayedPointCount = 2,
            bounds = PointCloudBounds(0f, 5f, 0f, 0f, 10f, 12f),
            hasRgb = false,
            coordinateFrame = frame
        )
    )

    @Test
    fun detectsWhetherPointCloudDataExistsInsideMissionArea() = runBlocking {
        val coveringPolygon = localPolygon(-2.0, -2.0, 7.0, 2.0)
        val distantPolygon = localPolygon(100.0, 100.0, 110.0, 110.0)

        assertTrue(model.hasPointsInside(coveringPolygon))
        assertFalse(model.hasPointsInside(distantPolygon))
    }

    @Test
    fun samplesObstacleAwarePathAtTerrainSegmentInterval() = runBlocking {
        val (startLat, startLon) = frame.localToLatLon(0.0, 0.0)
        val (endLat, endLon) = frame.localToLatLon(5.0, 0.0)

        val terrainPath = model.buildTerrainPath(
            path = listOf(LatLon(startLat, startLon), LatLon(endLat, endLon)),
            heightAboveTerrainMeters = 3.0,
            segmentMeters = 2.0,
            canopySmoothingMeters = 0.0
        )

        assertEquals(4, terrainPath.size)
        assertEquals(13.0, terrainPath.first().missionAltitudeMeters, 0.001)
        assertEquals(endLat, terrainPath.last().latLon.lat, 0.000001)
        assertEquals(endLon, terrainPath.last().latLon.lon, 0.000001)
    }

    @Test
    fun compactGridKeepsHighestPointInEachCell() {
        val gridModel = PointCloudTerrainModel(
            PointCloudData(
                positions = floatArrayOf(
                    0.1f, 0.1f, 5f,
                    0.9f, 0.8f, 8f,
                    -0.1f, -0.1f, 12f
                ),
                colors = floatArrayOf(),
                totalPointCount = 3,
                displayedPointCount = 3,
                bounds = PointCloudBounds(-0.1f, 0.9f, -0.1f, 0.8f, 5f, 12f),
                hasRgb = false,
                coordinateFrame = frame
            )
        )

        val summary = gridModel.terrainGridSummary()

        assertEquals(2, summary.cellCount)
        assertEquals(8.0, summary.minHeightMeters, 0.001)
        assertEquals(12.0, summary.maxHeightMeters, 0.001)
    }

    @Test
    fun validatedPathUsesHomeSurfaceAsRelativeAltitudeZero() = runBlocking {
        val positions = mutableListOf<Float>()
        for (x in 0..5) {
            positions += x.toFloat()
            positions += 0f
            positions += (10 + x * 2).toFloat()
        }
        val offsetModel = PointCloudTerrainModel(
            PointCloudData(
                positions = positions.toFloatArray(),
                colors = floatArrayOf(),
                totalPointCount = 6,
                displayedPointCount = 6,
                bounds = PointCloudBounds(0f, 5f, 0f, 0f, 10f, 20f),
                hasRgb = false,
                coordinateFrame = frame,
            )
        )
        val (homeLat, homeLon) = frame.localToLatLon(0.0, 0.0)
        val (endLat, endLon) = frame.localToLatLon(5.0, 0.0)

        val result = offsetModel.buildValidatedTerrainPath(
            path = listOf(LatLon(homeLat, homeLon), LatLon(endLat, endLon)),
            home = LatLon(homeLat, homeLon),
            heightAboveTerrainMeters = 5.0,
            segmentMeters = 2.5,
            canopySmoothingMeters = 0.0,
        )

        assertTrue(result.isValid)
        // The target at home includes the complete next mission segment in its safety envelope:
        // local maximum 16 - home surface 10 + requested clearance 5.
        assertEquals(11.0, result.waypoints.first().missionAltitudeMeters, 0.001)
        assertEquals(15.0, result.waypoints.last().missionAltitudeMeters, 0.001)
    }

    @Test
    fun validatedPathRejectsCoverageHoleBetweenCoveredEndpoints() = runBlocking {
        val sparseModel = PointCloudTerrainModel(
            PointCloudData(
                positions = floatArrayOf(0f, 0f, 10f, 10f, 0f, 10f),
                colors = floatArrayOf(),
                totalPointCount = 2,
                displayedPointCount = 2,
                bounds = PointCloudBounds(0f, 10f, 0f, 0f, 10f, 10f),
                hasRgb = false,
                coordinateFrame = frame,
            )
        )
        val (homeLat, homeLon) = frame.localToLatLon(0.0, 0.0)
        val (endLat, endLon) = frame.localToLatLon(10.0, 0.0)

        val result = sparseModel.buildValidatedTerrainPath(
            path = listOf(LatLon(homeLat, homeLon), LatLon(endLat, endLon)),
            home = LatLon(homeLat, homeLon),
            heightAboveTerrainMeters = 5.0,
            segmentMeters = 2.5,
            canopySmoothingMeters = 0.0,
        )

        assertFalse(result.isValid)
        assertEquals(TerrainPathFailure.PATH_UNCOVERED, result.failure)
        assertTrue(result.firstUncoveredPoint != null)
    }

    @Test
    fun validatedPathRejectsHomeOutsidePointCloud() = runBlocking {
        val (startLat, startLon) = frame.localToLatLon(0.0, 0.0)
        val (endLat, endLon) = frame.localToLatLon(5.0, 0.0)
        val (homeLat, homeLon) = frame.localToLatLon(100.0, 100.0)

        val result = model.buildValidatedTerrainPath(
            path = listOf(LatLon(startLat, startLon), LatLon(endLat, endLon)),
            home = LatLon(homeLat, homeLon),
            heightAboveTerrainMeters = 5.0,
            segmentMeters = 1.0,
            canopySmoothingMeters = 0.0,
        )

        assertFalse(result.isValid)
        assertEquals(TerrainPathFailure.HOME_UNCOVERED, result.failure)
    }

    @Test
    fun sprayingPathCoverageDoesNotRequireHomeInsidePointCloud() = runBlocking {
        val (startLat, startLon) = frame.localToLatLon(0.0, 0.0)
        val (endLat, endLon) = frame.localToLatLon(5.0, 0.0)
        val (homeLat, homeLon) = frame.localToLatLon(100.0, 100.0)

        val result = model.buildValidatedTerrainPath(
            path = listOf(LatLon(startLat, startLon), LatLon(endLat, endLon)),
            home = LatLon(homeLat, homeLon),
            heightAboveTerrainMeters = 5.0,
            segmentMeters = 1.0,
            canopySmoothingMeters = 0.0,
            requireHomeCoverage = false,
        )

        assertTrue(result.isValid)
        assertEquals(TerrainPathFailure.NONE, result.failure)
    }

    private fun localPolygon(minX: Double, minY: Double, maxX: Double, maxY: Double): List<LatLon> {
        return listOf(
            minX to minY,
            maxX to minY,
            maxX to maxY,
            minX to maxY
        ).map { (x, y) ->
            val (lat, lon) = frame.localToLatLon(x, y)
            LatLon(lat, lon)
        }
    }
}
