package com.example.droneservicesapp.data.diagnostics

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.droneservicesapp.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Small, failure-safe audit trail for field diagnostics. It intentionally records state changes
 * and summaries rather than high-rate telemetry, and never stores RTK credentials.
 */
object DiagnosticLog {
    private const val TAG = "DiagnosticLog"
    private const val DIRECTORY = "flight_diagnostics"
    private const val SESSIONS_DIRECTORY = "sessions"
    // Daily files preserve separate flights while the size ceiling protects tablet storage.
    private const val MAX_FILE_BYTES = 1024 * 1024L
    private const val MAX_ARCHIVES_PER_DAY = 2
    private const val MAX_TOTAL_BYTES = 48 * 1024 * 1024L
    private const val RETENTION_DAYS = 60L
    private val writeLock = Any()
    @Volatile private var appContext: Context? = null
    @Volatile private var sessionId: String? = null

    @JvmStatic
    fun initialize(context: Context) {
        appContext = context.applicationContext
        synchronized(writeLock) {
            createSession(context.applicationContext, System.currentTimeMillis())
        }
        event("app", "application_started", data = mapOf(
            "version" to BuildConfig.VERSION_NAME,
            "sdk" to Build.VERSION.SDK_INT,
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}"
        ))
    }

    /** Persist a concise fatal-error record before Android terminates the process. */
    @JvmStatic
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            event(
                module = "app",
                message = "fatal_uncaught_exception",
                severity = "FATAL",
                data = mapOf(
                    "thread" to thread.name,
                    "error" to error.javaClass.simpleName,
                    "message" to (error.message ?: "no message")
                )
            )
            previous?.uncaughtException(thread, error) ?: error.printStackTrace()
        }
    }

    @JvmStatic
    @JvmOverloads
    fun event(
        module: String,
        message: String,
        severity: String = "INFO",
        data: Map<String, Any?> = emptyMap()
    ) {
        val context = appContext ?: return
        runCatching {
            val now = System.currentTimeMillis()
            val safeModule = module.lowercase(Locale.US).replace(Regex("[^a-z0-9_-]"), "_")
                .ifBlank { "app" }
            val line = JSONObject().apply {
                put("timestampMillis", now)
                put("timestampUtc", isoUtc(now))
                put("timestampLocal", localTimestamp(now))
                put("severity", severity)
                put("event", message)
                put("data", JSONObject(data.mapValues { (_, value) -> sanitize(value) }))
            }.toString()
            synchronized(writeLock) {
                val session = createSession(context, now)
                val directory = if (safeModule == "geo-awareness") {
                    File(session, "geo-awareness")
                } else {
                    File(session, "diagnostics")
                }.apply { mkdirs() }
                val logFile = File(directory, "$safeModule-${logDate(now)}.jsonl")
                if (logFile.length() >= MAX_FILE_BYTES) rotate(logFile)
                logFile.appendText(line + "\n", Charsets.UTF_8)
                session.setLastModified(now)
                cleanup(File(context.filesDir, DIRECTORY))
            }
        }.onFailure { Log.w(TAG, "Diagnostic event was not persisted", it) }
    }

    /** Creates a portable ZIP in Downloads/DroneServicesApp/Logs for USB retrieval. */
    @JvmStatic
    fun export(context: Context): ExportResult = runCatching {
        val name = "DroneServicesApp-diagnostics-${fileTimestamp(System.currentTimeMillis())}.zip"
        val target = openDownloadOutput(context.applicationContext, name)
        try {
            ZipOutputStream(target.stream.buffered()).use { zip ->
                writeText(zip, "README.txt", "Drone Services App field diagnostics\n" +
                    "Generated: ${isoUtc(System.currentTimeMillis())}\n" +
                    "Each sessions/launch-YYYY-MM-DD_HH-mm-ss.SSS folder represents one app launch.\n" +
                    "Files are JSON Lines (.jsonl): one timestamped event per line.\n" +
                    "RTK passwords and authorization headers are intentionally excluded.\n" +
                    "Retention: up to 60 days, subject to a 48 MB diagnostic storage ceiling.\n")
                writeText(zip, "metadata.json", JSONObject().apply {
                    put("package", context.packageName)
                    put("version", BuildConfig.VERSION_NAME)
                    put("versionCode", BuildConfig.VERSION_CODE)
                    put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                    put("androidSdk", Build.VERSION.SDK_INT)
                    put("exportedAtUtc", isoUtc(System.currentTimeMillis()))
                    put("retentionDays", RETENTION_DAYS)
                    put("logCatalog", JSONArray().apply {
                        put(JSONObject().put("module", "app").put("includes", "startup, device/app version, memory pressure, fatal uncaught exceptions"))
                        put(JSONObject().put("module", "mavlink").put("includes", "session lifecycle, heartbeat/link health, RTCM queue drops, send failures"))
                        put(JSONObject().put("module", "rtk").put("includes", "network, NTRIP stream and GGA lifecycle, RTCM first-byte/stall/error, forwarding state, GPS fix/satellites/DOP"))
                        put(JSONObject().put("module", "flight").put("includes", "flight sessions, periodic/incident health snapshots, battery, movement, RC RSSI, obstacle/sprayer state, tablet battery/network/power/storage, RTK/link health, autopilot status text"))
                        put(JSONObject().put("module", "mission").put("includes", "plan generation, save/load, download, upload lifecycle and failure reasons"))
                        put(JSONObject().put("module", "geo-awareness").put("includes", "retained geo-zone, flight, safety, restriction, incident, and evidence events"))
                    })
                }.toString(2))
                synchronized(writeLock) {
                    addDirectory(zip, File(context.filesDir, "$DIRECTORY/$SESSIONS_DIRECTORY"), "sessions/")
                    // Retain previously collected geo logs while the new launch folders accumulate.
                    addDirectory(zip, File(context.filesDir, "geo_awareness/logs"), "legacy-geo-awareness/events/")
                    addDirectory(zip, File(context.filesDir, "geo_awareness/incidents"), "legacy-geo-awareness/incidents/")
                }
            }
            target.finish()
            ExportResult.Success(name, target.location)
        } catch (error: Throwable) {
            target.abort()
            throw error
        }
    }.getOrElse { ExportResult.Failure(it.message ?: "Could not create diagnostic package.") }

    private fun rotate(file: File) {
        val lastArchive = File(file.parentFile, "${file.nameWithoutExtension}.$MAX_ARCHIVES_PER_DAY.${file.extension}")
        if (lastArchive.exists()) lastArchive.delete()
        for (index in MAX_ARCHIVES_PER_DAY - 1 downTo 1) {
            val from = File(file.parentFile, "${file.nameWithoutExtension}.$index.${file.extension}")
            val to = File(file.parentFile, "${file.nameWithoutExtension}.${index + 1}.${file.extension}")
            if (from.exists()) from.renameTo(to)
        }
        val firstArchive = File(file.parentFile, "${file.nameWithoutExtension}.1.${file.extension}")
        if (file.exists()) file.renameTo(firstArchive)
    }

    private fun createSession(context: Context, now: Long): File {
        val existingId = sessionId
        if (existingId != null) {
            return File(context.filesDir, "$DIRECTORY/$SESSIONS_DIRECTORY/$existingId")
        }
        val root = File(context.filesDir, "$DIRECTORY/$SESSIONS_DIRECTORY").apply { mkdirs() }
        val id = "launch-${sessionTimestamp(now)}"
        val session = File(root, id).apply { mkdirs() }
        File(session, "diagnostics").mkdirs()
        File(session, "geo-awareness").mkdirs()
        File(session, "session.json").writeText(JSONObject().apply {
            put("sessionId", id)
            put("startedAtMillis", now)
            put("startedAtUtc", isoUtc(now))
            put("startedAtLocal", localTimestamp(now))
            put("appVersion", BuildConfig.VERSION_NAME)
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        }.toString(2), Charsets.UTF_8)
        sessionId = id
        return session
    }

    private fun cleanup(directory: File) {
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * 24 * 60 * 60 * 1000
        val sessions = File(directory, SESSIONS_DIRECTORY)
            .listFiles()?.filter { it.isDirectory }?.sortedBy { it.lastModified() }.orEmpty()
        sessions.filter { it.lastModified() < cutoff }.forEach { it.deleteRecursively() }
        var totalBytes = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        if (totalBytes <= MAX_TOTAL_BYTES) return
        sessions.filter { it.name != sessionId }.forEach { session ->
                if (totalBytes <= MAX_TOTAL_BYTES) return@forEach
                val size = session.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                if (session.deleteRecursively()) totalBytes -= size
            }
    }

    private fun addDirectory(zip: ZipOutputStream, directory: File, prefix: String) {
        if (!directory.exists()) return
        directory.walkTopDown().filter { it.isFile }.sortedBy { it.absolutePath }.forEach { file ->
            val relative = file.relativeTo(directory).invariantSeparatorsPath
            zip.putNextEntry(ZipEntry(prefix + relative))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun writeText(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun sanitize(value: Any?): Any? = when (value) {
        null -> JSONObject.NULL
        is CharSequence -> if (looksSensitive(value.toString())) "[redacted]" else value.toString().take(500)
        is Number, is Boolean -> value
        else -> value.toString().take(500)
    }

    private fun looksSensitive(value: String): Boolean {
        val lower = value.lowercase(Locale.US)
        return lower.contains("password") || lower.contains("authorization") || lower.contains("basic ")
    }

    private fun isoUtc(time: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(time))

    private fun localTimestamp(time: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        .format(Date(time))

    private fun fileTimestamp(time: Long): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(time))

    private fun sessionTimestamp(time: Long): String = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss.SSS", Locale.US).format(Date(time))

    private fun logDate(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(time))

    private fun openDownloadOutput(context: Context, name: String): DownloadTarget {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/DroneServicesApp/Logs")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            val stream = requireNotNull(context.contentResolver.openOutputStream(uri))
            return DownloadTarget(
                stream,
                "Downloads/DroneServicesApp/Logs/$name",
                {
                    values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                },
                { context.contentResolver.delete(uri, null, null) }
            )
        }
        @Suppress("DEPRECATION")
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DroneServicesApp/Logs").apply { mkdirs() }
        val file = File(directory, name)
        return DownloadTarget(FileOutputStream(file), file.absolutePath, {}, { file.delete() })
    }

    private class DownloadTarget(
        val stream: OutputStream,
        val location: String,
        private val onFinish: () -> Unit,
        private val onAbort: () -> Unit
    ) {
        fun finish() = onFinish()
        fun abort() = onAbort()
    }

    sealed class ExportResult {
        data class Success(val fileName: String, val location: String) : ExportResult()
        data class Failure(val reason: String) : ExportResult()
    }
}
