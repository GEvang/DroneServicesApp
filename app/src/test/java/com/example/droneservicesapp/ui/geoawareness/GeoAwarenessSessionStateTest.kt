package com.example.droneservicesapp.ui.geoawareness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeoAwarenessSessionStateTest {
    @Test
    fun `clear view state removes picker and telemetry but preserves reload progress`() {
        val state = GeoAwarenessSessionState().apply {
            pendingPickerOperation = GeoAwarenessSessionState.PendingPickerOperation(
                GeoAwarenessSessionState.PickerMode.UPDATE_EXISTING,
                "dataset.json",
            )
            lastProcessedReloadToken = 42L
        }

        state.clearViewState()

        assertNull(state.pendingPickerOperation)
        assertNull(state.telemetry)
        assertEquals(42L, state.lastProcessedReloadToken ?: -1L)
    }
}
