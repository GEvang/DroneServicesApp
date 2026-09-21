package com.example.droneservicesapp.domain.terrain

import org.junit.Assert.assertEquals
import org.junit.Test

class PointCloudCoverageTest {

    @Test
    fun completeCoverageRequiresOverlapAndEveryRequiredFlightPath() {
        assertEquals(
            PointCloudCoverage.COMPLETE,
            PointCloudCoverage.classify(
                hasPointCloudOverlap = true,
                allRequiredFlightPathsCovered = true,
            ),
        )
    }

    @Test
    fun overlapWithoutCompleteFlightCoverageIsPartial() {
        assertEquals(
            PointCloudCoverage.PARTIAL,
            PointCloudCoverage.classify(
                hasPointCloudOverlap = true,
                allRequiredFlightPathsCovered = false,
            ),
        )
    }

    @Test
    fun noOverlapIsSmallCropCoverage() {
        assertEquals(
            PointCloudCoverage.NONE,
            PointCloudCoverage.classify(
                hasPointCloudOverlap = false,
                allRequiredFlightPathsCovered = false,
            ),
        )
    }
}
