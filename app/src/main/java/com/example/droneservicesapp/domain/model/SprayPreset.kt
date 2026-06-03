package com.example.droneservicesapp.domain.model

data class SprayPreset(
    val id: String,
    val label: String,
    val cropType: CropType,
    val missionAngleDeg: Int,
    val lineSpacingM: Int,
    val altitudeM: Int,
    val sprayIntensityPercent: Int,
    val missionSpeedMs: Double,
    val estimatedTimeMin: Int,
    val description: String,
)

enum class CropType {
    OLIVE,
    GRAPE,
    GENERAL,
    CUSTOM,
}
