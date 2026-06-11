package com.example.droneservicesapp.domain.geoawareness

import com.example.droneservicesapp.domain.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveGeoAwarenessCheckerTest {
    private val checker = LiveGeoAwarenessChecker()

    @Test
    fun polygonMovingTowardBoundaryWithinThreeSecondsTriggersTimeWarning() {
        val result = checker.findNearestZoneWithinThreshold(
            position = pointSouthOfPolygon(distanceMeters = 20.0),
            zones = listOf(polygonZone()),
            thresholdMeters = 10.0,
            altitudeContext = GeoAltitudeContext(aglMeters = 50.0),
            groundSpeedMetersPerSecond = 10.0,
            headingDegrees = 0.0
        )

        assertNotNull(result)
        assertEquals("TIME_TO_BOUNDARY", result!!.warningMode)
        assertTrue(result.timeToBoundarySeconds!! <= 3.0)
        assertTrue(result.closingSpeedMetersPerSecond!! > 0.0)
    }

    @Test
    fun polygonMovingTowardBoundaryBeyondThreeSecondsAndOutsideFallbackDoesNotWarn() {
        val result = checker.findNearestZoneWithinThreshold(
            position = pointSouthOfPolygon(distanceMeters = 150.0),
            zones = listOf(polygonZone()),
            thresholdMeters = 100.0,
            altitudeContext = GeoAltitudeContext(aglMeters = 50.0),
            groundSpeedMetersPerSecond = 10.0,
            headingDegrees = 0.0
        )

        assertNull(result)
    }

    @Test
    fun movingAwayInsideFixedDistanceUsesFallbackOnly() {
        val result = checker.findNearestZoneWithinThreshold(
            position = pointSouthOfPolygon(distanceMeters = 25.0),
            zones = listOf(polygonZone()),
            thresholdMeters = 100.0,
            altitudeContext = GeoAltitudeContext(aglMeters = 50.0),
            groundSpeedMetersPerSecond = 10.0,
            headingDegrees = 180.0
        )

        assertNotNull(result)
        assertEquals("FIXED_DISTANCE_100M", result!!.warningMode)
        assertNull(result.timeToBoundarySeconds)
    }

    @Test
    fun circularZoneMovingTowardBoundaryTriggersTimeWarning() {
        val result = checker.findNearestZoneWithinThreshold(
            position = LatLon(lat = -metersToLatitudeDegrees(70.0), lon = 0.0),
            zones = listOf(circleZone(radiusMeters = 50.0)),
            thresholdMeters = 10.0,
            altitudeContext = GeoAltitudeContext(aglMeters = 50.0),
            groundSpeedMetersPerSecond = 10.0,
            headingDegrees = 0.0
        )

        assertNotNull(result)
        assertEquals("TIME_TO_BOUNDARY", result!!.warningMode)
        assertTrue(result.timeToBoundarySeconds!! <= 3.0)
    }

    @Test
    fun nonClosingMotionOutsideFallbackDoesNotWarnOrDivideByZero() {
        val result = checker.findNearestZoneWithinThreshold(
            position = pointSouthOfPolygon(distanceMeters = 150.0),
            zones = listOf(polygonZone()),
            thresholdMeters = 100.0,
            altitudeContext = GeoAltitudeContext(aglMeters = 50.0),
            groundSpeedMetersPerSecond = 10.0,
            headingDegrees = 90.0
        )

        assertNull(result)
    }

    @Test
    fun unavailableHeadingUsesFixedDistanceFallbackOnly() {
        val nearResult = checker.findNearestZoneWithinThreshold(
            position = pointSouthOfPolygon(distanceMeters = 80.0),
            zones = listOf(polygonZone()),
            thresholdMeters = 100.0,
            altitudeContext = GeoAltitudeContext(aglMeters = 50.0),
            groundSpeedMetersPerSecond = 10.0,
            headingDegrees = null
        )
        val farResult = checker.findNearestZoneWithinThreshold(
            position = pointSouthOfPolygon(distanceMeters = 150.0),
            zones = listOf(polygonZone()),
            thresholdMeters = 100.0,
            altitudeContext = GeoAltitudeContext(aglMeters = 50.0),
            groundSpeedMetersPerSecond = 10.0,
            headingDegrees = null
        )

        assertNotNull(nearResult)
        assertEquals("FIXED_DISTANCE_100M", nearResult!!.warningMode)
        assertNull(nearResult.timeToBoundarySeconds)
        assertNull(farResult)
    }

    @Test
    fun verticallyIrrelevantZoneDoesNotWarn() {
        val result = checker.findNearestZoneWithinThreshold(
            position = pointSouthOfPolygon(distanceMeters = 20.0),
            zones = listOf(polygonZone(lowerMeters = 100.0, upperMeters = 200.0)),
            thresholdMeters = 100.0,
            altitudeContext = GeoAltitudeContext(aglMeters = 50.0),
            groundSpeedMetersPerSecond = 10.0,
            headingDegrees = 0.0
        )

        assertNull(result)
    }

    @Test
    fun insideRelevantZoneStillTriggersInsideWarning() {
        val zones = checker.checkDronePosition(
            dronePosition = LatLon(lat = 0.0015, lon = 0.0),
            altitudeContext = GeoAltitudeContext(aglMeters = 50.0),
            zones = listOf(polygonZone())
        )

        assertEquals(1, zones.size)
        assertEquals("TEST-POLYGON", zones.first().id)
    }

    private fun polygonZone(
        lowerMeters: Double? = null,
        upperMeters: Double? = 120.0
    ): GeoZone {
        val south = 0.001
        val north = 0.002
        val west = -0.001
        val east = 0.001
        return zone(
            id = "TEST-POLYGON",
            name = "Test polygon",
            geometry = GeoZoneGeometry.Polygon(
                rings = listOf(
                    listOf(
                        LatLon(south, west),
                        LatLon(south, east),
                        LatLon(north, east),
                        LatLon(north, west),
                        LatLon(south, west)
                    )
                ),
                lowerLimitMeters = lowerMeters,
                upperLimitMeters = upperMeters
            )
        )
    }

    private fun circleZone(radiusMeters: Double): GeoZone {
        return zone(
            id = "TEST-CIRCLE",
            name = "Test circle",
            geometry = GeoZoneGeometry.Circle(
                center = LatLon(0.0, 0.0),
                radiusMeters = radiusMeters,
                lowerLimitMeters = null,
                upperLimitMeters = 120.0
            )
        )
    }

    private fun zone(id: String, name: String, geometry: GeoZoneGeometry): GeoZone {
        return GeoZone(
            id = id,
            country = "GR",
            name = name,
            type = null,
            restriction = GeoZoneRestriction.PROHIBITED,
            reason = emptyList(),
            otherReasonInfo = null,
            message = null,
            applicability = emptyList(),
            authorities = emptyList(),
            geometries = listOf(geometry),
            colorHex = null,
            arc = null,
            isDummy = false
        )
    }

    private fun pointSouthOfPolygon(distanceMeters: Double): LatLon {
        return LatLon(lat = 0.001 - metersToLatitudeDegrees(distanceMeters), lon = 0.0)
    }

    private fun metersToLatitudeDegrees(meters: Double): Double = meters / 111_320.0
}
