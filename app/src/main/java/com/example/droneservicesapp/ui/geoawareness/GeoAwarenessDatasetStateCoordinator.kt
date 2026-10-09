package com.example.droneservicesapp.ui.geoawareness

import android.content.Context
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import com.example.droneservicesapp.ui.home.geoawareness.GeoAwarenessDatasetController
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns loading and publication of the screen's active geo-awareness dataset snapshot. */
class GeoAwarenessDatasetStateCoordinator(
    context: Context,
    private val scope: CoroutineScope,
    private val sharedState: MainActivityViewModel,
    private val eventLogger: GeoAwarenessEventLogger,
    private val callbacks: Callbacks,
) {
    data class Snapshot(
        val datasetInfo: GeoZoneDatasetInfo? = null,
        val zones: List<GeoZone> = emptyList(),
        val records: List<GeoZoneDatasetRecord> = emptyList(),
        val validation: GeoZoneValidationResult? = null,
        val health: GeoAwarenessHealth,
        val importedActive: Boolean = false,
        val loadError: Throwable? = null,
    )

    data class Callbacks(
        val onSnapshot: (Snapshot) -> Unit,
        val onBusyChanged: (Boolean, String?) -> Unit,
        val onRefreshSucceeded: (GeoZoneLoadResult) -> Unit,
        val onFailure: (Throwable, Boolean) -> Unit,
        val onAuditLogChanged: () -> Unit,
    )

    private val datasetController = GeoAwarenessDatasetController(context.applicationContext)
    private var loadJob: Job? = null
    private var active = true
    private var generation = 0L
    private var lastReloadToken: Long? = null
    private var lastStaleSignature: String? = null
    private var pendingForcedReload = false

    val isLoading: Boolean
        get() = loadJob?.isActive == true

    fun loadIfNeeded(forceReload: Boolean = false) {
        if (!active || isLoading) return
        if (!forceReload) {
            sharedSnapshot()?.let {
                callbacks.onSnapshot(it)
                return
            }
        }
        load(forceReload = forceReload, manualRefresh = false)
    }

    fun refresh(manual: Boolean) {
        if (!active || isLoading) return
        load(forceReload = true, manualRefresh = manual)
    }

    fun handleReloadToken(token: Long?) {
        if (token == null || token <= 0L || token == lastReloadToken) return
        lastReloadToken = token
        if (isLoading) {
            pendingForcedReload = true
            return
        }
        loadIfNeeded(forceReload = true)
    }

    fun acceptMutationResult(result: GeoZoneLoadResult, importedActive: Boolean): Snapshot {
        val outcome = datasetController.acceptLoaded(result, importedActive)
        val snapshot = outcome.toSnapshot()
        publish(snapshot)
        val token = sharedState.notifyGeoZoneDatasetReloaded()
        lastReloadToken = token
        return snapshot
    }

    fun clear() {
        active = false
        generation++
        loadJob?.cancel()
        loadJob = null
    }

    private fun load(forceReload: Boolean, manualRefresh: Boolean) {
        val requestGeneration = ++generation
        callbacks.onBusyChanged(true, if (manualRefresh) "Refreshing geo-awareness status..." else "Loading geo-zone dataset...")
        loadJob = scope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) {
                    loadMutex.withLock {
                        if (forceReload) datasetController.reloadCurrent() else datasetController.loadCurrent()
                    }
                }
                if (!isCurrent(requestGeneration)) return@launch
                val snapshot = outcome.toSnapshot()
                publish(snapshot)
                if (manualRefresh) callbacks.onRefreshSucceeded(outcome.result)
            } catch (error: Exception) {
                if (!isCurrent(requestGeneration)) return@launch
                val failure = emptyFailureSnapshot(error)
                publish(failure)
                callbacks.onFailure(error, manualRefresh)
            } finally {
                if (isCurrent(requestGeneration)) {
                    loadJob = null
                    callbacks.onBusyChanged(false, null)
                    if (pendingForcedReload) {
                        pendingForcedReload = false
                        loadIfNeeded(forceReload = true)
                    }
                }
            }
        }
    }

    private fun publish(snapshot: Snapshot) {
        if (!active) return
        callbacks.onSnapshot(snapshot)
        sharedState.geoZoneDatasetInfo.value = snapshot.datasetInfo
        sharedState.geoZoneValidationResult.value = snapshot.validation
        sharedState.geoZoneDatasetRecords.value = snapshot.records
        sharedState.geoAwarenessHealth.value = snapshot.health
        sharedState.geoZoneImportedActive.value = snapshot.importedActive
        logNewStaleDatasets(snapshot.records, snapshot.health)
    }

    private fun sharedSnapshot(): Snapshot? {
        val info = sharedState.geoZoneDatasetInfo.value ?: return null
        val records = sharedState.geoZoneDatasetRecords.value ?: return null
        val validation = sharedState.geoZoneValidationResult.value
        val zones = datasetController.snapshot.zones
        if (zones.isEmpty()) return null
        val health = sharedState.geoAwarenessHealth.value ?: datasetController.ensureHealth(records)
        return Snapshot(
            datasetInfo = info,
            zones = zones,
            records = records,
            validation = validation,
            health = health,
            importedActive = sharedState.geoZoneImportedActive.value == true,
        )
    }

    private fun GeoAwarenessDatasetController.LoadOutcome.toSnapshot(): Snapshot {
        return Snapshot(
            datasetInfo = result.datasetInfo,
            zones = result.zones,
            records = result.datasetRecords,
            validation = result.validationResult,
            health = requireNotNull(snapshot.health),
            importedActive = snapshot.importedActive,
        )
    }

    private fun emptyFailureSnapshot(error: Throwable): Snapshot {
        val failed = datasetController.recordEmptyFailure(error)
        return Snapshot(health = requireNotNull(failed.health), loadError = error)
    }

    private fun logNewStaleDatasets(records: List<GeoZoneDatasetRecord>, health: GeoAwarenessHealth) {
        val staleRecords = records.filter { it.isStale }
        val signature = staleRecords.joinToString("|") { "${it.datasetId}:${it.updatedAtMillis}" }
        if (signature.isBlank()) {
            lastStaleSignature = null
            return
        }
        if (signature == lastStaleSignature) return
        lastStaleSignature = signature
        staleRecords.forEach { record ->
            eventLogger.logSimple(
                type = GeoAwarenessEventType.DATASET_MARKED_STALE,
                severity = "WARNING",
                message = "Geo-zone dataset marked stale",
                datasetTitle = record.displayName,
                datasetVersion = record.datasetInfo.version,
                healthState = health.state.name,
                details = mapOf(
                    "datasetId" to record.datasetId,
                    "storageFileName" to (record.storageFileName ?: ""),
                    "ageDescription" to (record.ageDescription ?: "Update time unknown"),
                    "stale" to record.isStale.toString(),
                ),
            )
        }
        callbacks.onAuditLogChanged()
    }

    private fun isCurrent(requestGeneration: Long) = active && generation == requestGeneration

    private companion object {
        val loadMutex = Mutex()
    }
}
