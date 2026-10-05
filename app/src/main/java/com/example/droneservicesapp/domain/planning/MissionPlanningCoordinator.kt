package com.example.droneservicesapp.domain.planning

import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.MissionObstacle
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.SurveyGridParams
import com.example.droneservicesapp.domain.survey.SurveyGridPlanner
import com.example.droneservicesapp.domain.survey.SurveyPlanner
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Owns the domain decisions used while turning operator input into a mission path.
 * Android lifecycle, rendering, and asynchronous execution remain with the screen.
 */
class MissionPlanningCoordinator {
    fun buildPointRoute(
        points: List<LatLon>,
        obstacles: List<MissionObstacle>,
    ): List<LatLon> {
        if (points.size < 2) return points
        val validObstacles = obstacles.filter { it.isValid() }
        if (validObstacles.isEmpty()) return points
        val planner = SurveyPlanner()
        return buildList {
            points.zipWithNext().forEachIndexed { segmentIndex, (from, to) ->
                val segment = planner.buildObstacleAvoidingTransitPath(from, to, validObstacles)
                if (segmentIndex == 0) addAll(segment) else addAll(segment.drop(1))
            }
        }
    }

    fun buildAreaPath(
        polygon: List<LatLon>,
        operationMode: PlanningOperationMode,
        obstacles: List<MissionObstacle>,
        sprayDistanceMeters: Double,
        sprayAngleDegrees: Int,
        surveyParams: SurveyGridParams?,
    ): List<LatLon> = when (operationMode) {
        PlanningOperationMode.SPRAY -> SurveyPlanner().buildSurveyPath(
            polygon = polygon,
            distanceMeters = sprayDistanceMeters,
            angleDeg = sprayAngleDegrees,
            obstacles = obstacles,
        )
        PlanningOperationMode.SURVEY -> surveyParams?.let {
            SurveyGridPlanner().buildSurveyPath(polygon, it, obstacles)
        }.orEmpty()
    }

    fun orderPathForHome(
        path: List<LatLon>,
        home: LatLon?,
        operationMode: PlanningOperationMode,
    ): List<LatLon> {
        if (home == null || path.size < 2) return path
        val firstDistance = distanceMeters(home, path.first())
        val lastDistance = distanceMeters(home, path.last())
        val shouldReverse = when (operationMode) {
            PlanningOperationMode.SPRAY -> lastDistance < firstDistance
            PlanningOperationMode.SURVEY -> lastDistance > firstDistance
        }
        return if (shouldReverse) path.reversed() else path
    }

    fun totalDistance(path: List<LatLon>, home: LatLon?): Double {
        val pathDistance = path.zipWithNext().sumOf { (from, to) -> distanceMeters(from, to) }
        val first = path.firstOrNull() ?: return pathDistance
        return pathDistance + (home?.let { distanceMeters(it, first) } ?: 0.0)
    }

    fun buildResourcePlan(
        path: List<LatLon>,
        home: LatLon?,
        operationMode: PlanningOperationMode,
        speedMetersPerSecond: Double,
        sprayRateLitersPerMinute: Double,
    ): MissionResourcePlan {
        val spraying = operationMode == PlanningOperationMode.SPRAY
        val effectiveSprayRate = if (spraying) sprayRateLitersPerMinute else 0.0
        return MissionResourcePlanner.plan(
            path = path,
            home = home,
            speedMetersPerSecond = speedMetersPerSecond.coerceAtLeast(0.1),
            sprayRateLitersPerMinute = effectiveSprayRate,
            usableBatteryMinutes = if (spraying) {
                MissionResourcePlanner.sprayerBatteryMinutes(effectiveSprayRate)
            } else {
                MissionResourcePlanner.USABLE_BATTERY_MINUTES
            },
        )
    }

    private fun distanceMeters(from: LatLon, to: LatLon): Double {
        val earthRadiusMeters = 6_371_000.0
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(to.lon - from.lon)
        val a = sin(deltaLat / 2.0) * sin(deltaLat / 2.0) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2.0) * sin(deltaLon / 2.0)
        return earthRadiusMeters * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }
}
