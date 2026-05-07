package com.example.droneservicesapp.data.geoawareness.logging

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class GeoAwarenessEventLogger(private val context: Context) {

    fun log(event: GeoAwarenessEvent) {
        synchronized(EVENT_LOCK) {
            try {
                memoryEvents.add(event)
                if (memoryEvents.size > MAX_IN_MEMORY_EVENTS) {
                    memoryEvents = memoryEvents.takeLast(MAX_IN_MEMORY_EVENTS).toMutableList()
                }
                Unit
            } catch (error: Exception) {
                Log.e(TAG, "Failed to record in-memory geo-awareness event", error)
            }
        }
    }

    fun logSimple(
        type: GeoAwarenessEventType,
        severity: String = "INFO",
        message: String,
        category: String? = null,
        operatorAction: String? = null,
        flightState: String? = null,
        connectionState: String? = null,
        missionId: String? = null,
        datasetTitle: String? = null,
        datasetVersion: String? = null,
        healthState: String? = null,
        zoneIds: List<String> = emptyList(),
        zoneNames: List<String> = emptyList(),
        restriction: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        altitudeMeters: Double? = null,
        speedMetersPerSecond: Double? = null,
        headingDegrees: Double? = null,
        batteryPercent: Int? = null,
        flightMode: String? = null,
        details: Map<String, String> = emptyMap()
    ) {
        val timestampMillis = System.currentTimeMillis()
        log(
            GeoAwarenessEvent(
                id = UUID.randomUUID().toString(),
                timestampMillis = timestampMillis,
                timestampIsoUtc = formatIsoUtc(timestampMillis),
                type = type,
                severity = severity,
                message = message,
                category = category,
                operatorAction = operatorAction,
                flightState = flightState,
                connectionState = connectionState,
                missionId = missionId,
                deviceTimeZone = TimeZone.getDefault().id,
                datasetTitle = datasetTitle,
                datasetVersion = datasetVersion,
                healthState = healthState,
                zoneIds = zoneIds,
                zoneNames = zoneNames,
                restriction = restriction,
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altitudeMeters,
                speedMetersPerSecond = speedMetersPerSecond,
                headingDegrees = headingDegrees,
                batteryPercent = batteryPercent,
                flightMode = flightMode,
                details = details
            )
        )
    }

    fun readEvents(maxLines: Int = 500): List<GeoAwarenessEvent> {
        synchronized(EVENT_LOCK) {
            return if (memoryEvents.size <= maxLines) {
                memoryEvents.toList()
            } else {
                memoryEvents.takeLast(maxLines)
            }
        }
    }

    fun getLogFile(): File {
        return File(File(context.filesDir, LOG_DIR), LOG_FILE_NAME)
    }

    fun getExportFile(): File {
        val exportDir = File(context.cacheDir, EXPORT_DIR)
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        return File(exportDir, "geo_awareness_export_${System.currentTimeMillis()}.json")
    }

    fun exportLogsToJson(): File {
        synchronized(EVENT_LOCK) {
            val exportFile = getExportFile()
            val root = JSONObject().apply {
                put("exportedAtMillis", System.currentTimeMillis())
                put("appPackage", context.packageName)
                put("format", "geo-awareness-events-ephemeral-v1")
                put("persistentStorageEnabled", false)
                put("events", JSONArray().apply {
                    memoryEvents.forEach { put(toJson(it)) }
                })
            }
            exportFile.writeText(root.toString(2))
            return exportFile
        }
    }

    fun clearLogs() {
        synchronized(EVENT_LOCK) {
            memoryEvents.clear()
        }
    }

    private fun toJson(event: GeoAwarenessEvent): JSONObject {
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

    private fun formatIsoUtc(timestampMillis: Long): String {
        return requireNotNull(ISO_UTC_FORMAT.get()).format(Date(timestampMillis))
    }

    companion object {
        private const val TAG = "GeoEventLogger"
        private const val LOG_DIR = "geo_awareness/logs"
        private const val EXPORT_DIR = "geo_awareness_exports"
        private const val LOG_FILE_NAME = "geo_awareness_events.jsonl"
        private const val MAX_IN_MEMORY_EVENTS = 200
        private val EVENT_LOCK = Any()
        private var memoryEvents = mutableListOf<GeoAwarenessEvent>()
        private val ISO_UTC_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            }
        }
    }
}
