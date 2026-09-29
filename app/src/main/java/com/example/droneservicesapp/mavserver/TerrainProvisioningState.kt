package com.example.droneservicesapp.mavserver

import com.example.droneservicesapp.domain.model.LatLon

enum class TerrainFailure {
    NO_INTERNET_OR_CACHE,
    SOURCE_DOWNLOAD_FAILED,
    INVALID_SOURCE_TILE,
    MISSING_ELEVATION,
    MISSION_OUTSIDE_COVERAGE,
    UNSUPPORTED_FLIGHT_CONTROLLER,
    PARAMETER_REJECTED,
    TRANSFER_TIMEOUT,
    VERIFICATION_TIMEOUT,
    DISCONNECTED,
}

sealed class TerrainProvisioningState {
    object Idle : TerrainProvisioningState()
    data class PreparingSource(
        val completedTiles: Int,
        val totalTiles: Int,
        val tileName: String?,
        val progressPercent: Int,
    ) : TerrainProvisioningState()
    data class SourceReady(val tileCount: Int, val center: LatLon, val radiusMeters: Double) : TerrainProvisioningState()
    object WaitingForMissionUpload : TerrainProvisioningState()
    data class Serving(val blocksSent: Int, val pending: Int?) : TerrainProvisioningState()
    data class Verifying(val checked: Int, val total: Int) : TerrainProvisioningState()
    data class Ready(val blocksSent: Int) : TerrainProvisioningState()
    data class Failed(val failure: TerrainFailure, val detail: String? = null) : TerrainProvisioningState()
}

internal fun TerrainProvisioningState.requiresAircraftTerrainGate(): Boolean =
    this !is TerrainProvisioningState.Idle && this !is TerrainProvisioningState.SourceReady
