package com.example.droneservicesapp.mavserver

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanningParameterPolicyTest {
    @Test
    fun terrainFrameEnablesThirtyMeterTerrainAndDisablesWaypointRangefinder() {
        assertEquals(
            PlanningParameterTargets(terrainEnable = 1, terrainSpacing = 30, waypointRangefinderUse = 0),
            PlanningParameterPolicy.targets(usesTerrainFrame = true, hasPointCloudProfile = false),
        )
    }

    @Test
    fun pointCloudSprayDisablesTerrainAndWaypointRangefinder() {
        assertEquals(
            PlanningParameterTargets(terrainEnable = 0, terrainSpacing = 30, waypointRangefinderUse = 0),
            PlanningParameterPolicy.targets(usesTerrainFrame = false, hasPointCloudProfile = true),
        )
    }

    @Test
    fun nonTerrainMissionNeverEnablesMissingRangefinder() {
        assertEquals(
            PlanningParameterTargets(terrainEnable = 0, terrainSpacing = 30, waypointRangefinderUse = 0),
            PlanningParameterPolicy.targets(usesTerrainFrame = false, hasPointCloudProfile = false),
        )
    }
}
