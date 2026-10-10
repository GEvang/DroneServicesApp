package com.example.droneservicesapp.ui.home.model

import com.example.droneservicesapp.mavserver.ArmCommandState

data class ArmControlUiState(
    val isOpen: Boolean = false,
    val isConnected: Boolean = false,
    val isArmed: Boolean = false,
    val isPending: Boolean = false,
    val failureMessage: String? = null,
) {
    val canStartGesture: Boolean
        get() = isConnected && !isArmed && !isPending
}

sealed interface ArmAction {
    data object Open : ArmAction
    data object Dismiss : ArmAction
    data object SlideCompleted : ArmAction
    data object ResultConsumed : ArmAction
}

object ArmUiStateMapper {
    fun map(
        isOpen: Boolean,
        isConnected: Boolean,
        isArmed: Boolean,
        commandState: ArmCommandState,
    ) = ArmControlUiState(
        isOpen = isOpen,
        isConnected = isConnected,
        isArmed = isArmed,
        isPending = commandState is ArmCommandState.Pending,
        failureMessage = (commandState as? ArmCommandState.Failed)?.reason,
    )
}
