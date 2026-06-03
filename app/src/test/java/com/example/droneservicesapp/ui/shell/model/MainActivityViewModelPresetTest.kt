package com.example.droneservicesapp.ui.shell.model

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.droneservicesapp.domain.survey.SprayPresets
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainActivityViewModelPresetTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun applyPresetCopiesDefaultsIntoActivePlanningValues() {
        val viewModel = MainActivityViewModel()

        viewModel.applySprayPreset("vineyard_dense_canopy")

        assertEquals("vineyard_dense_canopy", viewModel.selectedSprayPresetId.value)
        assertEquals(90.0, viewModel.angleProgress.value ?: -1.0, 0.001)
        assertEquals(6.0, viewModel.lineDistanceProgress.value ?: -1.0, 0.001)
        assertEquals(3.0, viewModel.flightAltProgress.value ?: -1.0, 0.001)
        assertEquals(65.0, viewModel.sprayerProgress.value ?: -1.0, 0.001)
        assertEquals(1.3, viewModel.flightSpeed.value ?: -1.0, 0.001)
    }

    @Test
    fun manualEditAfterPresetSwitchesToCustomWithoutResettingValues() {
        val viewModel = MainActivityViewModel()
        viewModel.applySprayPreset("olive_dakos_standard")

        viewModel.updateLineSpacing(13)

        assertEquals(SprayPresets.CUSTOM_ID, viewModel.selectedSprayPresetId.value)
        assertEquals(13.0, viewModel.lineDistanceProgress.value ?: -1.0, 0.001)
        assertEquals(48.0, viewModel.angleProgress.value ?: -1.0, 0.001)
        assertEquals(7.0, viewModel.flightAltProgress.value ?: -1.0, 0.001)
        assertEquals(50.0, viewModel.sprayerProgress.value ?: -1.0, 0.001)
        assertEquals(2.0, viewModel.flightSpeed.value ?: -1.0, 0.001)
    }

    @Test
    fun selectingCustomDoesNotResetCurrentValues() {
        val viewModel = MainActivityViewModel()
        viewModel.applySprayPreset("general_low_drift")

        viewModel.applySprayPreset(SprayPresets.CUSTOM_ID)

        assertEquals(SprayPresets.CUSTOM_ID, viewModel.selectedSprayPresetId.value)
        assertEquals(45.0, viewModel.angleProgress.value ?: -1.0, 0.001)
        assertEquals(10.0, viewModel.lineDistanceProgress.value ?: -1.0, 0.001)
        assertEquals(4.0, viewModel.flightAltProgress.value ?: -1.0, 0.001)
        assertEquals(40.0, viewModel.sprayerProgress.value ?: -1.0, 0.001)
        assertEquals(1.2, viewModel.flightSpeed.value ?: -1.0, 0.001)
    }
}
