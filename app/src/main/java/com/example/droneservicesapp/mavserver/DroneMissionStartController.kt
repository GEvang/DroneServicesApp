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

sealed class MissionStartCommandState {
    object Idle : MissionStartCommandState()
    object Pending : MissionStartCommandState()
    object Succeeded : MissionStartCommandState()
    data class Failed(val reason: String) : MissionStartCommandState()
}

enum class MissionStartRequestResult {
    SENT,
    DISCONNECTED,
    NOT_ARMED,
    MISSION_UNAVAILABLE,
    TARGET_UNAVAILABLE,
    ALREADY_PENDING,
}

/** Sends MAV_CMD_MISSION_START after AUTO has been confirmed by vehicle heartbeat telemetry. */
internal class DroneMissionStartController(
    private val mavlinkClient: MavlinkClient,
    private val scope: CoroutineScope,
    private val isConnected: () -> Boolean,
    private val isArmed: () -> Boolean,
    private val hasMission: () -> Boolean,
    private val targetSystemId: () -> Int,
    private val targetComponentId: () -> Int,
    private val commandTimeoutMs: Long = 5_000L,
) {
    companion object {
        private const val GCS_COMPONENT_ID = 190
        private const val MAVLINK_COMPONENT_ALL = 0
    }

    val state = MutableLiveData<MissionStartCommandState>(MissionStartCommandState.Idle)
    private var pending = false
    private var timeoutJob: Job? = null

    @Synchronized
    fun requestStart(): MissionStartRequestResult {
        if (!isConnected()) return MissionStartRequestResult.DISCONNECTED
        if (!isArmed()) return MissionStartRequestResult.NOT_ARMED
        if (!hasMission()) return MissionStartRequestResult.MISSION_UNAVAILABLE
        val systemId = targetSystemId()
        if (systemId < 0) return MissionStartRequestResult.TARGET_UNAVAILABLE
        if (pending) return MissionStartRequestResult.ALREADY_PENDING

        pending = true
        state.postValue(MissionStartCommandState.Pending)
        val componentId = targetComponentId().takeIf { it >= 0 } ?: MAVLINK_COMPONENT_ALL
        val command = CommandLong.builder()
            .targetSystem(systemId)
            .targetComponent(componentId)
            .command(MavCmd.MAV_CMD_MISSION_START)
            .confirmation(0)
            .param1(0f)
            .param2(0f)
            .param3(0f)
            .param4(0f)
            .param5(0f)
            .param6(0f)
            .param7(0f)
            .build()
        mavlinkClient.send2(mavlinkClient.gcsSystemId, GCS_COMPONENT_ID, command)
        DiagnosticLog.event(
            module = "flight",
            message = "mission_start_requested",
            data = mapOf("targetSystemId" to systemId, "targetComponentId" to componentId),
        )
        timeoutJob = scope.launch {
            delay(commandTimeoutMs)
            synchronized(this@DroneMissionStartController) {
                if (pending) failLocked("Timed out waiting for mission-start acknowledgement")
            }
        }
        return MissionStartRequestResult.SENT
    }

    @Synchronized
    fun onCommandAck(ack: CommandAck) {
        if (!pending || ack.command().entry() != MavCmd.MAV_CMD_MISSION_START) return
        when (ack.result().entry()) {
            MavResult.MAV_RESULT_ACCEPTED,
            MavResult.MAV_RESULT_IN_PROGRESS -> {
                pending = false
                timeoutJob?.cancel()
                timeoutJob = null
                state.postValue(MissionStartCommandState.Succeeded)
                DiagnosticLog.event("flight", "mission_start_accepted")
            }
            else -> failLocked(ack.result().entry()?.name ?: "Result ${ack.result().value()}")
        }
    }

    @Synchronized
    fun onConnectionLost() {
        if (pending) failLocked("Connection lost before mission-start acknowledgement")
    }

    private fun failLocked(reason: String) {
        pending = false
        timeoutJob?.cancel()
        timeoutJob = null
        state.postValue(MissionStartCommandState.Failed(reason))
        DiagnosticLog.event(
            module = "flight",
            message = "mission_start_failed",
            severity = "WARN",
            data = mapOf("reason" to reason),
        )
    }
}
