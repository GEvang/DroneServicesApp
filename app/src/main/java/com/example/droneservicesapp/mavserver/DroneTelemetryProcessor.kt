package com.example.droneservicesapp.mavserver

import android.location.Location
import android.util.Log
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.BatteryStatus
import io.dronefleet.mavlink.common.DistanceSensor
import io.dronefleet.mavlink.common.GlobalPositionInt
import io.dronefleet.mavlink.common.Gps2Raw
import io.dronefleet.mavlink.common.GpsRawInt
import io.dronefleet.mavlink.common.MavSensorOrientation
import io.dronefleet.mavlink.common.RcChannels
import io.dronefleet.mavlink.common.Statustext
import io.dronefleet.mavlink.minimal.Heartbeat
import io.dronefleet.mavlink.minimal.MavAutopilot
import io.dronefleet.mavlink.minimal.MavType
import kotlin.math.pow

internal class DroneTelemetryProcessor(
    private val stateStore: DroneUiStateStore,
    private val runtimeState: DroneRuntimeState,
    private val updateMissionTargets: (Int, Int) -> Unit,
    private val onAutopilotHeartbeatLocked: () -> Unit,
    private val onDroneLocationUpdated: () -> Unit,
    private val onGpsDebugMessage: (String, Int, Int, Int, Int) -> Unit,
    private val onArmedStateChanged: (Boolean, Location?, Double?, String?) -> Unit,
    private val onFlightModeChanged: (String?, String?) -> Unit,
    private val onGpsFixChanged: (Boolean) -> Unit,
    private val onBatteryLow: (Int) -> Unit,
    private val onTakeoffDetected: (Location?, Double?) -> Unit,
    private val onLandingDetected: (Location?, Double?) -> Unit
) {
    companion object {
        private const val TAG = "DroneViewModel"
        private const val GPS_TAG = "ArduPilotGps"
    }

    fun handle(message: MavlinkMessage<*>) {
        if (message.payload !is Heartbeat) {
            runtimeState.lastNonHeartbeatMs = System.currentTimeMillis()
        }

        if (runtimeState.autopilotSysId != -1 &&
            (message.originSystemId != runtimeState.autopilotSysId ||
                message.originComponentId != runtimeState.autopilotCompId)
        ) {
            if (message.payload !is Heartbeat) return
        }

        when (val payload = message.payload) {
            is Heartbeat -> handleHeartbeat(message, payload)
            is GlobalPositionInt -> handleGlobalPosition(payload)
            is GpsRawInt -> {
                handleGpsFix(payload.fixType().value().toInt())
                onGpsDebugMessage(
                    "GPS_RAW_INT",
                    payload.fixType().value().toInt(),
                    payload.satellitesVisible().toInt(),
                    payload.eph().toInt(),
                    payload.epv().toInt()
                )
            }
            is Gps2Raw -> {
                handleGpsFix(payload.fixType().value().toInt())
                onGpsDebugMessage(
                    "GPS2_RAW",
                    payload.fixType().value().toInt(),
                    payload.satellitesVisible().toInt(),
                    payload.eph().toInt(),
                    payload.epv().toInt()
                )
            }
            is Statustext -> {
                val text = payload.text()
                if (text.contains("GPS", ignoreCase = true) ||
                    text.contains("RTK", ignoreCase = true) ||
                    text.contains("EKF", ignoreCase = true)
                ) {
                    Log.i(GPS_TAG, "STATUSTEXT severity=${payload.severity().entry()} text=$text")
                }
            }
            is BatteryStatus -> {
                stateStore.droneBatteryVoltage.postValue(payload.voltages()[0].toFloat() * 10.0f.pow(-3))
                stateStore.droneBatteryPercentage.postValue(payload.batteryRemaining().toFloat() / 100.0F)
                stateStore.liquidLevel.postValue(payload.voltages()[1].toFloat())
                handleBatteryLevel(payload.batteryRemaining().toInt())
            }
            is RcChannels -> {
                stateStore.rcRSSI.postValue(payload.rssi() * 100.0F / 255.0F)
            }
            is DistanceSensor -> {
                val meters = payload.currentDistance() / 100
                val orientation = payload.orientation().entry()
                if (
                    orientation == MavSensorOrientation.MAV_SENSOR_ROTATION_NONE ||
                    orientation == MavSensorOrientation.MAV_SENSOR_ROTATION_YAW_45 ||
                    orientation == MavSensorOrientation.MAV_SENSOR_ROTATION_YAW_315
                ) {
                    stateStore.droneFrontDistance.postValue(meters)
                } else if (
                    orientation == MavSensorOrientation.MAV_SENSOR_ROTATION_YAW_180 ||
                    orientation == MavSensorOrientation.MAV_SENSOR_ROTATION_YAW_135 ||
                    orientation == MavSensorOrientation.MAV_SENSOR_ROTATION_YAW_225
                ) {
                    stateStore.droneBackDistance.postValue(meters)
                }
            }
        }
    }

    private fun handleHeartbeat(message: MavlinkMessage<*>, heartbeat: Heartbeat) {
        val isGcs = heartbeat.type().entry() == MavType.MAV_TYPE_GCS
        val hasAutopilot =
            heartbeat.autopilot().entry() != MavAutopilot.MAV_AUTOPILOT_INVALID

        if (!isGcs && hasAutopilot && runtimeState.autopilotSysId == -1) {
            runtimeState.autopilotSysId = message.originSystemId
            runtimeState.autopilotCompId = message.originComponentId
            Log.i(TAG, "Locked autopilot sys=${runtimeState.autopilotSysId} comp=${runtimeState.autopilotCompId}")
            updateMissionTargets(runtimeState.autopilotSysId, runtimeState.autopilotCompId)
            onAutopilotHeartbeatLocked()
        }

        if (runtimeState.autopilotSysId != -1 &&
            (message.originSystemId != runtimeState.autopilotSysId ||
                message.originComponentId != runtimeState.autopilotCompId)
        ) {
            return
        }

        stateStore.droneFlightMode.postValue(heartbeat.customMode().toInt())
        val previousMode = runtimeState.lastLoggedFlightMode
        val currentMode = heartbeat.customMode().toInt()
        if (previousMode != currentMode) {
            onFlightModeChanged(previousMode?.toString(), currentMode.toString())
            runtimeState.lastLoggedFlightMode = currentMode
        }
        val isArmed = (heartbeat.baseMode().value() and 0x80) != 0
        stateStore.armedState.postValue(isArmed)
        if (runtimeState.lastLoggedArmedState != isArmed) {
            runtimeState.lastLoggedArmedState = isArmed
            onArmedStateChanged(
                isArmed,
                runtimeState.lastDroneLocation,
                runtimeState.lastDroneLocation?.altitude,
                currentMode.toString()
            )
            if (!isArmed) {
                runtimeState.lastInferredFlightState = "LANDED"
            }
        }
    }

    private fun handleGlobalPosition(position: GlobalPositionInt) {
        val location = Location("").apply {
            latitude = position.lat().toDouble() * 10.0.pow(-7.0)
            longitude = position.lon().toDouble() * 10.0.pow(-7.0)
            altitude = position.relativeAlt().toDouble() * 10.0.pow(-3.0)
        }
        runtimeState.lastDroneLocation = Location(location)
        stateStore.droneHeading.postValue(position.hdg().toDouble() / 100.0)
        stateStore.droneLocationLiveData.postValue(location)
        inferFlightState(location)
        onDroneLocationUpdated()
    }

    private fun handleGpsFix(fixType: Int) {
        val acquired = fixType >= 3
        if (runtimeState.lastGpsFixAcquired != acquired) {
            runtimeState.lastGpsFixAcquired = acquired
            onGpsFixChanged(acquired)
        }
    }

    private fun handleBatteryLevel(percentRaw: Int) {
        if (percentRaw < 0) {
            return
        }
        val percent = percentRaw.coerceIn(0, 100)
        if (percent <= 20) {
            if (!runtimeState.batteryLowLogged) {
                runtimeState.batteryLowLogged = true
                onBatteryLow(percent)
            }
        } else {
            runtimeState.batteryLowLogged = false
        }
    }

    private fun inferFlightState(location: Location) {
        val altitudeMeters = location.altitude
        val armed = stateStore.armedState.value == true
        when {
            armed && altitudeMeters > 2.0 && runtimeState.lastInferredFlightState != "AIRBORNE" -> {
                runtimeState.lastInferredFlightState = "AIRBORNE"
                onTakeoffDetected(location, altitudeMeters)
            }
            runtimeState.lastInferredFlightState == "AIRBORNE" && altitudeMeters < 1.0 -> {
                runtimeState.lastInferredFlightState = "LANDED"
                onLandingDetected(location, altitudeMeters)
            }
        }
    }
}
