package com.example.droneservicesapp.data.geoawareness.evidence

import android.content.Context
import com.example.droneservicesapp.BuildConfig
import com.example.droneservicesapp.data.geoawareness.GeoZoneRepository
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEvent
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.data.geoawareness.verification.GeoAwarenessVerificationStatusStore
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthEvaluator
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.testing.GeoAwarenessTestRunResult
import com.example.droneservicesapp.domain.geoawareness.verification.GeoAwarenessVerificationChecklist
import com.example.droneservicesapp.domain.geoawareness.verification.GeoAwarenessVerificationStatus
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GeoAwarenessEvidencePackageExporter(
    private val context: Context,
    private val eventLogger: GeoAwarenessEventLogger,
    private val repository: GeoZoneRepository,
    private val verificationStatusStore: GeoAwarenessVerificationStatusStore? = null,
    private val latestDiagnosticsResultProvider: (() -> GeoAwarenessTestRunResult?)? = null
) {

    fun exportEvidencePackage(): File {
        val nowMillis = System.currentTimeMillis()
        val exportDir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        val zipFile = File(exportDir, "evidence_package_$nowMillis.zip")

        val currentLoad = runCatching { repository.loadCurrentDataset() }.getOrNull()
        val health = GeoAwarenessHealthEvaluator.evaluate(
            datasetInfo = currentLoad?.datasetInfo,
            zones = currentLoad?.zones.orEmpty(),
            datasetRecords = currentLoad?.datasetRecords.orEmpty(),
            validationResult = currentLoad?.validationResult,
            loadError = if (currentLoad == null) IllegalStateException("Current dataset unavailable during evidence export") else null
        )
        val diagnostics = latestDiagnosticsResultProvider?.invoke()
        val allEvents = eventLogger.readEvents(maxLines = Int.MAX_VALUE)
        val flightEvents = allEvents.filter { it.type in IMPORTANT_FLIGHT_EVENTS }
        val checklistStatuses = verificationStatusStore?.getAllStatuses().orEmpty()

        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            writeEntry(zip, "summary_report.txt", buildSummaryReport(nowMillis, currentLoad?.datasetRecords.orEmpty(), currentLoad?.validationResult?.warningCount ?: 0, health, diagnostics, checklistStatuses))
            writeEntry(zip, "geo_awareness_events.json", buildEventsJson(allEvents))
            writeEntry(zip, "flight_event_log.json", buildEventsJson(flightEvents))
            writeEntry(zip, "flight_event_log.txt", buildFlightEventLogText(flightEvents))
            writeEntry(zip, "dataset_status.json", buildDatasetStatusJson(currentLoad?.datasetRecords.orEmpty(), currentLoad?.datasetInfo))
            writeEntry(zip, "validation_summary.json", buildValidationSummaryJson(currentLoad?.validationResult))
            writeEntry(zip, "health_summary.json", buildHealthSummaryJson(health))
            writeEntry(zip, "verification_checklist_results.json", buildVerificationJson(checklistStatuses))
            writeEntry(zip, "diagnostics_test_results.json", buildDiagnosticsJson(diagnostics))
            writeEntry(zip, "known_limitations.txt", buildKnownLimitations(currentLoad?.datasetRecords.orEmpty(), currentLoad?.validationResult?.warningCount ?: 0))
            writeEntry(zip, "loaded_datasets.json", buildLoadedDatasetsJson(currentLoad?.datasetRecords.orEmpty()))
        }

        return zipFile
    }

    private fun buildSummaryReport(
        nowMillis: Long,
        datasetRecords: List<GeoZoneDatasetRecord>,
        warningCount: Int,
        health: GeoAwarenessHealth,
        diagnostics: GeoAwarenessTestRunResult?,
        checklistStatuses: Map<String, GeoAwarenessVerificationStatus>
    ): String {
        val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val passCount = checklistStatuses.values.count { it.name == "PASS" }
        val failCount = checklistStatuses.values.count { it.name == "FAIL" }
        val blockedCount = checklistStatuses.values.count { it.name == "BLOCKED" }
        val notRunCount = checklistStatuses.values.count { it.name == "NOT_RUN" }
        val summaryDataset = datasetRecords.firstOrNull()?.datasetInfo
        return buildString {
            appendLine("Geo-awareness evidence package")
            appendLine("Export timestamp UTC: ${isoUtc(nowMillis)}")
            appendLine("Export timestamp local: ${Date(nowMillis)}")
            appendLine("Device time zone: ${TimeZone.getDefault().id}")
            appendLine("App package: ${context.packageName}")
            appendLine("App versionName: ${packageInfo?.versionName ?: BuildConfig.VERSION_NAME}")
            appendLine("App versionCode: ${packageInfo?.longVersionCode ?: BuildConfig.VERSION_CODE}")
            appendLine("Build type: ${BuildConfig.BUILD_TYPE}")
            appendLine("Debug build: ${BuildConfig.DEBUG}")
            appendLine()
            appendLine("Active dataset summary")
            appendLine("- title: ${summaryDataset?.title ?: "Unavailable"}")
            appendLine("- source: ${summaryDataset?.source ?: "Unavailable"}")
            appendLine("- version: ${summaryDataset?.version ?: "N/A"}")
            appendLine("- loaded dataset count: ${datasetRecords.size}")
            appendLine("- total zones: ${summaryDataset?.zoneCount ?: datasetRecords.sumOf { it.zoneCount }}")
            appendLine("- validation warnings: $warningCount")
            appendLine("- health state: ${health.state}")
            appendLine()
            appendLine("Verification checklist summary")
            appendLine("- total: ${GeoAwarenessVerificationChecklist.cases.size}")
            appendLine("- pass: $passCount")
            appendLine("- fail: $failCount")
            appendLine("- blocked: $blockedCount")
            appendLine("- not run: $notRunCount")
            appendLine()
            appendLine("Diagnostics summary")
            if (diagnostics == null) {
                appendLine("- No diagnostics result available in this session.")
            } else {
                appendLine("- overall: ${diagnostics.overallStatus}")
                appendLine("- pass: ${diagnostics.passCount}")
                appendLine("- fail: ${diagnostics.failCount}")
                appendLine("- warning: ${diagnostics.warningCount}")
                appendLine("- skipped: ${diagnostics.skippedCount}")
            }
            appendLine()
            appendLine("Key limitations")
            append(buildKnownLimitations(datasetRecords, warningCount))
            appendLine()
            appendLine()
            append("This package is generated by the app for internal verification/evidence review. It does not by itself certify operational compliance.")
        }
    }

    private fun buildEventsJson(events: List<GeoAwarenessEvent>): String {
        return JSONObject().apply {
            put("exportedAtMillis", System.currentTimeMillis())
            put("appPackage", context.packageName)
            put("format", "geo-awareness-events-v2")
            put("events", JSONArray().apply {
                events.forEach { put(eventToJson(it)) }
            })
        }.toString(2)
    }

    private fun buildFlightEventLogText(events: List<GeoAwarenessEvent>): String {
        return buildString {
            events.sortedByDescending { it.timestampMillis }.forEach { event ->
                append(event.timestampIsoUtc)
                append(" | ")
                append(event.category ?: "UNKNOWN")
                append(" | ")
                append(event.type.name)
                append(" | ")
                append(event.message)
                if (event.zoneNames.isNotEmpty()) {
                    append(" | zones=")
                    append(event.zoneNames.joinToString(","))
                }
                event.restriction?.let {
                    append(" | restriction=")
                    append(it)
                }
                if (event.latitude != null && event.longitude != null) {
                    append(" | lat=")
                    append(event.latitude)
                    append(" lon=")
                    append(event.longitude)
                }
                event.altitudeMeters?.let {
                    append(" alt=")
                    append(it)
                }
                if (event.details.isNotEmpty()) {
                    append(" | details=")
                    append(event.details.entries.joinToString(";") { "${it.key}=${it.value}" })
                }
                appendLine()
            }
        }
    }

    private fun buildDatasetStatusJson(records: List<GeoZoneDatasetRecord>, datasetInfo: GeoZoneDatasetInfo?): String {
        return JSONObject().apply {
            put("source", datasetInfo?.source)
            put("title", datasetInfo?.title)
            put("version", datasetInfo?.version)
            put("country", datasetInfo?.country)
            put("validNonDummyDataset", datasetInfo?.isDummy == false && (datasetInfo.zoneCount > 0))
            put("dummy", datasetInfo?.isDummy)
            put("loadedAtMillis", datasetInfo?.loadedAtMillis)
            put("zoneCount", datasetInfo?.zoneCount)
            put("circleGeometryCount", datasetInfo?.circleGeometryCount)
            put("polygonGeometryCount", datasetInfo?.polygonGeometryCount)
            put("datasetRecords", JSONArray().apply {
                records.forEach { record ->
                    put(JSONObject().apply {
                        put("datasetId", record.datasetId)
                        put("displayName", record.displayName)
                        put("storageFileName", record.storageFileName)
                        put("sourceType", record.sourceType.name)
                        put("zoneCount", record.zoneCount)
                        put("importedAtMillis", record.importedAtMillis)
                        put("updatedAtMillis", record.updatedAtMillis)
                        put("isStale", record.isStale)
                        put("ageDescription", record.ageDescription)
                    })
                }
            })
        }.toString(2)
    }

    private fun buildValidationSummaryJson(validationResult: GeoZoneValidationResult?): String {
        val result = validationResult ?: GeoZoneValidationResult.ok()
        return JSONObject().apply {
            put("isValid", result.isValid)
            put("errorCount", result.errorCount)
            put("warningCount", result.warningCount)
            put("infoCount", result.infoCount)
            put("issues", JSONArray().apply {
                result.issues.take(200).forEach { issue ->
                    put(JSONObject().apply {
                        put("severity", issue.severity.name)
                        put("code", issue.code)
                        put("message", issue.message)
                        put("zoneId", issue.zoneId)
                        put("field", issue.field)
                    })
                }
            })
        }.toString(2)
    }

    private fun buildHealthSummaryJson(health: GeoAwarenessHealth): String {
        return JSONObject().apply {
            put("state", health.state.name)
            put("message", health.message)
            put("canPlan", health.canPlan)
            put("canUploadWithoutAcknowledgement", health.canUploadWithoutAcknowledgement)
            put("requiresAcknowledgementBeforeUpload", health.requiresAcknowledgementBeforeUpload)
            put("checkedAtMillis", health.checkedAtMillis)
        }.toString(2)
    }

    private fun buildVerificationJson(statuses: Map<String, GeoAwarenessVerificationStatus>): String {
        if (verificationStatusStore == null) {
            return JSONObject().apply {
                put("message", "Verification checklist store unavailable.")
            }.toString(2)
        }
        return JSONObject().apply {
            put("cases", JSONArray().apply {
                GeoAwarenessVerificationChecklist.cases.forEach { verificationCase ->
                    put(JSONObject().apply {
                        put("id", verificationCase.id)
                        put("title", verificationCase.title)
                        put("category", verificationCase.category)
                        put("purpose", verificationCase.purpose)
                        put("preconditions", JSONArray(verificationCase.preconditions))
                        put("steps", JSONArray(verificationCase.steps))
                        put("expectedResult", verificationCase.expectedResult)
                        put("evidenceToCapture", JSONArray(verificationCase.evidenceToCapture))
                        put("status", statuses[verificationCase.id]?.name ?: "NOT_RUN")
                    })
                }
            })
        }.toString(2)
    }

    private fun buildDiagnosticsJson(diagnostics: GeoAwarenessTestRunResult?): String {
        if (diagnostics == null) {
            return JSONObject().apply {
                put("message", "No diagnostics result available in this session.")
            }.toString(2)
        }
        return JSONObject().apply {
            put("runAtMillis", diagnostics.runAtMillis)
            put("overallStatus", diagnostics.overallStatus.name)
            put("passCount", diagnostics.passCount)
            put("failCount", diagnostics.failCount)
            put("warningCount", diagnostics.warningCount)
            put("skippedCount", diagnostics.skippedCount)
            put("results", JSONArray().apply {
                diagnostics.results.forEach { result ->
                    put(JSONObject().apply {
                        put("id", result.id)
                        put("name", result.name)
                        put("status", result.status.name)
                        put("message", result.message)
                        put("details", JSONObject(result.details))
                    })
                }
            })
        }.toString(2)
    }

    private fun buildKnownLimitations(records: List<GeoZoneDatasetRecord>, warningCount: Int): String {
        val datasetInfo = records.firstOrNull()?.datasetInfo
        return buildString {
            if (datasetInfo?.isDummy == true) appendLine("- Dataset is development/test dummy data.")
            if (warningCount > 0) appendLine("- Validation warnings are present ($warningCount).")
            appendLine("- Automatic authoritative DAGR/API retrieval is not implemented; operators must import or update official data.")
            appendLine("- AGL checks use mission height or drone relative altitude. Terrain-derived true AGL is not implemented.")
            appendLine("- AMSL checks use telemetry AMSL altitude when available during live monitoring.")
            appendLine("- Real drone testing may still be required for operational validation.")
            appendLine("- Takeoff/landing event detection is telemetry-derived best effort.")
        }
    }

    private fun buildLoadedDatasetsJson(records: List<GeoZoneDatasetRecord>): String {
        return JSONArray().apply {
            records.forEach { record ->
                put(JSONObject().apply {
                    put("datasetId", record.datasetId)
                    put("displayName", record.displayName)
                    put("storageFileName", record.storageFileName)
                    put("sourceType", record.sourceType.name)
                    put("title", record.datasetInfo.title)
                    put("version", record.datasetInfo.version)
                    put("country", record.datasetInfo.country)
                    put("zoneCount", record.zoneCount)
                    put("isStale", record.isStale)
                    put("updatedAtMillis", record.updatedAtMillis)
                })
            }
        }.toString(2)
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun eventToJson(event: GeoAwarenessEvent): JSONObject {
        return JSONObject().apply {
            put("id", event.id)
            put("timestampMillis", event.timestampMillis)
            put("timestampIsoUtc", event.timestampIsoUtc)
            put("type", event.type.name)
            put("severity", event.severity)
            put("message", event.message)
            put("category", event.category)
            put("operatorAction", event.operatorAction)
            put("flightState", event.flightState)
            put("connectionState", event.connectionState)
            put("missionId", event.missionId)
            put("deviceTimeZone", event.deviceTimeZone)
            put("datasetTitle", event.datasetTitle)
            put("datasetVersion", event.datasetVersion)
            put("healthState", event.healthState)
            put("zoneIds", JSONArray(event.zoneIds))
            put("zoneNames", JSONArray(event.zoneNames))
            put("restriction", event.restriction)
            put("latitude", event.latitude)
            put("longitude", event.longitude)
            put("altitudeMeters", event.altitudeMeters)
            put("speedMetersPerSecond", event.speedMetersPerSecond)
            put("headingDegrees", event.headingDegrees)
            put("batteryPercent", event.batteryPercent)
            put("flightMode", event.flightMode)
            put("details", JSONObject(event.details))
        }
    }

    private fun isoUtc(timestampMillis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestampMillis))
    }

    companion object {
        private const val EXPORT_DIR = "geo_awareness_exports"
        val IMPORTANT_FLIGHT_EVENTS = setOf(
            GeoAwarenessEventType.DRONE_CONNECTED,
            GeoAwarenessEventType.DRONE_DISCONNECTED,
            GeoAwarenessEventType.DRONE_ARMED,
            GeoAwarenessEventType.DRONE_DISARMED,
            GeoAwarenessEventType.TAKEOFF_DETECTED,
            GeoAwarenessEventType.LANDING_DETECTED,
            GeoAwarenessEventType.FLIGHT_MODE_CHANGED,
            GeoAwarenessEventType.MISSION_UPLOAD_STARTED,
            GeoAwarenessEventType.MISSION_UPLOAD_SUCCEEDED,
            GeoAwarenessEventType.MISSION_UPLOAD_FAILED,
            GeoAwarenessEventType.GEO_ZONE_ENTERED,
            GeoAwarenessEventType.GEO_ZONE_EXITED,
            GeoAwarenessEventType.UPLOAD_BLOCKED,
            GeoAwarenessEventType.UPLOAD_ACK_REQUIRED,
            GeoAwarenessEventType.UPLOAD_ACKNOWLEDGED,
            GeoAwarenessEventType.UPLOAD_CANCELLED,
            GeoAwarenessEventType.UPLOAD_CONTINUED_WITH_WARNING,
            GeoAwarenessEventType.UGZ_AUTHORIZATION_REQUIRED,
            GeoAwarenessEventType.UGZ_AUTHORIZATION_CONFIRMED,
            GeoAwarenessEventType.UGZ_AUTHORIZATION_RESET,
            GeoAwarenessEventType.LIVE_ZONE_ENTERED,
            GeoAwarenessEventType.LIVE_ZONE_EXITED,
            GeoAwarenessEventType.LIVE_STATUS_CHANGED,
            GeoAwarenessEventType.BATTERY_LOW,
            GeoAwarenessEventType.FAILSAFE_DETECTED,
            GeoAwarenessEventType.RTL_DETECTED
        )
    }
}
