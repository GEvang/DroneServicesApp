package com.example.droneservicesapp.ui.home.model

import com.example.droneservicesapp.mavserver.ArduCopterFlightMode
import com.example.droneservicesapp.mavserver.FlightModeCommandState

data class FlightModeControlUiState(
    val isOpen: Boolean = false,
    val currentMode: ArduCopterFlightMode? = null,
    val pendingMode: ArduCopterFlightMode? = null,
    val isControlEnabled: Boolean = false,
    val hasUploadedMission: Boolean = false,
    val pendingConfirmation: Confirmation? = null,
) {
    data class Confirmation(
        val mode: ArduCopterFlightMode,
        val gesture: Gesture,
        val holdDurationMs: Long = 0,
    )

    enum class Gesture { HOLD, SLIDE }
}

sealed interface FlightModeAction {
    data object Open : FlightModeAction
    data object Dismiss : FlightModeAction
    data class Select(val mode: ArduCopterFlightMode) : FlightModeAction
    data class Confirm(val mode: ArduCopterFlightMode) : FlightModeAction
}

object FlightModeUiStateMapper {
    fun map(
        isOpen: Boolean,
        currentCustomMode: Int?,
        isControlEnabled: Boolean,
        hasUploadedMission: Boolean,
        commandState: FlightModeCommandState,
        pendingConfirmation: FlightModeControlUiState.Confirmation? = null,
    ) = FlightModeControlUiState(
        isOpen = isOpen,
        currentMode = ArduCopterFlightMode.fromCustomMode(currentCustomMode),
        pendingMode = (commandState as? FlightModeCommandState.Pending)?.requestedMode,
        isControlEnabled = isControlEnabled && commandState !is FlightModeCommandState.Pending,
        hasUploadedMission = hasUploadedMission,
        pendingConfirmation = pendingConfirmation,
    )

    fun confirmationFor(mode: ArduCopterFlightMode): FlightModeControlUiState.Confirmation? = when {
        mode == ArduCopterFlightMode.AUTO -> FlightModeControlUiState.Confirmation(
            mode,
            FlightModeControlUiState.Gesture.SLIDE,
        )
        mode.holdDurationMs > 0 -> FlightModeControlUiState.Confirmation(
            mode,
            FlightModeControlUiState.Gesture.HOLD,
            mode.holdDurationMs,
        )
        else -> null
    }
}
