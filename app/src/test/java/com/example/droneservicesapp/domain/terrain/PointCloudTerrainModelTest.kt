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
