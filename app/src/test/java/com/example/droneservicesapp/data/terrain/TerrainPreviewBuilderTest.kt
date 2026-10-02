package com.example.droneservicesapp.data.terrain

import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.terrain.SrtmTileId
import com.example.droneservicesapp.domain.terrain.TerrainCoveragePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainPreviewBuilderTest {
    @Test
    fun `build creates a centered georeferenced point cloud for source tiles`() {
        val source = object : TerrainElevationSource {
            override val plan = TerrainCoveragePlan(
                center = LatLon(35.0, 24.0),
                radiusMeters = 60.0,
                requiredPathSamples = listOf(LatLon(35.0, 24.0)),
                sourceTiles = setOf(SrtmTileId(35, 24)),
            )

            override fun elevationMeters(latitude: Double, longitude: Double): Int =
                100 + ((latitude - 35.0) * 100_000).toInt()

            override fun contains(latitude: Double, longitude: Double): Boolean = true
            override fun close() = Unit
        }

        val preview = TerrainPreviewBuilder.build(source, spacingMeters = 30)

        assertEquals(listOf("N35E024.DAT"), preview.fileNames)
        assertEquals(30, preview.gridSpacingMeters)
        assertEquals(13, preview.pointCloud.displayedPointCount)
        assertEquals(preview.pointCloud.displayedPointCount * 3, preview.pointCloud.positions.size)
        assertNotNull(preview.pointCloud.coordinateFrame)
        assertTrue(preview.pointCloud.positions.minOrNull()!! <= 0f)
        assertTrue(preview.pointCloud.positions.maxOrNull()!! >= 0f)
    }
}
