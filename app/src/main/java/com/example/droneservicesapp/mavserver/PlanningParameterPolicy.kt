package com.example.droneservicesapp.mavserver

/** Vehicle parameter targets derived from the currently selected planning mode. */
data class PlanningParameterTargets(
    val terrainEnable: Int,
    val terrainSpacing: Int,
    val waypointRangefinderUse: Int,
)

object PlanningParameterPolicy {
    fun targets(
        usesTerrainFrame: Boolean,
        hasPointCloudProfile: Boolean,
    ): PlanningParameterTargets {
        val requiresFlightControllerTerrain = usesTerrainFrame && !hasPointCloudProfile
        return PlanningParameterTargets(
            terrainEnable = if (requiresFlightControllerTerrain) 1 else 0,
            terrainSpacing = 30,
            waypointRangefinderUse = 0,
        )
    }
}
