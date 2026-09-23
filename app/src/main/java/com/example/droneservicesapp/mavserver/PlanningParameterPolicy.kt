package com.example.droneservicesapp.mavserver

import com.example.droneservicesapp.domain.model.PlanningOperationMode

/** Vehicle parameter targets derived from the currently selected planning mode. */
data class PlanningParameterTargets(
    val terrainEnable: Int,
    val waypointRangefinderUse: Int,
)

object PlanningParameterPolicy {
    fun targets(
        operationMode: PlanningOperationMode,
        hasPointCloudProfile: Boolean,
    ): PlanningParameterTargets {
        val isSurvey = operationMode == PlanningOperationMode.SURVEY
        return PlanningParameterTargets(
            terrainEnable = if (isSurvey) 1 else 0,
            waypointRangefinderUse = if (isSurvey || hasPointCloudProfile) 0 else 1,
        )
    }
}
