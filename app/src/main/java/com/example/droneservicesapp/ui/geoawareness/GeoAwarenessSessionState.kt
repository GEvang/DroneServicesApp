package com.example.droneservicesapp.ui.geoawareness

import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.testing.GeoAwarenessTestRunResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult

/** Temporary state for one Geo Awareness fragment instance; no persistent app state belongs here. */
class GeoAwarenessSessionState {
    enum class PickerMode { IMPORT_NEW, UPDATE_EXISTING }

    data class PendingPickerOperation(
        val mode: PickerMode,
        val updateStorageFileName: String? = null,
    )

    data class DatasetPresentation(
        val datasetInfo: GeoZoneDatasetInfo? = null,
        val zones: List<GeoZone> = emptyList(),
        val records: List<GeoZoneDatasetRecord> = emptyList(),
        val validation: GeoZoneValidationResult? = null,
        val health: GeoAwarenessHealth? = null,
        val importedActive: Boolean = false,
        val loadError: Throwable? = null,
    )

    var pendingPickerOperation: PendingPickerOperation? = null
    var lastProcessedReloadToken: Long? = null
    var dataset: DatasetPresentation = DatasetPresentation()
    var telemetry: GeoAwarenessTelemetryObserver.Snapshot? = null
    var lastAutomatedTestResult: GeoAwarenessTestRunResult? = null

    fun apply(snapshot: GeoAwarenessDatasetStateCoordinator.Snapshot) {
        dataset = DatasetPresentation(
            datasetInfo = snapshot.datasetInfo,
            zones = snapshot.zones,
            records = snapshot.records,
            validation = snapshot.validation,
            health = snapshot.health,
            importedActive = snapshot.importedActive,
            loadError = snapshot.loadError,
        )
    }

    fun clearViewState() {
        pendingPickerOperation = null
        telemetry = null
    }
}
