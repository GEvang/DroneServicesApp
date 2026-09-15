package com.example.droneservicesapp.data.transport

import java.net.InetAddress

internal data class UdpEndpoint(
    val address: InetAddress,
    val port: Int,
)

/**
 * Elects one UDP peer from a valid autopilot heartbeat and keeps unrelated or
 * reflected MAVLink streams from being merged into the application's parser.
 */
internal class MavlinkUdpPeerTracker(
    private val silenceTimeoutMs: Long = DEFAULT_SILENCE_TIMEOUT_MS,
) {
    private var peer: UdpEndpoint? = null
    private var lastSeenMs: Long = 0L

    @Synchronized
    fun shouldAccept(
        endpoint: UdpEndpoint,
        containsAutopilotHeartbeat: Boolean,
        nowMs: Long,
    ): Boolean {
        val selected = peer
        if (selected == null) {
            if (!containsAutopilotHeartbeat) return false
            peer = endpoint
            lastSeenMs = nowMs
            return true
        }

        val selectedIsAlive = nowMs - lastSeenMs <= silenceTimeoutMs
        if (endpoint == selected && selectedIsAlive) {
            lastSeenMs = nowMs
            return true
        }

        if (!selectedIsAlive) {
            peer = null
            if (containsAutopilotHeartbeat) {
                peer = endpoint
                lastSeenMs = nowMs
                return true
            }
        }
        return false
    }

    @Synchronized
    fun currentEndpoint(nowMs: Long): UdpEndpoint? {
        val selected = peer ?: return null
        if (nowMs - lastSeenMs > silenceTimeoutMs) {
            peer = null
            return null
        }
        return selected
    }

    companion object {
        const val DEFAULT_SILENCE_TIMEOUT_MS = 3_000L
    }
}

internal object MavlinkDatagramInspector {
    private const val HEARTBEAT_MESSAGE_ID = 0
    private const val HEARTBEAT_MIN_PAYLOAD_LENGTH = 9
    private const val MAV_TYPE_GCS = 6
    private const val MAV_AUTOPILOT_INVALID = 8

    fun containsAutopilotHeartbeat(bytes: ByteArray, offset: Int, length: Int): Boolean {
        val limit = offset + length
        var index = offset
        while (index < limit) {
            val frame = frameAt(bytes, index, limit)
            if (frame == null) {
                index++
                continue
            }
            if (frame.messageId == HEARTBEAT_MESSAGE_ID &&
                frame.payloadLength >= HEARTBEAT_MIN_PAYLOAD_LENGTH
            ) {
                val vehicleType = bytes[frame.payloadOffset + 4].toInt() and 0xFF
                val autopilot = bytes[frame.payloadOffset + 5].toInt() and 0xFF
                if (frame.systemId > 0 && vehicleType != MAV_TYPE_GCS && autopilot != MAV_AUTOPILOT_INVALID) {
                    return true
                }
            }
            index += frame.packetLength
        }
        return false
    }

    private fun frameAt(bytes: ByteArray, index: Int, limit: Int): Frame? {
        return when (bytes[index].toInt() and 0xFF) {
            0xFE -> {
                if (index + 6 > limit) return null
                val payloadLength = bytes[index + 1].toInt() and 0xFF
                val packetLength = payloadLength + 8
                if (index + packetLength > limit) return null
                Frame(
                    payloadLength = payloadLength,
                    payloadOffset = index + 6,
                    packetLength = packetLength,
                    messageId = bytes[index + 5].toInt() and 0xFF,
                    systemId = bytes[index + 3].toInt() and 0xFF,
                )
            }
            0xFD -> {
                if (index + 10 > limit) return null
                val payloadLength = bytes[index + 1].toInt() and 0xFF
                val hasSignature = (bytes[index + 2].toInt() and 0x01) != 0
                val packetLength = payloadLength + 12 + if (hasSignature) 13 else 0
                if (index + packetLength > limit) return null
                Frame(
                    payloadLength = payloadLength,
                    payloadOffset = index + 10,
                    packetLength = packetLength,
                    messageId = (bytes[index + 7].toInt() and 0xFF) or
                        ((bytes[index + 8].toInt() and 0xFF) shl 8) or
                        ((bytes[index + 9].toInt() and 0xFF) shl 16),
                    systemId = bytes[index + 5].toInt() and 0xFF,
                )
            }
            else -> null
        }
    }

    private data class Frame(
        val payloadLength: Int,
        val payloadOffset: Int,
        val packetLength: Int,
        val messageId: Int,
        val systemId: Int,
    )
}
