package com.example.droneservicesapp.domain.terrain

import com.example.droneservicesapp.domain.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainCoveragePlannerTest {
    @Test fun createsOneKilometerCoverageAndDensifiesPath() {
        val path = listOf(LatLon(35.0, 24.0), LatLon(35.004, 24.0))
        val plan = TerrainCoveragePlanner.createPlan(path, home = path.first())

        assertEquals(1_000.0, plan.radiusMeters, 0.0)
        assertTrue(plan.requiredPathSamples.size > path.size)
        assertEquals(setOf(SrtmTileId(35, 23), SrtmTileId(35, 24), SrtmTileId(34, 23), SrtmTileId(34, 24)), plan.sourceTiles)
    }

    @Test(expected = MissionOutsideTerrainCoverageException::class)
    fun rejectsHomeOutsideCoverage() {
        TerrainCoveragePlanner.createPlan(
            missionPath = listOf(LatLon(35.0, 24.0), LatLon(35.001, 24.0)),
            home = LatLon(35.02, 24.0),
        )
    }

    @Test fun coverageCrossingDegreeBoundaryIncludesAdjacentTiles() {
        val path = listOf(LatLon(35.999, 24.999), LatLon(35.9995, 24.9995))
        val plan = TerrainCoveragePlanner.createPlan(path, home = path.first())
        assertTrue(SrtmTileId(35, 24) in plan.sourceTiles)
        assertTrue(SrtmTileId(36, 25) in plan.sourceTiles)
    }
}
