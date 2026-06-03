package com.example.droneservicesapp.domain.model

enum class PlanningWorkflow {
    AREA,
    POINTS
}

enum class PlanningOperationMode {
    SURVEY,
    SPRAY
}

data class RouteWaypoint(
    val id: String,
    val index: Int,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val speedMetersPerSecond: Double,
    val sprayEnabled: Boolean,
    val sprayerIntensityPercent: Int
)
