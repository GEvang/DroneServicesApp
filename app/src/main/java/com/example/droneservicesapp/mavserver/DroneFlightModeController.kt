package com.example.droneservicesapp.mavserver

import androidx.lifecycle.MutableLiveData
import com.example.droneservicesapp.data.diagnostics.DiagnosticLog
import com.example.droneservicesapp.data.mavlink.MavlinkClient
import io.dronefleet.mavlink.common.CommandAck
import io.dronefleet.mavlink.common.CommandLong
import io.dronefleet.mavlink.common.MavCmd
import io.dronefleet.mavlink.common.MavResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class DroneFlightModeController(
    private val mavlinkClient: MavlinkClient,
    private val scope: CoroutineScope,
    private val isConnected: () -> Boolean,
    private val targetSystemId: () -> Int,
    private val targetComponentId: () -> Int,
    private val commandTimeoutMs: Long = 4_000L,
) {
    companion object {
        private const val GCS_SYSTEM_ID = 255
        private const val GCS_COMPONENT_ID = 190
        private const val MAVLINK_COMPONENT_ALL = 0
    }

    val state = MutableLiveData<FlightModeCommandState>(FlightModeCommandState.Idle)

    private val lock = Any()
    private var pendingMode: ArduCopterFlightMode? = null
    private var timeoutJob: Job? = null

    fun requestMode(mode: ArduCopterFlightMode): FlightModeRequestResult {
        if (!isConnected()) return FlightModeRequestResult.Disconnected
        val systemId = targetSystemId()
        if (systemId < 0) return FlightModeRequestResult.TargetUnavailable

        synchronized(lock) {
            if (pendingMode != null) return FlightModeRequestResult.AlreadyPending
            pendingMode = mode
            state.postValue(FlightModeCommandState.Pending(mode, acknowledged = false))
        }

        val componentId = targetComponentId().takeIf { it >= 0 } ?: MAVLINK_COMPONENT_ALL
        val command = CommandLong.builder()
            .targetSystem(systemId)
            .targetComponent(componentId)
            .command(MavCmd.MAV_CMD_DO_SET_MODE)
            .confirmation(0)
            .param1(1f) // MAV_MODE_FLAG_CUSTOM_MODE_ENABLED
            .param2(mode.customMode.toFloat())
            .param3(0f)
            .param4(0f)
            .param5(0f)
            .param6(0f)
            .param7(0f)
            .build()

        mavlinkClient.send2(GCS_SYSTEM_ID, GCS_COMPONENT_ID, command)
        DiagnosticLog.event(
            module = "flight",
            message = "flight_mode_requested",
            data = mapOf("mode" to mode.name, "customMode" to mode.customMode)
        )
        scheduleTimeout(mode)
        return FlightModeRequestResult.Sent
    }

    fun onCommandAck(ack: CommandAck) {
        if (ack.command().entry() != MavCmd.MAV_CMD_DO_SET_MODE) return
        val mode = synchronized(lock) { pendingMode } ?: return
        when (ack.result().entry()) {
            MavResult.MAV_RESULT_ACCEPTED,
            MavResult.MAV_RESULT_IN_PROGRESS -> {
                state.postValue(FlightModeCommandState.Pending(mode, acknowledged = true))
            }
            else -> fail(mode, ack.result().entry()?.name ?: "Result ${ack.result().value()}")
        }
    }

    fun onHeartbeatMode(customMode: Int) {
        val mode = synchronized(lock) { pendingMode } ?: return
        if (customMode != mode.customMode) return
        synchronized(lock) {
            if (pendingMode != mode) return
            pendingMode = null
            timeoutJob?.cancel()
            timeoutJob = null
        }
        state.postValue(FlightModeCommandState.Succeeded(mode))
        DiagnosticLog.event(
            module = "flight",
            message = "flight_mode_confirmed",
            data = mapOf("mode" to mode.name, "customMode" to mode.customMode)
        )
    }

    fun onConnectionLost() {
        val mode = synchronized(lock) { pendingMode } ?: return
        fail(mode, "Connection lost before the vehicle confirmed the mode")
    }

    fun clearResult() {
        if (state.value !is FlightModeCommandState.Pending) {
            state.postValue(FlightModeCommandState.Idle)
        }
    }

    private fun scheduleTimeout(mode: ArduCopterFlightMode) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(commandTimeoutMs)
            val stillPending = synchronized(lock) { pendingMode == mode }
            if (stillPending) {
                fail(mode, "Timed out waiting for vehicle confirmation")
            }
        }
    }

    private fun fail(mode: ArduCopterFlightMode, reason: String) {
        synchronized(lock) {
            if (pendingMode != mode) return
            pendingMode = null
            timeoutJob?.cancel()
            timeoutJob = null
        }
        state.postValue(FlightModeCommandState.Failed(mode, reason))
        DiagnosticLog.event(
            module = "flight",
            message = "flight_mode_failed",
            severity = "WARN",
            data = mapOf("mode" to mode.name, "reason" to reason)
        )
    }
}
