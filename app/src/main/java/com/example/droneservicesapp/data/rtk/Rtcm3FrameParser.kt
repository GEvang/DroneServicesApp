package com.example.droneservicesapp.data.rtk

import android.util.Log

/**
 * Reassembles complete RTCM3 frames from arbitrary byte chunks.
 *
 * The parser preserves leftover bytes across appends, discards garbage before the next RTCM3
 * preamble, validates CRC24Q, and bounds internal buffer growth so malformed streams cannot grow
 * memory unbounded.
 */
class Rtcm3FrameParser {

    private var buffer = ByteArray(0)
    private var frameIndex = 0L
    private var appendCount = 0L
    private var lastAppendLogMs = 0L
    private var lastLeftoverLogMs = 0L
    private var lastLoggedLeftoverBytes = -1

    fun append(chunk: ByteArray): List<ByteArray> {
        if (chunk.isEmpty()) return emptyList()

        appendCount++
        buffer += chunk
        if (buffer.size > MAX_BUFFER_BYTES) {
            buffer = buffer.takeLast(MAX_BUFFER_BYTES).toByteArray()
        }

        maybeLogAppend(chunk.size)

        val frames = mutableListOf<ByteArray>()
        var cursor = 0

        while (cursor < buffer.size) {
            val start = findPreamble(buffer, cursor)
            if (start < 0) {
                buffer = buffer.takeLast(2).toByteArray()
                logLeftover()
                return frames
            }

            if (start > 0 && cursor == 0) {
                Log.w(TAG, "discarding garbageBytes=$start before preamble")
            }

            if (buffer.size - start < RTCM_HEADER_SIZE) {
                buffer = buffer.copyOfRange(start, buffer.size)
                logLeftover()
                return frames
            }

            val payloadLength =
                ((buffer[start + 1].toInt() and 0x03) shl 8) or (buffer[start + 2].toInt() and 0xFF)
            val frameLength = RTCM_HEADER_SIZE + payloadLength + RTCM_CRC_SIZE

            if (buffer.size - start < frameLength) {
                buffer = buffer.copyOfRange(start, buffer.size)
                logLeftover()
                return frames
            }

            val frame = buffer.copyOfRange(start, start + frameLength)
            if (isValidRtcmFrame(frame)) {
                frameIndex++
                frames += frame
                Log.i(
                    TAG,
                    "frame emitted index=$frameIndex frameLength=${frame.size} messageType=${extractMessageType(frame)}"
                )
                cursor = start + frameLength
            } else {
                Log.w(TAG, "invalid frame discarded atOffset=$start")
                cursor = start + 1
            }
        }

        buffer = ByteArray(0)
        logLeftover()
        return frames
    }

    fun reset() {
        buffer = ByteArray(0)
        lastLoggedLeftoverBytes = -1
    }

    private fun logLeftover() {
        val now = System.currentTimeMillis()
        if (buffer.size == lastLoggedLeftoverBytes &&
            now - lastLeftoverLogMs < LOG_INTERVAL_MS
        ) {
            return
        }
        lastLoggedLeftoverBytes = buffer.size
        lastLeftoverLogMs = now
        Log.i(TAG, "leftoverBytes=${buffer.size}")
    }

    private fun maybeLogAppend(chunkSize: Int) {
        val now = System.currentTimeMillis()
        if (appendCount == 1L || appendCount % 25L == 0L || now - lastAppendLogMs >= LOG_INTERVAL_MS) {
            lastAppendLogMs = now
            Log.i(TAG, "append rawChunkSize=$chunkSize bufferSize=${buffer.size} append=$appendCount")
        }
    }

    private fun findPreamble(bytes: ByteArray, fromIndex: Int): Int {
        for (index in fromIndex until bytes.size) {
            if ((bytes[index].toInt() and 0xFF) == RTCM_PREAMBLE) {
                return index
            }
        }
        return -1
    }

    private fun extractMessageType(frame: ByteArray): Int? {
        if (frame.size < RTCM_HEADER_SIZE + 2 + RTCM_CRC_SIZE) return null
        val payloadStart = RTCM_HEADER_SIZE
        val b0 = frame[payloadStart].toInt() and 0xFF
        val b1 = frame[payloadStart + 1].toInt() and 0xFF
        return (b0 shl 4) or (b1 shr 4)
    }

    private fun isValidRtcmFrame(frame: ByteArray): Boolean {
        if (frame.size < RTCM_HEADER_SIZE + RTCM_CRC_SIZE) return false

        val expected = ((frame[frame.size - 3].toInt() and 0xFF) shl 16) or
            ((frame[frame.size - 2].toInt() and 0xFF) shl 8) or
            (frame[frame.size - 1].toInt() and 0xFF)

        var crc = 0
        for (index in 0 until frame.size - RTCM_CRC_SIZE) {
            crc = crc xor ((frame[index].toInt() and 0xFF) shl 16)
            repeat(8) {
                crc = if ((crc and 0x800000) != 0) {
                    (crc shl 1) xor CRC24Q_POLY
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFFFF
            }
        }

        return crc == expected
    }

    companion object {
        private const val TAG = "RtcmParser"
        private const val RTCM_PREAMBLE = 0xD3
        private const val RTCM_HEADER_SIZE = 3
        private const val RTCM_CRC_SIZE = 3
        private const val CRC24Q_POLY = 0x1864CFB
        private const val MAX_BUFFER_BYTES = 8192
        private const val LOG_INTERVAL_MS = 5000L
    }
}
