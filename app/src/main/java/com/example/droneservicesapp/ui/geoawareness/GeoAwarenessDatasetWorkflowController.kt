package com.example.droneservicesapp.ui.geoawareness

import android.content.Context
import android.net.Uri
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.geoawareness.GeoZoneDatasetValidationException
import com.example.droneservicesapp.data.geoawareness.GeoZoneImportedFileDataSource
import com.example.droneservicesapp.data.geoawareness.GeoZoneRepository
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetSourceType
import com.example.droneservicesapp.domain.geoawareness.GeoZoneLoadResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Coordinates import, update, and removal without retaining fragment views. */
class GeoAwarenessDatasetWorkflowController(
    context: Context,
    private val scope: CoroutineScope,
    private val stateCoordinator: GeoAwarenessDatasetStateCoordinator,
    private val dialogs: GeoAwarenessDatasetDialogController,
    private val logger: GeoAwarenessEventLogger,
    private val auditFormatter: GeoAwarenessDatasetAuditFormatter,
    private val currentRecords: () -> List<GeoZoneDatasetRecord>,
    private val currentHealth: () -> GeoAwarenessHealth?,
    private val callbacks: Callbacks,
) {
    data class Callbacks(
        val onBusyChanged: (Boolean, String?) -> Unit,
        val onAuditLogChanged: () -> Unit,
        val onOperationResult: (OperationResult) -> Unit = {},
    )

    enum class Operation { IMPORT, UPDATE, REMOVE_ONE, REMOVE_ALL }
    enum class FailureKind { VALIDATION, FILE, REPOSITORY }

    data class Failure(
        val kind: FailureKind,
        val error: Throwable,
        val validation: GeoZoneValidationResult? = null,
    )

    sealed interface OperationResult {
        val operation: Operation

        data class Success(
            override val operation: Operation,
            val loadResult: GeoZoneLoadResult,
        ) : OperationResult

        data class Failed(
            override val operation: Operation,
            val failure: Failure,
        ) : OperationResult
    }

    private val appContext = context.applicationContext
    private var mutationJob: Job? = null
    private var active = true

    val isBusy: Boolean
        get() = mutationJob?.isActive == true

    fun repositoryForEvidenceExport(): GeoZoneRepository = repository()

    fun handleSelection(selection: GeoAwarenessDatasetPickerController.Selection) {
        when (selection.mode) {
            GeoAwarenessDatasetPickerController.Mode.IMPORT_NEW -> importDataset(selection)
            GeoAwarenessDatasetPickerController.Mode.UPDATE_EXISTING -> updateDataset(selection)
        }
    }

    fun confirmRemoveAll() = dialogs.confirmRemoveAll(::removeAll)

    fun confirmRemove(record: GeoZoneDatasetRecord) = dialogs.confirmRemove(record) { remove(record) }

    fun clear() {
        active = false
        mutationJob?.cancel()
        mutationJob = null
    }

    private fun importDataset(selection: GeoAwarenessDatasetPickerController.Selection) {
        if (!canMutate()) return
        logger.logSimple(
            type = GeoAwarenessEventType.DATASET_IMPORT_STARTED,
            severity = "INFO",
            message = "Geo-zone dataset import started",
            details = buildMap {
                put("uri", selection.uri.toString())
                selection.originalFileName?.let { put("originalFileName", it) }
            },
        )
        launchMutation("Importing geo-zone dataset...") {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository().importDataset(readUtf8(selection.uri), selection.originalFileName)
                }
                if (!active) return@launchMutation
                stateCoordinator.acceptMutationResult(result, importedActive = true)
                logger.logSimple(
                    type = GeoAwarenessEventType.DATASET_IMPORT_SUCCEEDED,
                    severity = "INFO",
                    message = "Geo-zone dataset import succeeded",
                    datasetTitle = result.datasetInfo.title,
                    datasetVersion = result.datasetInfo.version,
                    healthState = currentHealth()?.state?.name,
                    details = auditFormatter.standardDetails(
                        operation = "manual_import",
                        loadResult = result,
                        requestedUri = selection.uri.toString(),
                        originalFileName = selection.originalFileName,
                    ) + mapOf("addedDatasetTitle" to result.datasetRecords.lastOrNull()?.displayName.orEmpty()),
                )
                callbacks.onAuditLogChanged()
                dialogs.showImportSuccess(result)
                callbacks.onOperationResult(OperationResult.Success(Operation.IMPORT, result))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (active) handleImportFailure(classify(error))
            }
        }
    }

    private fun updateDataset(selection: GeoAwarenessDatasetPickerController.Selection) {
        val storageFileName = selection.updateStorageFileName ?: return
        if (!canMutate()) return
        logger.logSimple(
            type = GeoAwarenessEventType.DATASET_UPDATE_STARTED,
            severity = "INFO",
            message = "Geo-zone dataset update started",
            details = buildMap {
                put("uri", selection.uri.toString())
                put("storageFileName", storageFileName)
                selection.originalFileName?.let { put("originalFileName", it) }
            },
        )
        launchMutation("Updating geo-zone dataset...") {
            try {
                val (result, importedActive) = withContext(Dispatchers.IO) {
                    val repository = repository()
                    repository.updateImportedDataset(storageFileName, readUtf8(selection.uri), selection.originalFileName) to
                        repository.hasImportedDatasets()
                }
                if (!active) return@launchMutation
                stateCoordinator.acceptMutationResult(result, importedActive)
                logger.logSimple(
                    type = GeoAwarenessEventType.DATASET_UPDATE_SUCCEEDED,
                    severity = "INFO",
                    message = "Geo-zone dataset update succeeded",
                    datasetTitle = result.datasetInfo.title,
                    datasetVersion = result.datasetInfo.version,
                    healthState = currentHealth()?.state?.name,
                    details = auditFormatter.standardDetails(
                        operation = "manual_update",
                        loadResult = result,
                        requestedUri = selection.uri.toString(),
                        originalFileName = selection.originalFileName,
                        storageFileName = storageFileName,
                    ) + mapOf("storageFileName" to storageFileName),
                )
                callbacks.onAuditLogChanged()
                dialogs.showUpdateSuccess()
                callbacks.onOperationResult(OperationResult.Success(Operation.UPDATE, result))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (active) handleUpdateFailure(storageFileName, classify(error))
            }
        }
    }

    private fun removeAll() {
        if (!canMutate()) return
        val removedCount = currentRecords().count { it.sourceType == GeoZoneDatasetSourceType.IMPORTED_FILE }
        launchMutation(appContext.getString(R.string.geo_loading_dataset)) {
            try {
                val result = withContext(Dispatchers.IO) { repository().removeAllImportedDatasets() }
                if (!active) return@launchMutation
                stateCoordinator.acceptMutationResult(result, importedActive = false)
                if (removedCount > 0) {
                    logger.logSimple(
                        type = GeoAwarenessEventType.ALL_IMPORTED_DATASETS_REMOVED,
                        severity = "INFO",
                        message = "All imported geo-zone datasets removed",
                        details = mapOf("removedCount" to removedCount.toString()),
                    )
                }
                callbacks.onAuditLogChanged()
                dialogs.showRemoveAllSuccess()
                callbacks.onOperationResult(OperationResult.Success(Operation.REMOVE_ALL, result))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (active) {
                    val failure = classify(error)
                    dialogs.showRemoveFailure(removeAll = true, error)
                    callbacks.onOperationResult(OperationResult.Failed(Operation.REMOVE_ALL, failure))
                }
            }
        }
    }

    private fun remove(record: GeoZoneDatasetRecord) {
        val storageFileName = record.storageFileName ?: return
        if (!canMutate()) return
        launchMutation(appContext.getString(R.string.geo_loading_dataset)) {
            try {
                val (result, importedActive) = withContext(Dispatchers.IO) {
                    val repository = repository()
                    repository.removeImportedDataset(storageFileName) to repository.hasImportedDatasets()
                }
                if (!active) return@launchMutation
                stateCoordinator.acceptMutationResult(result, importedActive)
                logger.logSimple(
                    type = GeoAwarenessEventType.DATASET_REMOVED,
                    severity = "INFO",
                    message = "Imported geo-zone dataset removed",
                    datasetTitle = record.displayName,
                    datasetVersion = record.datasetInfo.version,
                    healthState = currentHealth()?.state?.name,
                    details = mapOf(
                        "datasetTitle" to record.displayName,
                        "storageFileName" to storageFileName,
                        "activeDatasetCount" to result.datasetRecords.size.toString(),
                        "totalZones" to result.datasetInfo.zoneCount.toString(),
                    ),
                )
                callbacks.onAuditLogChanged()
                callbacks.onOperationResult(OperationResult.Success(Operation.REMOVE_ONE, result))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (active) {
                    val failure = classify(error)
                    dialogs.showRemoveFailure(removeAll = false, error)
                    callbacks.onOperationResult(OperationResult.Failed(Operation.REMOVE_ONE, failure))
                }
            }
        }
    }

    private fun handleImportFailure(failure: Failure) {
        logFailure(GeoAwarenessEventType.DATASET_IMPORT_FAILED, "import", null, failure)
        dialogs.showImportFailure(failure.error, failure.validation)
        callbacks.onOperationResult(OperationResult.Failed(Operation.IMPORT, failure))
    }

    private fun handleUpdateFailure(storageFileName: String, failure: Failure) {
        logFailure(GeoAwarenessEventType.DATASET_UPDATE_FAILED, "update", storageFileName, failure)
        dialogs.showUpdateFailure(failure.error, failure.validation)
        callbacks.onOperationResult(OperationResult.Failed(Operation.UPDATE, failure))
    }

    private fun logFailure(type: GeoAwarenessEventType, operation: String, storageFileName: String?, failure: Failure) {
        logger.logSimple(
            type = type,
            severity = "ERROR",
            message = "Geo-zone dataset $operation failed: ${failure.error.message ?: failure.error::class.java.simpleName}",
            healthState = currentHealth()?.state?.name,
            details = buildMap {
                storageFileName?.let { put("storageFileName", it) }
                put("failureKind", failure.kind.name)
                put("errorMessage", failure.error.message ?: failure.error::class.java.simpleName)
                failure.validation?.let {
                    put("errorCount", it.errorCount.toString())
                    put("warningCount", it.warningCount.toString())
                    put("infoCount", it.infoCount.toString())
                }
            },
        )
        callbacks.onAuditLogChanged()
    }

    private fun classify(error: Exception): Failure = when (error) {
        is GeoZoneDatasetValidationException -> Failure(FailureKind.VALIDATION, error, error.validationResult)
        is IllegalStateException, is java.io.IOException, is SecurityException -> Failure(FailureKind.FILE, error)
        else -> Failure(FailureKind.REPOSITORY, error)
    }

    private fun launchMutation(message: String, operation: suspend () -> Unit) {
        callbacks.onBusyChanged(true, message)
        mutationJob = scope.launch {
            try {
                operation()
            } finally {
                mutationJob = null
                if (active) callbacks.onBusyChanged(false, null)
            }
        }
    }

    private fun canMutate() = active && !isBusy && !stateCoordinator.isLoading

    private fun repository() = GeoZoneRepository(GeoZoneImportedFileDataSource(appContext))

    private fun readUtf8(uri: Uri): String {
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                if (output.size().toLong() > MAX_IMPORT_BYTES) {
                    throw IllegalStateException(appContext.getString(R.string.geo_import_size_limit))
                }
            }
            return output.toString(Charsets.UTF_8.name()).takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Selected geo-zone file is empty.")
        }
        throw IllegalStateException("Failed to open selected geo-zone file.")
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 5L * 1024L * 1024L
    }
}
