package com.example.droneservicesapp.domain.model

data class SurveyGridParams(
    val stripSpacingMeters: Int = 70,
    val heightAboveTerrainMeters: Int = 50,
    val overlapPercent: Int = 80,
    val gridAngleDegrees: Int = 90,
    val terrainSegmentMeters: Double = 2.5,
    val canopySmoothingMeters: Int = 5,
)
