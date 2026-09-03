package com.example.droneservicesapp.data.storage

import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.MissionObstacle
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.model.RouteWaypoint
import com.example.droneservicesapp.domain.model.SurveyGridParams
import com.google.android.gms.maps.model.LatLng

data class SavedMission(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val name: String = "",
    val workflow: PlanningWorkflow = PlanningWorkflow.AREA,
    val operationMode: PlanningOperationMode = PlanningOperationMode.SURVEY,
    val altitudeMeters: Int = 0,
    val altitudeReferenceMode: AltitudeReferenceMode = AltitudeReferenceMode.RELATIVE,
    val angleDegrees: Int = 90,
    val lineDistanceMeters: Int = 5,
    val sprayerIntensityPercent: Int = 75,
    val flightSpeedMetersPerSecond: Double = 5.0,
    val surveyGridParams: SurveyGridParams = SurveyGridParams(),
    val polygon: List<LatLng> = emptyList(),
    val surveyPath: List<LatLng> = emptyList(),
    val terrainSurveyWaypoints: List<TerrainWaypointSnapshot> = emptyList(),
    val routeWaypoints: List<RouteWaypoint> = emptyList(),
    val plannedHomePosition: LatLon? = null,
    val obstacles: List<MissionObstacle> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

data class TerrainWaypointSnapshot(
    val position: LatLon,
    val displayAltitudeMeters: Double,
    val missionAltitudeMeters: Double,
)
