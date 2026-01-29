package com.example.droneservicesapp.mavserver

import android.location.Location
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.droneservicesapp.mavlink.MavlinkConfig
import com.example.droneservicesapp.mavlink.MavlinkRepository
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.BatteryStatus
import io.dronefleet.mavlink.common.DistanceSensor
import io.dronefleet.mavlink.common.GlobalPositionInt
import io.dronefleet.mavlink.common.MavSensorOrientation
import io.dronefleet.mavlink.common.MissionItemInt
import io.dronefleet.mavlink.common.RcChannels
import io.dronefleet.mavlink.minimal.Heartbeat
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlin.math.pow


class DroneViewModel : ViewModel() {

    private val repoDisposables = CompositeDisposable()
    @Volatile private var bridgeAttached = false
    @Volatile private var lastNonHeartbeatMs: Long = 0L

    @Volatile private var autopilotSysId: Int = -1
    @Volatile private var autopilotCompId: Int = -1


    val mavlinkCommunicationLiveData : MutableLiveData<MavLinkComm> by lazy{
        MutableLiveData<MavLinkComm>().default(MavLinkComm(null))
    }

    val mavlinkRepository: MavlinkRepository by lazy { MavlinkRepository() }

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

    val rcRSSI: MutableLiveData<Float> by lazy{
        MutableLiveData<Float>().default(0.0F)
    }

    val missionItems: MutableLiveData<ArrayList<MissionItemInt>> by lazy{
        MutableLiveData<ArrayList<MissionItemInt>>().default(ArrayList())
    }

    val liquidLevel: MutableLiveData<Float> by lazy {
        MutableLiveData<Float>().default(0.0F)
    }

    private fun <T : Any?> MutableLiveData<T>.default(initialValue: T) = apply { setValue(initialValue) }


    fun startMavlink(config: MavlinkConfig) {
        mavlinkRepository.restart(config)
        attachRepositoryBridgeOnce()
    }

    private fun attachRepositoryBridgeOnce() {

        if (bridgeAttached) return
        bridgeAttached = true

        // 1) Connection state ticker (heartbeat freshness)
        repoDisposables.add(
            Observable.interval(0, 500, java.util.concurrent.TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .subscribe {
                    val connected = (System.currentTimeMillis() - mavlinkRepository.lastHeartbeatMs) < 5500
                    conStateLiveData.postValue(connected)

                    val telemetryAlive = (System.currentTimeMillis() - lastNonHeartbeatMs) < 2500
                    telemetryAliveLiveData.postValue(telemetryAlive)

                    if (!connected) {
                        telemetryAliveLiveData.postValue(false)
                    }

                }
        )

        // 2) Message stream -> update LiveData (temporary bridge)
        repoDisposables.add(
            mavlinkRepository.messages()
                .subscribeOn(Schedulers.io())
                .subscribe(
                    { msg -> handleMavlinkMessage(msg) },
                    { err -> Log.e("DroneViewModel", "MAVLink stream error: ${err.message}", err) }
                )
        )
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
                    p.autopilot().entry() != io.dronefleet.mavlink.minimal.MavAutopilot.MAV_AUTOPILOT_INVALID

                // Only use real autopilot heartbeat to "lock on"
                if (!isGcs && hasAutopilot && autopilotSysId == -1) {
                    autopilotSysId = message.originSystemId
                    autopilotCompId = message.originComponentId
                    Log.i("HB_LOCK", "Locked autopilot sys=$autopilotSysId comp=$autopilotCompId")
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
                droneHeading.postValue(p.hdg().toDouble())
                droneLocationLiveData.postValue(loc)
            }

            is BatteryStatus -> {
                droneBatteryVoltage.postValue(p.voltages()[0].toFloat() * 10.0f.pow(-3))
                droneBatteryPercentage.postValue(p.batteryRemaining().toFloat() * 10.0f.pow(-2))

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

    override fun onCleared() {
        super.onCleared()
        repoDisposables.clear()
        mavlinkRepository.stop()
    }


}


