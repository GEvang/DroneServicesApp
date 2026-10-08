package com.example.droneservicesapp.ui.home.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewMapFocusControllerTest {
    @Test
    fun `safe zoom stays within map limits for a point-sized asset`() {
        val zoom = PreviewMapFocusController.calculateSafeZoom(
            minLat = 35.0,
            maxLat = 35.0,
            minLon = 24.0,
            maxLon = 24.0,
            viewportWidth = 1080,
            viewportHeight = 720,
        )

        assertEquals(21.0, zoom, 0.0)
    }

    @Test
    fun `safe zoom fits wider bounds farther out`() {
        val narrow = PreviewMapFocusController.calculateSafeZoom(
            35.0, 35.01, 24.0, 24.01, 1080, 720,
        )
        val wide = PreviewMapFocusController.calculateSafeZoom(
            35.0, 36.0, 24.0, 25.0, 1080, 720,
        )

        assertTrue(wide < narrow)
        assertTrue(wide in 2.0..21.0)
    }

    @Test
    fun `safe zoom tolerates an unmeasured map view`() {
        val zoom = PreviewMapFocusController.calculateSafeZoom(
            35.0, 35.1, 24.0, 24.1, 0, 0,
        )

        assertTrue(zoom.isFinite())
        assertTrue(zoom in 2.0..21.0)
    }
}
