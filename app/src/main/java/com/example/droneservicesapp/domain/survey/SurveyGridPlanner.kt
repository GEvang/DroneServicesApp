package com.example.droneservicesapp.domain.survey

import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.MissionObstacle
import com.example.droneservicesapp.domain.model.SurveyGridParams

class SurveyGridPlanner(
    private val basePlanner: SurveyPlanner = SurveyPlanner(),
) {
    fun buildSurveyPath(
        polygon: List<LatLon>,
        params: SurveyGridParams,
        obstacles: List<MissionObstacle> = emptyList(),
        maxAreaM2: Double = 40000.0,
    ): List<LatLon> {
        val overlapFraction = (params.overlapPercent.coerceIn(0, 95)) / 100.0
        val effectiveSpacing = (params.stripSpacingMeters * (1.0 - overlapFraction))
            .coerceAtLeast(1.0)

        return basePlanner.buildSurveyPath(
            polygon = polygon,
            distanceMeters = effectiveSpacing,
            angleDeg = params.gridAngleDegrees,
            obstacles = obstacles,
            maxAreaM2 = maxAreaM2
        )
    }
}
