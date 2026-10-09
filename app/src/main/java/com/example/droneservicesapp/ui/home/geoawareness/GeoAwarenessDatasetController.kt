package com.example.droneservicesapp.ui.home.geoawareness

import android.content.Context
import com.example.droneservicesapp.data.geoawareness.GeoZoneImportedFileDataSource
import com.example.droneservicesapp.data.geoawareness.GeoZoneRepository
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthEvaluator
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult

/** Owns the active geo-awareness dataset and its derived health state. */
class GeoAwarenessDatasetController(context: Context) {
    data class Snapshot(
        val zones: List<GeoZone> = emptyList(),
        val datasetInfo: GeoZoneDatasetInfo? = null,
        val validationResult: GeoZoneValidationResult? = null,
        val health: GeoAwarenessHealth? = null,
        val loadError: Throwable? = null,
        val importedActive: Boolean = false,
    )

    data class LoadOutcome(
        val result: GeoZoneLoadResult,
        val snapshot: Snapshot,
    )

    private val appContext = context.applicationContext

    var snapshot: Snapshot = Snapshot()
        private set

    var loadAttempted: Boolean = false
        private set

    fun loadCurrent(): LoadOutcome {
        loadAttempted = true
        val repository = repository()
        val result = repository.loadCurrentDataset()
        return apply(result, repository.hasImportedDatasets())
    }

    fun reloadCurrent(): LoadOutcome = loadCurrent()

    fun acceptLoaded(result: GeoZoneLoadResult, importedActive: Boolean): LoadOutcome {
        return apply(result, importedActive)
    }

    fun recordEmptyFailure(error: Throwable): Snapshot {
        loadAttempted = true
        val health = GeoAwarenessHealthEvaluator.evaluate(
            datasetInfo = null,
            zones = emptyList(),
            datasetRecords = emptyList(),
            loadError = error,
        )
        snapshot = Snapshot(health = health, loadError = error)
        return snapshot
    }

    fun recordFailure(error: Throwable, datasetRecords: List<GeoZoneDatasetRecord>): Snapshot {
        loadAttempted = true
        snapshot = snapshot.copy(
            loadError = error,
            health = GeoAwarenessHealthEvaluator.evaluate(
                datasetInfo = snapshot.datasetInfo,
                zones = snapshot.zones,
                datasetRecords = datasetRecords,
                validationResult = snapshot.validationResult,
                loadError = error,
            ),
        )
        return snapshot
    }

    fun ensureHealth(datasetRecords: List<GeoZoneDatasetRecord>): GeoAwarenessHealth {
        snapshot.health?.let { return it }
        val health = GeoAwarenessHealthEvaluator.evaluate(
            datasetInfo = snapshot.datasetInfo,
            zones = snapshot.zones,
            datasetRecords = datasetRecords,
            validationResult = snapshot.validationResult,
            loadError = snapshot.loadError,
        )
        snapshot = snapshot.copy(health = health)
        return health
    }

    private fun apply(result: GeoZoneLoadResult, importedActive: Boolean): LoadOutcome {
        val health = GeoAwarenessHealthEvaluator.evaluate(
            datasetInfo = result.datasetInfo,
            zones = result.zones,
            datasetRecords = result.datasetRecords,
            validationResult = result.validationResult,
        )
        snapshot = Snapshot(
            zones = result.zones,
            datasetInfo = result.datasetInfo,
            validationResult = result.validationResult,
            health = health,
            importedActive = importedActive,
        )
        return LoadOutcome(result, snapshot)
    }

    private fun repository() = GeoZoneRepository(
        importedFileDataSource = GeoZoneImportedFileDataSource(appContext),
    )
}
