package com.example.droneservicesapp.ui.geoawareness

import android.content.Intent
import android.net.Uri
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEvent
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import com.example.droneservicesapp.domain.geoawareness.verification.GeoAwarenessVerificationCase

data class GeoAwarenessUiState(
    val overlayVisible: Boolean = true,
    val importedDatasetActive: Boolean = false,
    val datasetInfo: GeoZoneDatasetInfo? = null,
    val datasetRecords: List<GeoZoneDatasetRecord> = emptyList(),
    val validation: GeoZoneValidationResult = GeoZoneValidationResult.ok(),
    val health: GeoAwarenessHealth? = null,
    val liveStatus: GeoAwarenessLiveStatusController.State? = null,
    val events: List<GeoAwarenessEvent> = emptyList(),
    val verification: GeoAwarenessVerificationController.State? = null,
    val busy: Boolean = false,
    val busyMessage: String? = null,
    val errorMessage: String? = null,
    val dialog: GeoAwarenessDialog? = null,
    val pendingPicker: GeoAwarenessPickerRequest? = null,
)

sealed interface GeoAwarenessDialog {
    data class Message(val title: String, val message: String) : GeoAwarenessDialog
    data object ConfirmRemoveAll : GeoAwarenessDialog
    data class ConfirmRemove(val record: GeoZoneDatasetRecord) : GeoAwarenessDialog
    data object Verification : GeoAwarenessDialog
    data class VerificationCaseDetails(val case: GeoAwarenessVerificationCase) : GeoAwarenessDialog
    data object ConfirmVerificationReset : GeoAwarenessDialog
}

data class GeoAwarenessPickerRequest(
    val mode: GeoAwarenessSessionState.PickerMode,
    val updateStorageFileName: String? = null,
)

data class GeoAwarenessDatasetSelection(
    val mode: GeoAwarenessSessionState.PickerMode,
    val uri: Uri,
    val originalFileName: String?,
    val updateStorageFileName: String? = null,
)

sealed interface GeoAwarenessAction {
    data class SetOverlayVisibility(val visible: Boolean) : GeoAwarenessAction
    data object ImportDataset : GeoAwarenessAction
    data class UpdateDataset(val record: GeoZoneDatasetRecord) : GeoAwarenessAction
    data object RemoveAllDatasets : GeoAwarenessAction
    data class RemoveDataset(val record: GeoZoneDatasetRecord) : GeoAwarenessAction
    data object RefreshStatus : GeoAwarenessAction
    data object ShowValidation : GeoAwarenessAction
    data class ShowDatasetValidation(val record: GeoZoneDatasetRecord) : GeoAwarenessAction
    data object ShowLiveStatus : GeoAwarenessAction
    data object ShowDetailedLogs : GeoAwarenessAction
    data object ExportLogs : GeoAwarenessAction
    data object ExportEvidence : GeoAwarenessAction
    data object ExportEncryptedIncidents : GeoAwarenessAction
    data object ShowVerification : GeoAwarenessAction
    data object RunVerificationTests : GeoAwarenessAction
    data object ResetVerification : GeoAwarenessAction
    data object DismissDialog : GeoAwarenessAction
}

sealed interface GeoAwarenessEffect {
    data class LaunchFilePicker(val mimeTypes: Array<String>) : GeoAwarenessEffect
    data class LaunchIntent(val intent: Intent) : GeoAwarenessEffect
    data class Message(val message: String) : GeoAwarenessEffect
}
