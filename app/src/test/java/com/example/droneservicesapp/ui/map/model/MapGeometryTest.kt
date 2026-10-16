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

    @Test
    fun tileCountMatchesTheWebMercatorGrid() {
        val world = MapBounds(MapCoordinate(-85.0, -180.0), MapCoordinate(85.0, 180.0))
        assertEquals(1L + 4L + 16L, MapGeometry.tileCount(world, 0, 2))
        // A single point touches exactly one tile per zoom level.
        assertEquals(5L, MapGeometry.tileCount(MapBounds(origin, origin), 14, 18))
        val oneKilometre = MapBounds(MapCoordinate(35.36, 24.47), MapCoordinate(35.37, 24.48))
        assertEquals(107L, MapGeometry.tileCount(oneKilometre, 14, 18))
    }

    @Test
    fun offlineGuardAllowsAZoom14ViewAndRefusesWiderAreas() {
        // A 2340 x 1080 px landscape view over Crete at zoom 14 vs a 0.2 degree box.
        val zoom14View = MapBounds(MapCoordinate(35.3254, 24.3698), MapCoordinate(35.3946, 24.5702))
        val wide = MapBounds(MapCoordinate(35.26, 24.37), MapCoordinate(35.46, 24.57))
        assertTrue(MapGeometry.tileCount(zoom14View, 14, 18) in 10_000L..20_000L)
        assertTrue(MapGeometry.tileCount(wide, 14, 18) > 20_000L)
    }
}
