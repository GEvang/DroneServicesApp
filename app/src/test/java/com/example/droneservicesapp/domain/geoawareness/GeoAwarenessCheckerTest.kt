package com.example.droneservicesapp.domain.geoawareness

import com.example.droneservicesapp.domain.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoAwarenessCheckerTest {
    private val checker = GeoAwarenessChecker()

    @Test
    fun prohibitedMissionRequiresAcknowledgementButDoesNotBlockUpload() {
        val result = checker.checkMission(
            missionPolygon = missionPolygonInsideTestZone(),
            surveyPath = emptyList(),
            altitudeContext = GeoAltitudeContext(aglMeters = 50.0),
            zones = listOf(testZone(restriction = GeoZoneRestriction.PROHIBITED))
        )

        assertTrue(result.hasConflicts)
        assertEquals(GeoZoneRestriction.PROHIBITED, result.highestRestriction)
        assertTrue(result.canUpload)
        assertTrue(result.requiresAcknowledgement)
    }

    @Test
    fun inactiveProhibitedZoneDoesNotCreatePlanningConflict() {
        val result = checker.checkMission(
            missionPolygon = missionPolygonInsideTestZone(),
            surveyPath = emptyList(),
            altitudeContext = GeoAltitudeContext(aglMeters = 50.0),
            zones = listOf(
                testZone(
                    restriction = GeoZoneRestriction.PROHIBITED,
                    applicability = listOf(
                        GeoZoneApplicability(
                            startDateTime = "2020-01-01T00:00:00Z",
                            endDateTime = "2020-01-02T00:00:00Z",
                            permanent = false
                        )
                    )
                )
            )
        )

        assertFalse(result.hasConflicts)
        assertTrue(result.canUpload)
        assertFalse(result.requiresAcknowledgement)
    }

    @Test
    fun activeAuthorizationZoneRequiresAcknowledgement() {
        val result = checker.checkMission(
            missionPolygon = missionPolygonInsideTestZone(),
            surveyPath = emptyList(),
            altitudeContext = GeoAltitudeContext(aglMeters = 50.0),
            zones = listOf(testZone(restriction = GeoZoneRestriction.REQ_AUTHORISATION))
        )

        assertTrue(result.hasConflicts)
        assertEquals(GeoZoneRestriction.REQ_AUTHORISATION, result.highestRestriction)
        assertTrue(result.canUpload)
        assertTrue(result.requiresAcknowledgement)
    }

    private fun testZone(
        restriction: GeoZoneRestriction,
        applicability: List<GeoZoneApplicability> = emptyList()
    ): GeoZone {
        return GeoZone(
            id = "TEST-ZONE",
            country = "GR",
            name = "Test zone",
            type = null,
            restriction = restriction,
            reason = emptyList(),
            otherReasonInfo = null,
            message = null,
            applicability = applicability,
            authorities = emptyList(),
            geometries = listOf(
                GeoZoneGeometry.Polygon(
                    rings = listOf(
                        listOf(
                            LatLon(0.0, 0.0),
                            LatLon(0.0, 0.01),
                            LatLon(0.01, 0.01),
                            LatLon(0.01, 0.0),
                            LatLon(0.0, 0.0)
                        )
                    ),
                    lowerLimitMeters = 0.0,
                    upperLimitMeters = 120.0
                )
            ),
            colorHex = null,
            arc = null,
            isDummy = false
        )
    }

    private fun missionPolygonInsideTestZone(): List<LatLon> {
        return listOf(
            LatLon(0.002, 0.002),
            LatLon(0.002, 0.004),
            LatLon(0.004, 0.004),
            LatLon(0.004, 0.002),
            LatLon(0.002, 0.002)
        )
    }
}
