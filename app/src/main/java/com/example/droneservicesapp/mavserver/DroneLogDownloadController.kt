package com.example.droneservicesapp.mavserver

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.droneservicesapp.data.diagnostics.DiagnosticLog
import com.example.droneservicesapp.data.mavlink.MavlinkClient
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.LogData
import io.dronefleet.mavlink.common.LogEntry
import io.dronefleet.mavlink.common.LogRequestData
import io.dronefleet.mavlink.common.LogRequestEnd
import io.dronefleet.mavlink.common.LogRequestList
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.min

data class DroneLogFile(
    val id: Int,
    val sizeBytes: Long,
    val timeUtcSeconds: Long,
    val totalLogs: Int,
)

data class DroneLogCatalogState(
    val logs: List<DroneLogFile> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

sealed class DroneLogDownloadState {
    object Idle : DroneLogDownloadState()
    data class Downloading(val logId: Int, val receivedBytes: Long, val totalBytes: Long) : DroneLogDownloadState()
    data class Succeeded(val location: String) : DroneLogDownloadState()
    data class Failed(val reason: String) : DroneLogDownloadState()
}

enum class DroneLogRequestResult { SENT, DISCONNECTED, ARMED, BUSY, INVALID_LOG }

/** Implements the MAVLink LOG_* transfer service and exports DataFlash bytes as a .bin file. */
internal class DroneLogDownloadController(
    context: Context,
    private val mavlinkClient: MavlinkClient,
    private val isConnected: () -> Boolean,
    private val isArmed: () -> Boolean,
    private val targetSystemId: () -> Int,
    private val targetComponentId: () -> Int,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    companion object {
        private const val GCS_SYSTEM_ID = 255
        private const val GCS_COMPONENT_ID = 190
        private const val LIST_TIMEOUT_MS = 5_000L
        private const val LIST_SETTLE_MS = 1_200L
        private const val DATA_TIMEOUT_MS = 2_500L
        private const val DATA_BYTES = 90
        private const val BLOCK_BYTES = DATA_BYTES * 256
        private const val MAX_BLOCK_RETRIES = 4
    }

    private val appContext = context.applicationContext
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mutableCatalog = MutableLiveData(DroneLogCatalogState())
    val catalog: LiveData<DroneLogCatalogState> = mutableCatalog
    private val mutableDownload = MutableLiveData<DroneLogDownloadState>(DroneLogDownloadState.Idle)
    val download: LiveData<DroneLogDownloadState> = mutableDownload
    private val logsById = linkedMapOf<Int, DroneLogFile>()
    private var listGeneration = 0
    private var downloadGeneration = 0
    private var activeLog: DroneLogFile? = null
    private var tempFile: File? = null
    private var output: FileOutputStream? = null
    private var nextOffset = 0L
    private var block: DownloadBlock? = null
    private var blockRetries = 0
    private var disconnected = true

    fun refreshLogs(): DroneLogRequestResult {
        if (!ready()) return DroneLogRequestResult.DISCONNECTED
        disconnected = false
        if (activeLog != null) return DroneLogRequestResult.BUSY
        listGeneration += 1
        val current = listGeneration
        logsById.clear()
        mutableCatalog.value = DroneLogCatalogState(loading = true)
        mavlinkClient.send2(
            GCS_SYSTEM_ID,
            GCS_COMPONENT_ID,
            LogRequestList.builder()
                .targetSystem(targetSystemId())
                .targetComponent(targetComponentId())
                .start(0)
                .end(0xffff)
                .build()
        )
        handler.postDelayed({ finishList(current) }, LIST_TIMEOUT_MS)
        DiagnosticLog.event("mavlink", "dataflash_log_list_requested")
        return DroneLogRequestResult.SENT
    }

    fun download(logId: Int): DroneLogRequestResult {
        if (!ready()) return DroneLogRequestResult.DISCONNECTED
        disconnected = false
        if (isArmed()) return DroneLogRequestResult.ARMED
        if (activeLog != null) return DroneLogRequestResult.BUSY
        val selected = logsById[logId] ?: return DroneLogRequestResult.INVALID_LOG
        if (selected.sizeBytes <= 0L) return DroneLogRequestResult.INVALID_LOG

        downloadGeneration += 1
        activeLog = selected
        nextOffset = 0L
        blockRetries = 0
        tempFile = File.createTempFile("ardupilot-log-${selected.id}-", ".bin", appContext.cacheDir)
        output = FileOutputStream(tempFile)
        mutableDownload.value = DroneLogDownloadState.Downloading(selected.id, 0L, selected.sizeBytes)
        startNextBlock(downloadGeneration)
        DiagnosticLog.event("mavlink", "dataflash_log_download_started", data = mapOf("id" to selected.id, "size" to selected.sizeBytes))
        return DroneLogRequestResult.SENT
    }

    fun handle(message: MavlinkMessage<*>) {
        if (message.originSystemId != targetSystemId() || message.originComponentId != targetComponentId()) return
        when (val payload = message.payload) {
            is LogEntry -> handleEntry(payload)
            is LogData -> handleData(payload)
        }
    }

    fun clearDownloadResult() {
        if (mutableDownload.value !is DroneLogDownloadState.Downloading) {
            mutableDownload.value = DroneLogDownloadState.Idle
        }
    }

    fun onDisconnected() {
        if (disconnected) return
        disconnected = true
        listGeneration += 1
        if (activeLog != null) failDownload("Aircraft link lost during log download")
        mutableCatalog.value = DroneLogCatalogState(error = "No active aircraft link")
    }

    fun clear() {
        listGeneration += 1
        downloadGeneration += 1
        handler.removeCallbacksAndMessages(null)
        runCatching { output?.close() }
        tempFile?.delete()
        ioExecutor.shutdownNow()
    }

    private fun handleEntry(entry: LogEntry) {
        if (mutableCatalog.value?.loading != true) return
        logsById[entry.id()] = DroneLogFile(entry.id(), entry.size(), entry.timeUtc(), entry.numLogs())
        mutableCatalog.value = DroneLogCatalogState(
            logs = logsById.values.sortedByDescending { it.id },
            loading = true,
        )
        val current = listGeneration
        if (entry.numLogs() > 0 && logsById.size >= entry.numLogs()) finishList(current)
        else handler.postDelayed({ finishList(current) }, LIST_SETTLE_MS)
    }

    private fun finishList(generation: Int) {
        if (generation != listGeneration || mutableCatalog.value?.loading != true) return
        val logs = logsById.values.sortedByDescending { it.id }
        mutableCatalog.value = DroneLogCatalogState(
            logs = logs,
            error = if (logs.isEmpty()) "No on-board logs were returned; log transfer may be unsupported" else null,
        )
        DiagnosticLog.event("mavlink", "dataflash_log_list_received", data = mapOf("count" to logs.size))
    }

    private fun startNextBlock(generation: Int) {
        val log = activeLog ?: return
        if (generation != downloadGeneration) return
        if (nextOffset >= log.sizeBytes) {
            finishDownload(generation)
            return
        }
        val length = min(BLOCK_BYTES.toLong(), log.sizeBytes - nextOffset).toInt()
        block = DownloadBlock(nextOffset, length)
        blockRetries = 0
        requestRange(log.id, nextOffset, length.toLong())
        scheduleBlockTimeout(generation, nextOffset)
    }

    private fun handleData(data: LogData) {
        val log = activeLog ?: return
        val currentBlock = block ?: return
        if (data.id() != log.id || data.count() <= 0) return
        val relative = data.ofs() - currentBlock.start
        if (relative < 0 || relative >= currentBlock.length) return
        val count = min(data.count(), min(data.data().size, currentBlock.length - relative.toInt()))
        System.arraycopy(data.data(), 0, currentBlock.bytes, relative.toInt(), count)
        val firstSegment = relative.toInt() / DATA_BYTES
        val lastSegment = (relative.toInt() + count - 1) / DATA_BYTES
        for (segment in firstSegment..lastSegment) currentBlock.received[segment] = true
        if (!currentBlock.received.all { it }) return

        val generation = downloadGeneration
        val completedOffset = currentBlock.start
        block = null
        ioExecutor.execute {
            runCatching { output?.write(currentBlock.bytes) }
                .onSuccess {
                    handler.post {
                        if (generation != downloadGeneration || activeLog?.id != log.id) return@post
                        nextOffset = completedOffset + currentBlock.length
                        mutableDownload.value = DroneLogDownloadState.Downloading(log.id, nextOffset, log.sizeBytes)
                        startNextBlock(generation)
                    }
                }
                .onFailure { error ->
                    handler.post {
                        if (generation == downloadGeneration) {
                            failDownload(error.message ?: "Could not write log data")
                        }
                    }
                }
        }
    }

    private fun scheduleBlockTimeout(generation: Int, expectedStart: Long) {
        handler.postDelayed({
            val current = block
            if (generation != downloadGeneration || current?.start != expectedStart) return@postDelayed
            blockRetries += 1
            if (blockRetries > MAX_BLOCK_RETRIES) {
                failDownload("Log transfer timed out near byte $expectedStart")
                return@postDelayed
            }
            requestMissing(current)
            scheduleBlockTimeout(generation, expectedStart)
        }, DATA_TIMEOUT_MS)
    }

    private fun requestMissing(current: DownloadBlock) {
        val logId = activeLog?.id ?: return
        var index = 0
        while (index < current.received.size) {
            if (current.received[index]) { index += 1; continue }
            val startIndex = index
            while (index < current.received.size && !current.received[index]) index += 1
            val offset = current.start + startIndex * DATA_BYTES
            val end = min(current.length, index * DATA_BYTES)
            requestRange(logId, offset, (end - startIndex * DATA_BYTES).toLong())
        }
    }

    private fun requestRange(logId: Int, offset: Long, count: Long) {
        mavlinkClient.send2(
            GCS_SYSTEM_ID,
            GCS_COMPONENT_ID,
            LogRequestData.builder()
                .targetSystem(targetSystemId())
                .targetComponent(targetComponentId())
                .id(logId)
                .ofs(offset)
                .count(count)
                .build()
        )
    }

    private fun finishDownload(generation: Int) {
        val log = activeLog ?: return
        val file = tempFile ?: return
        ioExecutor.execute {
            val result = runCatching {
                output?.flush()
                output?.close()
                output = null
                exportToDownloads(file, "ArduPilot-log-${log.id}-${System.currentTimeMillis()}.bin")
            }
            handler.post {
                if (generation != downloadGeneration) return@post
                sendRequestEnd()
                file.delete()
                tempFile = null
                activeLog = null
                block = null
                result.onSuccess { location ->
                    mutableDownload.value = DroneLogDownloadState.Succeeded(location)
                    DiagnosticLog.event("mavlink", "dataflash_log_download_finished", data = mapOf("id" to log.id, "location" to location))
                }.onFailure { failDownload(it.message ?: "Could not export the log") }
            }
        }
    }

    private fun failDownload(reason: String) {
        downloadGeneration += 1
        sendRequestEnd()
        runCatching { output?.close() }
        output = null
        tempFile?.delete()
        tempFile = null
        activeLog = null
        block = null
        mutableDownload.value = DroneLogDownloadState.Failed(reason)
        DiagnosticLog.event("mavlink", "dataflash_log_download_failed", "WARN", mapOf("reason" to reason))
    }

    private fun sendRequestEnd() {
        if (targetSystemId() < 0 || targetComponentId() < 0) return
        mavlinkClient.send2(
            GCS_SYSTEM_ID,
            GCS_COMPONENT_ID,
            LogRequestEnd.builder()
                .targetSystem(targetSystemId())
                .targetComponent(targetComponentId())
                .build()
        )
    }

    private fun exportToDownloads(source: File, name: String): String {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/DroneServicesApp/Logs")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = requireNotNull(appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        try {
            requireNotNull(appContext.contentResolver.openOutputStream(uri)).use { target ->
                source.inputStream().use { it.copyTo(target) }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            appContext.contentResolver.update(uri, values, null, null)
            return "Downloads/DroneServicesApp/Logs/$name"
        } catch (error: Throwable) {
            appContext.contentResolver.delete(uri, null, null)
            throw error
        }
    }

    private fun ready(): Boolean = isConnected() && targetSystemId() >= 0 && targetComponentId() >= 0

    private class DownloadBlock(val start: Long, val length: Int) {
        val bytes = ByteArray(length)
        val received = BooleanArray((length + DATA_BYTES - 1) / DATA_BYTES)
    }
}
