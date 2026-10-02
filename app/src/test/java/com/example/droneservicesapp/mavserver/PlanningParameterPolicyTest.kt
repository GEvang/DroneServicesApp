package com.example.droneservicesapp.mavserver

import com.example.droneservicesapp.domain.model.PlanningOperationMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanningParameterPolicyTest {
    @Test
    fun surveyEnablesThirtyMeterTerrainAndDisablesWaypointRangefinder() {
        assertEquals(
            PlanningParameterTargets(terrainEnable = 1, terrainSpacing = 30, waypointRangefinderUse = 0),
            PlanningParameterPolicy.targets(PlanningOperationMode.SURVEY, hasPointCloudProfile = false),
        )
    }

    @Test
    fun pointCloudSprayDisablesTerrainAndWaypointRangefinder() {
        assertEquals(
            PlanningParameterTargets(terrainEnable = 0, terrainSpacing = 30, waypointRangefinderUse = 0),
            PlanningParameterPolicy.targets(PlanningOperationMode.SPRAY, hasPointCloudProfile = true),
        )
    }

    @Test
    fun ordinarySprayDisablesTerrainAndEnablesWaypointRangefinder() {
        assertEquals(
            PlanningParameterTargets(terrainEnable = 0, terrainSpacing = 30, waypointRangefinderUse = 1),
            PlanningParameterPolicy.targets(PlanningOperationMode.SPRAY, hasPointCloudProfile = false),
        )
    }
}
