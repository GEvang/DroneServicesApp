package com.example.droneservicesapp.ui.geoawareness

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

/** Normalizes shared geo-awareness observations into presentation callbacks. */
class GeoAwarenessObserverCoordinator {
    private val clearActions = mutableListOf<() -> Unit>()

    data class Callbacks(
        val onOverlayVisibility: (Boolean) -> Unit,
        val onDatasetInfo: (GeoZoneDatasetInfo) -> Unit,
        val onValidation: (GeoZoneValidationResult?) -> Unit,
        val onRecords: (List<GeoZoneDatasetRecord>) -> Unit,
        val onImportedActive: (Boolean) -> Unit,
        val onHealth: (GeoAwarenessHealth?) -> Unit,
        val onReloadToken: (Long?) -> Unit,
    )

    fun observe(state: MainActivityViewModel, callbacks: Callbacks) {
        clear()
        observeForever(state.geoAwarenessLayerVisible) { callbacks.onOverlayVisibility(it ?: true) }
        observeForever(state.geoZoneDatasetInfo) { info -> info?.let(callbacks.onDatasetInfo) }
        observeForever(state.geoZoneValidationResult, callbacks.onValidation)
        observeForever(state.geoZoneDatasetRecords) { callbacks.onRecords(it.orEmpty()) }
        observeForever(state.geoZoneImportedActive) { callbacks.onImportedActive(it == true) }
        observeForever(state.geoAwarenessHealth, callbacks.onHealth)
        observeForever(state.geoZoneReloadToken, callbacks.onReloadToken)
    }

    fun clear() {
        clearActions.forEach { it() }
        clearActions.clear()
    }

    private fun <T> observeForever(source: LiveData<T>, onChanged: (T) -> Unit) {
        val observer = Observer<T>(onChanged)
        source.observeForever(observer)
        clearActions += { source.removeObserver(observer) }
    }
}
