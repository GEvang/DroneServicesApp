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

sealed class ArmCommandState {
    object Idle : ArmCommandState()
    data class Pending(val acknowledged: Boolean = false) : ArmCommandState()
    object Succeeded : ArmCommandState()
    data class Failed(val reason: String) : ArmCommandState()
}

enum class ArmRequestResult {
    SENT,
    DISCONNECTED,
    TARGET_UNAVAILABLE,
    ALREADY_ARMED,
    ALREADY_PENDING
}

/** Sends a deliberate arm request and waits for the aircraft heartbeat to confirm it. */
internal class DroneArmController(
    private val mavlinkClient: MavlinkClient,
    private val scope: CoroutineScope,
    private val isConnected: () -> Boolean,
    private val isArmed: () -> Boolean,
    private val targetSystemId: () -> Int,
    private val targetComponentId: () -> Int,
    private val commandTimeoutMs: Long = 5_000L,
) {
    companion object {
        private const val GCS_SYSTEM_ID = 255
        private const val GCS_COMPONENT_ID = 190
        private const val MAVLINK_COMPONENT_ALL = 0
    }

    val state = MutableLiveData<ArmCommandState>(ArmCommandState.Idle)
    private var pending = false
    private var timeoutJob: Job? = null

    @Synchronized
    fun requestArm(): ArmRequestResult {
        if (!isConnected()) return ArmRequestResult.DISCONNECTED
        if (isArmed()) return ArmRequestResult.ALREADY_ARMED
        val systemId = targetSystemId()
        if (systemId < 0) return ArmRequestResult.TARGET_UNAVAILABLE
        if (pending) return ArmRequestResult.ALREADY_PENDING

        pending = true
        state.postValue(ArmCommandState.Pending())
        val componentId = targetComponentId().takeIf { it >= 0 } ?: MAVLINK_COMPONENT_ALL
        val command = CommandLong.builder()
            .targetSystem(systemId)
            .targetComponent(componentId)
            .command(MavCmd.MAV_CMD_COMPONENT_ARM_DISARM)
            .confirmation(0)
            .param1(1f)
            .param2(0f)
            .param3(0f)
            .param4(0f)
            .param5(0f)
            .param6(0f)
            .param7(0f)
            .build()
        mavlinkClient.send2(GCS_SYSTEM_ID, GCS_COMPONENT_ID, command)
        DiagnosticLog.event("flight", "arm_requested", data = mapOf("targetSystemId" to systemId))
        timeoutJob = scope.launch {
            delay(commandTimeoutMs)
            synchronized(this@DroneArmController) {
                if (pending) failLocked("Timed out waiting for the vehicle to confirm arming")
            }
        }
        return ArmRequestResult.SENT
    }

    @Synchronized
    fun onCommandAck(ack: CommandAck) {
        if (!pending || ack.command().entry() != MavCmd.MAV_CMD_COMPONENT_ARM_DISARM) return
        when (ack.result().entry()) {
            MavResult.MAV_RESULT_ACCEPTED,
            MavResult.MAV_RESULT_IN_PROGRESS -> state.postValue(ArmCommandState.Pending(acknowledged = true))
            else -> failLocked(ack.result().entry()?.name ?: "Result ${ack.result().value()}")
        }
    }

    @Synchronized
    fun onHeartbeatArmed(armed: Boolean) {
        if (!pending || !armed) return
        pending = false
        timeoutJob?.cancel()
        timeoutJob = null
        state.postValue(ArmCommandState.Succeeded)
        DiagnosticLog.event("flight", "arm_confirmed")
    }

    @Synchronized
    fun onConnectionLost() {
        if (pending) failLocked("Connection lost before the vehicle confirmed arming")
    }

    fun clearResult() {
        if (state.value !is ArmCommandState.Pending) state.postValue(ArmCommandState.Idle)
    }

    private fun failLocked(reason: String) {
        pending = false
        timeoutJob?.cancel()
        timeoutJob = null
        state.postValue(ArmCommandState.Failed(reason))
        DiagnosticLog.event("flight", "arm_failed", "WARN", mapOf("reason" to reason))
    }
}
