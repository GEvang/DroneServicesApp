package com.example.droneservicesapp.data.rtk

import android.location.Location
import android.content.Context
import android.os.PowerManager
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
import javax.net.SocketFactory
import java.util.concurrent.atomic.AtomicBoolean

class RtkForwardingService(
    context: Context,
    private val mavlinkClient: MavlinkClient,
    private val ntripClientFactory: () -> NtripClient = { NtripClient() },
    private val socketFactoryProvider: () -> SocketFactory? = { null }
) {
    companion object {
        private const val TAG = "RtkForwarding"
        private const val RTCM_TAG = "MavlinkRtcm"
        private const val RECONNECT_DELAY_MS = 3000L
        private const val RTCM_FRAME_PACING_DELAY_MS = 10L
    }

    private val rtkPreferences = RtkPreferences(context.applicationContext)
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<RtkForwardingState>(RtkForwardingState.Idle)
    private val lifecycleLock = Any()
    private val startInFlight = AtomicBoolean(false)

    private var forwardingJob: Job? = null
    private val rtcmParser = Rtcm3FrameParser()
    private var totalRawChunksReceived = 0L
    private var totalRawBytesReceived = 0L
    private var totalRtcmFramesForwarded = 0L
    private var lastRtcmProgressLogMs = 0L
    private var totalRtcmBytesForwarded = 0L
    private var lastRawChunkLogMs = 0L
    private var lastRtcmByteAtMs = 0L
    private var lastReconnectReason = ""

    val state: StateFlow<RtkForwardingState> = _state.asStateFlow()

    fun start(
        targetSystemId: Int,
        targetComponentId: Int,
        shouldKeepRunning: () -> Boolean = { true },
        locationProvider: () -> Location? = { null }
    ) {
        synchronized(lifecycleLock) {
            if (isStartOrStreamActiveLocked()) {
                when (_state.value) {
                    is RtkForwardingState.Streaming -> {
                        Log.i(TAG, "duplicate RTK start suppressed: already streaming")
                    }
                    is RtkForwardingState.ConnectingToCaster -> {
                        Log.i(TAG, "duplicate RTK start suppressed: connect already in flight")
                    }
                    is RtkForwardingState.Reconnecting,
                    is RtkForwardingState.WaitingForGps -> {
                        Log.i(TAG, "duplicate RTK start suppressed: reconnect already scheduled")
                    }
                    else -> Log.i(TAG, "duplicate RTK start suppressed: forwarding already active")
                }
                return
            }

            startInFlight.set(true)

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
                startInFlight.set(false)
                updateState(RtkForwardingState.InvalidConfig(reason))
                return
            }

            resetParser("starting new forwarding session")
            totalRawChunksReceived = 0L
            totalRawBytesReceived = 0L
            totalRtcmFramesForwarded = 0L
            totalRtcmBytesForwarded = 0L
            lastRtcmProgressLogMs = 0L
            lastRtcmByteAtMs = 0L
            lastReconnectReason = ""
            lateinit var launchedJob: Job
            launchedJob = scope.launch {
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
                            updateState(RtkForwardingState.WaitingForGps)
                            delay(2000L)
                            continue
                        }
                        Log.i(
                            TAG,
                            "gga location available=${ggaLocation != null} mountpoint=${latestConfig.mountpoint.trim()}"
                        )
                        resetParser("before attempt $attempt")
                        val ntripClient = ntripClientFactory()
                        Log.i(TAG, "new NtripClient instance created attempt=$attempt")

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
                            ggaDataProvider = {
                                locationProvider()?.takeIf { isUsableLocation(it) }?.let { location ->
                                    currentGgaData(location)
                                }
                            },
                            onStreamStarted = {
                                Log.i(
                                    TAG,
                                    "streaming started attempt=$attempt targetSys=$targetSystemId targetComp=$targetComponentId"
                                )
                                val powerManager = appContext.getSystemService(PowerManager::class.java)
                                if (powerManager?.isInteractive == false) {
                                    Log.i(TAG, "service entering streaming while screen off or device locked")
                                }
                                updateState(RtkForwardingState.Streaming)
                            },
                            socketFactory = socketFactoryProvider(),
                            onBytesReceived = { bytes ->
                                lastRtcmByteAtMs = System.currentTimeMillis()
                                totalRawChunksReceived++
                                totalRawBytesReceived += bytes.size
                                maybeLogRawChunk(bytes.size)
                                val frames = try {
                                    rtcmParser.append(bytes)
                                } catch (e: Exception) {
                                    Log.e(
                                        TAG,
                                        "RTCM parser failure, resetting parser state type=${e.javaClass.simpleName} message=${e.message}",
                                        e
                                    )
                                    resetParser("unexpected parser failure")
                                    emptyList()
                                }
                                if (frames.isNotEmpty()) {
                                    Log.i(TAG, "complete RTCM frames extracted count=${frames.size}")
                                }
                                for ((frameIndexInBurst, frame) in frames.withIndex()) {
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
                                        if (frameIndexInBurst < frames.lastIndex) {
                                            Log.i(
                                                RTCM_TAG,
                                                "pacing applied between frames delayMs=$RTCM_FRAME_PACING_DELAY_MS burstIndex=${frameIndexInBurst + 1}/${frames.size}"
                                            )
                                            Thread.sleep(RTCM_FRAME_PACING_DELAY_MS)
                                        }
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
                        Log.i(TAG, "old stream fully torn down attempt=$attempt result=${result.javaClass.simpleName}")

                        when (result) {
                            is NtripResult.NetworkFailure -> {
                                if (!shouldKeepRunning()) {
                                    Log.w(TAG, "reconnect abandoned: user stopped or drone disconnected")
                                    updateState(RtkForwardingState.WaitingForDrone)
                                    break
                                }
                                lastReconnectReason = result.message
                                Log.w(
                                    TAG,
                                    "reconnect scheduled in ${RECONNECT_DELAY_MS}ms attempt=${attempt + 1} reason=${result.message} lastRtcmAgeMs=${lastRtcmByteAgeMs()}"
                                )
                                updateState(RtkForwardingState.Reconnecting(result.message))
                                delay(RECONNECT_DELAY_MS)
                                attempt++
                            }

                            is NtripResult.AuthFailure -> {
                                Log.w(TAG, "reconnect abandoned: auth failure")
                                updateState(RtkForwardingState.AuthFailed("Authentication failed."))
                                break
                            }

                            is NtripResult.MountpointNotFound -> {
                                Log.w(TAG, "reconnect abandoned: mountpoint not found")
                                updateState(RtkForwardingState.MountpointInvalid("Mountpoint not found."))
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
                    Log.i(TAG, "forwarding loop finished lastReconnectReason=${lastReconnectReason.ifBlank { "--" }}")
                    synchronized(lifecycleLock) {
                        if (forwardingJob === launchedJob) {
                            forwardingJob = null
                        }
                        startInFlight.set(false)
                    }
                }
            }
            forwardingJob = launchedJob
        }
    }

    fun stop(updateState: Boolean = true) {
        Log.i(TAG, "stop updateState=$updateState")
        RtkKeepAliveForegroundService.setWakeActive(appContext, false)
        synchronized(lifecycleLock) {
            startInFlight.set(false)
            forwardingJob?.cancel()
            forwardingJob = null
        }
        resetParser("stop")
        if (updateState) {
            updateState(RtkForwardingState.Stopped)
        }
    }

    fun shutdown() {
        stop()
        RtkKeepAliveForegroundService.stopSession(appContext)
        scope.cancel()
    }

    fun isRunning(): Boolean = synchronized(lifecycleLock) { forwardingJob?.isActive == true }

    fun isStartingOrRunning(): Boolean = synchronized(lifecycleLock) {
        startInFlight.get() || forwardingJob?.isActive == true
    }

    private fun isStartOrStreamActiveLocked(): Boolean {
        if (startInFlight.get() || forwardingJob?.isActive == true) return true
        return when (_state.value) {
            is RtkForwardingState.ConnectingToCaster,
            is RtkForwardingState.Streaming -> true
            else -> false
        }
    }

    private fun updateState(newState: RtkForwardingState) {
        if (_state.value == newState) return
        Log.i(TAG, "state transition: ${_state.value.javaClass.simpleName} -> ${newState.javaClass.simpleName}")
        syncWakeState(newState)
        _state.value = newState
    }

    private fun syncWakeState(state: RtkForwardingState) {
        val wakeActive =
            state is RtkForwardingState.ConnectingToCaster ||
                state is RtkForwardingState.Reconnecting ||
                state is RtkForwardingState.Streaming
        RtkKeepAliveForegroundService.setWakeActive(appContext, wakeActive)
    }

    private fun resetParser(reason: String) {
        rtcmParser.reset()
        Log.i(TAG, "parser reset reason=$reason")
    }

    private fun lastRtcmByteAgeMs(): Long {
        if (lastRtcmByteAtMs <= 0L) return -1L
        return System.currentTimeMillis() - lastRtcmByteAtMs
    }

    private fun currentGgaData(location: Location): NmeaGgaBuilder.GgaData {
        val gpsStatus = rtkPreferences.getGpsStatus()
        return NmeaGgaBuilder.GgaData(
            location = location,
            fixType = gpsStatus.fixType,
            satellites = gpsStatus.satellitesVisible,
            hdop = gpsStatus.hdop
        )
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
                RtkForwardingState.MountpointInvalid("Mountpoint not found.")
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
