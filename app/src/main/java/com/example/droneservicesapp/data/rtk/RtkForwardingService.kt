package com.example.droneservicesapp.data.rtk

import android.location.Location
import android.content.Context
import android.util.Log
import com.example.droneservicesapp.data.mavlink.MavlinkClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RtkForwardingService(
    context: Context,
    private val mavlinkClient: MavlinkClient,
    private val ntripClient: NtripClient = NtripClient()
) {
    companion object {
        private const val TAG = "RtkForwarding"
    }

    private val rtkPreferences = RtkPreferences(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<RtkForwardingState>(RtkForwardingState.Idle)

    private var forwardingJob: Job? = null
    private val rtcmParser = Rtcm3FrameParser()
    private var totalRawChunksReceived = 0L
    private var totalRawBytesReceived = 0L
    private var totalRtcmFramesForwarded = 0L
    private var lastRtcmProgressLogMs = 0L
    private var totalRtcmBytesForwarded = 0L
    private var lastRawChunkLogMs = 0L

    val state: StateFlow<RtkForwardingState> = _state.asStateFlow()

    fun start(
        targetSystemId: Int,
        targetComponentId: Int,
        shouldKeepRunning: () -> Boolean = { true },
        locationProvider: () -> Location? = { null }
    ) {
        if (forwardingJob?.isActive == true) {
            when (_state.value) {
                is RtkForwardingState.Streaming -> Log.i(TAG, "start ignored: already streaming")
                is RtkForwardingState.Reconnecting,
                is RtkForwardingState.ConnectingToCaster,
                is RtkForwardingState.WaitingForDroneGps -> Log.i(TAG, "start ignored: reconnect already scheduled")
                else -> Log.i(TAG, "start ignored: forwarding already active")
            }
            return
        }

        Log.i(
            TAG,
            "start targetSys=$targetSystemId targetComp=$targetComponentId"
        )

        val config = rtkPreferences.getConfig()
        Log.i(
            TAG,
            "start: requested mountpoint=${config.mountpoint.trim()} mavConnected=${shouldKeepRunning()} configValid=${RtkValidator.isValidConfig(config)}"
        )
        if (!RtkValidator.isValidConfig(config)) {
            val reason = when {
                config.mountpoint.isBlank() -> "Missing mountpoint."
                else -> "RTK settings are incomplete."
            }
            Log.w(TAG, "start blocked: $reason")
            updateState(RtkForwardingState.InvalidConfig(reason))
            return
        }

        rtcmParser.reset()
        totalRawChunksReceived = 0L
        totalRawBytesReceived = 0L
        totalRtcmFramesForwarded = 0L
        totalRtcmBytesForwarded = 0L
        lastRtcmProgressLogMs = 0L
        forwardingJob = scope.launch {
            try {
                var attempt = 1
                while (isActive) {
                    if (!shouldKeepRunning()) {
                        Log.w(TAG, "reconnect abandoned: drone disconnected or user stopped")
                        updateState(RtkForwardingState.WaitingForDrone)
                        break
                    }

                    val latestConfig = rtkPreferences.getConfig()
                    if (!RtkValidator.isValidConfig(latestConfig)) {
                        val reason = when {
                            latestConfig.mountpoint.isBlank() -> "Missing mountpoint."
                            else -> "RTK settings are incomplete."
                        }
                        Log.w(TAG, "reconnect abandoned: invalid config reason=$reason")
                        updateState(RtkForwardingState.InvalidConfig(reason))
                        break
                    }

                    val requiresGga = latestConfig.mountpoint.trim().equals("NEAR", ignoreCase = true)
                    val ggaLocation = locationProvider()?.takeIf { isUsableLocation(it) }
                    if (requiresGga && ggaLocation == null) {
                        Log.w(TAG, "waiting for drone gps for NEAR mountpoint")
                        updateState(RtkForwardingState.WaitingForDroneGps)
                        delay(2000L)
                        continue
                    }
                    Log.i(
                        TAG,
                        "gga location available=${ggaLocation != null} mountpoint=${latestConfig.mountpoint.trim()}"
                    )
                    rtcmParser.reset()

                    if (attempt == 1) {
                        updateState(RtkForwardingState.ConnectingToCaster)
                    } else {
                        val message = "attempt=$attempt mountpoint=${latestConfig.mountpoint.trim()}"
                        updateState(RtkForwardingState.Reconnecting(message))
                        Log.i(TAG, "reconnect attempt #$attempt")
                    }

                    val result = ntripClient.streamCorrections(
                        config = latestConfig,
                        attemptNumber = attempt,
                        ggaLocationProvider = {
                            locationProvider()?.takeIf { isUsableLocation(it) }
                        },
                        onStreamStarted = {
                            Log.i(
                                TAG,
                                "streaming started attempt=$attempt targetSys=$targetSystemId targetComp=$targetComponentId"
                            )
                            updateState(RtkForwardingState.Streaming)
                        },
                        onBytesReceived = { bytes ->
                            totalRawChunksReceived++
                            totalRawBytesReceived += bytes.size
                            maybeLogRawChunk(bytes.size)
                            val frames = rtcmParser.append(bytes)
                            if (frames.isNotEmpty()) {
                                Log.i(TAG, "complete RTCM frames extracted count=${frames.size}")
                            }
                            for (frame in frames) {
                                try {
                                    if (totalRtcmFramesForwarded == 0L || (totalRtcmFramesForwarded + 1) % 25L == 0L) {
                                        Log.i(
                                            TAG,
                                            "forwarding complete RTCM frame index=${totalRtcmFramesForwarded + 1} frameLength=${frame.size}"
                                        )
                                    }
                                    totalRtcmBytesForwarded += frame.size
                                    mavlinkClient.sendGpsRtcmData(targetSystemId, targetComponentId, frame)
                                    totalRtcmFramesForwarded++
                                    maybeLogRtcmProgress(frame.size)
                                } catch (e: Exception) {
                                    Log.e(
                                        TAG,
                                        "mavlink send failed type=${e.javaClass.simpleName} message=${e.message}",
                                        e
                                    )
                                    throw IllegalStateException(
                                        e.message ?: "Failed to send GPS_RTCM_DATA."
                                    )
                                }
                            }
                        }
                    )

                    when (result) {
                        is NtripResult.NetworkFailure -> {
                            if (!shouldKeepRunning()) {
                                Log.w(TAG, "reconnect abandoned: user stopped or drone disconnected")
                                updateState(RtkForwardingState.WaitingForDrone)
                                break
                            }
                            val retryDelayMs = 3000L
                            Log.w(
                                TAG,
                                "reconnect scheduled in ${retryDelayMs}ms attempt=${attempt + 1} reason=${result.message}"
                            )
                            updateState(RtkForwardingState.Reconnecting(result.message))
                            delay(retryDelayMs)
                            attempt++
                        }

                        is NtripResult.AuthFailure -> {
                            Log.w(TAG, "reconnect abandoned: auth failure")
                            updateState(RtkForwardingState.AuthFailed("Authentication failed."))
                            break
                        }

                        is NtripResult.MountpointNotFound -> {
                            Log.w(TAG, "reconnect abandoned: mountpoint not found")
                            updateState(RtkForwardingState.ProtocolError("Mountpoint not found."))
                            break
                        }

                        else -> {
                            updateState(mapResultToState(result))
                            break
                        }
                    }
                }
            } catch (_: CancellationException) {
                // stop() drives the visible state in this case
            } catch (e: Exception) {
                Log.e(TAG, "streaming failed: ${e.message}", e)
                updateState(
                    RtkForwardingState.ProtocolError(
                    e.message ?: "Failed to forward RTCM data."
                )
                )
            } finally {
                forwardingJob = null
            }
        }
    }

    fun stop(updateState: Boolean = true) {
        Log.i(TAG, "stop updateState=$updateState")
        forwardingJob?.cancel()
        forwardingJob = null
        rtcmParser.reset()
        if (updateState) {
            updateState(RtkForwardingState.Stopped)
        }
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    fun isRunning(): Boolean = forwardingJob?.isActive == true

    private fun updateState(newState: RtkForwardingState) {
        if (_state.value == newState) return
        Log.i(TAG, "state transition: ${_state.value.javaClass.simpleName} -> ${newState.javaClass.simpleName}")
        _state.value = newState
    }

    private fun isUsableLocation(location: Location): Boolean {
        return !location.latitude.isNaN() &&
            !location.longitude.isNaN() &&
            !(location.latitude == 0.0 && location.longitude == 0.0)
    }

    private fun maybeLogRtcmProgress(lastFrameSize: Int) {
        val now = System.currentTimeMillis()
        if (totalRtcmFramesForwarded == 1L ||
            totalRtcmFramesForwarded % 25L == 0L ||
            now - lastRtcmProgressLogMs >= 5000L
        ) {
            lastRtcmProgressLogMs = now
            Log.i(
                TAG,
                "rtcm progress totalRawBytes=$totalRawBytesReceived totalFrames=$totalRtcmFramesForwarded totalFrameBytes=$totalRtcmBytesForwarded lastFrameSize=$lastFrameSize"
            )
        }
    }

    private fun maybeLogRawChunk(chunkSize: Int) {
        val now = System.currentTimeMillis()
        if (totalRawChunksReceived == 1L ||
            totalRawChunksReceived % 25L == 0L ||
            now - lastRawChunkLogMs >= 5000L
        ) {
            lastRawChunkLogMs = now
            Log.i(
                TAG,
                "raw chunk received chunk=$totalRawChunksReceived size=$chunkSize totalRawBytes=$totalRawBytesReceived"
            )
        }
    }

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

}
