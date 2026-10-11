package com.example.droneservicesapp.ui.compose.screens.datasets

import com.example.droneservicesapp.data.pointcloud.PointCloudDetailLevel
import com.example.droneservicesapp.data.preview.PreviewDatasetRecord

data class DatasetsUiState(
    val datasets: List<PreviewDatasetRecord> = emptyList(),
    val activeDatasetId: String? = null,
    val loading: Boolean = false,
    val loadingMessage: String = "",
    val loadingPercent: Int? = null,
    val dialog: DatasetDialog? = null,
    val renameValue: String = "",
    val renameError: Boolean = false,
) {
    val activeDataset: PreviewDatasetRecord?
        get() = datasets.firstOrNull { it.id == activeDatasetId }
}

sealed interface DatasetDialog {
    data object Rename : DatasetDialog
    data object DeleteActive : DatasetDialog
    data object DeleteAll : DatasetDialog
}

sealed interface DatasetEffect {
    data class Message(val value: String) : DatasetEffect
}

internal val datasetDetailLevels = listOf(
    PointCloudDetailLevel.FAST,
    PointCloudDetailLevel.BALANCED,
    PointCloudDetailLevel.HIGH,
)

internal fun isSupportedOrthoImage(name: String): Boolean =
    name.endsWith(".tif", ignoreCase = true) || name.endsWith(".tiff", ignoreCase = true)

internal fun isSupportedWorldFile(name: String): Boolean =
    name.endsWith(".tfw", ignoreCase = true) || name.endsWith(".wld", ignoreCase = true)

internal fun isSupportedPointCloud(name: String): Boolean =
    listOf(".ply", ".pcd", ".csv", ".txt", ".xyz").any { name.endsWith(it, ignoreCase = true) }
