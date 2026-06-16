package com.example.droneservicesapp.domain.geoawareness

import android.util.Log
import com.example.droneservicesapp.domain.model.LatLon

class GeoAwarenessChecker {

    fun checkMission(
        missionPolygon: List<LatLon>?,
        surveyPath: List<LatLon>?,
        missionAltitudeMeters: Double?,
        zones: List<GeoZone>
    ): GeoAwarenessResult {
        return checkMission(
            missionPolygon = missionPolygon,
            surveyPath = surveyPath,
            altitudeContext = GeoAltitudeContext(aglMeters = missionAltitudeMeters),
            zones = zones
        )
    }

    fun checkMission(
        missionPolygon: List<LatLon>?,
        surveyPath: List<LatLon>?,
        altitudeContext: GeoAltitudeContext?,
        zones: List<GeoZone>
    ): GeoAwarenessResult {
        val normalizedMissionPolygon = missionPolygon?.takeIf(::isValidMissionPolygon)
        val normalizedSurveyPath = surveyPath.orEmpty().takeIf { it.isNotEmpty() }.orEmpty()

        if (normalizedMissionPolygon == null && normalizedSurveyPath.isEmpty()) {
            return GeoAwarenessResult.clear()
        }

        if (zones.isEmpty()) {
            return GeoAwarenessResult.clear()
        }

        val conflicts = mutableListOf<GeoZoneConflict>()

        for (zone in zones) {
            if (!GeoZoneApplicabilityEvaluator.isActiveNow(zone)) {
                continue
            }
            var missionAreaGeometryCount = 0
            var surveyPathGeometryCount = 0
            var waypointInsideGeometryCount = 0

            for (geometry in zone.geometries) {
                try {
                    val missionAreaIntersects = normalizedMissionPolygon != null &&
                        GeoAwarenessGeometryUtils.polygonIntersectsGeometry(
                            polygon = normalizedMissionPolygon,
                            geometry = geometry,
                            altitudeContext = altitudeContext
                        )
                    if (missionAreaIntersects) {
                        missionAreaGeometryCount += 1
                    }

                    val surveyPathIntersects = normalizedSurveyPath.isNotEmpty() &&
                        GeoAwarenessGeometryUtils.pathIntersectsGeometry(
                            path = normalizedSurveyPath,
                            geometry = geometry,
                            altitudeContext = altitudeContext
                        )
                    if (surveyPathIntersects) {
                        surveyPathGeometryCount += 1
                    }

                    val waypointInside = normalizedSurveyPath.isNotEmpty() &&
                        normalizedSurveyPath.any { waypoint ->
                            GeoAwarenessGeometryUtils.pointInZone(
                                point = waypoint,
                                geometry = geometry,
                                altitudeContext = altitudeContext
                            )
                        }
                    if (waypointInside) {
                        waypointInsideGeometryCount += 1
                    }
                } catch (error: Exception) {
                    Log.w(TAG, "Skipping malformed geometry while checking zone ${zone.id}", error)
                }
            }

            if (missionAreaGeometryCount > 0) {
                conflicts += buildConflict(
                    zone = zone,
                    conflictType = GeoConflictType.MISSION_AREA_INTERSECTS_ZONE,
                    affectedGeometryCount = missionAreaGeometryCount
                )
            }
            if (surveyPathGeometryCount > 0) {
                conflicts += buildConflict(
                    zone = zone,
                    conflictType = GeoConflictType.SURVEY_PATH_INTERSECTS_ZONE,
                    affectedGeometryCount = surveyPathGeometryCount
                )
            }
            if (waypointInsideGeometryCount > 0) {
                conflicts += buildConflict(
                    zone = zone,
                    conflictType = GeoConflictType.WAYPOINT_INSIDE_ZONE,
                    affectedGeometryCount = waypointInsideGeometryCount
                )
            }
        }

        if (conflicts.isEmpty()) {
            return GeoAwarenessResult.clear()
        }

        val highestRestriction = highestRestriction(conflicts)
        val canUpload = true
        val requiresAcknowledgement = highestRestriction == GeoZoneRestriction.PROHIBITED ||
            highestRestriction == GeoZoneRestriction.REQ_AUTHORISATION

        return GeoAwarenessResult(
            conflicts = conflicts,
            highestRestriction = highestRestriction,
            canUpload = canUpload,
            requiresAcknowledgement = requiresAcknowledgement
        )
    }

    private fun restrictionRank(restriction: GeoZoneRestriction): Int {
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED -> 4
            GeoZoneRestriction.REQ_AUTHORISATION -> 3
            GeoZoneRestriction.CONDITIONAL -> 2
            GeoZoneRestriction.INFORMATION -> 1
            GeoZoneRestriction.UNKNOWN -> 0
        }
    }

    private fun highestRestriction(conflicts: List<GeoZoneConflict>): GeoZoneRestriction {
        return conflicts.maxByOrNull { restrictionRank(it.restriction) }?.restriction
            ?: GeoZoneRestriction.UNKNOWN
    }

    private fun buildConflict(
        zone: GeoZone,
        conflictType: GeoConflictType,
        affectedGeometryCount: Int
    ): GeoZoneConflict {
        return GeoZoneConflict(
            zone = zone,
            conflictType = conflictType,
            restriction = zone.restriction,
            message = zone.message,
            affectedGeometryCount = affectedGeometryCount
        )
    }

    private fun isValidMissionPolygon(polygon: List<LatLon>): Boolean {
        return GeoAwarenessGeometryUtils.normalizeRing(polygon).distinct().size >= 3
    }

    companion object {
        private const val TAG = "GeoAwarenessChecker"
    }
}
