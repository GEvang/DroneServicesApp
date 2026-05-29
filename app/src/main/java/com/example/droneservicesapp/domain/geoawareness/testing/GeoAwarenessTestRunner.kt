package com.example.droneservicesapp.domain.geoawareness.testing

import android.content.Context
import com.example.droneservicesapp.data.geoawareness.GeoZoneRepository
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessChecker
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessGeometryUtils
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthEvaluator
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthState
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessResult
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneConflict
import com.example.droneservicesapp.domain.geoawareness.GeoZoneGeometry
import com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction
import com.example.droneservicesapp.domain.geoawareness.LiveGeoAwarenessChecker
import com.example.droneservicesapp.domain.model.LatLon

class GeoAwarenessTestRunner(
    private val context: Context,
    private val repository: GeoZoneRepository,
    private val eventLogger: GeoAwarenessEventLogger? = null
) {

    fun runAllTests(): GeoAwarenessTestRunResult {
        val runAtMillis = System.currentTimeMillis()
        return try {
            val loadResult = repository.loadCurrentDataset()
            val results = listOf(
                testDatasetLoad(loadResult),
                testDatasetValidation(loadResult),
                testDatasetHealth(loadResult),
                testGeometryPointInZone(loadResult),
                testGeometryPathIntersection(loadResult),
                testMissionCheckerProhibited(loadResult),
                testMissionCheckerClearMission(loadResult),
                testAltitudeFiltering(loadResult),
                testLiveCheckerInsideClear(loadResult),
                testImportSourceState(loadResult),
                testEventLoggerWriteRead(),
                testUploadGuardPolicy(loadResult),
                testNearZoneWarning()
            )
            GeoAwarenessTestRunResult(runAtMillis = runAtMillis, results = results).also(::logRunSummary)
        } catch (error: Exception) {
            val failResult = GeoAwarenessTestResult(
                id = "GA-TEST-UNEXPECTED",
                name = "Unexpected runner failure",
                status = GeoAwarenessTestStatus.FAIL,
                message = error.message ?: error::class.java.simpleName
            )
            GeoAwarenessTestRunResult(runAtMillis = runAtMillis, results = listOf(failResult)).also {
                eventLogger?.logSimple(
                    type = GeoAwarenessEventType.GEO_TEST_RUN_FAILED,
                    severity = "ERROR",
                    message = "Geo-awareness test runner failed unexpectedly",
                    details = mapOf("errorMessage" to (error.message ?: error::class.java.simpleName))
                )
            }
        }
    }

    private fun testDatasetLoad(loadResult: GeoZoneLoadResult): GeoAwarenessTestResult {
        val hasZones = loadResult.zones.isNotEmpty()
        return GeoAwarenessTestResult(
            id = "GA-TEST-001",
            name = "Dataset load",
            status = if (hasZones) GeoAwarenessTestStatus.PASS else GeoAwarenessTestStatus.FAIL,
            message = if (hasZones) "Loaded geo-awareness dataset successfully." else "No geo-awareness zones were loaded.",
            details = mapOf(
                "zoneCount" to loadResult.zones.size.toString(),
                "datasetCount" to loadResult.datasetRecords.size.toString(),
                "source" to (loadResult.datasetInfo.source ?: "N/A")
            )
        )
    }

    private fun testDatasetValidation(loadResult: GeoZoneLoadResult): GeoAwarenessTestResult {
        val validation = loadResult.validationResult
        val status = when {
            validation.hasErrors -> GeoAwarenessTestStatus.FAIL
            validation.hasWarnings -> GeoAwarenessTestStatus.WARNING
            else -> GeoAwarenessTestStatus.PASS
        }
        return GeoAwarenessTestResult(
            id = "GA-TEST-002",
            name = "Dataset validation",
            status = status,
            message = when (status) {
                GeoAwarenessTestStatus.FAIL -> "Validation errors were detected."
                GeoAwarenessTestStatus.WARNING -> "Validation completed with warnings."
                else -> "Validation passed."
            },
            details = mapOf(
                "errorCount" to validation.errorCount.toString(),
                "warningCount" to validation.warningCount.toString(),
                "infoCount" to validation.infoCount.toString()
            )
        )
    }

    private fun testDatasetHealth(loadResult: GeoZoneLoadResult): GeoAwarenessTestResult {
        val health = GeoAwarenessHealthEvaluator.evaluate(
            datasetInfo = loadResult.datasetInfo,
            zones = loadResult.zones,
            datasetRecords = loadResult.datasetRecords,
            validationResult = loadResult.validationResult
        )
        val passLike = health.state != GeoAwarenessHealthState.UNAVAILABLE
        return GeoAwarenessTestResult(
            id = "GA-TEST-003",
            name = "Dataset health",
            status = if (passLike) {
                if (health.state == GeoAwarenessHealthState.DEGRADED || health.state == GeoAwarenessHealthState.STALE) {
                    GeoAwarenessTestStatus.WARNING
                } else {
                    GeoAwarenessTestStatus.PASS
                }
            } else {
                GeoAwarenessTestStatus.FAIL
            },
            message = "Health state ${health.state}: ${health.message}",
            details = mapOf("healthState" to health.state.name)
        )
    }

    private fun testGeometryPointInZone(loadResult: GeoZoneLoadResult): GeoAwarenessTestResult {
        val geometry = loadResult.zones.find { it.id == HOSPITAL_ZONE_ID }?.geometries?.firstOrNull()
            ?: syntheticProhibitedZone().geometries.first()
        val insidePoint = LatLon(35.36505, 24.47135)
        val outsidePoint = LatLon(35.35000, 24.45000)
        val inside = GeoAwarenessGeometryUtils.pointInZone(insidePoint, geometry, 50.0)
        val outside = GeoAwarenessGeometryUtils.pointInZone(outsidePoint, geometry, 50.0)
        val pass = inside && !outside
        return GeoAwarenessTestResult(
            id = "GA-TEST-004",
            name = "Geometry point-in-zone",
            status = if (pass) GeoAwarenessTestStatus.PASS else GeoAwarenessTestStatus.FAIL,
            message = if (pass) "Point-in-zone checks matched expectations." else "Point-in-zone checks failed.",
            details = mapOf("insideExpected" to inside.toString(), "outsideExpected" to (!outside).toString())
        )
    }

    private fun testGeometryPathIntersection(loadResult: GeoZoneLoadResult): GeoAwarenessTestResult {
        val geometry = loadResult.zones.find { it.id == BEACH_ZONE_ID }?.geometries?.firstOrNull()
            ?: syntheticAuthorizationZone().geometries.first()
        val path = listOf(
            LatLon(35.36580, 24.48500),
            LatLon(35.38100, 24.56000)
        )
        val intersects = GeoAwarenessGeometryUtils.pathIntersectsGeometry(path, geometry, 50.0)
        return GeoAwarenessTestResult(
            id = "GA-TEST-005",
            name = "Geometry path intersection",
            status = if (intersects) GeoAwarenessTestStatus.PASS else GeoAwarenessTestStatus.FAIL,
            message = if (intersects) "Path intersection detected as expected." else "Expected path intersection was not detected."
        )
    }

    private fun testMissionCheckerProhibited(loadResult: GeoZoneLoadResult): GeoAwarenessTestResult {
        val checker = GeoAwarenessChecker()
        val zones = if (loadResult.zones.any { it.id == HOSPITAL_ZONE_ID }) {
            loadResult.zones
        } else {
            listOf(syntheticProhibitedZone())
        }
        val result = checker.checkMission(
            missionPolygon = hospitalMissionPolygon(),
            surveyPath = emptyList(),
            missionAltitudeMeters = 50.0,
            zones = zones
        )
        val pass = result.hasConflicts &&
            result.highestRestriction == GeoZoneRestriction.PROHIBITED &&
            !result.canUpload
        return GeoAwarenessTestResult(
            id = "GA-TEST-006",
            name = "Mission checker prohibited conflict",
            status = if (pass) GeoAwarenessTestStatus.PASS else GeoAwarenessTestStatus.FAIL,
            message = if (pass) "Mission checker blocked prohibited mission as expected." else "Mission checker did not block prohibited mission."
        )
    }

    private fun testMissionCheckerClearMission(loadResult: GeoZoneLoadResult): GeoAwarenessTestResult {
        val checker = GeoAwarenessChecker()
        val result = checker.checkMission(
            missionPolygon = clearMissionPolygon(),
            surveyPath = listOf(
                LatLon(35.35020, 24.45020),
                LatLon(35.35180, 24.45180)
            ),
            missionAltitudeMeters = 50.0,
            zones = loadResult.zones.ifEmpty { listOf(syntheticProhibitedZone()) }
        )
        val pass = !result.hasConflicts && result.canUpload
        return GeoAwarenessTestResult(
            id = "GA-TEST-007",
            name = "Mission checker clear mission",
            status = if (pass) GeoAwarenessTestStatus.PASS else GeoAwarenessTestStatus.FAIL,
            message = if (pass) "Clear mission remained uploadable." else "Clear mission unexpectedly conflicted."
        )
    }

    private fun testAltitudeFiltering(loadResult: GeoZoneLoadResult): GeoAwarenessTestResult {
        val checker = GeoAwarenessChecker()
        val zones = if (loadResult.zones.any { it.id == HOSPITAL_ZONE_ID }) {
            loadResult.zones
        } else {
            listOf(syntheticProhibitedZone())
        }
        val altitudeAboveResult = checker.checkMission(
            missionPolygon = hospitalMissionPolygon(),
            surveyPath = emptyList(),
            missionAltitudeMeters = 150.0,
            zones = zones
        )
        val unknownAltitudeResult = checker.checkMission(
            missionPolygon = hospitalMissionPolygon(),
            surveyPath = emptyList(),
            missionAltitudeMeters = null,
            zones = zones
        )
        val pass = !altitudeAboveResult.hasConflicts &&
            unknownAltitudeResult.hasConflicts &&
            unknownAltitudeResult.highestRestriction == GeoZoneRestriction.PROHIBITED
        return GeoAwarenessTestResult(
            id = "GA-TEST-008",
            name = "Altitude filtering",
            status = if (pass) GeoAwarenessTestStatus.PASS else GeoAwarenessTestStatus.FAIL,
            message = if (pass) "Altitude filtering behaved as expected." else "Altitude filtering did not match expectations."
        )
    }

    private fun testLiveCheckerInsideClear(loadResult: GeoZoneLoadResult): GeoAwarenessTestResult {
        val checker = LiveGeoAwarenessChecker()
        val zones = if (loadResult.zones.any { it.id == HOSPITAL_ZONE_ID }) {
            loadResult.zones
        } else {
            listOf(syntheticProhibitedZone())
        }
        val inside = checker.checkDronePosition(
            dronePosition = LatLon(35.36505, 24.47135),
            droneAltitudeMeters = 50.0,
            zones = zones
        ).isNotEmpty()
        val clear = checker.checkDronePosition(
            dronePosition = LatLon(35.35000, 24.45000),
            droneAltitudeMeters = 50.0,
            zones = zones
        ).isEmpty()
        val pass = inside && clear
        return GeoAwarenessTestResult(
            id = "GA-TEST-009",
            name = "Live checker inside/clear",
            status = if (pass) GeoAwarenessTestStatus.PASS else GeoAwarenessTestStatus.FAIL,
            message = if (pass) "Live checker returned inside/clear correctly." else "Live checker inside/clear checks failed."
        )
    }

    private fun testImportSourceState(loadResult: GeoZoneLoadResult): GeoAwarenessTestResult {
        val hasRecords = loadResult.datasetRecords.isNotEmpty()
        val importedActive = loadResult.datasetRecords.any { it.sourceType.name == "IMPORTED_FILE" }
        val status = when {
            !hasRecords -> GeoAwarenessTestStatus.FAIL
            importedActive -> GeoAwarenessTestStatus.PASS
            else -> GeoAwarenessTestStatus.WARNING
        }
        return GeoAwarenessTestResult(
            id = "GA-TEST-010",
            name = "Import/source state",
            status = status,
            message = when (status) {
                GeoAwarenessTestStatus.FAIL -> "No dataset records were reported."
                GeoAwarenessTestStatus.WARNING -> "Only bundled dummy dataset is active."
                else -> "Imported dataset records are active."
            },
            details = mapOf("datasetRecordCount" to loadResult.datasetRecords.size.toString())
        )
    }

    private fun testEventLoggerWriteRead(): GeoAwarenessTestResult {
        val logger = eventLogger ?: return GeoAwarenessTestResult(
            id = "GA-TEST-011",
            name = "Event logger write/read",
            status = GeoAwarenessTestStatus.SKIPPED,
            message = "Event logger unavailable."
        )
        logger.logSimple(
            type = GeoAwarenessEventType.GEO_TEST_RUN,
            severity = "INFO",
            message = "Geo-awareness test runner executed",
            details = mapOf("phase" to "write-read-check")
        )
        val hasEvents = logger.readEvents(maxLines = 20).isNotEmpty()
        return GeoAwarenessTestResult(
            id = "GA-TEST-011",
            name = "Event logger write/read",
            status = if (hasEvents) GeoAwarenessTestStatus.PASS else GeoAwarenessTestStatus.FAIL,
            message = if (hasEvents) "Event logger wrote and read events successfully." else "Event logger did not return any events."
        )
    }

    private fun testUploadGuardPolicy(loadResult: GeoZoneLoadResult): GeoAwarenessTestResult {
        val checker = GeoAwarenessChecker()
        val prohibitedZones = if (loadResult.zones.any { it.id == HOSPITAL_ZONE_ID }) {
            loadResult.zones
        } else {
            listOf(syntheticProhibitedZone())
        }
        val authorizationZones = if (loadResult.zones.any { it.id == BEACH_ZONE_ID }) {
            loadResult.zones
        } else {
            listOf(syntheticAuthorizationZone())
        }
        val prohibited = checker.checkMission(
            missionPolygon = hospitalMissionPolygon(),
            surveyPath = emptyList(),
            missionAltitudeMeters = 50.0,
            zones = prohibitedZones
        )
        val authorization = checker.checkMission(
            missionPolygon = null,
            surveyPath = listOf(
                LatLon(35.36580, 24.48500),
                LatLon(35.38100, 24.56000)
            ),
            missionAltitudeMeters = 50.0,
            zones = authorizationZones
        )
        val clear = GeoAwarenessResult.clear()
        val pass = !prohibited.canUpload &&
            authorization.requiresAcknowledgement &&
            authorization.canUpload &&
            clear.canUpload &&
            !clear.requiresAcknowledgement
        return GeoAwarenessTestResult(
            id = "GA-TEST-012",
            name = "Upload guard policy simulation",
            status = if (pass) GeoAwarenessTestStatus.PASS else GeoAwarenessTestStatus.FAIL,
            message = if (pass) "Upload policy simulation matched expected behavior." else "Upload policy simulation did not match expected behavior."
        )
    }

    private fun testNearZoneWarning(): GeoAwarenessTestResult {
        val checker = LiveGeoAwarenessChecker()
        val syntheticCircleZone = GeoZone(
            id = "GA-TEST-013-CIRCLE",
            country = "GRC",
            name = "Synthetic near prohibited circle",
            type = "TEST",
            restriction = GeoZoneRestriction.PROHIBITED,
            reason = emptyList(),
            otherReasonInfo = null,
            message = "Synthetic near-zone test",
            applicability = emptyList(),
            authorities = emptyList(),
            geometries = listOf(
                GeoZoneGeometry.Circle(
                    center = LatLon(35.0, 24.0),
                    radiusMeters = 100.0,
                    lowerLimitMeters = 0.0,
                    upperLimitMeters = 120.0
                )
            ),
            colorHex = null,
            arc = null,
            isDummy = false
        )

        val nearPoint = LatLon(35.0, 24.001648)
        val nearResult = checker.findNearestZoneWithinThreshold(
            position = nearPoint,
            zones = listOf(syntheticCircleZone),
            thresholdMeters = 100.0,
            altitudeMeters = 50.0
        )
        val farResult = checker.findNearestZoneWithinThreshold(
            position = LatLon(35.0, 24.01),
            zones = listOf(syntheticCircleZone),
            thresholdMeters = 100.0,
            altitudeMeters = 50.0
        )
        val insideResult = checker.findNearestZoneWithinThreshold(
            position = LatLon(35.0, 24.0005),
            zones = listOf(syntheticCircleZone),
            thresholdMeters = 100.0,
            altitudeMeters = 50.0
        )
        val pass = nearResult != null &&
            nearResult.distanceMeters in 40.0..70.0 &&
            farResult == null &&
            insideResult == null
        return GeoAwarenessTestResult(
            id = "GA-TEST-013",
            name = "Near-zone warning",
            status = if (pass) GeoAwarenessTestStatus.PASS else GeoAwarenessTestStatus.FAIL,
            message = if (pass) {
                "Near-zone proximity warning behaved as expected."
            } else {
                "Near-zone proximity warning did not match expected behavior."
            },
            details = mapOf(
                "nearDistanceMeters" to (nearResult?.distanceMeters?.toInt()?.toString() ?: "null"),
                "farDetected" to (farResult != null).toString(),
                "insideReturnedNear" to (insideResult != null).toString()
            )
        )
    }

    private fun logRunSummary(result: GeoAwarenessTestRunResult) {
        eventLogger?.logSimple(
            type = GeoAwarenessEventType.GEO_TEST_RUN,
            severity = when (result.overallStatus) {
                GeoAwarenessTestStatus.FAIL -> "ERROR"
                GeoAwarenessTestStatus.WARNING -> "WARNING"
                else -> "INFO"
            },
            message = "Geo-awareness test run completed",
            details = mapOf(
                "passCount" to result.passCount.toString(),
                "failCount" to result.failCount.toString(),
                "warningCount" to result.warningCount.toString(),
                "skippedCount" to result.skippedCount.toString(),
                "overallStatus" to result.overallStatus.name
            )
        )
    }

    private fun syntheticProhibitedZone(): GeoZone {
        return GeoZone(
            id = HOSPITAL_ZONE_ID,
            country = "GRC",
            name = "Synthetic prohibited zone",
            type = "TEST",
            restriction = GeoZoneRestriction.PROHIBITED,
            reason = emptyList(),
            otherReasonInfo = null,
            message = "Synthetic test zone",
            applicability = emptyList(),
            authorities = emptyList(),
            geometries = listOf(
                GeoZoneGeometry.Polygon(
                    rings = listOf(hospitalMissionPolygon()),
                    lowerLimitMeters = 0.0,
                    upperLimitMeters = 120.0
                )
            ),
            colorHex = null,
            arc = null,
            isDummy = false
        )
    }

    private fun syntheticAuthorizationZone(): GeoZone {
        return GeoZone(
            id = BEACH_ZONE_ID,
            country = "GRC",
            name = "Synthetic authorization zone",
            type = "TEST",
            restriction = GeoZoneRestriction.REQ_AUTHORISATION,
            reason = emptyList(),
            otherReasonInfo = null,
            message = "Synthetic authorization zone",
            applicability = emptyList(),
            authorities = emptyList(),
            geometries = listOf(
                GeoZoneGeometry.Polygon(
                    rings = listOf(
                        listOf(
                            LatLon(35.36637, 24.48621),
                            LatLon(35.37827, 24.56393),
                            LatLon(35.38394, 24.56256),
                            LatLon(35.37309, 24.48750),
                            LatLon(35.36637, 24.48621)
                        )
                    ),
                    lowerLimitMeters = 0.0,
                    upperLimitMeters = 120.0
                )
            ),
            colorHex = null,
            arc = null,
            isDummy = false
        )
    }

    private fun hospitalMissionPolygon(): List<LatLon> {
        return listOf(
            LatLon(35.36460, 24.47040),
            LatLon(35.36460, 24.47250),
            LatLon(35.36570, 24.47250),
            LatLon(35.36570, 24.47040),
            LatLon(35.36460, 24.47040)
        )
    }

    private fun clearMissionPolygon(): List<LatLon> {
        return listOf(
            LatLon(35.35000, 24.45000),
            LatLon(35.35000, 24.45200),
            LatLon(35.35200, 24.45200),
            LatLon(35.35200, 24.45000),
            LatLon(35.35000, 24.45000)
        )
    }

    companion object {
        private const val HOSPITAL_ZONE_ID = "GR-RTH-DUMMY-007"
        private const val BEACH_ZONE_ID = "GR-RTH-DUMMY-006"
    }
}
