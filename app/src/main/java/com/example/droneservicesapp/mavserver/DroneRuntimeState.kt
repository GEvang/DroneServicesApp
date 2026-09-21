package com.example.droneservicesapp.mavserver

import android.location.Location
import io.reactivex.disposables.Disposable
import java.util.concurrent.ConcurrentHashMap

internal class DroneRuntimeState {
    @Volatile var bridgeAttached: Boolean = false
    @Volatile var lastAutopilotHeartbeatMs: Long = 0L
    @Volatile var lastNonHeartbeatMs: Long = 0L
    @Volatile var lastDroneLocation: Location? = null
    @Volatile var autopilotSysId: Int = -1
    @Volatile var autopilotCompId: Int = -1
    @Volatile var lastLoggedConnectionState: Boolean? = null
    @Volatile var lastLoggedTelemetryAlive: Boolean? = null
    @Volatile var lastLoggedArmedState: Boolean? = null
    @Volatile var lastLoggedFlightMode: Int? = null
    @Volatile var lastGpsFixAcquired: Boolean? = null
    @Volatile var batteryLowLogged: Boolean = false
    @Volatile var lastInferredFlightState: String = "UNKNOWN"
    @Volatile var lastSpeedSourceRank: Int = Int.MAX_VALUE
    @Volatile var lastSpeedSourceUpdatedMs: Long = 0L
    @Volatile var lastUiPositionPublishMs: Long = 0L
    @Volatile var lastUiSpeedPublishMs: Long = 0L
    @Volatile var lastMissionRefreshMs: Long = 0L
    @Volatile var lastBatteryVoltageSourceRank: Int = Int.MAX_VALUE
    @Volatile var lastBatteryVoltageSourceUpdatedMs: Long = 0L
    @Volatile var rtkGroundAltitudeOffsetMeters: Double? = null
    val lastTelemetryMappingSummaries: ConcurrentHashMap<String, String> = ConcurrentHashMap()
    val batteryPercentageStabilizer = BatteryPercentageStabilizer()
    val batteryVoltageStabilizer = BatteryVoltageStabilizer()
    @Volatile var mavlinkMessagesDisposable: Disposable? = null

    fun clearAutopilotTarget() {
        autopilotSysId = -1
        autopilotCompId = -1
        lastAutopilotHeartbeatMs = 0L
        lastNonHeartbeatMs = 0L
        lastSpeedSourceRank = Int.MAX_VALUE
        lastSpeedSourceUpdatedMs = 0L
        lastUiPositionPublishMs = 0L
        lastUiSpeedPublishMs = 0L
        lastMissionRefreshMs = 0L
        lastBatteryVoltageSourceRank = Int.MAX_VALUE
        lastBatteryVoltageSourceUpdatedMs = 0L
        batteryPercentageStabilizer.reset()
        batteryVoltageStabilizer.reset()
    }
}
