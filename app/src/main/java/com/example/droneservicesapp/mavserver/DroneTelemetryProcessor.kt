package com.example.droneservicesapp.mavserver

import android.location.Location
import android.util.Log
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.BatteryStatus
import io.dronefleet.mavlink.common.CommandAck
import io.dronefleet.mavlink.common.DistanceSensor
import io.dronefleet.mavlink.common.GlobalPositionInt
import io.dronefleet.mavlink.common.GpsFixType
import io.dronefleet.mavlink.common.Gps2Raw
import io.dronefleet.mavlink.common.GpsRawInt
import io.dronefleet.mavlink.common.LocalPositionNed
import io.dronefleet.mavlink.common.MavSensorOrientation
import io.dronefleet.mavlink.common.RcChannels
import io.dronefleet.mavlink.common.ServoOutputRaw
import io.dronefleet.mavlink.common.Statustext
import io.dronefleet.mavlink.common.VfrHud
import io.dronefleet.mavlink.minimal.Heartbeat
import io.dronefleet.mavlink.minimal.MavAutopilot
import io.dronefleet.mavlink.minimal.MavType
import kotlin.math.abs
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
        private const val MAPPING_TAG = "TelemetryMapping"
        private const val SPEED_SOURCE_STALE_MS = 1500L
        private const val SPEED_SOURCE_VFR_HUD = 1
        private const val SPEED_SOURCE_GLOBAL_POSITION = 2
        private const val SPEED_SOURCE_GPS_RAW = 3
        private const val SPEED_SOURCE_LOCAL_POSITION = 4
    }

    fun handle(message: MavlinkMessage<*>) {
        if (message.payload !is Heartbeat) {
            runtimeState.lastNonHeartbeatMs = System.currentTimeMillis()
        }

        if (message.payload is CommandAck) {
            handleCommandAck(message.payload as CommandAck)
        }

        if (runtimeState.autopilotSysId != -1 &&
            (message.originSystemId != runtimeState.autopilotSysId ||
                message.originComponentId != runtimeState.autopilotCompId)
        ) {
            if (message.payload !is Heartbeat && message.payload !is CommandAck) return
        }

        when (val payload = message.payload) {
            is Heartbeat -> handleHeartbeat(message, payload)
            is GlobalPositionInt -> handleGlobalPosition(payload)
            is VfrHud -> updateGroundSpeed(
                source = "VFR_HUD.groundspeed",
                sourceRank = SPEED_SOURCE_VFR_HUD,
                speedMetersPerSecond = payload.groundspeed()
            )
            is LocalPositionNed -> updateGroundSpeed(
                source = "LOCAL_POSITION_NED.vx/vy",
                sourceRank = SPEED_SOURCE_LOCAL_POSITION,
                speedMetersPerSecond = TelemetryMapping.globalHorizontalSpeedMetersPerSecond(
                    (payload.vx() * 100f).toInt(),
                    (payload.vy() * 100f).toInt()
                )
            )
            is GpsRawInt -> {
                handleGpsFix(payload.fixType().entry(), payload.fixType().value())
                updateGroundSpeed(
                    source = "GPS_RAW_INT.vel",
                    sourceRank = SPEED_SOURCE_GPS_RAW,
                    speedMetersPerSecond = TelemetryMapping.gpsRawSpeedMetersPerSecond(payload.vel())
                )
                onGpsDebugMessage(
                    "GPS_RAW_INT",
                    payload.fixType().value().toInt(),
                    payload.satellitesVisible().toInt(),
                    payload.eph().toInt(),
                    payload.epv().toInt()
                )
            }
            is Gps2Raw -> {
                handleGpsFix(payload.fixType().entry(), payload.fixType().value())
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
                val voltages = payload.voltages()
                val batteryVoltage = voltages.getOrNull(0)
                    ?.takeIf { it in 0 until TelemetryMapping.UINT16_MAX }
                    ?.toFloat()
                    ?.times(10.0f.pow(-3))
                    ?: 0.0f
                val batteryFraction = TelemetryMapping.batteryFractionFromVoltage(batteryVoltage)
                val sprayerPercent = TelemetryMapping.displayPercentFromRaw(voltages.getOrNull(1)?.toFloat())
                    ?: TelemetryMapping.UNKNOWN_PERCENT
                stateStore.droneBatteryVoltage.postValue(batteryVoltage)
                stateStore.droneBatteryPercentage.postValue(batteryFraction)
                stateStore.liquidLevel.postValue(sprayerPercent.toFloat())
                logMappingSummary(
                    key = "battery-sprayer",
                    "batteryVoltage=${String.format(java.util.Locale.US, "%.1f", batteryVoltage)}V " +
                        "batteryDisplay=${TelemetryMapping.formatBatteryText(batteryVoltage, batteryFraction)} " +
                        "sprayerRaw=${voltages.getOrNull(1)} sprayerDisplay=${TelemetryMapping.displayPercentFromRaw(voltages.getOrNull(1)?.toFloat())?.let { "$it%" } ?: "--%"}"
                )
                handleBatteryLevel(TelemetryMapping.displayPercentFromFraction(batteryFraction) ?: -1)
            }
            is RcChannels -> {
                stateStore.rcRSSI.postValue(payload.rssi() * 100.0F / 255.0F)
            }
            is ServoOutputRaw -> {
                stateStore.servo5OutputRaw.postValue(payload.servo5Raw())
                Log.i("SprayerDebug", "RX SERVO_OUTPUT_RAW port=${payload.port()} servo5=${payload.servo5Raw()}")
                logMappingSummary(
                    "servo5",
                    "servoOutputRaw port=${payload.port()} servo5=${payload.servo5Raw()}"
                )
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

    private fun handleCommandAck(payload: CommandAck) {
        val commandName = payload.command().entry()?.name ?: payload.command().value().toString()
        val resultName = payload.result().entry()?.name ?: payload.result().value().toString()
        Log.i(
            "SprayerDebug",
            "RX COMMAND_ACK command=$commandName rawCommand=${payload.command().value()} result=$resultName rawResult=${payload.result().value()} progress=${payload.progress()} resultParam2=${payload.resultParam2()} targetSys=${payload.targetSystem()} targetComp=${payload.targetComponent()}"
        )
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
        val relativeAltitudeMeters = position.relativeAlt().toDouble() * 10.0.pow(-3.0)
        val altitudeAmslMeters = position.alt().toDouble() * 10.0.pow(-3.0)
        val isRtkFixed = stateStore.gpsFixType.value == GpsFixType.GPS_FIX_TYPE_RTK_FIXED
        val isArmed = stateStore.armedState.value == true
        if (isRtkFixed && !isArmed) {
            runtimeState.rtkGroundAltitudeOffsetMeters = altitudeAmslMeters
        }
        val adjustedAltitudeMeters = if (isRtkFixed) {
            val offset = runtimeState.rtkGroundAltitudeOffsetMeters ?: altitudeAmslMeters
            val adjusted = altitudeAmslMeters - offset
            if (!isArmed && abs(adjusted) < 0.5) 0.0 else adjusted
        } else {
            relativeAltitudeMeters
        }
        val location = Location("").apply {
            latitude = position.lat().toDouble() * 10.0.pow(-7.0)
            longitude = position.lon().toDouble() * 10.0.pow(-7.0)
            altitude = adjustedAltitudeMeters
        }
        stateStore.droneAltitudeAmslMeters.postValue(altitudeAmslMeters)
        stateStore.droneVerticalSpeedMetersPerSecond.postValue(position.vz().toFloat() / 100.0f)
        runtimeState.lastDroneLocation = Location(location)
        stateStore.droneHeading.postValue(position.hdg().toDouble() / 100.0)
        stateStore.droneLocationLiveData.postValue(location)
        updateGroundSpeed(
            source = "GLOBAL_POSITION_INT.vx/vy",
            sourceRank = SPEED_SOURCE_GLOBAL_POSITION,
            speedMetersPerSecond = TelemetryMapping.globalHorizontalSpeedMetersPerSecond(position.vx(), position.vy())
        )
        inferFlightState(location)
        onDroneLocationUpdated()
    }

    private fun handleGpsFix(fixType: GpsFixType?, rawFixType: Int) {
        stateStore.gpsFixType.postValue(fixType)
        logMappingSummary("gps", "gpsFixTypeRaw=$rawFixType gpsDisplay=${TelemetryMapping.gpsFixLabel(fixType)}")
        val acquired = rawFixType >= 3
        if (runtimeState.lastGpsFixAcquired != acquired) {
            runtimeState.lastGpsFixAcquired = acquired
            onGpsFixChanged(acquired)
        }
    }

    private fun updateGroundSpeed(source: String, sourceRank: Int, speedMetersPerSecond: Float?) {
        val speed = speedMetersPerSecond?.takeIf { TelemetryMapping.isValidGroundSpeedMetersPerSecond(it) }
            ?: return
        val now = System.currentTimeMillis()
        val currentStale = now - runtimeState.lastSpeedSourceUpdatedMs > SPEED_SOURCE_STALE_MS
        if (sourceRank <= runtimeState.lastSpeedSourceRank || currentStale) {
            runtimeState.lastSpeedSourceRank = sourceRank
            runtimeState.lastSpeedSourceUpdatedMs = now
            stateStore.droneGroundSpeedMetersPerSecond.postValue(speed)
            logMappingSummary("speed", "speedSource=$source speedDisplay=${"%.1f".format(speed)}")
        }
    }

    private fun logMappingSummary(key: String, summary: String) {
        if (runtimeState.lastTelemetryMappingSummaries[key] == summary) return
        runtimeState.lastTelemetryMappingSummaries[key] = summary
        Log.d(MAPPING_TAG, summary)
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
