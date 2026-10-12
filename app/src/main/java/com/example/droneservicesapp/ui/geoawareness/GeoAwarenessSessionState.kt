package com.example.droneservicesapp.ui.geoawareness

import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.testing.GeoAwarenessTestRunResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult

/** Non-UI workflow snapshots owned by the configuration-safe Geo-awareness ViewModel. */
class GeoAwarenessSessionState {
    enum class PickerMode { IMPORT_NEW, UPDATE_EXISTING }

    data class DatasetPresentation(
        val datasetInfo: GeoZoneDatasetInfo? = null,
        val zones: List<GeoZone> = emptyList(),
        val records: List<GeoZoneDatasetRecord> = emptyList(),
        val validation: GeoZoneValidationResult? = null,
        val health: GeoAwarenessHealth? = null,
        val importedActive: Boolean = false,
        val loadError: Throwable? = null,
    )

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
}
