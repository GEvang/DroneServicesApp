package com.example.droneservicesapp.mavserver

import android.location.Location
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.droneservicesapp.Application
import com.example.droneservicesapp.core.util.Event
import com.example.droneservicesapp.data.mavlink.MavlinkClient
import com.example.droneservicesapp.data.mavlink.MavlinkConfig
import com.example.droneservicesapp.data.mavlink.MavlinkConnectionManager
import com.example.droneservicesapp.data.mavlink.MissionService
import com.example.droneservicesapp.data.rtk.RtkForwardingService
import com.example.droneservicesapp.data.rtk.RtkForwardingState
import com.example.droneservicesapp.data.rtk.RtkKeepAliveForegroundService
import com.example.droneservicesapp.data.rtk.RtkInternetMonitor
import com.example.droneservicesapp.data.rtk.RtkPreferences
import com.example.droneservicesapp.data.rtk.RtkValidator
import com.example.droneservicesapp.ui.home.model.MainActivityViewModel
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.BatteryStatus
import io.dronefleet.mavlink.common.DistanceSensor
import io.dronefleet.mavlink.common.GlobalPositionInt
import io.dronefleet.mavlink.common.Gps2Raw
import io.dronefleet.mavlink.common.GpsRawInt
import io.dronefleet.mavlink.common.MavSensorOrientation
import io.dronefleet.mavlink.common.MissionItemInt
import io.dronefleet.mavlink.common.RcChannels
import io.dronefleet.mavlink.common.Statustext
import io.dronefleet.mavlink.minimal.Heartbeat
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow

class DroneViewModel : ViewModel() {

    companion object {
        private const val TAG = "DroneViewModel"
        private const val GPS_TAG = "ArduPilotGps"
        private const val CONNECTION_TICK_MS = 500L
        private const val HEARTBEAT_STALE_MS = 5500L
        private const val TELEMETRY_STALE_MS = 2500L
        private const val MISSION_DEBOUNCE_MS = 1500L
        private const val GPS_LOG_INTERVAL_MS = 5000L
        private const val RTK_AUTO_START_SUPPRESS_LOG_INTERVAL_MS = 5000L

        // How long each wait cycle in MissionService uses (your default is fine; keep consistent)
        private const val UPLOAD_TIMEOUT_MS = 2000L
    }

    // Lifecycle / disposable
    private val repoDisposables = CompositeDisposable()

    // State for connection/filters
    @Volatile private var bridgeAttached = false
    @Volatile private var lastNonHeartbeatMs: Long = 0L
    @Volatile private var lastDroneLocation: Location? = null
    @Volatile private var lastGpsLogSummary: String? = null
    @Volatile private var lastGpsLogMs: Long = 0L
    @Volatile private var lastGpsMessageTimeMs: Long = 0L
    @Volatile private var lastFixType: Int = -1
    @Volatile private var lastSatellitesVisible: Int = -1
    @Volatile private var lastGpsSource: String = "--"
    @Volatile private var lastGpsEph: Int = -1
    @Volatile private var lastGpsEpv: Int = -1
    @Volatile private var lastRtkAutoStartSuppressLogMs: Long = 0L

    // Autopilot addressing
    @Volatile private var autopilotSysId: Int = -1
    @Volatile private var autopilotCompId: Int = -1

    // Mission control (download)
    @Volatile private var missionDownloadInProgress = false
    @Volatile private var lastDownloadAttemptMs: Long = 0L

    // Mission control (upload) — NEW
    @Volatile private var currentUploadDisposable: Disposable? = null
    @Volatile private var currentUploadCancelToken: AtomicBoolean? = null
    @Volatile private var mavlinkMessagesDisposable: Disposable? = null
    @Volatile private var internetAvailable = false
    @Volatile private var lastLoggedConnectionState: Boolean? = null

    // Expose target IDs
    fun getTargetSystemId(): Int = autopilotSysId
    fun getTargetComponentId(): Int = autopilotCompId

