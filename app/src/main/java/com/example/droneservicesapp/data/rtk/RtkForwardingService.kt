package com.example.droneservicesapp.data.rtk

import android.content.Context
import android.util.Log
import com.example.droneservicesapp.data.mavlink.MavlinkClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RtkForwardingService(
    context: Context,
    private val mavlinkClient: MavlinkClient,
    private val ntripClient: NtripClient = NtripClient()
) {

    private val rtkPreferences = RtkPreferences(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<RtkForwardingState>(RtkForwardingState.Idle)

    private var forwardingJob: Job? = null
    private val rtcmFramer = RtcmFramer()

    val state: StateFlow<RtkForwardingState> = _state.asStateFlow()

    fun start(targetSystemId: Int, targetComponentId: Int) {
        if (forwardingJob?.isActive == true) return

        val config = rtkPreferences.getConfig()
        if (!RtkValidator.isValidConfig(config)) {
            _state.value = RtkForwardingState.InvalidConfig("RTK settings are incomplete.")
            return
        }

        rtcmFramer.reset()
        forwardingJob = scope.launch {
            try {
                _state.value = RtkForwardingState.ConnectingToCaster

                val result = ntripClient.streamCorrections(
                    config = config,
                    onStreamStarted = {
                        _state.value = RtkForwardingState.Streaming
                    },
                    onBytesReceived = { bytes ->
                        val frames = rtcmFramer.append(bytes)
                        for (frame in frames) {
                            try {
                                mavlinkClient.sendGpsRtcmData(targetSystemId, targetComponentId, frame)
                            } catch (e: Exception) {
                                throw IllegalStateException(
                                    e.message ?: "Failed to send GPS_RTCM_DATA."
                                )
                            }
                        }
                    }
                )

                _state.value = mapResultToState(result)
                if (result is NtripResult.NetworkFailure) {
                    Log.w(TAG, "RTK forwarding stopped: ${result.message}")
                }
            } catch (_: CancellationException) {
                // stop() drives the visible state in this case
            } catch (e: Exception) {
                _state.value = RtkForwardingState.ProtocolError(
                    e.message ?: "Failed to forward RTCM data."
                )
            } finally {
                forwardingJob = null
            }
        }
    }

    fun stop(updateState: Boolean = true) {
        forwardingJob?.cancel()
        forwardingJob = null
        rtcmFramer.reset()
        if (updateState) {
            _state.value = RtkForwardingState.Stopped
        }
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    fun isRunning(): Boolean = forwardingJob?.isActive == true

    private fun mapResultToState(result: NtripResult): RtkForwardingState {
        return when (result) {
            is NtripResult.AuthFailure -> RtkForwardingState.AuthFailed("Authentication failed.")
            is NtripResult.InvalidConfig -> RtkForwardingState.InvalidConfig(result.message)
            is NtripResult.MountpointNotFound -> {
                RtkForwardingState.ProtocolError("Mountpoint not found.")
            }
            is NtripResult.NetworkFailure -> RtkForwardingState.NetworkError(result.message)
            is NtripResult.ProtocolFailure -> RtkForwardingState.ProtocolError(result.message)
            is NtripResult.ConnectionSuccess -> RtkForwardingState.Stopped
            is NtripResult.SourceTableSuccess -> {
                RtkForwardingState.ProtocolError("Unexpected sourcetable response.")
            }
        }
    }

    private class RtcmFramer {
        private var buffer = ByteArray(0)

        fun append(chunk: ByteArray): List<ByteArray> {
            if (chunk.isEmpty()) return emptyList()
            buffer += chunk

            val frames = mutableListOf<ByteArray>()
            var cursor = 0

            while (cursor < buffer.size) {
                val start = findPreamble(buffer, cursor)
                if (start < 0) {
                    buffer = buffer.takeLast(2).toByteArray()
                    return frames
                }

                if (buffer.size - start < RTCM_HEADER_SIZE) {
                    buffer = buffer.copyOfRange(start, buffer.size)
                    return frames
                }

                val payloadLength =
                    ((buffer[start + 1].toInt() and 0x03) shl 8) or (buffer[start + 2].toInt() and 0xFF)
                val frameLength = RTCM_HEADER_SIZE + payloadLength + RTCM_CRC_SIZE

                if (buffer.size - start < frameLength) {
                    buffer = buffer.copyOfRange(start, buffer.size)
                    return frames
                }

                val frame = buffer.copyOfRange(start, start + frameLength)
                if (isValidRtcmFrame(frame)) {
                    frames += frame
                    cursor = start + frameLength
                } else {
                    cursor = start + 1
                }
            }

            buffer = ByteArray(0)
            return frames
        }

        fun reset() {
            buffer = ByteArray(0)
        }

        private fun findPreamble(bytes: ByteArray, fromIndex: Int): Int {
            for (index in fromIndex until bytes.size) {
                if ((bytes[index].toInt() and 0xFF) == RTCM_PREAMBLE) {
                    return index
                }
            }
            return -1
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
            private const val RTCM_PREAMBLE = 0xD3
            private const val RTCM_HEADER_SIZE = 3
            private const val RTCM_CRC_SIZE = 3
            private const val CRC24Q_POLY = 0x1864CFB
        }
    }

    companion object {
        private const val TAG = "RtkForwardingService"
    }
}
