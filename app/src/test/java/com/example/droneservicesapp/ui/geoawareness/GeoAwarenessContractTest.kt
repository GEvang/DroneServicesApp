package com.example.droneservicesapp.ui.geoawareness

import com.example.droneservicesapp.ui.architecture.CommonUiEffect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoAwarenessContractTest {
    @Test
    fun busyStateIsImmutableAndClearsItsTransientMessage() {
        val holder = GeoAwarenessUiStateHolder()

        holder.dispatch(GeoAwarenessAction.BusyChanged(true, "Loading"))
        val loading = holder.state.value
        holder.dispatch(GeoAwarenessAction.BusyChanged(false))

        assertTrue(loading.busy)
        assertEquals("Loading", loading.busyMessage)
        assertFalse(holder.state.value.busy)
        assertNull(holder.state.value.busyMessage)
    }

    @Test
    fun filePickerIsAOneTimeEffectNotPersistentState() = runBlocking {
        val holder = GeoAwarenessUiStateHolder()

        holder.dispatch(GeoAwarenessAction.ImportDataset)

        val effect = holder.effects.first() as GeoAwarenessEffect.Platform
        assertTrue(effect.effect is CommonUiEffect.LaunchFilePicker)
        assertFalse(holder.state.value.busy)
    }

    @Test
    fun observedOverlayStateDoesNotRequestPersistence() {
        val reduction = GeoAwarenessReducer.reduce(
            GeoAwarenessUiState(overlayVisible = true),
            GeoAwarenessAction.OverlayVisibilityObserved(false),
        )

        assertFalse(reduction.state.overlayVisible)
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun nullValidationMapsToSuccessfulPresentation() {
        val presentation = GeoAwarenessStatusPresentationMapper.validation(null)

        assertEquals(GeoAwarenessStatusTone.SUCCESS, presentation.tone)
        assertEquals(0, presentation.result.errorCount)
    }
}
