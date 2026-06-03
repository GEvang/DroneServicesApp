package com.example.droneservicesapp.domain.survey

import com.example.droneservicesapp.domain.model.CropType
import com.example.droneservicesapp.domain.model.SprayPreset

object SprayPresets {
    const val CUSTOM_ID = "custom"

    val all: List<SprayPreset> = listOf(
        SprayPreset(
            id = CUSTOM_ID,
            label = "Προσαρμοσμένο",
            cropType = CropType.CUSTOM,
            missionAngleDeg = 0,
            lineSpacingM = 0,
            altitudeM = 0,
            sprayIntensityPercent = 0,
            missionSpeedMs = 0.0,
            estimatedTimeMin = 0,
            description = "Χειροκίνητες ρυθμίσεις αποστολής",
        ),
        SprayPreset(
            id = "olive_dakos_light",
            label = "Ελιά - Δάκος Ελαφρύς",
            cropType = CropType.OLIVE,
            missionAngleDeg = 45,
            lineSpacingM = 18,
            altitudeM = 7,
            sprayIntensityPercent = 35,
            missionSpeedMs = 2.5,
            estimatedTimeMin = 6,
            description = "Για ελαιώνες με χαμηλή πίεση δάκου",
        ),
        SprayPreset(
            id = "olive_dakos_standard",
            label = "Ελιά - Δάκος Κανονικός",
            cropType = CropType.OLIVE,
            missionAngleDeg = 48,
            lineSpacingM = 15,
            altitudeM = 7,
            sprayIntensityPercent = 50,
            missionSpeedMs = 2.0,
            estimatedTimeMin = 8,
            description = "Για ελαιώνες με τυπική πυκνότητα",
        ),
        SprayPreset(
            id = "olive_dakos_dense_grove",
            label = "Ελιά - Δάκος Πυκνή Φύτευση",
            cropType = CropType.OLIVE,
            missionAngleDeg = 45,
            lineSpacingM = 12,
            altitudeM = 6,
            sprayIntensityPercent = 65,
            missionSpeedMs = 1.6,
            estimatedTimeMin = 10,
            description = "Για πυκνούς ελαιώνες",
        ),
        SprayPreset(
            id = "olive_fungus_preventive",
            label = "Ελιά - Προληπτικός Μυκητολογικός",
            cropType = CropType.OLIVE,
            missionAngleDeg = 50,
            lineSpacingM = 14,
            altitudeM = 6,
            sprayIntensityPercent = 55,
            missionSpeedMs = 1.8,
            estimatedTimeMin = 9,
            description = "Προληπτική κάλυψη ελιάς",
        ),
        SprayPreset(
            id = "vineyard_standard",
            label = "Αμπέλι - Κανονικός Ψεκασμός",
            cropType = CropType.GRAPE,
            missionAngleDeg = 90,
            lineSpacingM = 8,
            altitudeM = 4,
            sprayIntensityPercent = 45,
            missionSpeedMs = 1.8,
            estimatedTimeMin = 9,
            description = "Για αμπέλια με τυπική κόμη",
        ),
        SprayPreset(
            id = "vineyard_dense_canopy",
            label = "Αμπέλι - Πυκνή Κόμη",
            cropType = CropType.GRAPE,
            missionAngleDeg = 90,
            lineSpacingM = 6,
            altitudeM = 3,
            sprayIntensityPercent = 65,
            missionSpeedMs = 1.3,
            estimatedTimeMin = 12,
            description = "Για αμπέλια με πυκνή κόμη",
        ),
        SprayPreset(
            id = "vineyard_fungus_preventive",
            label = "Αμπέλι - Προληπτικός Μυκητολογικός",
            cropType = CropType.GRAPE,
            missionAngleDeg = 90,
            lineSpacingM = 7,
            altitudeM = 4,
            sprayIntensityPercent = 55,
            missionSpeedMs = 1.5,
            estimatedTimeMin = 11,
            description = "Προληπτική κάλυψη αμπελιού",
        ),
        SprayPreset(
            id = "general_low_drift",
            label = "Γενικό - Χαμηλή Διασπορά",
            cropType = CropType.GENERAL,
            missionAngleDeg = 45,
            lineSpacingM = 10,
            altitudeM = 4,
            sprayIntensityPercent = 40,
            missionSpeedMs = 1.2,
            estimatedTimeMin = 12,
            description = "Συντηρητικό προφίλ χαμηλής διασποράς",
        ),
    )

    fun byId(id: String?): SprayPreset = all.firstOrNull { it.id == id } ?: custom

    val custom: SprayPreset
        get() = all.first { it.id == CUSTOM_ID }
}
