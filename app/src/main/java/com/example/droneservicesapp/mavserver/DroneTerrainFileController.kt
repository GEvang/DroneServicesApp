package com.example.droneservicesapp.mavserver

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.droneservicesapp.data.diagnostics.DiagnosticLog
import com.example.droneservicesapp.data.mavlink.MavlinkClient
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.FileTransferProtocol
import java.nio.charset.StandardCharsets
import java.util.Locale

data class AircraftTerrainFile(
    val name: String,
    val sizeBytes: Long,
    val modifiedAtSeconds: Long?,
    val coversCurrentMission: Boolean,
)

data class AircraftTerrainCatalogState(
    val files: List<AircraftTerrainFile> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val timestampsAvailable: Boolean = false,
)

enum class AircraftTerrainRequestResult { SENT, DISCONNECTED, BUSY }

/** Read-only MAVFTP client for listing the aircraft's APM/TERRAIN directory. */
internal class DroneTerrainFileController(
    private val mavlinkClient: MavlinkClient,
    private val isConnected: () -> Boolean,
    private val targetSystemId: () -> Int,
    private val targetComponentId: () -> Int,
    private val currentMissionTileNames: () -> Set<String>,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    companion object {
        private const val GCS_COMPONENT_ID = 190
        private const val TERRAIN_DIRECTORY = "APM/TERRAIN"
        private const val PAYLOAD_BYTES = 251
        private const val HEADER_BYTES = 12
        private const val OPCODE_LIST_DIRECTORY = 3
        private const val OPCODE_LIST_DIRECTORY_WITH_TIME = 16
        private const val OPCODE_ACK = 128
        private const val OPCODE_NAK = 129
        private const val ERROR_EOF = 6
        private const val ERROR_UNKNOWN_COMMAND = 7
        private const val REQUEST_TIMEOUT_MS = 4_000L
    }

    private val mutableState = MutableLiveData(AircraftTerrainCatalogState())
    val state: LiveData<AircraftTerrainCatalogState> = mutableState
    private val files = linkedMapOf<String, AircraftTerrainFile>()
    private var generation = 0
    private var sequence = 0
    private var offset = 0
    private var activeOpcode: Int? = null

    @Synchronized
    fun refresh(): AircraftTerrainRequestResult {
        if (!isConnected() || targetSystemId() < 0 || targetComponentId() < 0) {
            return AircraftTerrainRequestResult.DISCONNECTED
        }
        if (activeOpcode != null) return AircraftTerrainRequestResult.BUSY
        generation += 1
        files.clear()
        offset = 0
        mutableState.value = AircraftTerrainCatalogState(loading = true)
        requestPage(OPCODE_LIST_DIRECTORY_WITH_TIME, generation)
        DiagnosticLog.event("mavlink", "aircraft_terrain_file_list_requested")
        return AircraftTerrainRequestResult.SENT
    }

    @Synchronized
    fun handle(message: MavlinkMessage<*>) {
        if (message.originSystemId != targetSystemId() || message.originComponentId != targetComponentId()) return
        val ftp = message.payload as? FileTransferProtocol ?: return
        val payload = ftp.payload()
        if (payload.size < HEADER_BYTES || activeOpcode == null) return
        val responseSequence = readUInt16(payload, 0)
        if (responseSequence != sequence) return
        val opcode = unsigned(payload[3])
        val requestedOpcode = unsigned(payload[5])
        if (requestedOpcode != activeOpcode) return
        when (opcode) {
            OPCODE_ACK -> handleAck(payload)
            OPCODE_NAK -> handleNak(payload)
        }
    }

    @Synchronized
    fun onDisconnected() {
        if (activeOpcode != null) finish(error = "Aircraft disconnected while listing terrain files")
    }

    @Synchronized
    fun clear() {
        generation += 1
        activeOpcode = null
        handler.removeCallbacksAndMessages(this)
    }

    private fun handleAck(payload: ByteArray) {
        val dataSize = unsigned(payload[4]).coerceAtMost(payload.size - HEADER_BYTES)
        val data = payload.copyOfRange(HEADER_BYTES, HEADER_BYTES + dataSize)
        val entries = String(data, StandardCharsets.UTF_8)
            .split('\u0000')
            .filter(String::isNotBlank)
        if (entries.isEmpty()) {
            finish()
            return
        }
        val missionTiles = currentMissionTileNames().flatMap(::terrainNameKeys).toSet()
        entries.forEach { entry ->
            if (entry.firstOrNull() != 'F') return@forEach
            val fields = entry.drop(1).split('\t')
            val name = fields.getOrNull(0)?.trim().orEmpty()
            if (name.isBlank()) return@forEach
            val size = fields.getOrNull(1)?.toLongOrNull() ?: 0L
            val modified = fields.getOrNull(2)?.toLongOrNull()?.takeIf { it > 0L }
            files[name] = AircraftTerrainFile(
                name = name,
                sizeBytes = size,
                modifiedAtSeconds = modified,
                coversCurrentMission = terrainNameKeys(name).any { it in missionTiles },
            )
        }
        offset += entries.size
        requestPage(requireNotNull(activeOpcode), generation)
    }

    private fun handleNak(payload: ByteArray) {
        val dataSize = unsigned(payload[4])
        val errorCode = if (dataSize > 0 && payload.size > HEADER_BYTES) unsigned(payload[HEADER_BYTES]) else -1
        when {
            errorCode == ERROR_EOF -> finish()
            activeOpcode == OPCODE_LIST_DIRECTORY_WITH_TIME && errorCode == ERROR_UNKNOWN_COMMAND -> {
                files.clear()
                offset = 0
                requestPage(OPCODE_LIST_DIRECTORY, generation)
            }
            else -> finish(error = "MAVFTP directory listing failed (error $errorCode)")
        }
    }

    private fun requestPage(opcode: Int, requestGeneration: Int) {
        activeOpcode = opcode
        sequence = (sequence + 1) and 0xffff
        val payload = ByteArray(PAYLOAD_BYTES)
        writeUInt16(payload, 0, sequence)
        payload[3] = opcode.toByte()
        val path = TERRAIN_DIRECTORY.toByteArray(StandardCharsets.UTF_8)
        payload[4] = path.size.toByte()
        writeUInt32(payload, 8, offset)
        path.copyInto(payload, HEADER_BYTES)
        mavlinkClient.send2(
            mavlinkClient.gcsSystemId,
            GCS_COMPONENT_ID,
            FileTransferProtocol.builder()
                .targetNetwork(0)
                .targetSystem(targetSystemId())
                .targetComponent(targetComponentId())
                .payload(payload)
                .build(),
        )
        val expectedSequence = sequence
        handler.postAtTime({
            synchronized(this) {
                if (generation != requestGeneration || sequence != expectedSequence || activeOpcode == null) return@synchronized
                if (activeOpcode == OPCODE_LIST_DIRECTORY_WITH_TIME) {
                    files.clear()
                    offset = 0
                    requestPage(OPCODE_LIST_DIRECTORY, requestGeneration)
                } else {
                    finish(error = "Timed out waiting for the aircraft terrain directory")
                }
            }
        }, this, android.os.SystemClock.uptimeMillis() + REQUEST_TIMEOUT_MS)
    }

    private fun finish(error: String? = null) {
        val usedTimestamps = activeOpcode == OPCODE_LIST_DIRECTORY_WITH_TIME
        activeOpcode = null
        handler.removeCallbacksAndMessages(this)
        mutableState.postValue(
            AircraftTerrainCatalogState(
                files = files.values.sortedBy { it.name },
                error = error,
                timestampsAvailable = usedTimestamps,
            )
        )
        DiagnosticLog.event(
            "mavlink",
            "aircraft_terrain_file_list_finished",
            if (error == null) "INFO" else "WARN",
            mapOf("files" to files.size, "error" to error),
        )
    }

    private fun terrainNameKeys(value: String): Set<String> {
        val base = value.substringAfterLast('/').substringBeforeLast('.').uppercase(Locale.US)
        return setOf(base, base.replace("W", "").replace("E", ""))
    }

    private fun unsigned(value: Byte): Int = value.toInt() and 0xff

    private fun readUInt16(bytes: ByteArray, offset: Int): Int =
        unsigned(bytes[offset]) or (unsigned(bytes[offset + 1]) shl 8)

    private fun writeUInt16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeUInt32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
