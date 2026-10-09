package com.example.droneservicesapp.ui.geoawareness

import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

/** Normalizes shared geo-awareness observations into presentation callbacks. */
class GeoAwarenessObserverCoordinator {
    data class Callbacks(
        val onOverlayVisibility: (Boolean) -> Unit,
        val onDatasetInfo: (GeoZoneDatasetInfo) -> Unit,
        val onValidation: (GeoZoneValidationResult?) -> Unit,
        val onRecords: (List<GeoZoneDatasetRecord>) -> Unit,
        val onImportedActive: (Boolean) -> Unit,
        val onHealth: (GeoAwarenessHealth?) -> Unit,
        val onReloadToken: (Long?) -> Unit,
    )

    fun observe(owner: LifecycleOwner, state: MainActivityViewModel, callbacks: Callbacks) {
        state.geoAwarenessLayerVisible.observe(owner) { callbacks.onOverlayVisibility(it ?: true) }
        state.geoZoneDatasetInfo.observe(owner) { info -> info?.let(callbacks.onDatasetInfo) }
        state.geoZoneValidationResult.observe(owner) { callbacks.onValidation(it) }
        state.geoZoneDatasetRecords.observe(owner) { callbacks.onRecords(it.orEmpty()) }
        state.geoZoneImportedActive.observe(owner) { callbacks.onImportedActive(it == true) }
        state.geoAwarenessHealth.observe(owner) { callbacks.onHealth(it) }
        state.geoZoneReloadToken.observe(owner) { callbacks.onReloadToken(it) }
    }
}
