package com.example.droneservicesapp.domain.survey

import com.example.droneservicesapp.domain.model.CropType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SprayPresetsTest {
    @Test
    fun includesCustomOliveGrapeAndGeneralPresets() {
        assertEquals(9, SprayPresets.all.size)
        assertTrue(SprayPresets.all.any { it.id == SprayPresets.CUSTOM_ID && it.cropType == CropType.CUSTOM })
        assertTrue(SprayPresets.all.any { it.cropType == CropType.OLIVE })
        assertTrue(SprayPresets.all.any { it.cropType == CropType.GRAPE })
        assertTrue(SprayPresets.all.any { it.cropType == CropType.GENERAL })
    }

    @Test
    fun oliveDakosStandardHasExpectedDefaults() {
        val preset = SprayPresets.byId("olive_dakos_standard")

        assertEquals("Ελιά - Δάκος Κανονικός", preset.label)
        assertEquals(48, preset.missionAngleDeg)
        assertEquals(15, preset.lineSpacingM)
        assertEquals(7, preset.altitudeM)
        assertEquals(50, preset.sprayIntensityPercent)
        assertEquals(2.0, preset.missionSpeedMs, 0.001)
        assertEquals(8, preset.estimatedTimeMin)
    }
}
