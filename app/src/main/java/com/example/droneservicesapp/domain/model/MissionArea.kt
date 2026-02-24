package com.example.droneservicesapp.domain.model

import com.google.android.gms.maps.model.LatLng

/**
 * Pure mission-area state (NO map SDK rendering objects).
 */
data class MissionArea(
    val vertices: MutableList<LatLng> = mutableListOf(),
    var surveyPath: List<LatLng> = emptyList()
) {
    fun clearAll() {
        vertices.clear()
        surveyPath = emptyList()
    }

    fun clearSurveyPath() {
        surveyPath = emptyList()
    }

    fun hasValidPolygon(): Boolean = vertices.size >= 3
}