package com.example.droneservicesapp.domain.model

data class SurveyGridParams(
    val stripSpacingMeters: Int = 8,
    val heightAboveTerrainMeters: Int = 5,
    val overlapPercent: Int = 20,
    val gridAngleDegrees: Int = 0,
    val terrainSegmentMeters: Double = 2.5,
    val canopySmoothingMeters: Int = 5,
)
