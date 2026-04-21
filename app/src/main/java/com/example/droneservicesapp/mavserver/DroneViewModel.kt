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
import com.example.droneservicesapp.ui.main.MainActivityViewModel
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

    // Autopilot addressing
    @Volatile private var autopilotSysId: Int = -1
    @Volatile private var autopilotCompId: Int = -1

    // Mission control (download)
    @Volatile private var missionDownloadInProgress = false
    @Volatile private var lastDownloadAttemptMs: Long = 0L

    // Mission control (upload) — NEW
    @Volatile private var currentUploadDisposable: Disposable? = null
    @Volatile private var currentUploadCancelToken: AtomicBoolean? = null
    @Volatile private var rtkRequested = false
    @Volatile private var mavlinkMessagesDisposable: Disposable? = null

    // Expose target IDs
    fun getTargetSystemId(): Int = autopilotSysId
    fun getTargetComponentId(): Int = autopilotCompId

    // Dependencies
    private val repo = MavlinkConnectionManager()
    val mavlinkClient: MavlinkClient = repo
    val missionService: MissionService by lazy { MissionService(mavlinkClient) }
    private val rtkForwardingService: RtkForwardingService by lazy {
        RtkForwardingService(Application.getInstance().applicationContext, mavlinkClient)
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
        viewModelScope.launch {
            rtkForwardingService.state.collect { state ->
                rtkForwardingState.postValue(state)
                if (state is RtkForwardingState.Stopped ||
                    state is RtkForwardingState.InvalidConfig ||
                    state is RtkForwardingState.AuthFailed ||
                    state is RtkForwardingState.NetworkError ||
                    state is RtkForwardingState.ProtocolError
                ) {
                    RtkKeepAliveForegroundService.stopSession(Application.getInstance().applicationContext)
                }
            }
        }
    }

    // Public API
    fun startMavlink(config: MavlinkConfig) {
        mavlinkClient.restart(config)
        attachRepositoryBridge()
    }

    fun onAppForegrounded(config: MavlinkConfig) {
        if (shouldKeepRtkAliveInBackground()) {
            Log.i(TAG, "onAppForegrounded keeping existing MAVLink/RTK session")
            attachRepositoryBridge()
            return
        }
        mavlinkClient.restart(config)
        attachRepositoryBridge()
    }

    fun onAppBackgrounded() {
        if (shouldKeepRtkAliveInBackground()) {
            Log.i(TAG, "onAppBackgrounded preserving MAVLink/RTK keep-alive")
            return
        }
        stopRtkForwarding(clearRequest = true)
        mavlinkClient.stop()
    }

    fun startRtkForwarding() {
        val config = Application.getInstance().let {
            com.example.droneservicesapp.data.rtk.RtkPreferences(it.applicationContext).getConfig()
        }
        val currentState = rtkForwardingState.value
        if (rtkForwardingService.isRunning()) {
            when (currentState) {
                is RtkForwardingState.Streaming -> Log.i(TAG, "start ignored: already streaming")
                is RtkForwardingState.Reconnecting,
                is RtkForwardingState.ConnectingToCaster,
                is RtkForwardingState.WaitingForDroneGps -> Log.i(TAG, "start ignored: reconnect already scheduled")
                else -> Log.i(TAG, "start ignored: service already active")
            }
            return
        }
        Log.i(
            TAG,
            "startRtkForwarding called sys=$autopilotSysId comp=$autopilotCompId connected=${conStateLiveData.value == true} mountpoint=${config.mountpoint.trim()} configValid=${com.example.droneservicesapp.data.rtk.RtkValidator.isValidConfig(config)}"
        )
        rtkRequested = true
        RtkKeepAliveForegroundService.startSession(Application.getInstance().applicationContext)
        ensureRtkForwardingState(forceStart = true)
    }

    fun reportRtkStartBlocked(message: String) {
        Log.w(TAG, "startRtkForwarding blocked: $message")
        rtkRequested = false
        RtkKeepAliveForegroundService.stopSession(Application.getInstance().applicationContext)
        rtkForwardingState.postValue(RtkForwardingState.InvalidConfig(message))
    }

    fun stopRtkForwarding(clearRequest: Boolean = true) {
        Log.i(
            TAG,
            "stopRtkForwarding called clearRequest=$clearRequest connected=${conStateLiveData.value == true} sys=$autopilotSysId comp=$autopilotCompId"
        )
        if (clearRequest) {
            rtkRequested = false
        }
        rtkForwardingService.stop()
        RtkKeepAliveForegroundService.stopSession(Application.getInstance().applicationContext)
        if (clearRequest) {
            rtkForwardingState.postValue(RtkForwardingState.Stopped)
        }
    }

    fun shouldKeepRtkAliveInBackground(): Boolean {
        return rtkRequested || rtkForwardingService.isRunning()
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

            // 1) Connection state ticker (heartbeat freshness)
            repoDisposables.add(
                Observable.interval(0, CONNECTION_TICK_MS, TimeUnit.MILLISECONDS)
                    .subscribeOn(Schedulers.io())
                    .subscribe {
                        val connected =
                            (System.currentTimeMillis() - mavlinkClient.lastHeartbeatMs) < HEARTBEAT_STALE_MS
                        conStateLiveData.postValue(connected)

                        val telemetryAlive =
                            (System.currentTimeMillis() - lastNonHeartbeatMs) < TELEMETRY_STALE_MS
                        telemetryAliveLiveData.postValue(telemetryAlive)

                        if (!connected) {
                            telemetryAliveLiveData.postValue(false)
                            if (autopilotSysId != -1) {
                                autopilotSysId = -1
                                autopilotCompId = -1
                            }
                            if (rtkRequested) {
                                Log.w(TAG, "RTK waiting: drone disconnected during forwarding")
                                rtkForwardingService.stop(updateState = false)
                                rtkForwardingState.postValue(RtkForwardingState.WaitingForDrone)
                            }
                        } else if (rtkRequested) {
                            ensureRtkForwardingState()
                        }

                        updateGpsDebugStatus()
                    }
            )
        } else {
            Log.i(TAG, "duplicate collector prevented")
        }

        mavlinkMessagesDisposable?.let { disposable ->
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
        Log.i(TAG, "Attached MAVLink message bridge to current session")
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
        val connected = conStateLiveData.value == true
        val targetReady = autopilotSysId >= 0 && autopilotCompId >= 0
        val currentState = rtkForwardingState.value
        Log.i(
            TAG,
            "ensureRtkForwardingState forceStart=$forceStart connected=$connected targetReady=$targetReady sys=$autopilotSysId comp=$autopilotCompId"
        )

        when {
            !rtkRequested -> rtkForwardingState.postValue(RtkForwardingState.Stopped)
            !connected -> {
                Log.w(TAG, "RTK start waiting: drone not connected")
                rtkForwardingState.postValue(RtkForwardingState.WaitingForDrone)
            }
            !targetReady -> {
                Log.w(TAG, "RTK start blocked: missing autopilot target")
                rtkForwardingState.postValue(
                    RtkForwardingState.MissingAutopilotTarget(
                        "Missing autopilot target."
                    )
                )
            }
            !rtkForwardingService.isRunning() && (
                forceStart ||
                    currentState == null ||
                    currentState is RtkForwardingState.Idle ||
                    currentState is RtkForwardingState.WaitingForDrone ||
                    currentState is RtkForwardingState.MissingAutopilotTarget ||
                    currentState is RtkForwardingState.Reconnecting ||
                    currentState is RtkForwardingState.Stopped
                ) -> {
                val config = com.example.droneservicesapp.data.rtk.RtkPreferences(
                    Application.getInstance().applicationContext
                ).getConfig()
                Log.i(
                    TAG,
                    "start forwarding mountpoint=${config.mountpoint.trim()} configValid=${com.example.droneservicesapp.data.rtk.RtkValidator.isValidConfig(config)}"
                )
                rtkForwardingService.start(
                    targetSystemId = autopilotSysId,
                    targetComponentId = autopilotCompId,
                    shouldKeepRunning = {
                        rtkRequested &&
                            conStateLiveData.value == true &&
                            autopilotSysId >= 0 &&
                            autopilotCompId >= 0
                    },
                    locationProvider = {
                        lastDroneLocation?.let { location -> Location(location) }
                    }
                )
            }
        }
    }

    private fun handleGpsDebugMessage(
        source: String,
        fixType: Int,
        satellitesVisible: Int,
        eph: Int,
        epv: Int
    ) {
        val now = System.currentTimeMillis()
        lastGpsMessageTimeMs = now
        lastFixType = fixType
        lastSatellitesVisible = satellitesVisible
        lastGpsSource = source
        lastGpsEph = eph
        lastGpsEpv = epv
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
        rtkForwardingService.shutdown()
        RtkKeepAliveForegroundService.stopSession(Application.getInstance().applicationContext)
        mavlinkClient.stop()
    }
}
