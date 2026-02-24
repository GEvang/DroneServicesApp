package com.example.droneservicesapp.domain.model

import com.google.android.gms.maps.model.LatLng

/**
 * Pure mission-area state (NO map SDK rendering objects).
 * Represents only polygon input via vertices.
 */
data class MissionArea(
    val vertices: MutableList<LatLng> = mutableListOf()
) {
    fun clearAll() {
        vertices.clear()
    }

    @Deprecated("surveyPath has been removed. Use clearAll() for vertices only.")
    fun clearSurveyPath() {
        // No-op: surveyPath is no longer part of MissionArea
    }

    fun hasValidPolygon(): Boolean = vertices.size >= 3
}