package com.example.droneservicesapp.mavserver

import android.location.Location
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.droneservicesapp.Application
import com.example.droneservicesapp.data.mavlink.MavlinkClient
import com.example.droneservicesapp.data.mavlink.MavlinkConfig
import com.example.droneservicesapp.data.mavlink.MavlinkConnectionManager
import com.example.droneservicesapp.data.mavlink.MissionService
import com.example.droneservicesapp.data.rtk.RtkForwardingState
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.MissionItemInt
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class DroneViewModel : ViewModel() {

    companion object {
        private const val TAG = "DroneViewModel"
        private const val CONNECTION_TICK_MS = 500L
        private const val HEARTBEAT_STALE_MS = 5500L
        private const val TELEMETRY_STALE_MS = 2500L
        private const val MISSION_DEBOUNCE_MS = 1500L
        private const val UPLOAD_TIMEOUT_MS = 2000L
    }

    private val repoDisposables = CompositeDisposable()
    private val runtimeState = DroneRuntimeState()
    private val stateStore = DroneUiStateStore(Application.getInstance().applicationContext)

    private val repo = MavlinkConnectionManager()
    val mavlinkClient: MavlinkClient = repo

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
                rtkController.onAutopilotHeartbeatLocked()
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
            }
        )
    }

    val droneLocationLiveData: MutableLiveData<Location> = stateStore.droneLocationLiveData
    val conStateLiveData: MutableLiveData<Boolean> = stateStore.conStateLiveData
    val telemetryAliveLiveData: MutableLiveData<Boolean> = stateStore.telemetryAliveLiveData
    val armedState: MutableLiveData<Boolean> = stateStore.armedState
    val droneHeading: MutableLiveData<Double> = stateStore.droneHeading
    val droneBatteryVoltage: MutableLiveData<Float> = stateStore.droneBatteryVoltage
    val droneBatteryPercentage: MutableLiveData<Float> = stateStore.droneBatteryPercentage
    val droneFrontDistance: MutableLiveData<Int> = stateStore.droneFrontDistance
    val droneBackDistance: MutableLiveData<Int> = stateStore.droneBackDistance
    val droneFlightMode: MutableLiveData<Int> = stateStore.droneFlightMode
    val rcRSSI: MutableLiveData<Float> = stateStore.rcRSSI
    val missionItems: MutableLiveData<ArrayList<MissionItemInt>> = stateStore.missionItems
    val liquidLevel: MutableLiveData<Float> = stateStore.liquidLevel
    val uploadProgressPercent: MutableLiveData<Int> = stateStore.uploadProgressPercent
    val rtkForwardingState: MutableLiveData<RtkForwardingState> = stateStore.rtkForwardingState
    val rtkGpsDebugStatus: MutableLiveData<String> = stateStore.rtkGpsDebugStatus

    init {
        rtkController.bind(viewModelScope)
    }

    fun getTargetSystemId(): Int = runtimeState.autopilotSysId

    fun getTargetComponentId(): Int = runtimeState.autopilotCompId

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
        if (isMavlinkSessionHealthy()) {
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
        if (shouldKeepRtkAliveInBackground()) {
            Log.i(TAG, "onAppBackgrounded preserving MAVLink/RTK keep-alive")
            return
        }
        stopRtkForwarding(clearRequest = true)
        mavlinkClient.stop()
    }

    fun onRtkConfigurationChanged(forceStart: Boolean = false) {
        rtkController.onRtkConfigurationChanged(forceStart)
    }

    fun currentRtkSocketFactory() = rtkController.currentRtkSocketFactory()

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
                            (System.currentTimeMillis() - runtimeState.lastNonHeartbeatMs) < TELEMETRY_STALE_MS
                        stateStore.telemetryAliveLiveData.postValue(telemetryAlive)

                        if (!connected) {
                            stateStore.telemetryAliveLiveData.postValue(false)
                            if (runtimeState.autopilotSysId != -1) {
                                runtimeState.clearAutopilotTarget()
                            }
                        }

                        rtkController.onConnectionStateEvaluated(connected)
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
        telemetryProcessor.handle(message)
    }

    override fun onCleared() {
        super.onCleared()
        missionController.clear()
        repoDisposables.clear()
        Log.i(TAG, "bridge detached: clearing all subscriptions in onCleared")
        rtkController.shutdown()
        mavlinkClient.stop()
    }
}
