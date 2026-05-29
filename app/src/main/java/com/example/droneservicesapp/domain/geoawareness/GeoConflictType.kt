package com.example.droneservicesapp.domain.geoawareness

enum class GeoConflictType {
    MISSION_AREA_INTERSECTS_ZONE,
    SURVEY_PATH_INTERSECTS_ZONE,
    WAYPOINT_INSIDE_ZONE
}
