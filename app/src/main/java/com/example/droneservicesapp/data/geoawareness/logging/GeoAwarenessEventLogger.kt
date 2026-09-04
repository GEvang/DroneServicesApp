package com.example.droneservicesapp.data.geoawareness.logging

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.droneservicesapp.data.diagnostics.DiagnosticLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class GeoAwarenessEventLogger(private val context: Context) {

    fun log(event: GeoAwarenessEvent) {
        synchronized(EVENT_LOCK) {
            try {
                memoryEvents.add(event)
                if (memoryEvents.size > MAX_IN_MEMORY_EVENTS) {
                    memoryEvents = memoryEvents.takeLast(MAX_IN_MEMORY_EVENTS).toMutableList()
                }
                appendPersistentEvent(event)
                DiagnosticLog.event(
                    module = "geo-awareness",
                    message = "geo_event",
                    severity = event.severity,
                    data = mapOf(
                        "type" to event.type.name,
                        "message" to event.message,
                        "category" to event.category,
                        "operatorAction" to event.operatorAction,
                        "flightState" to event.flightState,
                        "connectionState" to event.connectionState,
                        "datasetTitle" to event.datasetTitle,
                        "datasetVersion" to event.datasetVersion,
                        "healthState" to event.healthState,
                        "zoneIds" to event.zoneIds.joinToString(","),
                        "zoneNames" to event.zoneNames.joinToString(","),
                        "restriction" to event.restriction,
                        "latitude" to event.latitude,
                        "longitude" to event.longitude,
                        "altitudeMeters" to event.altitudeMeters,
                        "batteryPercent" to event.batteryPercent,
                        "flightMode" to event.flightMode,
                        "details" to event.details.entries.joinToString(";") { "${it.key}=${it.value}" }
                    )
                )
                cleanupExpiredPersistentLogs()
            } catch (error: Exception) {
                Log.e(TAG, "Failed to record geo-awareness event", error)
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
            val persistedEvents = runCatching {
                cleanupExpiredPersistentLogs()
                getPersistentLogFiles()
                    .flatMap(::readEventsFromFile)
                    .sortedBy { it.timestampMillis }
            }.getOrElse { error ->
                Log.e(TAG, "Failed to read persistent geo-awareness events", error)
                emptyList()
            }
            val events = persistedEvents.ifEmpty { memoryEvents.toList() }
            return events.takeLast(maxLines)
        }
    }

    fun getLogFile(): File {
        return getDailyLogFile(System.currentTimeMillis())
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
                put("format", "geo-awareness-events-retained-v1")
                put("persistentStorageEnabled", true)
                put("retentionDays", RETENTION_DAYS)
                put("events", JSONArray().apply {
                    readEvents(maxLines = Int.MAX_VALUE).forEach { put(toJson(it)) }
                })
            }
            exportFile.writeText(root.toString(2))
            return exportFile
        }
    }

    fun clearLogs() {
        synchronized(EVENT_LOCK) {
            // Retained logs must not be deleted by user-facing actions. Keep this as a
            // no-op compatibility hook for older UI code paths.
            memoryEvents.clear()
        }
    }

    fun getPersistentLogFiles(): List<File> {
        val dir = File(context.filesDir, LOG_DIR)
        if (!dir.exists()) {
            return emptyList()
        }
        return dir.listFiles { file ->
            file.isFile && file.name.startsWith(LOG_FILE_PREFIX) && file.extension.equals("jsonl", ignoreCase = true)
        }?.sortedBy { it.name }.orEmpty()
    }

    private fun appendPersistentEvent(event: GeoAwarenessEvent) {
        val logFile = getDailyLogFile(event.timestampMillis)
        logFile.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }
        logFile.appendText(encryptPersistentEvent(event).toString() + "\n", Charsets.UTF_8)
    }

    private fun readEventsFromFile(file: File): List<GeoAwarenessEvent> {
        if (!file.exists()) {
            return emptyList()
        }
        return file.readLines(Charsets.UTF_8)
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) {
                    null
                } else {
                    runCatching { readPersistentEvent(JSONObject(trimmed)) }
                        .onFailure { Log.w(TAG, "Skipping malformed geo-awareness log line in ${file.name}", it) }
                        .getOrNull()
                }
            }
    }

    private fun cleanupExpiredPersistentLogs(nowMillis: Long = System.currentTimeMillis()) {
        val cutoffMillis = nowMillis - RETENTION_MILLIS
        getPersistentLogFiles()
            .filter { file -> resolveLogDateMillis(file) < cutoffMillis }
            .forEach { file ->
                if (!file.delete()) {
                    Log.w(TAG, "Failed to delete expired geo-awareness log file: ${file.name}")
                }
            }
    }

    private fun getDailyLogFile(timestampMillis: Long): File {
        val dir = File(context.filesDir, LOG_DIR)
        val date = requireNotNull(LOG_DATE_FORMAT.get()).format(Date(timestampMillis))
        return File(dir, "$LOG_FILE_PREFIX$date.jsonl")
    }

    private fun resolveLogDateMillis(file: File): Long {
        val dateText = file.name
            .removePrefix(LOG_FILE_PREFIX)
            .removeSuffix(".jsonl")
        return runCatching {
            requireNotNull(LOG_DATE_FORMAT.get()).parse(dateText)?.time ?: file.lastModified()
        }.getOrDefault(file.lastModified())
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

    private fun fromJson(root: JSONObject): GeoAwarenessEvent {
        return GeoAwarenessEvent(
            id = root.optString("id"),
            timestampMillis = root.optLong("timestampMillis"),
            timestampIsoUtc = root.optString("timestampIsoUtc"),
            type = runCatching { GeoAwarenessEventType.valueOf(root.optString("type")) }
                .getOrDefault(GeoAwarenessEventType.GEO_TEST_RUN),
            severity = root.optString("severity"),
            message = root.optString("message"),
            category = root.optString("category").takeIf { it.isNotBlank() && it != "null" },
            operatorAction = root.optString("operatorAction").takeIf { it.isNotBlank() && it != "null" },
            flightState = root.optString("flightState").takeIf { it.isNotBlank() && it != "null" },
            connectionState = root.optString("connectionState").takeIf { it.isNotBlank() && it != "null" },
            missionId = root.optString("missionId").takeIf { it.isNotBlank() && it != "null" },
            deviceTimeZone = root.optString("deviceTimeZone").takeIf { it.isNotBlank() && it != "null" },
            datasetTitle = root.optString("datasetTitle").takeIf { it.isNotBlank() && it != "null" },
            datasetVersion = root.optString("datasetVersion").takeIf { it.isNotBlank() && it != "null" },
            healthState = root.optString("healthState").takeIf { it.isNotBlank() && it != "null" },
            zoneIds = root.optJSONArray("zoneIds").toStringList(),
            zoneNames = root.optJSONArray("zoneNames").toStringList(),
            restriction = root.optString("restriction").takeIf { it.isNotBlank() && it != "null" },
            latitude = root.optNullableDouble("latitude"),
            longitude = root.optNullableDouble("longitude"),
            altitudeMeters = root.optNullableDouble("altitudeMeters"),
            speedMetersPerSecond = root.optNullableDouble("speedMetersPerSecond"),
            headingDegrees = root.optNullableDouble("headingDegrees"),
            batteryPercent = root.optNullableInt("batteryPercent"),
            flightMode = root.optString("flightMode").takeIf { it.isNotBlank() && it != "null" },
            details = root.optJSONObject("details").toStringMap()
        )
    }

    private fun encryptPersistentEvent(event: GeoAwarenessEvent): JSONObject {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(toJson(event).toString().toByteArray(Charsets.UTF_8))
        return JSONObject().apply {
            put("format", PERSISTENT_EVENT_FORMAT)
            put("algorithm", "AES-256-GCM")
            put("timestampMillis", event.timestampMillis)
            put("ivBase64", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            put("ciphertextBase64", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        }
    }

    private fun readPersistentEvent(root: JSONObject): GeoAwarenessEvent {
        if (root.optString("format") != PERSISTENT_EVENT_FORMAT) {
            // Backward compatible reader for any retained plain JSONL entries created
            // before encrypted-at-rest retention was introduced.
            return fromJson(root)
        }
        val iv = Base64.decode(root.getString("ivBase64"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(root.getString("ciphertextBase64"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        return fromJson(JSONObject(plaintext))
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun formatIsoUtc(timestampMillis: Long): String {
        return requireNotNull(ISO_UTC_FORMAT.get()).format(Date(timestampMillis))
    }

    companion object {
        private const val TAG = "GeoEventLogger"
        private const val LOG_DIR = "geo_awareness/logs"
        private const val EXPORT_DIR = "geo_awareness_exports"
        private const val LOG_FILE_PREFIX = "geo_awareness_events_"
        private const val RETENTION_DAYS = 90L
        private const val RETENTION_MILLIS = RETENTION_DAYS * 24L * 60L * 60L * 1000L
        private const val MAX_IN_MEMORY_EVENTS = 200
        private const val PERSISTENT_EVENT_FORMAT = "geo-awareness-event-encrypted-v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "aigaio_geo_awareness_event_log_key_v1"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private val EVENT_LOCK = Any()
        private var memoryEvents = mutableListOf<GeoAwarenessEvent>()
        private val ISO_UTC_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            }
        }
        private val LOG_DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                }
            }
        }
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
}

private fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    return keys().asSequence().associateWith { key -> optString(key) }
}

private fun JSONObject.optNullableDouble(name: String): Double? {
    return if (has(name) && !isNull(name)) optDouble(name) else null
}

private fun JSONObject.optNullableInt(name: String): Int? {
    return if (has(name) && !isNull(name)) optInt(name) else null
}
