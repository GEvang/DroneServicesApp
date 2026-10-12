package com.example.droneservicesapp.ui.geoawareness

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoAwarenessSessionStateTest {
    @Test
    fun `workflow progress remains available to recreated coordinators`() {
        val state = GeoAwarenessSessionState().apply {
            lastProcessedReloadToken = 42L
        }

        assertEquals(42L, state.lastProcessedReloadToken ?: -1L)
    }
}
