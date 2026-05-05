package com.example.droneservicesapp.data.geoawareness.logging

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class GeoAwarenessEventLogger(private val context: Context) {

    fun log(event: GeoAwarenessEvent) {
        synchronized(FILE_LOCK) {
            try {
                val logFile = getLogFile()
                ensureParentDirs(logFile)
                rotateIfNeeded(logFile)
                logFile.appendText(toJson(event).toString() + "\n")
            } catch (error: Exception) {
                Log.e(TAG, "Failed to append geo-awareness event", error)
            }
        }
    }

    fun logSimple(
        type: GeoAwarenessEventType,
        severity: String = "INFO",
        message: String,
        datasetTitle: String? = null,
        datasetVersion: String? = null,
        healthState: String? = null,
        zoneIds: List<String> = emptyList(),
        zoneNames: List<String> = emptyList(),
        restriction: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        altitudeMeters: Double? = null,
        details: Map<String, String> = emptyMap()
    ) {
        log(
            GeoAwarenessEvent(
                id = UUID.randomUUID().toString(),
                timestampMillis = System.currentTimeMillis(),
                type = type,
                severity = severity,
                message = message,
                datasetTitle = datasetTitle,
                datasetVersion = datasetVersion,
                healthState = healthState,
                zoneIds = zoneIds,
                zoneNames = zoneNames,
                restriction = restriction,
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altitudeMeters,
                details = details
            )
        )
    }

    fun readEvents(maxLines: Int = 500): List<GeoAwarenessEvent> {
        val events = mutableListOf<GeoAwarenessEvent>()
        val files = listOf(getRotatedLogFile(), getLogFile())
        for (file in files) {
            if (!file.exists()) continue
            try {
                file.useLines { lines ->
                    lines.forEach { line ->
                        if (line.isBlank()) return@forEach
                        try {
                            val event = fromJson(JSONObject(line))
                            if (event != null) {
                                events += event
                            }
                        } catch (error: Exception) {
                            Log.w(TAG, "Skipping malformed geo-awareness log line", error)
                        }
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to read geo-awareness events", error)
            }
        }
        return if (events.size <= maxLines) {
            events
        } else {
            events.takeLast(maxLines)
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
        synchronized(FILE_LOCK) {
            val exportFile = getExportFile()
            try {
                val root = JSONObject().apply {
                    put("exportedAtMillis", System.currentTimeMillis())
                    put("appPackage", context.packageName)
                    put("format", "geo-awareness-events-v1")
                    put("events", JSONArray().apply {
                        readEvents(maxLines = Int.MAX_VALUE).forEach { put(toJson(it)) }
                    })
                }
                exportFile.writeText(root.toString(2))
                logSimple(
                    type = GeoAwarenessEventType.LOGS_EXPORTED,
                    severity = "INFO",
                    message = "Geo-awareness logs exported",
                    details = mapOf("exportFile" to exportFile.absolutePath)
                )
            } catch (error: Exception) {
                Log.e(TAG, "Failed to export geo-awareness logs", error)
                throw error
            }
            return exportFile
        }
    }

    fun clearLogs() {
        synchronized(FILE_LOCK) {
            try {
                getLogFile().delete()
                getRotatedLogFile().delete()
                logSimple(
                    type = GeoAwarenessEventType.LOGS_CLEARED,
                    severity = "INFO",
                    message = "Geo-awareness logs cleared"
                )
            } catch (error: Exception) {
                Log.e(TAG, "Failed to clear geo-awareness logs", error)
            }
        }
    }

    private fun rotateIfNeeded(logFile: File) {
        if (logFile.exists() && logFile.length() >= MAX_LOG_BYTES) {
            val rotated = getRotatedLogFile()
            if (rotated.exists()) {
                rotated.delete()
            }
            logFile.renameTo(rotated)
        }
    }

    private fun getRotatedLogFile(): File {
        return File(File(context.filesDir, LOG_DIR), ROTATED_LOG_FILE_NAME)
    }

    private fun ensureParentDirs(file: File) {
        file.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }
    }

    private fun toJson(event: GeoAwarenessEvent): JSONObject {
        return JSONObject().apply {
            put("id", event.id)
            put("timestampMillis", event.timestampMillis)
            put("type", event.type.name)
            put("severity", event.severity)
            put("message", event.message)
            put("datasetTitle", event.datasetTitle)
            put("datasetVersion", event.datasetVersion)
            put("healthState", event.healthState)
            put("zoneIds", JSONArray(event.zoneIds))
            put("zoneNames", JSONArray(event.zoneNames))
            put("restriction", event.restriction)
            put("latitude", event.latitude)
            put("longitude", event.longitude)
            put("altitudeMeters", event.altitudeMeters)
            put("details", JSONObject(event.details))
        }
    }

    private fun fromJson(obj: JSONObject): GeoAwarenessEvent? {
        val typeName = obj.optString("type").takeIf { it.isNotBlank() } ?: return null
        val type = runCatching { GeoAwarenessEventType.valueOf(typeName) }.getOrNull() ?: return null
        return GeoAwarenessEvent(
            id = obj.optString("id"),
            timestampMillis = obj.optLong("timestampMillis"),
            type = type,
            severity = obj.optString("severity"),
            message = obj.optString("message"),
            datasetTitle = obj.optString("datasetTitle").takeIf { it.isNotBlank() },
            datasetVersion = obj.optString("datasetVersion").takeIf { it.isNotBlank() },
            healthState = obj.optString("healthState").takeIf { it.isNotBlank() },
            zoneIds = jsonArrayToStrings(obj.optJSONArray("zoneIds")),
            zoneNames = jsonArrayToStrings(obj.optJSONArray("zoneNames")),
            restriction = obj.optString("restriction").takeIf { it.isNotBlank() },
            latitude = obj.optDoubleOrNull("latitude"),
            longitude = obj.optDoubleOrNull("longitude"),
            altitudeMeters = obj.optDoubleOrNull("altitudeMeters"),
            details = jsonObjectToMap(obj.optJSONObject("details"))
        )
    }

    private fun jsonArrayToStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    private fun jsonObjectToMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        return buildMap {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, obj.optString(key))
            }
        }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key)
    }

    companion object {
        private const val TAG = "GeoEventLogger"
        private const val LOG_DIR = "geo_awareness/logs"
        private const val EXPORT_DIR = "geo_awareness_exports"
        private const val LOG_FILE_NAME = "geo_awareness_events.jsonl"
        private const val ROTATED_LOG_FILE_NAME = "geo_awareness_events.1.jsonl"
        private const val MAX_LOG_BYTES = 2 * 1024 * 1024L
        private val FILE_LOCK = Any()
    }
}
