package com.example.droneservicesapp.ui.geoawareness

import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEvent
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import com.example.droneservicesapp.ui.architecture.CommonUiEffect
import com.example.droneservicesapp.ui.architecture.UiReducer
import com.example.droneservicesapp.ui.architecture.UiReduction
import com.example.droneservicesapp.ui.architecture.UiStateStore

data class GeoAwarenessUiState(
    val overlayVisible: Boolean = true,
    val importedDatasetActive: Boolean = false,
    val datasetInfo: GeoZoneDatasetInfo? = null,
    val datasetRecords: List<GeoZoneDatasetRecord> = emptyList(),
    val validation: GeoZoneValidationResult = GeoZoneValidationResult.ok(),
    val health: GeoAwarenessHealth? = null,
    val events: List<GeoAwarenessEvent> = emptyList(),
    val busy: Boolean = false,
    val busyMessage: String? = null,
    val errorMessage: String? = null,
)

sealed interface GeoAwarenessAction {
    data class SetOverlayVisibility(val visible: Boolean) : GeoAwarenessAction
    data class OverlayVisibilityObserved(val visible: Boolean) : GeoAwarenessAction
    data object ImportDataset : GeoAwarenessAction
    data object RemoveAllDatasets : GeoAwarenessAction
    data object RefreshStatus : GeoAwarenessAction
    data object ShowValidation : GeoAwarenessAction
    data object ExportLogs : GeoAwarenessAction
    data object ShowDetailedLogs : GeoAwarenessAction
    data object ExportEvidence : GeoAwarenessAction
    data object ExportEncryptedIncidents : GeoAwarenessAction
    data object ShowVerification : GeoAwarenessAction

    data class DatasetChanged(
        val importedActive: Boolean,
        val info: GeoZoneDatasetInfo?,
        val records: List<GeoZoneDatasetRecord>,
        val validation: GeoZoneValidationResult,
        val health: GeoAwarenessHealth?,
        val errorMessage: String? = null,
    ) : GeoAwarenessAction

    data class EventsChanged(val events: List<GeoAwarenessEvent>) : GeoAwarenessAction
    data class BusyChanged(val busy: Boolean, val message: String? = null) : GeoAwarenessAction
}

sealed interface GeoAwarenessEffect {
    data class Platform(val effect: CommonUiEffect) : GeoAwarenessEffect
    data object PersistOverlayVisibility : GeoAwarenessEffect
    data object RefreshDatasetStatus : GeoAwarenessEffect
    data object OpenValidationDetails : GeoAwarenessEffect
    data object ExportStandardLogs : GeoAwarenessEffect
    data object OpenDetailedLogs : GeoAwarenessEffect
    data object ExportEvidencePackage : GeoAwarenessEffect
    data object ExportEncryptedIncidentLogs : GeoAwarenessEffect
    data object OpenVerification : GeoAwarenessEffect
}

object GeoAwarenessReducer : UiReducer<GeoAwarenessUiState, GeoAwarenessAction, GeoAwarenessEffect> {
    override fun reduce(
        state: GeoAwarenessUiState,
        action: GeoAwarenessAction,
    ): UiReduction<GeoAwarenessUiState, GeoAwarenessEffect> = when (action) {
        is GeoAwarenessAction.SetOverlayVisibility -> UiReduction(
            state.copy(overlayVisible = action.visible),
            listOf(GeoAwarenessEffect.PersistOverlayVisibility),
        )
        is GeoAwarenessAction.OverlayVisibilityObserved -> UiReduction(
            state.copy(overlayVisible = action.visible)
        )
        GeoAwarenessAction.ImportDataset -> UiReduction(
            state,
            listOf(
                GeoAwarenessEffect.Platform(
                    CommonUiEffect.LaunchFilePicker(
                        requestKey = "geo-awareness-import",
                        mimeTypes = setOf("application/json", "application/geo+json", "text/plain"),
                    )
                )
            ),
        )
        GeoAwarenessAction.RemoveAllDatasets -> UiReduction(
            state,
            listOf(
                GeoAwarenessEffect.Platform(
                    CommonUiEffect.ShowConfirmation(
                        requestKey = "geo-awareness-remove-all",
                        title = "Remove all datasets?",
                        message = "This removes every imported geo-awareness dataset.",
                        destructive = true,
                    )
                )
            ),
        )
        GeoAwarenessAction.RefreshStatus -> UiReduction(state, listOf(GeoAwarenessEffect.RefreshDatasetStatus))
        GeoAwarenessAction.ShowValidation -> UiReduction(state, listOf(GeoAwarenessEffect.OpenValidationDetails))
        GeoAwarenessAction.ExportLogs -> UiReduction(state, listOf(GeoAwarenessEffect.ExportStandardLogs))
        GeoAwarenessAction.ShowDetailedLogs -> UiReduction(state, listOf(GeoAwarenessEffect.OpenDetailedLogs))
        GeoAwarenessAction.ExportEvidence -> UiReduction(state, listOf(GeoAwarenessEffect.ExportEvidencePackage))
        GeoAwarenessAction.ExportEncryptedIncidents -> UiReduction(
            state,
            listOf(GeoAwarenessEffect.ExportEncryptedIncidentLogs),
        )
        GeoAwarenessAction.ShowVerification -> UiReduction(state, listOf(GeoAwarenessEffect.OpenVerification))
        is GeoAwarenessAction.DatasetChanged -> UiReduction(
            state.copy(
                importedDatasetActive = action.importedActive,
                datasetInfo = action.info,
                datasetRecords = action.records.toList(),
                validation = action.validation,
                health = action.health,
                errorMessage = action.errorMessage,
            )
        )
        is GeoAwarenessAction.EventsChanged -> UiReduction(state.copy(events = action.events.toList()))
        is GeoAwarenessAction.BusyChanged -> UiReduction(
            state.copy(busy = action.busy, busyMessage = action.message.takeIf { action.busy })
        )
    }
}

class GeoAwarenessUiStateHolder(
    initialState: GeoAwarenessUiState = GeoAwarenessUiState(),
) {
    private val store = UiStateStore(initialState, GeoAwarenessReducer)
    val state = store.state
    val effects = store.effects
    fun dispatch(action: GeoAwarenessAction) = store.dispatch(action)
}
