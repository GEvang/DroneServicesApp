package com.example.droneservicesapp.data.geoawareness

import android.content.Context
import android.util.Log
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessChecker
import com.example.droneservicesapp.domain.geoawareness.GeoConflictType
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction
import com.example.droneservicesapp.domain.model.LatLon

object GeoAwarenessCheckerDebugProbe {

    fun logCheckerValidation(context: Context) {
        try {
            val repository = GeoZoneRepository(
                GeoZoneAssetDataSource(context.applicationContext)
            )
            val zones = repository.loadDummyRethymnoZones()
            val checker = GeoAwarenessChecker()

            val hospitalPolygon = listOf(
                LatLon(35.36460, 24.47040),
                LatLon(35.36460, 24.47250),
                LatLon(35.36570, 24.47250),
                LatLon(35.36570, 24.47040),
                LatLon(35.36460, 24.47040)
            )

            val hospitalResult = checker.checkMission(
                missionPolygon = hospitalPolygon,
                surveyPath = emptyList(),
                missionAltitudeMeters = 50.0,
                zones = zones
            )
            val hospitalBlocked = hospitalResult.hasConflicts &&
                hospitalResult.highestRestriction == GeoZoneRestriction.PROHIBITED &&
                !hospitalResult.canUpload &&
                hospitalResult.conflicts.any {
                    it.zone.id == "GR-RTH-DUMMY-007" &&
                        it.conflictType == GeoConflictType.MISSION_AREA_INTERSECTS_ZONE
                }
            logFailureIfNeeded("hospitalBlocked", expected = true, actual = hospitalBlocked)

            val beachResult = checker.checkMission(
                missionPolygon = null,
                surveyPath = listOf(
                    LatLon(35.36580, 24.48500),
                    LatLon(35.38100, 24.56000)
                ),
                missionAltitudeMeters = 50.0,
                zones = zones
            )
            val beachRequiresAck = beachResult.hasConflicts &&
                beachResult.highestRestriction == GeoZoneRestriction.REQ_AUTHORISATION &&
                beachResult.canUpload &&
                beachResult.requiresAcknowledgement &&
                beachResult.conflicts.any {
                    it.zone.id == "GR-RTH-DUMMY-006" &&
                        (it.conflictType == GeoConflictType.SURVEY_PATH_INTERSECTS_ZONE ||
                            it.conflictType == GeoConflictType.WAYPOINT_INSIDE_ZONE)
                }
            logFailureIfNeeded("beachRequiresAck", expected = true, actual = beachRequiresAck)

            val clearResult = checker.checkMission(
                missionPolygon = listOf(
                    LatLon(35.35000, 24.45000),
                    LatLon(35.35000, 24.45200),
                    LatLon(35.35200, 24.45200),
                    LatLon(35.35200, 24.45000),
                    LatLon(35.35000, 24.45000)
                ),
                surveyPath = listOf(
                    LatLon(35.35020, 24.45020),
                    LatLon(35.35180, 24.45180)
                ),
                missionAltitudeMeters = 50.0,
                zones = zones
            )
            val clearMission = !clearResult.hasConflicts &&
                clearResult.canUpload &&
                !clearResult.requiresAcknowledgement
            logFailureIfNeeded("clearMission", expected = true, actual = clearMission)

            val altitudeAboveResult = checker.checkMission(
                missionPolygon = hospitalPolygon,
                surveyPath = emptyList(),
                missionAltitudeMeters = 150.0,
                zones = zones
            )
            val altitudeAboveClear = !altitudeAboveResult.hasConflicts
            logFailureIfNeeded("altitudeAboveClear", expected = true, actual = altitudeAboveClear)

            val unknownAltitudeResult = checker.checkMission(
                missionPolygon = hospitalPolygon,
                surveyPath = emptyList(),
                missionAltitudeMeters = null,
                zones = zones
            )
            val unknownAltitudeBlocked = unknownAltitudeResult.hasConflicts &&
                unknownAltitudeResult.highestRestriction == GeoZoneRestriction.PROHIBITED
            logFailureIfNeeded("unknownAltitudeBlocked", expected = true, actual = unknownAltitudeBlocked)

            Log.d(
                TAG,
                "Checker validation: hospitalBlocked=$hospitalBlocked beachRequiresAck=$beachRequiresAck clearMission=$clearMission altitudeAboveClear=$altitudeAboveClear unknownAltitudeBlocked=$unknownAltitudeBlocked"
            )
        } catch (error: Exception) {
            Log.e(TAG, "Checker validation failed unexpectedly", error)
        }
    }

    private fun logFailureIfNeeded(name: String, expected: Boolean, actual: Boolean) {
        if (expected != actual) {
            Log.e(TAG, "Checker validation failed: $name expected $expected actual $actual")
        }
    }

    private const val TAG = "GeoCheckerDebug"
}
