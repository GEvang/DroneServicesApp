package com.example.droneservicesapp.data.terrain

import com.example.droneservicesapp.domain.terrain.SrtmTileId
import com.example.droneservicesapp.domain.terrain.TerrainCoveragePlan
import com.example.droneservicesapp.domain.terrain.TerrainCoveragePlanner
import com.example.droneservicesapp.domain.model.LatLon
import java.io.Closeable
import kotlin.math.roundToInt

interface TerrainElevationSource : Closeable {
    val plan: TerrainCoveragePlan
    fun elevationMeters(latitude: Double, longitude: Double): Int?
    fun contains(latitude: Double, longitude: Double): Boolean
}

class PreparedTerrainSource(
    override val plan: TerrainCoveragePlan,
    private val tiles: Map<SrtmTileId, SrtmHgtTile>,
) : TerrainElevationSource {
    override fun elevationMeters(latitude: Double, longitude: Double): Int? {
        return tiles[SrtmTileId.containing(latitude, longitude)]
            ?.elevationMeters(latitude, longitude)
            ?.roundToInt()
    }

    override fun contains(latitude: Double, longitude: Double): Boolean =
        TerrainCoveragePlanner.distanceMeters(plan.center, LatLon(latitude, longitude)) <= plan.radiusMeters + 0.01

    override fun close() {
        tiles.values.forEach(SrtmHgtTile::close)
    }
}