    // Dependencies
    private val repo = MavlinkConnectionManager()
    val mavlinkClient: MavlinkClient = repo
    val missionService: MissionService by lazy { MissionService(mavlinkClient) }
    private val rtkPreferences: RtkPreferences by lazy {
        RtkPreferences(Application.getInstance().applicationContext)
    }
    private val rtkInternetMonitor: RtkInternetMonitor by lazy {
        RtkInternetMonitor(Application.getInstance().applicationContext)
    }
    private val rtkForwardingService: RtkForwardingService by lazy {
        RtkForwardingService(
            Application.getInstance().applicationContext,
            mavlinkClient,
            socketFactoryProvider = { rtkInternetMonitor.currentInternetSocketFactory() }
        )
    }

    // LiveData
    val droneLocationLiveData: MutableLiveData<Location> by lazy {
        MutableLiveData<Location>().default(Location(""))
    }
    val conStateLiveData: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>().default(false)
    }
    val telemetryAliveLiveData: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>().default(false)
    }
    val armedState: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>().default(false)
    }
    val droneHeading: MutableLiveData<Double> by lazy {
        MutableLiveData<Double>().default(0.0)
    }
    val droneBatteryVoltage: MutableLiveData<Float> by lazy {
        MutableLiveData<Float>().default(0.0F)
    }
    val droneBatteryPercentage: MutableLiveData<Float> by lazy {
        MutableLiveData<Float>().default(0.0F)
    }
    val droneFrontDistance: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }
    val droneBackDistance: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }
    val droneFlightMode: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>().default(0)
    }
    val rcRSSI: MutableLiveData<Float> by lazy {
        MutableLiveData<Float>().default(0.0F)
    }
    val missionItems: MutableLiveData<ArrayList<MissionItemInt>> by lazy {
        MutableLiveData<ArrayList<MissionItemInt>>().default(ArrayList())
    }
    val liquidLevel: MutableLiveData<Float> by lazy {
        MutableLiveData<Float>().default(0.0F)
    }
    val uploadProgressPercent: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>().default(0)
    }
    val rtkForwardingState: MutableLiveData<RtkForwardingState> by lazy {
        MutableLiveData<RtkForwardingState>().default(RtkForwardingState.Idle)
    }
    val rtkGpsDebugStatus: MutableLiveData<String> by lazy {
        MutableLiveData<String>().default("Src: -- | Fix: -- | Sats: -- | HDOP: -- | Last GPS: --")
    }

    // Helpers
    private fun <T : Any?> MutableLiveData<T>.default(initialValue: T) =
        apply { postValue(initialValue) }

    init {
        internetAvailable = rtkInternetMonitor.isInternetAvailable.value
        viewModelScope.launch {
            rtkForwardingService.state.collect { state ->
                rtkForwardingState.postValue(state)
                if (state is RtkForwardingState.Stopped ||
                    state is RtkForwardingState.WaitingForMountpoint ||
                    state is RtkForwardingState.InvalidConfig ||
                    state is RtkForwardingState.AuthFailed ||
                    state is RtkForwardingState.MountpointInvalid ||
                    state is RtkForwardingState.ProtocolError
                ) {
                    RtkKeepAliveForegroundService.stopSession(Application.getInstance().applicationContext)
                }
            }
        }
        viewModelScope.launch {
            rtkInternetMonitor.isInternetAvailable.collect { available ->
                if (internetAvailable == available) return@collect
                internetAvailable = available
                Log.i(TAG, "internet availability changed available=$available")
                ensureRtkForwardingState()
            }
        }
    }

    // Public API
    fun startMavlink(config: MavlinkConfig) {
        Log.i(TAG, "connect requested via startMavlink config=$config")
        mavlinkClient.restart(config)
        attachRepositoryBridge()
    }

    fun onAppForegrounded(config: MavlinkConfig) {
        Log.i(
            TAG,
            "foreground transition keepAlive=${shouldKeepRtkAliveInBackground()} healthy=${isMavlinkSessionHealthy()} lastHeartbeatMs=${mavlinkClient.lastHeartbeatMs}"
        )
        if (shouldKeepRtkAliveInBackground() && isMavlinkSessionHealthy()) {
            Log.i(TAG, "restart skipped: reusing healthy MAVLink session while RTK keep-alive is active")
            attachRepositoryBridge()
            ensureRtkForwardingState()
            return
        }
        if (shouldKeepRtkAliveInBackground()) {
            Log.w(TAG, "onAppForegrounded restarting stale MAVLink session before resuming RTK")
            if (rtkForwardingService.isRunning()) {
                rtkForwardingService.stop(updateState = false)
            }
        }
        mavlinkClient.restart(config)
        attachRepositoryBridge()
        ensureRtkForwardingState()
    }

    fun onAppBackgrounded() {
        Log.i(
            TAG,
            "background transition keepAlive=${shouldKeepRtkAliveInBackground()} rtkRunning=${rtkForwardingService.isRunning()} lastHeartbeatMs=${mavlinkClient.lastHeartbeatMs}"
        )
        if (shouldKeepRtkAliveInBackground()) {
            Log.i(TAG, "onAppBackgrounded preserving MAVLink/RTK keep-alive")
            return
        }
        stopRtkForwarding(clearRequest = true)
        mavlinkClient.stop()
    }

    fun onRtkConfigurationChanged(forceStart: Boolean = false) {
        val config = currentRtkConfig()
        Log.i(
            TAG,
            "onRtkConfigurationChanged mountpoint=${config.mountpoint.trim()} desired=${isRtkDesired(config)} baseValid=${RtkValidator.isValidBaseConfig(config)}"
        )
        ensureRtkForwardingState(forceStart = forceStart)
    }

    fun currentRtkSocketFactory() = rtkInternetMonitor.currentInternetSocketFactory()

    fun reportRtkStartBlocked(message: String) {
        Log.w(TAG, "startRtkForwarding blocked: $message")
        RtkKeepAliveForegroundService.stopSession(Application.getInstance().applicationContext)
        rtkForwardingState.postValue(RtkForwardingState.InvalidConfig(message))
    }

    fun stopRtkForwarding(clearRequest: Boolean = true) {
        Log.i(
            TAG,
            "stopRtkForwarding called clearRequest=$clearRequest connected=${conStateLiveData.value == true} sys=$autopilotSysId comp=$autopilotCompId"
        )
        rtkForwardingService.stop()
        RtkKeepAliveForegroundService.stopSession(Application.getInstance().applicationContext)
        rtkForwardingState.postValue(RtkForwardingState.Stopped)
    }

    fun shouldKeepRtkAliveInBackground(): Boolean {
        return isRtkDesired(currentRtkConfig()) || rtkForwardingService.isRunning()
    }

    private fun isMavlinkSessionHealthy(): Boolean {
        return (System.currentTimeMillis() - mavlinkClient.lastHeartbeatMs) < HEARTBEAT_STALE_MS
    }

    fun downloadMissionNew() {
        val now = System.currentTimeMillis()

        // basic debounce
        if (now - lastDownloadAttemptMs < MISSION_DEBOUNCE_MS) return
        lastDownloadAttemptMs = now

        // in-flight guard
        if (missionDownloadInProgress) return
        missionDownloadInProgress = true

        repoDisposables.add(
            Single.fromCallable { missionService.downloadMission() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally { missionDownloadInProgress = false }
                .subscribe(
                    { items -> missionItems.postValue(items) },
                    { err ->
                        Log.e(TAG, "downloadMission failed: ${err.message}", err)
                    }
                )
        )
    }

    /**
     * Latest-press-wins upload:
     * - If an upload is running, cancel it immediately.
     * - Start a new upload with the new items.
     */
    fun uploadMissionNew(items: ArrayList<MissionItemInt>, activityVm: MainActivityViewModel) {
        // Cancel any in-flight upload (if exists)
        currentUploadCancelToken?.set(true)
        currentUploadDisposable?.dispose()
        currentUploadDisposable = null
        currentUploadCancelToken = null

        // Reset progress
        uploadProgressPercent.postValue(0)

        // Create a fresh cancel token for this run
        val token = AtomicBoolean(false)
        currentUploadCancelToken = token

        // Start new upload
        val d =
            Single.fromCallable {
                missionService.uploadMission(
                    items,
                    timeoutMs = UPLOAD_TIMEOUT_MS,
                    cancel = token,
                    onProgress = { sentSeq, total, percent ->
                        uploadProgressPercent.postValue(percent)
                    }
                )
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally {
                    // Only clear if this run is still the active one
                    if (currentUploadCancelToken === token) {
                        // If cancelled, reset progress to 0
                        if (token.get()) {
                            uploadProgressPercent.postValue(0)
                        }
                        currentUploadDisposable = null
                        currentUploadCancelToken = null
                    }
                }
                .subscribe(
                    { ok ->
                        Log.i(TAG, "uploadMission result=$ok")
                        if (ok) {
                            uploadProgressPercent.postValue(100)
                            activityVm.mapAction.postValue(Event(MainActivityViewModel.MapAction.UploadMissionSuccess))
                        } else {
                            uploadProgressPercent.postValue(0)
                            activityVm.mapAction.postValue(Event(MainActivityViewModel.MapAction.UploadMissionFailed("Upload rejected or timed out")))
                        }
                    },
                    { err ->
                        Log.e(TAG, "uploadMission failed: ${err.message}", err)
                        uploadProgressPercent.postValue(0)
                        activityVm.mapAction.postValue(Event(MainActivityViewModel.MapAction.UploadMissionFailed(err.message ?: "Upload error")))
                    }
                )

        currentUploadDisposable = d
        repoDisposables.add(d)
    }

    // Internal
    private fun attachRepositoryBridge() {
        if (!bridgeAttached) {
            bridgeAttached = true
            Log.i(TAG, "bridge attached: starting connection ticker")

            // 1) Connection state ticker (heartbeat freshness)
            repoDisposables.add(
                Observable.interval(0, CONNECTION_TICK_MS, TimeUnit.MILLISECONDS)
                    .subscribeOn(Schedulers.io())
                    .subscribe {
                        val connected =
                            (System.currentTimeMillis() - mavlinkClient.lastHeartbeatMs) < HEARTBEAT_STALE_MS
                        conStateLiveData.postValue(connected)
                        if (lastLoggedConnectionState != connected) {
                            lastLoggedConnectionState = connected
                            if (connected) {
                                Log.i(
                                    TAG,
                                    "heartbeat healthy lastHeartbeatAgeMs=${System.currentTimeMillis() - mavlinkClient.lastHeartbeatMs}"
                                )
                            } else {
                                Log.w(
                                    TAG,
                                    "heartbeat lost lastHeartbeatAgeMs=${System.currentTimeMillis() - mavlinkClient.lastHeartbeatMs}"
                                )
                            }
                        }

                        val telemetryAlive =
                            (System.currentTimeMillis() - lastNonHeartbeatMs) < TELEMETRY_STALE_MS
                        telemetryAliveLiveData.postValue(telemetryAlive)

                        if (!connected) {
                            telemetryAliveLiveData.postValue(false)
                            if (autopilotSysId != -1) {
                                autopilotSysId = -1
                                autopilotCompId = -1
                            }
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
            )
        } else {
            Log.i(TAG, "bridge attach skipped: connection ticker already active")
        }

        mavlinkMessagesDisposable?.let { disposable ->
            Log.i(TAG, "bridge detached: disposing previous MAVLink message subscription")
            repoDisposables.remove(disposable)
            disposable.dispose()
        }

        mavlinkMessagesDisposable =
            mavlinkClient.messages()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { msg -> handleMavlinkMessage(msg) },
                    { err -> Log.e(TAG, "MAVLink stream error: ${err.message}", err) }
                )
        repoDisposables.add(mavlinkMessagesDisposable!!)
        Log.i(TAG, "bridge attached: subscribed to current MAVLink session stream")
    }

    private fun handleMavlinkMessage(message: MavlinkMessage<*>) {
        if (message.payload !is Heartbeat) {
            lastNonHeartbeatMs = System.currentTimeMillis()
        }

        if (autopilotSysId != -1 &&
            (message.originSystemId != autopilotSysId || message.originComponentId != autopilotCompId)
        ) {
            // Allow the heartbeat through to acquire lock, everything else ignored
            if (message.payload !is Heartbeat) return
        }

        when (val p = message.payload) {
            is Heartbeat -> {
                val isGcs = p.type().entry() == io.dronefleet.mavlink.minimal.MavType.MAV_TYPE_GCS
                val hasAutopilot =
                    p.autopilot()
                        .entry() != io.dronefleet.mavlink.minimal.MavAutopilot.MAV_AUTOPILOT_INVALID

                // Only use real autopilot heartbeat to "lock on"
                if (!isGcs && hasAutopilot && autopilotSysId == -1) {
                    autopilotSysId = message.originSystemId
                    autopilotCompId = message.originComponentId
                    Log.i(TAG, "Locked autopilot sys=$autopilotSysId comp=$autopilotCompId")
                    missionService.targetSystemId = autopilotSysId
                    missionService.targetComponentId = autopilotCompId
                    Log.i(TAG, "RTK auto-start check triggered by first autopilot heartbeat")
                    ensureRtkForwardingState()
                }

                // If we’ve locked on already, ignore heartbeats from other components/systems
                if (autopilotSysId != -1 &&
                    (message.originSystemId != autopilotSysId || message.originComponentId != autopilotCompId)
                ) {
                    return
                }

                droneFlightMode.postValue(p.customMode().toInt())

                val isArmed = (p.baseMode().value() and 0x80) != 0
                armedState.postValue(isArmed)
            }

            is GlobalPositionInt -> {
                val loc = Location("").apply {
                    latitude = p.lat().toDouble() * 10.0.pow(-7.0)
                    longitude = p.lon().toDouble() * 10.0.pow(-7.0)
                    altitude = p.relativeAlt().toDouble() * 10.0.pow(-3.0)
                }
                lastDroneLocation = Location(loc)
                droneHeading.postValue(p.hdg().toDouble() / 100.0)
                droneLocationLiveData.postValue(loc)
                if (isRtkDesired(currentRtkConfig())) {
                    if (shouldSkipRedundantRtkAutoStartCheck("drone GPS update")) {
                        maybeLogRtkAutoStartSuppressed("drone GPS update")
                    } else {
                        Log.i(TAG, "RTK auto-start check triggered by drone GPS update")
                        ensureRtkForwardingState()
                    }
                }
            }

            is GpsRawInt -> {
                handleGpsDebugMessage(
                    source = "GPS_RAW_INT",
                    fixType = p.fixType().value().toInt(),
                    satellitesVisible = p.satellitesVisible().toInt(),
                    eph = p.eph().toInt(),
                    epv = p.epv().toInt()
                )
            }

            is Gps2Raw -> {
                handleGpsDebugMessage(
                    source = "GPS2_RAW",
                    fixType = p.fixType().value().toInt(),
                    satellitesVisible = p.satellitesVisible().toInt(),
                    eph = p.eph().toInt(),
                    epv = p.epv().toInt()
                )
            }

            is Statustext -> {
                val text = p.text()
                if (text.contains("GPS", ignoreCase = true) ||
                    text.contains("RTK", ignoreCase = true) ||
                    text.contains("EKF", ignoreCase = true)
                ) {
                    Log.i(GPS_TAG, "STATUSTEXT severity=${p.severity().entry()} text=$text")
                }
            }

            is BatteryStatus -> {
                droneBatteryVoltage.postValue(p.voltages()[0].toFloat() * 10.0f.pow(-3))
                droneBatteryPercentage.postValue(p.batteryRemaining().toFloat() / 100.0F)

                // You were using voltages[1] as liquid level before
                liquidLevel.postValue(p.voltages()[1].toFloat())
            }

            is RcChannels -> {
                rcRSSI.postValue(p.rssi() * 100.0F / 255.0F)
            }

            is DistanceSensor -> {
                val cm = p.currentDistance()
                val meters = cm / 100

                val ori = p.orientation().entry()
                if (
                    ori == MavSensorOrientation.MAV_SENSOR_ROTATION_NONE ||
                    ori == MavSensorOrientation.MAV_SENSOR_ROTATION_YAW_45 ||
                    ori == MavSensorOrientation.MAV_SENSOR_ROTATION_YAW_315
                ) {
                    droneFrontDistance.postValue(meters)
                } else if (
                    ori == MavSensorOrientation.MAV_SENSOR_ROTATION_YAW_180 ||
                    ori == MavSensorOrientation.MAV_SENSOR_ROTATION_YAW_135 ||
                    ori == MavSensorOrientation.MAV_SENSOR_ROTATION_YAW_225
                ) {
                    droneBackDistance.postValue(meters)
                }
            }
        }
    }

    private fun ensureRtkForwardingState(forceStart: Boolean = false) {
        val config = currentRtkConfig()
        val desired = isRtkDesired(config)
        val connected = conStateLiveData.value == true
        val targetReady = autopilotSysId >= 0 && autopilotCompId >= 0
        val currentState = rtkForwardingState.value
        Log.i(
            TAG,
            "ensureRtkForwardingState forceStart=$forceStart desired=$desired internet=$internetAvailable connected=$connected targetReady=$targetReady sys=$autopilotSysId comp=$autopilotCompId"
        )

        when {
            !RtkValidator.isValidMountpoint(config.mountpoint) -> {
                if (rtkForwardingService.isRunning()) {
                    rtkForwardingService.stop(updateState = false)
                }
                RtkKeepAliveForegroundService.stopSession(Application.getInstance().applicationContext)
                rtkForwardingState.postValue(RtkForwardingState.WaitingForMountpoint)
            }
            !RtkValidator.isValidBaseConfig(config) -> {
                if (rtkForwardingService.isRunning()) {
                    rtkForwardingService.stop(updateState = false)
                }
                RtkKeepAliveForegroundService.stopSession(Application.getInstance().applicationContext)
                rtkForwardingState.postValue(
                    RtkForwardingState.InvalidConfig("RTK settings are incomplete.")
                )
            }
            !desired -> {
                if (rtkForwardingService.isRunning()) {
                    rtkForwardingService.stop(updateState = false)
                }
                RtkKeepAliveForegroundService.stopSession(Application.getInstance().applicationContext)
                rtkForwardingState.postValue(RtkForwardingState.Idle)
            }
            !internetAvailable -> {
                if (rtkForwardingService.isRunning()) {
                    rtkForwardingService.stop(updateState = false)
                }
                RtkKeepAliveForegroundService.startSession(Application.getInstance().applicationContext)
                Log.i(TAG, "waiting-state reason=no internet")
                rtkForwardingState.postValue(RtkForwardingState.WaitingForInternet)
            }
            !connected -> {
                if (rtkForwardingService.isRunning()) {
                    rtkForwardingService.stop(updateState = false)
                }
                RtkKeepAliveForegroundService.startSession(Application.getInstance().applicationContext)
                Log.w(TAG, "RTK start waiting: drone not connected")
                rtkForwardingState.postValue(RtkForwardingState.WaitingForDrone)
            }
            !targetReady -> {
                if (rtkForwardingService.isRunning()) {
                    rtkForwardingService.stop(updateState = false)
                }
                RtkKeepAliveForegroundService.startSession(Application.getInstance().applicationContext)
                Log.w(TAG, "RTK waiting: missing autopilot target")
                rtkForwardingState.postValue(RtkForwardingState.WaitingForDrone)
            }
            requiresDroneGps(config) && lastDroneLocation?.let { isUsableLocation(it) } != true -> {
                if (rtkForwardingService.isRunning()) {
                    rtkForwardingService.stop(updateState = false)
                }
                RtkKeepAliveForegroundService.startSession(Application.getInstance().applicationContext)
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
                RtkKeepAliveForegroundService.startSession(Application.getInstance().applicationContext)
                rtkForwardingService.start(
                    targetSystemId = autopilotSysId,
                    targetComponentId = autopilotCompId,
                    shouldKeepRunning = {
                        isRtkDesired(currentRtkConfig()) &&
                            internetAvailable &&
                            conStateLiveData.value == true &&
                            autopilotSysId >= 0 &&
                            autopilotCompId >= 0 &&
                            (!requiresDroneGps(currentRtkConfig()) ||
                                lastDroneLocation?.let { isUsableLocation(it) } == true)
                    },
                    locationProvider = {
                        lastDroneLocation?.let { location -> Location(location) }
                    }
                )
            }
        }
    }

    private fun currentRtkConfig() = rtkPreferences.getConfig()

    private fun isRtkDesired(config: com.example.droneservicesapp.data.rtk.RtkConfig): Boolean {
        return RtkValidator.isValidConfig(config)
    }

    private fun requiresDroneGps(config: com.example.droneservicesapp.data.rtk.RtkConfig): Boolean {
        return config.mountpoint.trim().equals("NEAR", ignoreCase = true)
    }

    private fun isUsableLocation(location: Location): Boolean {
        return !location.latitude.isNaN() &&
            !location.longitude.isNaN() &&
            !(location.latitude == 0.0 && location.longitude == 0.0)
    }

    private fun shouldSkipRedundantRtkAutoStartCheck(trigger: String): Boolean {
        val config = currentRtkConfig()
        val connected = conStateLiveData.value == true
        val targetReady = autopilotSysId >= 0 && autopilotCompId >= 0
        val hasRequiredGps = !requiresDroneGps(config) || lastDroneLocation?.let { isUsableLocation(it) } == true
        val streaming = rtkForwardingService.isRunning() &&
            rtkForwardingState.value is RtkForwardingState.Streaming

        val skip = streaming &&
            isRtkDesired(config) &&
            internetAvailable &&
            connected &&
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
            conStateLiveData.value == true
    }

    private fun handleGpsDebugMessage(
        source: String,
        fixType: Int,
        satellitesVisible: Int,
        eph: Int,
        epv: Int
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
        if (summary != lastGpsLogSummary || now - lastGpsLogMs >= GPS_LOG_INTERVAL_MS) {
            lastGpsLogSummary = summary
            lastGpsLogMs = now
            Log.i(GPS_TAG, summary)
        }
        updateGpsDebugStatus()
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
            "Src: $source | Fix: $fix | Sats: $sats | HDOP: $hdop | EPH: $ephText | Last GPS: $lastGpsAge"
        )
    }

    private fun formatGpsMetric(value: Int): String {
        return if (value >= 0 && value != 255 && value != 65535) value.toString() else "--"
    }

    private fun formatDop(value: Int): String {
        if (value < 0 || value == 65535) return "--"
        return String.format(java.util.Locale.US, "%.2f", value / 100.0)
    }

    // Lifecycle
    override fun onCleared() {
        super.onCleared()

        // Cancel any in-flight upload immediately
        currentUploadCancelToken?.set(true)
        currentUploadDisposable?.dispose()
        currentUploadDisposable = null
        currentUploadCancelToken = null

        repoDisposables.clear()
        Log.i(TAG, "bridge detached: clearing all subscriptions in onCleared")
        rtkForwardingService.shutdown()
        rtkInternetMonitor.shutdown()
        RtkKeepAliveForegroundService.stopSession(Application.getInstance().applicationContext)
        mavlinkClient.stop()
    }
}
