package com.example.droneservicesapp.data.geoawareness.incident

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

class GeoIncidentEncryptedLogStore(private val context: Context) {

    fun append(event: GeoIncidentEvent) {
        synchronized(FILE_LOCK) {
            try {
                val publicKey = GeoIncidentPublicKeyProvider.getPublicKey()
                val logFile = getCurrentLogFile()
                ensureParentDirs(logFile)
                rotateIfNeeded(logFile)
                val envelope = GeoIncidentCrypto.encryptEvent(event.toJson(), publicKey, GeoIncidentPublicKeyProvider.KEY_ID)
                logFile.appendText(envelope.toJson().toString() + "\n")
            } catch (error: Exception) {
                Log.e(TAG, "Failed to append encrypted geo incident event", error)
            }
        }
    }

    fun getEncryptedLogFiles(): List<File> {
        return listOf(getRotatedLogFile(), getCurrentLogFile()).filter { it.exists() }
    }

    fun getCurrentLogFile(): File {
        return File(File(context.filesDir, INCIDENT_DIR), CURRENT_LOG_FILE)
    }

    fun clearInternalLogsForDebugOnly() {
        synchronized(FILE_LOCK) {
            runCatching { getCurrentLogFile().delete() }
            runCatching { getRotatedLogFile().delete() }
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
        return File(File(context.filesDir, INCIDENT_DIR), ROTATED_LOG_FILE)
    }

    private fun ensureParentDirs(file: File) {
        file.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }
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
        private const val CURRENT_LOG_FILE = "geo_incident_events.enc.jsonl"
        private const val ROTATED_LOG_FILE = "geo_incident_events.1.enc.jsonl"
        private const val MAX_LOG_BYTES = 2 * 1024 * 1024L
        private val FILE_LOCK = Any()
    }
}
