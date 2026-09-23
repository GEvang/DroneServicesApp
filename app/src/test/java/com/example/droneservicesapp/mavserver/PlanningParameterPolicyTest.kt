package com.example.droneservicesapp.mavserver

import com.example.droneservicesapp.domain.model.PlanningOperationMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanningParameterPolicyTest {
    @Test
    fun surveyEnablesTerrainAndDisablesWaypointRangefinder() {
        assertEquals(
            PlanningParameterTargets(terrainEnable = 1, waypointRangefinderUse = 0),
            PlanningParameterPolicy.targets(PlanningOperationMode.SURVEY, hasPointCloudProfile = false),
        )
    }

    @Test
    fun pointCloudSprayDisablesTerrainAndWaypointRangefinder() {
        assertEquals(
            PlanningParameterTargets(terrainEnable = 0, waypointRangefinderUse = 0),
            PlanningParameterPolicy.targets(PlanningOperationMode.SPRAY, hasPointCloudProfile = true),
        )
    }

    @Test
    fun ordinarySprayDisablesTerrainAndEnablesWaypointRangefinder() {
        assertEquals(
            PlanningParameterTargets(terrainEnable = 0, waypointRangefinderUse = 1),
            PlanningParameterPolicy.targets(PlanningOperationMode.SPRAY, hasPointCloudProfile = false),
        )
    }
}
