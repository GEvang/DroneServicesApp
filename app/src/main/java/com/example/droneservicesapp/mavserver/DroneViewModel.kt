package com.example.droneservicesapp.mavserver

import android.location.Location
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.droneservicesapp.Application
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.data.geoawareness.logging.OperatorFlightEventLogger
import com.example.droneservicesapp.data.diagnostics.DiagnosticLog
import com.example.droneservicesapp.data.mavlink.MavlinkClient
import com.example.droneservicesapp.data.mavlink.MavlinkConfig
import com.example.droneservicesapp.data.mavlink.MavlinkConnectionManager
import com.example.droneservicesapp.data.mavlink.MissionService
import com.example.droneservicesapp.data.rtk.RtkForwardingState
import com.example.droneservicesapp.data.rtk.RtkMountpoint
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.CommandLong
import io.dronefleet.mavlink.common.GpsFixType
import io.dronefleet.mavlink.common.MavCmd
import io.dronefleet.mavlink.common.MissionItemInt
import io.dronefleet.mavlink.minimal.Heartbeat
import io.dronefleet.mavlink.minimal.MavAutopilot
import io.dronefleet.mavlink.minimal.MavModeFlag
import io.dronefleet.mavlink.minimal.MavState
import io.dronefleet.mavlink.minimal.MavType
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class DroneViewModel : ViewModel() {

    companion object {
        private const val TAG = "DroneViewModel"
        private const val MAPPING_TAG = "TelemetryMapping"
        private const val CONNECTION_TICK_MS = 500L
        private const val HEARTBEAT_STALE_MS = 2500L
        private const val TELEMETRY_STALE_MS = 2500L
        private const val MISSION_DEBOUNCE_MS = 1500L
        private const val UPLOAD_TIMEOUT_MS = 8000L
        private const val GCS_SYSTEM_ID = 255
        private const val GCS_COMPONENT_ID = 190
        private const val MAVLINK_SYSTEM_ALL = 0
        private const val MAVLINK_COMPONENT_ALL = 0
        private const val SERVO_OUTPUT_RAW_MESSAGE_ID = 36
        private const val SERVO_OUTPUT_RAW_INTERVAL_US = 200_000f
        private const val NORMAL_HEALTH_SNAPSHOT_INTERVAL_MS = 10_000L
        private const val INCIDENT_HEALTH_SNAPSHOT_INTERVAL_MS = 1_000L
        private const val INCIDENT_SNAPSHOT_WINDOW_MS = 5 * 60_000L
    }

    private val repoDisposables = CompositeDisposable()
    private val runtimeState = DroneRuntimeState()
    private val stateStore = DroneUiStateStore(Application.getInstance().applicationContext)
    private val eventLogger = GeoAwarenessEventLogger(Application.getInstance().applicationContext)
    private val operatorEventLogger = OperatorFlightEventLogger(eventLogger)

    private val repo = MavlinkConnectionManager()
    val mavlinkClient: MavlinkClient = repo
    private var activeMavlinkConfigKey: String? = null
    @Volatile private var lastHealthSnapshotMs = 0L
    @Volatile private var highFrequencySnapshotsUntilMs = 0L
    @Volatile private var flightSessionStartedMs = 0L
    @Volatile private var lastSnapshotRtkStreaming: Boolean? = null

    private val missionService: MissionService by lazy { MissionService(mavlinkClient) }
    private val missionController: DroneMissionController by lazy {
        DroneMissionController(
            missionService = missionService,
            missionItems = stateStore.missionItems,
            uploadProgressPercent = stateStore.uploadProgressPercent,
            repoDisposables = repoDisposables
        )
    }
    private val rtkController: DroneRtkController by lazy {
        DroneRtkController(
            context = Application.getInstance().applicationContext,
            mavlinkClient = mavlinkClient,
            rtkForwardingState = stateStore.rtkForwardingState,
            selectedRtkMountpoint = stateStore.selectedRtkMountpoint,
            rtkGpsDebugStatus = stateStore.rtkGpsDebugStatus,
            isConnected = { stateStore.conStateLiveData.value == true },
            targetSystemId = { runtimeState.autopilotSysId },
            targetComponentId = { runtimeState.autopilotCompId },
            lastDroneLocation = { runtimeState.lastDroneLocation }
        )
    }
    private val telemetryProcessor: DroneTelemetryProcessor by lazy {
        DroneTelemetryProcessor(
            stateStore = stateStore,
            runtimeState = runtimeState,
            updateMissionTargets = { systemId, componentId ->
                missionController.updateTargetIds(systemId, componentId)
            },
            onAutopilotHeartbeatLocked = {
                requestServoOutputRawStream()
                rtkController.onAutopilotHeartbeatLocked()
                parameterController.refreshAll()
            },
            onDroneLocationUpdated = {
                rtkController.onDroneLocationUpdated()
            },
            onGpsDebugMessage = { source, fixType, satellitesVisible, eph, epv ->
                rtkController.handleGpsDebugMessage(
                    source = source,
                    fixType = fixType,
                    satellitesVisible = satellitesVisible,
                    eph = eph,
                    epv = epv
                )
            },
            onArmedStateChanged = { armed, location, altitude, flightMode ->
                val position = location?.let { LatLon(it.latitude, it.longitude) }
                if (armed) {
                    operatorEventLogger.logDroneArmed(position, altitude, flightMode)
                } else {
                    operatorEventLogger.logDroneDisarmed(position, altitude, flightMode)
                }
                recordFlightSessionBoundary(armed, flightMode)
            },
            onFlightModeChanged = { previous, current ->
                operatorEventLogger.logFlightModeChanged(previous, current)
                if (current == "6") {
                    operatorEventLogger.logRtlDetected()
                }
            },
            onGpsFixChanged = { acquired ->
                eventLogger.logSimple(
                    type = if (acquired) GeoAwarenessEventType.GPS_FIX_ACQUIRED else GeoAwarenessEventType.GPS_FIX_LOST,
                    severity = if (acquired) "INFO" else "WARNING",
                    message = if (acquired) "GPS fix acquired" else "GPS fix lost",
                    category = "TELEMETRY"
                )
            },
            onBatteryLow = { percent ->
                operatorEventLogger.logBatteryLow(percent)
            },
            onTakeoffDetected = { location, altitude ->
                val position = location?.let { LatLon(it.latitude, it.longitude) }
                operatorEventLogger.logTakeoffDetected(position, altitude)
            },
            onLandingDetected = { location, altitude ->
                val position = location?.let { LatLon(it.latitude, it.longitude) }
                operatorEventLogger.logLandingDetected(position, altitude)
            }
        )
    }
    private val parameterController: DroneParameterController by lazy {
        DroneParameterController(
            mavlinkClient = mavlinkClient,
            isConnected = { stateStore.conStateLiveData.value == true },
            targetSystemId = { runtimeState.autopilotSysId },
            targetComponentId = { runtimeState.autopilotCompId }
        )
    }

    val droneLocationLiveData: MutableLiveData<Location> = stateStore.droneLocationLiveData
    val conStateLiveData: MutableLiveData<Boolean> = stateStore.conStateLiveData
    val telemetryAliveLiveData: MutableLiveData<Boolean> = stateStore.telemetryAliveLiveData
    val armedState: MutableLiveData<Boolean> = stateStore.armedState
    val droneHeading: MutableLiveData<Double> = stateStore.droneHeading
    val droneBatteryVoltage: MutableLiveData<Float> = stateStore.droneBatteryVoltage
    val droneBatteryPercentage: MutableLiveData<Float> = stateStore.droneBatteryPercentage
    val gpsFixType: MutableLiveData<GpsFixType?> = stateStore.gpsFixType
    val droneGroundSpeedMetersPerSecond: MutableLiveData<Float> = stateStore.droneGroundSpeedMetersPerSecond
    val droneVerticalSpeedMetersPerSecond: MutableLiveData<Float?> = stateStore.droneVerticalSpeedMetersPerSecond
    val droneAltitudeAmslMeters: MutableLiveData<Double?> = stateStore.droneAltitudeAmslMeters
    val droneFrontDistance: MutableLiveData<Int> = stateStore.droneFrontDistance
    val droneBackDistance: MutableLiveData<Int> = stateStore.droneBackDistance
    val droneFlightMode: MutableLiveData<Int> = stateStore.droneFlightMode
    val rcRSSI: MutableLiveData<Float> = stateStore.rcRSSI
    val missionItems: MutableLiveData<ArrayList<MissionItemInt>> = stateStore.missionItems
    val liquidLevel: MutableLiveData<Float> = stateStore.liquidLevel
    val servo5OutputRaw: MutableLiveData<Int?> = stateStore.servo5OutputRaw
    val uploadProgressPercent: MutableLiveData<Int> = stateStore.uploadProgressPercent
    val rtkForwardingState: MutableLiveData<RtkForwardingState> = stateStore.rtkForwardingState
    val selectedRtkMountpoint: MutableLiveData<RtkMountpoint?> = stateStore.selectedRtkMountpoint
    val rtkGpsDebugStatus: MutableLiveData<String> = stateStore.rtkGpsDebugStatus
    val terrainEnableParameter = parameterController.state(DroneParameterController.TERRAIN_ENABLE)
    val waypointRangefinderParameter = parameterController.state(DroneParameterController.WP_RFND_USE)
    val surfaceTrackingParameter = parameterController.state(DroneParameterController.SURFTRAK_MODE)
    val avoidanceParameter = parameterController.state(DroneParameterController.AVOID_ENABLE)
    val parameterFeedback = parameterController.feedback

    init {
        rtkController.bind(viewModelScope)
    }

    fun getTargetSystemId(): Int = runtimeState.autopilotSysId

    fun getTargetComponentId(): Int = runtimeState.autopilotCompId

    fun refreshTerrainFollowingParameters() {
        parameterController.refreshAll()
    }

    fun setWaypointRangefinderEnabled(enabled: Boolean) {
        parameterController.setValue(DroneParameterController.WP_RFND_USE, if (enabled) 1 else 0)
    }

    /**
     * Terrain-altitude mission items require ArduPilot's terrain database, not the
     * WP_RFND_USE rangefinder preference. This starts/continues the bounded PARAM
     * transaction and returns a state the upload UI can explain to the operator.
     */
    fun terrainMissionReadiness(): TerrainMissionReadiness {
        val state = terrainEnableParameter.value ?: VehicleParameterUiState()
        return when {
            state.availability == VehicleParameterAvailability.UNSUPPORTED -> TerrainMissionReadiness.UNSUPPORTED
            state.result == VehicleParameterResult.ERROR -> TerrainMissionReadiness.REJECTED
            state.availability == VehicleParameterAvailability.SUPPORTED &&
                !state.isWriting && state.value == 1 -> TerrainMissionReadiness.READY
            else -> {
                parameterController.setDesiredValue(DroneParameterController.TERRAIN_ENABLE, 1)
                TerrainMissionReadiness.CHECKING
            }
        }
    }

    fun setAvoidanceEnabled(enabled: Boolean) {
        // AVOID_ENABLE is a bitmask. Restore the vehicle's previous non-zero selection,
        // or enable all three supported sources when it has not supplied one yet.
        parameterController.setEnabledBitmask(DroneParameterController.AVOID_ENABLE, enabled, defaultOnValue = 7)
    }

    fun setSurfaceTrackingMode(mode: Int) {
        require(mode in 0..2) { "Surface tracking mode must be 0, 1, or 2." }
        parameterController.setValue(DroneParameterController.SURFTRAK_MODE, mode)
    }

    fun setSurfaceTrackingEnabled(enabled: Boolean) {
        parameterController.setValue(DroneParameterController.SURFTRAK_MODE, if (enabled) 1 else 0)
    }

    fun sendDebugSprayerServoPwm(pwm: Int): Boolean {
        if (stateStore.conStateLiveData.value != true || runtimeState.autopilotSysId == -1) {
            Log.w(TAG, "debug sprayer command skipped: no active MAVLink target")
            return false
        }

        val targetSystemId = runtimeState.autopilotSysId
        val targetComponentId = runtimeState.autopilotCompId.takeIf { it >= 0 } ?: MAVLINK_COMPONENT_ALL
        val command = CommandLong.builder()
            .targetSystem(targetSystemId)
            .targetComponent(targetComponentId)
            .command(MavCmd.MAV_CMD_DO_SET_SERVO)
            .confirmation(0)
            .param1(5.0f)
            .param2(pwm.coerceIn(0, 3000).toFloat())
            .param3(0.0f)
            .param4(0.0f)
            .param5(0.0f)
            .param6(0.0f)
            .param7(0.0f)
            .build()

        mavlinkClient.send2(GCS_SYSTEM_ID, GCS_COMPONENT_ID, command)
        Log.i(
            "SprayerDebug",
            "TX COMMAND_LONG DO_SET_SERVO targetSys=$targetSystemId targetComp=$targetComponentId senderSys=$GCS_SYSTEM_ID senderComp=$GCS_COMPONENT_ID channel=5 pwm=${command.param2()}"
        )
        return true
    }

    /** Operator marker for a visible anomaly; preserves five minutes of one-second snapshots. */
    fun markDiagnosticIncident() {
        val now = System.currentTimeMillis()
        startHighFrequencyCapture("operator_mark", now)
        DiagnosticLog.event(
            module = "flight",
            message = "operator_incident_marked",
            severity = "WARN",
            data = mapOf("highFrequencyWindowSeconds" to (INCIDENT_SNAPSHOT_WINDOW_MS / 1000L))
        )
        recordFlightHealthSnapshot(now, reason = "operator_mark")
    }

    private fun sendGcsHeartbeat() {
        val heartbeat = Heartbeat.builder()
            .type(MavType.MAV_TYPE_GCS)
            .autopilot(MavAutopilot.MAV_AUTOPILOT_INVALID)
            .baseMode(MavModeFlag.MAV_MODE_FLAG_CUSTOM_MODE_ENABLED)
            .customMode(0)
            .systemStatus(MavState.MAV_STATE_ACTIVE)
            .mavlinkVersion(3)
            .build()

        mavlinkClient.send2(GCS_SYSTEM_ID, GCS_COMPONENT_ID, heartbeat)
        Log.i("SprayerDebug", "TX GCS HEARTBEAT senderSys=$GCS_SYSTEM_ID senderComp=$GCS_COMPONENT_ID")
    }

    fun requestServoOutputRawStream(): Boolean {
        val targetSystemId = runtimeState.autopilotSysId
        val targetComponentId = runtimeState.autopilotCompId
        if (stateStore.conStateLiveData.value != true || targetSystemId == -1 || targetComponentId == -1) {
            Log.w(TAG, "servo output stream request skipped: no active MAVLink target")
            return false
        }

        val command = CommandLong.builder()
            .targetSystem(targetSystemId)
            .targetComponent(MAVLINK_COMPONENT_ALL)
            .command(MavCmd.MAV_CMD_SET_MESSAGE_INTERVAL)
            .confirmation(0)
            .param1(SERVO_OUTPUT_RAW_MESSAGE_ID.toFloat())
            .param2(SERVO_OUTPUT_RAW_INTERVAL_US)
            .param3(0.0f)
            .param4(0.0f)
            .param5(0.0f)
            .param6(0.0f)
            .param7(0.0f)
            .build()

        mavlinkClient.send2(GCS_SYSTEM_ID, GCS_COMPONENT_ID, command)
        Log.i("SprayerDebug", "TX request SERVO_OUTPUT_RAW intervalUs=$SERVO_OUTPUT_RAW_INTERVAL_US")
        return true
    }

    fun startMavlink(config: MavlinkConfig) {
        Log.i(TAG, "connect requested via startMavlink config=$config")
        DiagnosticLog.event("mavlink", "connection_requested", data = mapOf(
            "interface" to config.interfaceType.name,
            "port" to config.port,
            "targetHostConfigured" to !config.targetHost.isNullOrBlank(),
            "targetPort" to config.targetPort
        ))
        mavlinkClient.restart(config)
        activeMavlinkConfigKey = config.toConnectionKey()
        attachRepositoryBridge()
    }

    fun onAppForegrounded(config: MavlinkConfig) {
        val newConfigKey = config.toConnectionKey()
        val configChanged = activeMavlinkConfigKey != newConfigKey
        Log.i(
            TAG,
            "foreground transition keepAlive=${shouldKeepRtkAliveInBackground()} healthy=${isMavlinkSessionHealthy()} configChanged=$configChanged lastConfig=$activeMavlinkConfigKey newConfig=$newConfigKey lastHeartbeatMs=${mavlinkClient.lastHeartbeatMs}"
        )
        DiagnosticLog.event("app", "foregrounded", data = mapOf("mavlinkHealthy" to isMavlinkSessionHealthy(), "configChanged" to configChanged))
        if (isMavlinkSessionHealthy() && !configChanged) {
            Log.i(TAG, "restart skipped: reusing healthy MAVLink session on foreground")
            attachRepositoryBridge()
            rtkController.onRtkConfigurationChanged()
            return
        }
        try {
            if (shouldKeepRtkAliveInBackground()) {
                Log.w(TAG, "onAppForegrounded restarting stale MAVLink session before resuming RTK")
                rtkController.stopStreamingForMavlinkRestart()
            }
            mavlinkClient.restart(config)
            activeMavlinkConfigKey = newConfigKey
            attachRepositoryBridge()
            rtkController.onRtkConfigurationChanged()
        } catch (error: Exception) {
            Log.e(TAG, "Failed to restart MAVLink session on foreground", error)
            conStateLiveData.postValue(false)
            telemetryAliveLiveData.postValue(false)
        }
    }

    fun onAppBackgrounded() {
        Log.i(
            TAG,
            "background transition keepAlive=${shouldKeepRtkAliveInBackground()} lastHeartbeatMs=${mavlinkClient.lastHeartbeatMs}"
        )
        DiagnosticLog.event("app", "backgrounded", data = mapOf("rtkKeepAlive" to shouldKeepRtkAliveInBackground()))
        if (shouldKeepRtkAliveInBackground()) {
            Log.i(TAG, "onAppBackgrounded preserving MAVLink/RTK keep-alive")
            return
        }
        stopRtkForwarding(clearRequest = true)
        mavlinkClient.stop()
        activeMavlinkConfigKey = null
    }

    private fun MavlinkConfig.toConnectionKey(): String {
        return "${interfaceType.name}|$port|${targetHost.orEmpty()}|$targetPort|${network?.networkHandle ?: "default"}"
    }

    fun onRtkConfigurationChanged(forceStart: Boolean = false) {
        rtkController.onRtkConfigurationChanged(forceStart)
    }

    fun currentRtkSocketFactory() = rtkController.currentRtkSocketFactory()

    fun currentRtkInternetNetwork() = rtkController.currentRtkInternetNetwork()

    fun reportRtkStartBlocked(message: String) {
        rtkController.reportRtkStartBlocked(message)
    }

    fun stopRtkForwarding(clearRequest: Boolean = true) {
        rtkController.stopRtkForwarding(clearRequest)
    }

    fun shouldKeepRtkAliveInBackground(): Boolean {
        return rtkController.shouldKeepRtkAliveInBackground()
    }

    fun downloadMissionNew() {
        missionController.downloadMission(
            debounceMs = MISSION_DEBOUNCE_MS,
            logTag = TAG
        )
    }

    fun uploadMissionNew(items: ArrayList<MissionItemInt>, activityVm: MainActivityViewModel) {
        operatorEventLogger.logMissionUploadStarted(items.size)
        missionController.uploadMission(
            items = items,
            activityVm = activityVm,
            uploadTimeoutMs = UPLOAD_TIMEOUT_MS,
            logTag = TAG
        )
    }

    private fun isMavlinkSessionHealthy(): Boolean {
        return (System.currentTimeMillis() - mavlinkClient.lastHeartbeatMs) < HEARTBEAT_STALE_MS
    }

    private fun attachRepositoryBridge() {
        if (!runtimeState.bridgeAttached) {
            runtimeState.bridgeAttached = true
            Log.i(TAG, "bridge attached: starting connection ticker")

            repoDisposables.add(
                Observable.interval(0, CONNECTION_TICK_MS, TimeUnit.MILLISECONDS)
                    .subscribeOn(Schedulers.io())
                    .subscribe {
                        val connected =
                            (System.currentTimeMillis() - mavlinkClient.lastHeartbeatMs) < HEARTBEAT_STALE_MS
                        stateStore.conStateLiveData.postValue(connected)
                        if (runtimeState.lastLoggedConnectionState != connected) {
                            runtimeState.lastLoggedConnectionState = connected
                            if (connected) {
                                operatorEventLogger.logDroneConnected()
                                Log.d(MAPPING_TAG, "connection=connected")
                                Log.i(
                                    TAG,
                                    "heartbeat healthy lastHeartbeatAgeMs=${System.currentTimeMillis() - mavlinkClient.lastHeartbeatMs}"
                                )
                            } else {
                                operatorEventLogger.logDroneDisconnected("Heartbeat timed out")
                                startHighFrequencyCapture("mavlink_connection_lost", System.currentTimeMillis())
                                Log.d(MAPPING_TAG, "connection=disconnected")
                                Log.w(
                                    TAG,
                                    "heartbeat lost lastHeartbeatAgeMs=${System.currentTimeMillis() - mavlinkClient.lastHeartbeatMs}"
                                )
                            }
                        }

                        val telemetryAlive =
                            (System.currentTimeMillis() - runtimeState.lastNonHeartbeatMs) < TELEMETRY_STALE_MS
                        stateStore.telemetryAliveLiveData.postValue(telemetryAlive)
                        if (runtimeState.lastLoggedTelemetryAlive != telemetryAlive) {
                            runtimeState.lastLoggedTelemetryAlive = telemetryAlive
                            if (!telemetryAlive && connected) {
                                eventLogger.logSimple(
                                    type = GeoAwarenessEventType.TELEMETRY_STALE,
                                    severity = "WARNING",
                                    message = "Telemetry became stale",
                                    category = "TELEMETRY",
                                    connectionState = "STALE"
                                )
                            }
                        }

                        if (!connected) {
                            stateStore.telemetryAliveLiveData.postValue(false)
                            stateStore.gpsFixType.postValue(null)
                            stateStore.droneBatteryPercentage.postValue(-1.0f)
                            stateStore.droneGroundSpeedMetersPerSecond.postValue(0.0f)
                            stateStore.liquidLevel.postValue(TelemetryMapping.UNKNOWN_PERCENT.toFloat())
                            if (runtimeState.autopilotSysId != -1) {
                                runtimeState.clearAutopilotTarget()
                            }
                            parameterController.onDisconnected()
                        }

                        rtkController.onConnectionStateEvaluated(connected)
                        maybeRecordFlightHealthSnapshot(connected)
                    }
            )
        } else {
            Log.i(TAG, "bridge attach skipped: connection ticker already active")
        }

        runtimeState.mavlinkMessagesDisposable?.let { disposable ->
            Log.i(TAG, "bridge detached: disposing previous MAVLink message subscription")
            repoDisposables.remove(disposable)
            disposable.dispose()
        }

        runtimeState.mavlinkMessagesDisposable =
            mavlinkClient.messages()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { msg -> handleMavlinkMessage(msg) },
                    { err -> Log.e(TAG, "MAVLink stream error: ${err.message}", err) }
                )
        repoDisposables.add(runtimeState.mavlinkMessagesDisposable!!)
        Log.i(TAG, "bridge attached: subscribed to current MAVLink session stream")
    }

    private fun handleMavlinkMessage(message: MavlinkMessage<*>) {
        parameterController.handle(message)
        telemetryProcessor.handle(message)
    }

    private fun maybeRecordFlightHealthSnapshot(connected: Boolean) {
        val now = System.currentTimeMillis()
        val armed = stateStore.armedState.value == true
        val rtkStreaming = stateStore.rtkForwardingState.value is RtkForwardingState.Streaming
        if (lastSnapshotRtkStreaming == true && !rtkStreaming) {
            startHighFrequencyCapture("rtk_stream_lost", now)
        }
        lastSnapshotRtkStreaming = rtkStreaming
        val highCaptureActive = now < highFrequencySnapshotsUntilMs
        if (!connected && !armed && !rtkStreaming && !highCaptureActive) return
        val interval = if (now < highFrequencySnapshotsUntilMs) {
            INCIDENT_HEALTH_SNAPSHOT_INTERVAL_MS
        } else {
            NORMAL_HEALTH_SNAPSHOT_INTERVAL_MS
        }
        if (now - lastHealthSnapshotMs < interval) return
        recordFlightHealthSnapshot(now, reason = if (interval == INCIDENT_HEALTH_SNAPSHOT_INTERVAL_MS) "incident" else "periodic")
    }

    private fun recordFlightHealthSnapshot(now: Long, reason: String) {
        lastHealthSnapshotMs = now
        val location = runtimeState.lastDroneLocation
        DiagnosticLog.event(
            module = "flight",
            message = "health_snapshot",
            data = mapOf(
                "reason" to reason,
                "connected" to (stateStore.conStateLiveData.value == true),
                "telemetryAlive" to (stateStore.telemetryAliveLiveData.value == true),
                "armed" to (stateStore.armedState.value == true),
                "flightMode" to stateStore.droneFlightMode.value,
                "altitudeMeters" to location?.altitude,
                "altitudeAmslMeters" to stateStore.droneAltitudeAmslMeters.value,
                "latitude" to location?.latitude,
                "longitude" to location?.longitude,
                "groundSpeedMps" to stateStore.droneGroundSpeedMetersPerSecond.value,
                "verticalSpeedMps" to stateStore.droneVerticalSpeedMetersPerSecond.value,
                "headingDegrees" to stateStore.droneHeading.value,
                "batteryVoltage" to stateStore.droneBatteryVoltage.value,
                "batteryFraction" to stateStore.droneBatteryPercentage.value,
                "rcRssiPercent" to stateStore.rcRSSI.value,
                "sprayerLevelPercent" to stateStore.liquidLevel.value,
                "frontObstacleMeters" to stateStore.droneFrontDistance.value,
                "rearObstacleMeters" to stateStore.droneBackDistance.value,
                "gpsFix" to TelemetryMapping.gpsFixLabel(stateStore.gpsFixType.value),
                "gpsDebug" to stateStore.rtkGpsDebugStatus.value,
                "rtkState" to stateStore.rtkForwardingState.value?.javaClass?.simpleName,
                "rtcmQueueDepth" to mavlinkClient.currentRtcmQueueDepth(),
                "heartbeatAgeMs" to (now - mavlinkClient.lastHeartbeatMs),
                "tabletBatteryPercent" to tabletBatteryPercent(),
                "tabletCharging" to isTabletCharging(),
                "tabletNetwork" to tabletNetworkDescription(),
                "tabletPowerSaver" to tabletPowerSaverEnabled(),
                "tabletInteractive" to tabletIsInteractive(),
                "diagnosticStorageFreeBytes" to Application.getInstance().applicationContext.filesDir.usableSpace
            )
        )
    }

    private fun startHighFrequencyCapture(reason: String, now: Long) {
        val alreadyActive = now < highFrequencySnapshotsUntilMs
        highFrequencySnapshotsUntilMs = maxOf(highFrequencySnapshotsUntilMs, now + INCIDENT_SNAPSHOT_WINDOW_MS)
        if (!alreadyActive) {
            DiagnosticLog.event(
                module = "flight",
                message = "high_frequency_capture_started",
                severity = "WARN",
                data = mapOf("reason" to reason, "durationSeconds" to (INCIDENT_SNAPSHOT_WINDOW_MS / 1000L))
            )
        }
    }

    private fun recordFlightSessionBoundary(armed: Boolean, flightMode: String?) {
        val now = System.currentTimeMillis()
        if (armed && flightSessionStartedMs == 0L) {
            flightSessionStartedMs = now
            DiagnosticLog.event("flight", "session_started", data = mapOf(
                "autopilotSystemId" to runtimeState.autopilotSysId,
                "autopilotComponentId" to runtimeState.autopilotCompId,
                "flightMode" to flightMode
            ))
        } else if (!armed && flightSessionStartedMs != 0L) {
            DiagnosticLog.event("flight", "session_ended", data = mapOf(
                "durationMs" to (now - flightSessionStartedMs),
                "flightMode" to flightMode
            ))
            flightSessionStartedMs = 0L
        }
    }

    private fun tabletBatteryIntent(): Intent? = Application.getInstance().applicationContext
        .registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun tabletBatteryPercent(): Int? = tabletBatteryIntent()?.let { intent ->
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) (level * 100 / scale) else null
    }

    private fun isTabletCharging(): Boolean? = tabletBatteryIntent()?.let { intent ->
        when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        }
    }

    private fun tabletNetworkDescription(): String {
        val manager = Application.getInstance().applicationContext
            .getSystemService(ConnectivityManager::class.java) ?: return "unavailable"
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "offline"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    }

    private fun tabletPowerSaverEnabled(): Boolean? = Application.getInstance().applicationContext
        .getSystemService(PowerManager::class.java)
        ?.isPowerSaveMode

    private fun tabletIsInteractive(): Boolean? = Application.getInstance().applicationContext
        .getSystemService(PowerManager::class.java)
        ?.isInteractive

    override fun onCleared() {
        super.onCleared()
        missionController.clear()
        parameterController.clear()
        repoDisposables.clear()
        Log.i(TAG, "bridge detached: clearing all subscriptions in onCleared")
        rtkController.shutdown()
        mavlinkClient.stop()
    }
}
