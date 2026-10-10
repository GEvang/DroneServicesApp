package com.example.droneservicesapp.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewModePresenterTest {
    @Test
    fun surveyMapCyclesDirectlyToPointCloud() {
        val state = PreviewModePresenter.present(
            mode = PreviewMode.MAP,
            surveyMode = true,
            hasPointCloud = false,
            settings = PreviewSettings(),
        )

        assertEquals(PreviewMode.POINT_CLOUD, state.nextMode)
        assertFalse(state.showOrthoMode)
        assertTrue(state.showMapSurface)
    }

    @Test
    fun pointCloudResetIsHiddenUntilACloudExists() {
        val state = PreviewModePresenter.present(
            mode = PreviewMode.POINT_CLOUD,
            surveyMode = false,
            hasPointCloud = false,
            settings = PreviewSettings(),
        )

        assertNull(state.secondaryAction)
        assertTrue(state.showPointCloudSurface)
    }
}
