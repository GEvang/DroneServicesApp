package com.example.droneservicesapp.ui.map.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapGeometryTest {
    private val origin = MapCoordinate(35.0, 24.0)

    @Test
    fun arrowRotationFollowsScreenDirection() {
        // osmdroid rotates markers counter-clockwise: a right-pointing arrow needs 0° for east,
        // 90° for north, 180° for west and 270° for south.
        assertEquals(0f, MapGeometry.screenArrowRotationDegrees(origin, MapCoordinate(35.0, 24.01)), 0.01f)
        assertEquals(90f, MapGeometry.screenArrowRotationDegrees(origin, MapCoordinate(35.01, 24.0)), 0.01f)
        assertEquals(180f, MapGeometry.screenArrowRotationDegrees(origin, MapCoordinate(35.0, 23.99)), 0.01f)
        assertEquals(270f, MapGeometry.screenArrowRotationDegrees(origin, MapCoordinate(34.99, 24.0)), 0.01f)
        assertEquals(0f, MapGeometry.screenArrowRotationDegrees(origin, origin), 0f)
    }

    @Test
    fun screenDirectionIsUnitLengthAndNullForZeroSegments() {
        val direction = MapGeometry.screenDirection(origin, MapCoordinate(35.01, 24.01))
        assertNotNull(direction)
        val (x, y) = direction!!
        assertEquals(1.0, x * x + y * y, 1e-9)
        assertTrue(x > 0.0 && y < 0.0)
        assertNull(MapGeometry.screenDirection(origin, origin))
    }

    @Test
    fun bearingIsClockwiseFromNorth() {
        assertEquals(0f, MapGeometry.bearingDegrees(origin, MapCoordinate(35.1, 24.0)), 0.01f)
        assertEquals(90f, MapGeometry.bearingDegrees(origin, MapCoordinate(35.0, 24.1)), 0.1f)
        assertEquals(-90f, MapGeometry.bearingDegrees(origin, MapCoordinate(35.0, 23.9)), 0.1f)
    }

    @Test
    fun circleIsClosedAndKeepsItsRadius() {
        val ring = MapGeometry.circle(origin, 100.0, 36)
        assertEquals(37, ring.size)
        assertEquals(ring.first(), ring.last())
        ring.forEach { point ->
            val dLat = Math.toRadians(point.latitude - origin.latitude)
            val dLon = Math.toRadians(point.longitude - origin.longitude) * Math.cos(Math.toRadians(origin.latitude))
            val meters = Math.sqrt(dLat * dLat + dLon * dLon) * 6_371_000.0
            assertEquals(100.0, meters, 0.5)
        }
        assertTrue(MapGeometry.circle(origin, 0.0, 36).isEmpty())
        assertTrue(MapGeometry.circle(origin, 10.0, 2).isEmpty())
    }

    @Test
    fun circleNearTheAntimeridianStaysInRange() {
        val ring = MapGeometry.circle(MapCoordinate(10.0, 179.9999), 1_000.0, 12)
        assertTrue(ring.all { it.longitude in -180.0..180.0 })
    }

    @Test
    fun closeRingAppendsTheFirstPointOnce() {
        val open = listOf(origin, MapCoordinate(35.0, 24.1), MapCoordinate(35.1, 24.1))
        val closed = MapGeometry.closeRing(open)
        assertEquals(4, closed.size)
        assertEquals(closed, MapGeometry.closeRing(closed))
        assertTrue(MapGeometry.closeRing(emptyList()).isEmpty())
    }

    @Test
    fun noFixPlaceholderIsNotUsable() {
        assertFalse(MapGeometry.isUsable(0.0, 0.0))
        assertFalse(MapGeometry.isUsable(Double.NaN, 24.0))
        assertFalse(MapGeometry.isUsable(91.0, 24.0))
        assertTrue(MapGeometry.isUsable(35.0, 24.0))
        assertNull(MapGeometry.coordinateOrNull(0.0, 0.0))
        assertEquals(origin, MapGeometry.coordinateOrNull(35.0, 24.0))
    }

    @Test
    fun midpointAveragesCoordinates() {
        val midpoint = MapGeometry.midpoint(origin, MapCoordinate(35.1, 24.1))
        assertEquals(35.05, midpoint.latitude, 1e-9)
        assertEquals(24.05, midpoint.longitude, 1e-9)
    }
}
