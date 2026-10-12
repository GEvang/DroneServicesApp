package com.example.droneservicesapp.ui.geoawareness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoAwarenessContractTest {
    @Test
    fun busyStateIsImmutableAndClearsItsTransientMessage() {
        val initial = GeoAwarenessUiState()
        val loading = initial.copy(busy = true, busyMessage = "Loading")
        val complete = loading.copy(busy = false, busyMessage = null)

        assertFalse(initial.busy)
        assertTrue(loading.busy)
        assertEquals("Loading", loading.busyMessage)
        assertFalse(complete.busy)
        assertNull(complete.busyMessage)
    }

    @Test
    fun updatePickerRequestRetainsItsDatasetTargetAcrossRecreation() {
        val request = GeoAwarenessPickerRequest(
            mode = GeoAwarenessSessionState.PickerMode.UPDATE_EXISTING,
            updateStorageFileName = "active-dataset.json",
        )
        val restored = GeoAwarenessUiState(pendingPicker = request)

        assertEquals(GeoAwarenessSessionState.PickerMode.UPDATE_EXISTING, restored.pendingPicker?.mode)
        assertEquals("active-dataset.json", restored.pendingPicker?.updateStorageFileName)
    }

    @Test
    fun confirmationIsDurableStateInsteadOfADuplicatedTransientDialogEffect() {
        val state = GeoAwarenessUiState(dialog = GeoAwarenessDialog.ConfirmRemoveAll)

        assertEquals(GeoAwarenessDialog.ConfirmRemoveAll, state.copy().dialog)
        assertNull(state.copy(dialog = null).dialog)
    }

    @Test
    fun nullValidationMapsToSuccessfulPresentation() {
        val presentation = GeoAwarenessStatusPresentationMapper.validation(null)

        assertEquals(GeoAwarenessStatusTone.SUCCESS, presentation.tone)
        assertEquals(0, presentation.result.errorCount)
    }
}
