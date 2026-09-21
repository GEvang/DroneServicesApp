package com.example.droneservicesapp.domain.terrain

import org.junit.Assert.assertEquals
import org.junit.Test

class PointCloudCoverageTest {

    @Test
    fun completeCoverageRequiresOverlapAndEverySprayingPathPoint() {
        assertEquals(
            PointCloudCoverage.COMPLETE,
            PointCloudCoverage.classify(
                hasPointCloudOverlap = true,
                allSprayingPathPointsCovered = true,
            ),
        )
    }

    @Test
    fun overlapWithoutCompleteFlightCoverageIsPartial() {
        assertEquals(
            PointCloudCoverage.PARTIAL,
            PointCloudCoverage.classify(
                hasPointCloudOverlap = true,
                allSprayingPathPointsCovered = false,
            ),
        )
    }

    @Test
    fun noOverlapIsSmallCropCoverage() {
        assertEquals(
            PointCloudCoverage.NONE,
            PointCloudCoverage.classify(
                hasPointCloudOverlap = false,
                allSprayingPathPointsCovered = false,
            ),
        )
    }
}
