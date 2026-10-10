package com.example.droneservicesapp.ui.home.model

import com.example.droneservicesapp.mavserver.ArduCopterFlightMode
import com.example.droneservicesapp.mavserver.ArmCommandState
import com.example.droneservicesapp.mavserver.FlightModeCommandState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlUiStateMapperTest {
    @Test
    fun autoModeRequiresSlideConfirmation() {
        val confirmation = FlightModeUiStateMapper.confirmationFor(ArduCopterFlightMode.AUTO)!!

        assertEquals(FlightModeControlUiState.Gesture.SLIDE, confirmation.gesture)
    }

    @Test
    fun pendingFlightModeDisablesAllModeActions() {
        val state = FlightModeUiStateMapper.map(
            isOpen = true,
            currentCustomMode = ArduCopterFlightMode.LOITER.customMode,
            isControlEnabled = true,
            hasUploadedMission = true,
            commandState = FlightModeCommandState.Pending(ArduCopterFlightMode.AUTO, false),
        )

        assertFalse(state.isControlEnabled)
        assertEquals(ArduCopterFlightMode.AUTO, state.pendingMode)
    }

    @Test
    fun armGestureRequiresConnectionAndNonPendingState() {
        val ready = ArmUiStateMapper.map(false, true, false, ArmCommandState.Idle)
        val pending = ArmUiStateMapper.map(true, true, false, ArmCommandState.Pending())

        assertTrue(ready.canStartGesture)
        assertFalse(pending.canStartGesture)
    }
}
