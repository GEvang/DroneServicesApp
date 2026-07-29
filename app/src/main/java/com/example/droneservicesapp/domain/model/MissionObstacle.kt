package com.example.droneservicesapp.domain.model

data class MissionObstacle(
    val id: String,
    val shape: MissionObstacleShape,
    val center: LatLon? = null,
    val radiusMeters: Double = 0.0,
    val vertices: List<LatLon> = emptyList()
) {
    fun isValid(): Boolean {
        return when (shape) {
            MissionObstacleShape.CIRCLE -> center != null && radiusMeters > 0.0
            MissionObstacleShape.POLYGON -> vertices.size >= 3
        }
    }
}

enum class MissionObstacleShape {
    CIRCLE,
    POLYGON
}
