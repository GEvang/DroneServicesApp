package com.example.droneservicesapp.mavserver

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.mavlink.MavlinkClient
import com.example.droneservicesapp.data.diagnostics.DiagnosticLog
import com.example.droneservicesapp.data.rtk.RtkConfig
import com.example.droneservicesapp.data.rtk.RtkForwardingService
import com.example.droneservicesapp.data.rtk.RtkForwardingState
import com.example.droneservicesapp.data.rtk.RtkInternetMonitor
import com.example.droneservicesapp.data.rtk.RtkKeepAliveForegroundService
import com.example.droneservicesapp.data.rtk.RtkMountpoint
import com.example.droneservicesapp.data.rtk.RtkPreferences
import com.example.droneservicesapp.data.rtk.RtkValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale

internal class DroneRtkController(
    private val context: Context,
    private val mavlinkClient: MavlinkClient,
    private val rtkForwardingState: MutableLiveData<RtkForwardingState>,
    private val selectedRtkMountpoint: MutableLiveData<RtkMountpoint?>,
    private val rtkGpsDebugStatus: MutableLiveData<String>,
    private val isConnected: () -> Boolean,
    private val targetSystemId: () -> Int,
    private val targetComponentId: () -> Int,
    private val lastDroneLocation: () -> Location?,
) {
    companion object {
        private const val TAG = "DroneViewModel"
        private const val GPS_TAG = "ArduPilotGps"
        private const val GPS_LOG_INTERVAL_MS = 5000L
        private const val RTK_AUTO_START_SUPPRESS_LOG_INTERVAL_MS = 5000L
    }

    private val rtkPreferences: RtkPreferences by lazy { RtkPreferences(context) }
    private val rtkInternetMonitor: RtkInternetMonitor by lazy { RtkInternetMonitor(context) }
    private val rtkForwardingService: RtkForwardingService by lazy {
        RtkForwardingService(
            context,
            mavlinkClient,
            socketFactoryProvider = { rtkInternetMonitor.currentInternetSocketFactory() },
            networkProvider = { rtkInternetMonitor.currentInternetNetwork() }
        )
    }

    @Volatile private var internetAvailable = false
    @Volatile private var lastGpsLogSummary: String? = null
    @Volatile private var lastGpsLogMs: Long = 0L
    @Volatile private var lastGpsMessageTimeMs: Long = 0L
    @Volatile private var lastConnectionDiagnosticState: Boolean? = null
    @Volatile private var lastFixType: Int = -1
    @Volatile private var lastSatellitesVisible: Int = -1
    @Volatile private var lastGpsSource: String = "--"
    @Volatile private var lastGpsEph: Int = -1
    @Volatile private var lastGpsEpv: Int = -1
    @Volatile private var lastRtkAutoStartSuppressLogMs: Long = 0L

    fun bind(scope: CoroutineScope) {
        internetAvailable = rtkInternetMonitor.isInternetAvailable.value
        selectedRtkMountpoint.postValue(currentRtkConfig().selectedMountpoint)

        scope.launch {
            rtkForwardingService.state.collect { state ->
                rtkForwardingState.postValue(state)
                if (state is RtkForwardingState.Stopped ||
                    state is RtkForwardingState.WaitingForMountpoint ||
                    state is RtkForwardingState.InvalidConfig ||
                    state is RtkForwardingState.AuthFailed ||
                    state is RtkForwardingState.MountpointInvalid ||
                    state is RtkForwardingState.ProtocolError
                ) {
                    RtkKeepAliveForegroundService.stopSession(context)
                }
            }
        }

        scope.launch {
            rtkInternetMonitor.isInternetAvailable.collect { available ->
                if (internetAvailable == available) return@collect
                internetAvailable = available
                Log.i(TAG, "internet availability changed available=$available")
                DiagnosticLog.event(
                    module = "rtk",
                    message = if (available) "internet_available" else "internet_lost",
                    severity = if (available) "INFO" else "WARN"
                )
                ensureRtkForwardingState()
            }
        }
    }

    fun onRtkConfigurationChanged(forceStart: Boolean = false) {
        val config = currentRtkConfig()
        selectedRtkMountpoint.postValue(config.selectedMountpoint)
        Log.i(
            TAG,
            "onRtkConfigurationChanged mountpoint=${config.mountpoint.trim()} desired=${isRtkDesired(config)} baseValid=${RtkValidator.isValidBaseConfig(config)}"
        )
        DiagnosticLog.event(
            module = "rtk",
            message = "configuration_changed",
            data = mapOf(
                "mountpoint" to config.mountpoint.trim(),
                "host" to config.ip.trim(),
                "port" to config.port,
                "valid" to RtkValidator.isValidConfig(config),
                "forceStart" to forceStart
            )
        )
        ensureRtkForwardingState(forceStart = forceStart)
    }

    fun currentRtkSocketFactory() = rtkInternetMonitor.currentInternetSocketFactory()

    fun currentRtkInternetNetwork() = rtkInternetMonitor.currentInternetNetwork()

    fun reportRtkStartBlocked(message: String) {
        Log.w(TAG, "startRtkForwarding blocked: $message")
        RtkKeepAliveForegroundService.stopSession(context)
        rtkForwardingState.postValue(RtkForwardingState.InvalidConfig(message))
    }

    fun stopRtkForwarding(clearRequest: Boolean = true) {
        Log.i(
            TAG,
            "stopRtkForwarding called clearRequest=$clearRequest connected=${isConnected()} sys=${targetSystemId()} comp=${targetComponentId()}"
        )
        rtkForwardingService.stop()
        RtkKeepAliveForegroundService.stopSession(context)
        rtkForwardingState.postValue(RtkForwardingState.Stopped)
    }

    fun shouldKeepRtkAliveInBackground(): Boolean {
        return isRtkDesired(currentRtkConfig()) || rtkForwardingService.isRunning()
    }

    fun isStreaming(): Boolean = rtkForwardingService.isRunning()

    fun stopStreamingForMavlinkRestart() {
        if (rtkForwardingService.isRunning()) {
            rtkForwardingService.stop(updateState = false)
        }
    }

    fun onAutopilotHeartbeatLocked() {
        Log.i(TAG, "RTK auto-start check triggered by first autopilot heartbeat")
        ensureRtkForwardingState()
    }

    fun onDroneLocationUpdated() {
        if (isRtkDesired(currentRtkConfig())) {
            if (shouldSkipRedundantRtkAutoStartCheck("drone GPS update")) {
                maybeLogRtkAutoStartSuppressed("drone GPS update")
            } else {
                Log.i(TAG, "RTK auto-start check triggered by drone GPS update")
                ensureRtkForwardingState()
            }
        }
    }

    fun onConnectionStateEvaluated(connected: Boolean) {
        if (lastConnectionDiagnosticState != connected) {
            lastConnectionDiagnosticState = connected
            DiagnosticLog.event(
                module = "mavlink",
                message = if (connected) "connection_healthy" else "connection_lost",
                severity = if (connected) "INFO" else "WARN",
                data = mapOf("lastHeartbeatAgeMs" to (System.currentTimeMillis() - mavlinkClient.lastHeartbeatMs))
            )
        }
        if (!connected) {
            if (rtkForwardingService.isRunning()) {
                Log.w(TAG, "RTK waiting: drone disconnected during forwarding")
                rtkForwardingService.stop(updateState = false)
                rtkForwardingState.postValue(RtkForwardingState.WaitingForDrone)
            }
        } else if (isRtkDesired(currentRtkConfig())) {
            if (shouldSkipRedundantRtkAutoStartCheck("healthy MAVLink ticker")) {
                maybeLogRtkAutoStartSuppressed("healthy MAVLink ticker")
            } else {
                Log.i(TAG, "RTK auto-start check triggered by healthy MAVLink ticker")
                ensureRtkForwardingState()
            }
        }

        updateGpsDebugStatus()
    }

    fun handleGpsDebugMessage(
        source: String,
        fixType: Int,
        satellitesVisible: Int,
        eph: Int,
        epv: Int,
    ) {
        val now = System.currentTimeMillis()
        val previousFixType = lastFixType
        lastGpsMessageTimeMs = now
        lastFixType = fixType
        lastSatellitesVisible = satellitesVisible
        lastGpsSource = source
        lastGpsEph = eph
        lastGpsEpv = epv
        rtkPreferences.saveGpsStatus(
            fixType = fixType,
            satellitesVisible = satellitesVisible,
            hdop = eph.takeIf { it >= 0 && it != 65535 }?.div(100.0)
        )
        if (previousFixType == 5 && fixType == 4 && isHealthyRtkStreamActive()) {
            Log.w(
                GPS_TAG,
                "GPS fix degraded 5->4 while RTK stream healthy source=$source sats=$satellitesVisible eph=$eph epv=$epv"
            )
        }
        if (previousFixType == 6 && fixType == 4) {
            Log.w(GPS_TAG, "GPS fix changed 6->4 source=$source")
        }
        if (previousFixType == 4 && fixType == 6) {
            Log.i(GPS_TAG, "GPS fix changed 4->6 source=$source")
        }
        val summary = "source=$source fixType=$fixType sats=$satellitesVisible eph=$eph epv=$epv"
        if (summary != lastGpsLogSummary) {
            DiagnosticLog.event(
                module = "rtk",
                message = "gps_status_changed",
                severity = if (fixType < 5) "WARN" else "INFO",
                data = mapOf("source" to source, "fixType" to fixType, "satellitesVisible" to satellitesVisible, "eph" to eph, "epv" to epv)
            )
        }
        if (summary != lastGpsLogSummary || now - lastGpsLogMs >= GPS_LOG_INTERVAL_MS) {
            lastGpsLogSummary = summary
            lastGpsLogMs = now
            Log.i(GPS_TAG, summary)
        }
        updateGpsDebugStatus()
    }

    fun shutdown() {
        rtkForwardingService.shutdown()
        rtkInternetMonitor.shutdown()
        RtkKeepAliveForegroundService.stopSession(context)
    }

    private fun ensureRtkForwardingState(forceStart: Boolean = false) {
        val config = currentRtkConfig()
        selectedRtkMountpoint.postValue(config.selectedMountpoint)
        val desired = isRtkDesired(config)
        val connected = isConnected()
        val targetReady = targetSystemId() >= 0 && targetComponentId() >= 0
        val currentState = rtkForwardingState.value
        Log.i(
            TAG,
            "ensureRtkForwardingState forceStart=$forceStart desired=$desired internet=$internetAvailable connected=$connected targetReady=$targetReady sys=${targetSystemId()} comp=${targetComponentId()}"
        )

        when {
            !RtkValidator.isValidMountpoint(config.mountpoint) -> {
                if (rtkForwardingService.isRunning()) {
                    rtkForwardingService.stop(updateState = false)
                }
                RtkKeepAliveForegroundService.stopSession(context)
                rtkForwardingState.postValue(RtkForwardingState.WaitingForMountpoint)
            }
            !RtkValidator.isValidBaseConfig(config) -> {
                if (rtkForwardingService.isRunning()) {
                    rtkForwardingService.stop(updateState = false)
                }
                RtkKeepAliveForegroundService.stopSession(context)
                rtkForwardingState.postValue(
                    RtkForwardingState.InvalidConfig("RTK settings are incomplete.")
                )
            }
            !desired -> {
                if (rtkForwardingService.isRunning()) {
                    rtkForwardingService.stop(updateState = false)
                }
                RtkKeepAliveForegroundService.stopSession(context)
                rtkForwardingState.postValue(RtkForwardingState.Idle)
            }
            !internetAvailable -> {
                if (rtkForwardingService.isRunning()) {
                    rtkForwardingService.stop(updateState = false)
                }
                RtkKeepAliveForegroundService.startSession(context)
                Log.i(TAG, "waiting-state reason=no internet")
                rtkForwardingState.postValue(RtkForwardingState.WaitingForInternet)
            }
            !connected -> {
                if (rtkForwardingService.isRunning()) {
                    rtkForwardingService.stop(updateState = false)
                }
                RtkKeepAliveForegroundService.startSession(context)
                Log.w(TAG, "RTK start waiting: drone not connected")
                rtkForwardingState.postValue(RtkForwardingState.WaitingForDrone)
            }
            !targetReady -> {
                if (rtkForwardingService.isRunning()) {
                    rtkForwardingService.stop(updateState = false)
                }
                RtkKeepAliveForegroundService.startSession(context)
                Log.w(TAG, "RTK waiting: missing autopilot target")
                rtkForwardingState.postValue(RtkForwardingState.WaitingForDrone)
            }
            requiresDroneGps(config) && lastDroneLocation()?.let { isUsableLocation(it) } != true -> {
                if (rtkForwardingService.isRunning()) {
                    rtkForwardingService.stop(updateState = false)
                }
                RtkKeepAliveForegroundService.startSession(context)
                Log.w(TAG, "RTK waiting: NEAR mountpoint requires GPS")
                rtkForwardingState.postValue(RtkForwardingState.WaitingForGps)
            }
            !rtkForwardingService.isStartingOrRunning() && (
                forceStart ||
                    currentState == null ||
                    currentState is RtkForwardingState.Idle ||
                    currentState is RtkForwardingState.WaitingForMountpoint ||
                    currentState is RtkForwardingState.WaitingForInternet ||
                    currentState is RtkForwardingState.WaitingForDrone ||
                    currentState is RtkForwardingState.WaitingForGps ||
                    currentState is RtkForwardingState.Reconnecting ||
                    currentState is RtkForwardingState.NetworkError ||
                    currentState is RtkForwardingState.Stopped
                ) -> {
                Log.i(
                    TAG,
                    "automatic RTK start due to mountpoint + internet + drone readiness mountpoint=${config.mountpoint.trim()} mavHeartbeatAgeMs=${System.currentTimeMillis() - mavlinkClient.lastHeartbeatMs}"
                )
                RtkKeepAliveForegroundService.startSession(context)
                rtkForwardingService.start(
                    targetSystemId = targetSystemId(),
                    targetComponentId = targetComponentId(),
                    shouldKeepRunning = {
                        isRtkDesired(currentRtkConfig()) &&
                            internetAvailable &&
                            isConnected() &&
                            targetSystemId() >= 0 &&
                            targetComponentId() >= 0 &&
                            (!requiresDroneGps(currentRtkConfig()) ||
                                lastDroneLocation()?.let { isUsableLocation(it) } == true)
                    },
                    locationProvider = {
                        lastDroneLocation()?.let { location -> Location(location) }
                    }
                )
            }
        }
    }

    private fun currentRtkConfig(): RtkConfig = rtkPreferences.getConfig()

    private fun isRtkDesired(config: RtkConfig): Boolean = RtkValidator.isValidConfig(config)

    private fun requiresDroneGps(config: RtkConfig): Boolean {
        return config.mountpoint.trim().equals("NEAR", ignoreCase = true)
    }

    private fun isUsableLocation(location: Location): Boolean {
        return !location.latitude.isNaN() &&
            !location.longitude.isNaN() &&
            !(location.latitude == 0.0 && location.longitude == 0.0)
    }

    private fun shouldSkipRedundantRtkAutoStartCheck(trigger: String): Boolean {
        val config = currentRtkConfig()
        val targetReady = targetSystemId() >= 0 && targetComponentId() >= 0
        val hasRequiredGps =
            !requiresDroneGps(config) || lastDroneLocation()?.let { isUsableLocation(it) } == true
        val streaming = rtkForwardingService.isRunning() &&
            rtkForwardingState.value is RtkForwardingState.Streaming

        val skip = streaming &&
            isRtkDesired(config) &&
            internetAvailable &&
            isConnected() &&
            targetReady &&
            hasRequiredGps

        if (!skip) return false
        Log.v(TAG, "suppressing redundant RTK auto-start check trigger=$trigger")
        return true
    }

    private fun maybeLogRtkAutoStartSuppressed(trigger: String) {
        val now = System.currentTimeMillis()
        if (now - lastRtkAutoStartSuppressLogMs < RTK_AUTO_START_SUPPRESS_LOG_INTERVAL_MS) return
        lastRtkAutoStartSuppressLogMs = now
        Log.i(TAG, "RTK auto-start check suppressed while streaming healthy trigger=$trigger")
    }

    private fun isHealthyRtkStreamActive(): Boolean {
        return rtkForwardingService.isRunning() &&
            rtkForwardingState.value is RtkForwardingState.Streaming &&
            internetAvailable &&
            isConnected()
    }

    private fun updateGpsDebugStatus() {
        val lastGpsAge = if (lastGpsMessageTimeMs > 0L) {
            "${((System.currentTimeMillis() - lastGpsMessageTimeMs) / 1000L)}s ago"
        } else {
            "--"
        }
        val hdop = formatDop(lastGpsEph)
        val ephText = if (lastGpsEph >= 0 && lastGpsEph != 65535) lastGpsEph.toString() else "--"
        val source = lastGpsSource.ifBlank { "--" }
        val fix = formatGpsMetric(lastFixType)
        val sats = formatGpsMetric(lastSatellitesVisible)
        rtkGpsDebugStatus.postValue(
            context.getString(
                R.string.rtk_gps_debug_summary,
                source,
                fix,
                sats,
                hdop,
                ephText,
                lastGpsAge
            )
        )
    }

    private fun formatGpsMetric(value: Int): String {
        return if (value >= 0 && value != 255 && value != 65535) value.toString() else "--"
    }

    private fun formatDop(value: Int): String {
        if (value < 0 || value == 65535) return "--"
        return String.format(Locale.US, "%.2f", value / 100.0)
    }
}
