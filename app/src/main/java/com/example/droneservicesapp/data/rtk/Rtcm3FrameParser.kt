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
    private var lastResyncLogMs = 0L
    private var lastPartialLogMs = 0L
    private var lastInvalidLengthLogMs = 0L
    private var lastFrameEmitLogMs = 0L

    fun append(chunk: ByteArray): List<ByteArray> {
        if (chunk.isEmpty()) return emptyList()

        appendCount++
        buffer = appendBounded(buffer, chunk)

        maybeLogAppend(chunk.size)

        val frames = mutableListOf<ByteArray>()
        var cursor = 0

        while (cursor < buffer.size) {
            val start = findPreamble(buffer, cursor)
            if (start < 0) {
                if (buffer.size > HEADER_PREFIX_BYTES) {
                    logBytesDiscarded("invalid preamble resync", buffer.size - HEADER_PREFIX_BYTES)
                }
                buffer = buffer.takeLast(HEADER_PREFIX_BYTES).toByteArray()
                logLeftover()
                return frames
            }

            if (start > 0 && cursor == 0) {
                logBytesDiscarded("invalid preamble resync", start)
            }

            if (buffer.size - start < RTCM_HEADER_SIZE) {
                buffer = safeSlice(start, buffer.size) ?: ByteArray(0)
                maybeLogPartialFrame(buffer.size)
                logLeftover()
                return frames
            }

            val payloadLength =
                ((buffer[start + 1].toInt() and 0x03) shl 8) or (buffer[start + 2].toInt() and 0xFF)
            val frameLength = RTCM_HEADER_SIZE + payloadLength + RTCM_CRC_SIZE

            if (frameLength !in MIN_FRAME_BYTES..MAX_FRAME_BYTES) {
                maybeLogInvalidLength(payloadLength, frameLength, start)
                cursor = start + 1
                continue
            }

            if (buffer.size - start < frameLength) {
                buffer = safeSlice(start, buffer.size) ?: ByteArray(0)
                maybeLogPartialFrame(frameLength - (buffer.size - start))
                logLeftover()
                return frames
            }

            val frame = safeSlice(start, start + frameLength)
            if (frame == null) {
                logBytesDiscarded("invalid preamble resync", (buffer.size - start).coerceAtLeast(1))
                buffer = ByteArray(0)
                logLeftover()
                return frames
            }
            if (isValidRtcmFrame(frame)) {
                frameIndex++
                frames += frame
                maybeLogFrameEmitted(frame)
                cursor = start + frameLength
            } else {
                logBytesDiscarded("invalid preamble resync", 1)
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

    private fun appendBounded(existing: ByteArray, chunk: ByteArray): ByteArray {
        val overflow = (existing.size + chunk.size - MAX_BUFFER_BYTES).coerceAtLeast(0)
        val retainedExisting = if (overflow >= existing.size) {
            ByteArray(0)
        } else {
            safeSlice(existing, overflow, existing.size) ?: ByteArray(0)
        }
        val combined = retainedExisting + chunk
        if (overflow > 0) {
            logBytesDiscarded("bytes discarded for resync", overflow)
        }
        return combined
    }

    private fun safeSlice(start: Int, end: Int): ByteArray? {
        return safeSlice(buffer, start, end)
    }

    private fun safeSlice(source: ByteArray, start: Int, end: Int): ByteArray? {
        if (start < 0 || end < start || end > source.size) {
            Log.w(
                TAG,
                "bytes discarded for resync invalidRange start=$start end=$end size=${source.size}"
            )
            return null
        }
        return source.copyOfRange(start, end)
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

    private fun maybeLogPartialFrame(missingBytes: Int) {
        val now = System.currentTimeMillis()
        if (now - lastPartialLogMs < LOG_INTERVAL_MS) return
        lastPartialLogMs = now
        Log.i(TAG, "partial frame waiting for more bytes missing=$missingBytes buffered=${buffer.size}")
    }

    private fun maybeLogInvalidLength(payloadLength: Int, frameLength: Int, start: Int) {
        val now = System.currentTimeMillis()
        if (now - lastInvalidLengthLogMs < LOG_INTERVAL_MS) return
        lastInvalidLengthLogMs = now
        Log.w(
            TAG,
            "invalid frame length payloadLength=$payloadLength frameLength=$frameLength offset=$start"
        )
    }

    private fun maybeLogFrameEmitted(frame: ByteArray) {
        val now = System.currentTimeMillis()
        if (frameIndex == 1L || frameIndex % 25L == 0L || now - lastFrameEmitLogMs >= LOG_INTERVAL_MS) {
            lastFrameEmitLogMs = now
            Log.i(
                TAG,
                "frame emitted length=${frame.size} type=${extractMessageType(frame)} index=$frameIndex"
            )
        }
    }

    private fun logBytesDiscarded(reason: String, discardedBytes: Int) {
        if (discardedBytes <= 0) return
        val now = System.currentTimeMillis()
        if (now - lastResyncLogMs < LOG_INTERVAL_MS && discardedBytes < RESYNC_LOG_THRESHOLD_BYTES) {
            return
        }
        lastResyncLogMs = now
        Log.w(TAG, "$reason bytesDiscarded=$discardedBytes")
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
        private const val HEADER_PREFIX_BYTES = 2
        private const val MIN_FRAME_BYTES = RTCM_HEADER_SIZE + RTCM_CRC_SIZE
        private const val MAX_RTCM_PAYLOAD_BYTES = 1023
        private const val MAX_FRAME_BYTES = RTCM_HEADER_SIZE + MAX_RTCM_PAYLOAD_BYTES + RTCM_CRC_SIZE
        private const val LOG_INTERVAL_MS = 5000L
        private const val RESYNC_LOG_THRESHOLD_BYTES = 16
    }
}
