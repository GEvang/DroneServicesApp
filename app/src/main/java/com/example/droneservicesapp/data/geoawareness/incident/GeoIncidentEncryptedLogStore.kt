package com.example.droneservicesapp.data.geoawareness.incident

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GeoIncidentEncryptedLogStore(private val context: Context) {

    fun append(event: GeoIncidentEvent) {
        synchronized(FILE_LOCK) {
            try {
                val publicKey = GeoIncidentPublicKeyProvider.getPublicKey()
                val logFile = getDailyLogFile(event.timestampMillis)
                ensureParentDirs(logFile)
                cleanupExpiredLogs()
                val envelope = GeoIncidentCrypto.encryptEvent(event.toJson(), publicKey, GeoIncidentPublicKeyProvider.KEY_ID)
                logFile.appendText(envelope.toJson().toString() + "\n")
            } catch (error: Exception) {
                Log.e(TAG, "Failed to append encrypted geo incident event", error)
            }
        }
    }

    fun getEncryptedLogFiles(): List<File> {
        synchronized(FILE_LOCK) {
            cleanupExpiredLogs()
            val dir = File(context.filesDir, INCIDENT_DIR)
            if (!dir.exists()) {
                return emptyList()
            }
            return dir.listFiles { file ->
                file.isFile &&
                    file.name.startsWith(LOG_FILE_PREFIX) &&
                    file.extension.equals("jsonl", ignoreCase = true)
            }?.sortedBy { it.name }.orEmpty()
        }
    }

    fun getCurrentLogFile(): File {
        return getDailyLogFile(System.currentTimeMillis())
    }

    fun clearInternalLogsForDebugOnly() {
        synchronized(FILE_LOCK) {
            Log.w(TAG, "Retained encrypted geo incident logs cannot be deleted from the app.")
        }
    }

    private fun getDailyLogFile(timestampMillis: Long): File {
        val date = requireNotNull(LOG_DATE_FORMAT.get()).format(Date(timestampMillis))
        return File(File(context.filesDir, INCIDENT_DIR), "$LOG_FILE_PREFIX$date.enc.jsonl")
    }

    private fun ensureParentDirs(file: File) {
        file.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }
    }

    private fun cleanupExpiredLogs(nowMillis: Long = System.currentTimeMillis()) {
        val cutoffMillis = nowMillis - RETENTION_MILLIS
        val dir = File(context.filesDir, INCIDENT_DIR)
        if (!dir.exists()) {
            return
        }
        dir.listFiles { file ->
            file.isFile &&
                file.name.startsWith(LOG_FILE_PREFIX) &&
                file.extension.equals("jsonl", ignoreCase = true)
        }.orEmpty()
            .filter { file -> resolveLogDateMillis(file) < cutoffMillis }
            .forEach { file ->
                if (!file.delete()) {
                    Log.w(TAG, "Failed to delete expired encrypted geo incident log: ${file.name}")
                }
            }
    }

    private fun resolveLogDateMillis(file: File): Long {
        val dateText = file.name
            .removePrefix(LOG_FILE_PREFIX)
            .removeSuffix(".enc.jsonl")
        return runCatching {
            requireNotNull(LOG_DATE_FORMAT.get()).parse(dateText)?.time ?: file.lastModified()
        }.getOrDefault(file.lastModified())
    }

    private fun GeoIncidentEvent.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("timestampMillis", timestampMillis)
            put("timestampIsoUtc", timestampIsoUtc)
            put("type", type.name)
            put("zoneIds", org.json.JSONArray(zoneIds))
            put("zoneNames", org.json.JSONArray(zoneNames))
            put("highestRestriction", highestRestriction)
            put("restrictions", org.json.JSONArray(restrictions))
            put("latitude", latitude)
            put("longitude", longitude)
            put("altitudeMeters", altitudeMeters)
            put("datasetTitle", datasetTitle)
            put("datasetVersion", datasetVersion)
            put("healthState", healthState)
            put("source", source)
            put("message", message)
            put("details", JSONObject(details))
        }
    }

    private fun GeoIncidentEncryptedEnvelope.toJson(): JSONObject {
        return JSONObject().apply {
            put("format", format)
            put("keyId", keyId)
            put("algorithm", algorithm)
            put("timestampMillis", timestampMillis)
            put("encryptedKeyBase64", encryptedKeyBase64)
            put("ivBase64", ivBase64)
            put("ciphertextBase64", ciphertextBase64)
        }
    }

    companion object {
        private const val TAG = "GeoIncidentLog"
        private const val INCIDENT_DIR = "geo_awareness/incidents"
        private const val LOG_FILE_PREFIX = "geo_incident_events_"
        private const val RETENTION_DAYS = 90L
        private const val RETENTION_MILLIS = RETENTION_DAYS * 24L * 60L * 60L * 1000L
        private val FILE_LOCK = Any()
        private val LOG_DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                }
            }
        }
    }
}
