package com.example.droneservicesapp.ui.home.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.droneservicesapp.domain.model.LatLon

/** Flight-controller terrain coverage disc. Port of the former `TerrainCoverageOverlayController`. */
class TerrainCoverageLayerState {
    data class Coverage(val center: LatLon, val radiusMeters: Double)

    var coverage by mutableStateOf<Coverage?>(null)
        private set

    fun render(center: LatLon, radiusMeters: Double) {
        val next = Coverage(center, radiusMeters).takeIf { radiusMeters > 0.0 }
        if (next != coverage) coverage = next
    }

    fun clear() {
        coverage = null
    }
}
