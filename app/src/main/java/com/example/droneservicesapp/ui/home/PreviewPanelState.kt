package com.example.droneservicesapp.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.droneservicesapp.data.pointcloud.PointCloudData
import com.example.droneservicesapp.ui.pointcloud.PointCloudMissionOverlay
import com.example.droneservicesapp.ui.preview.PreviewMode

/** Map/Ortho/3D preview selection and the inputs of the embedded point-cloud renderer. */
class PreviewPanelState {
    var mode by mutableStateOf(PreviewMode.MAP)
    var surveyMode by mutableStateOf(true)
    var terrainStatus by mutableStateOf<String?>(null)

    var pointCloud by mutableStateOf<PointCloudData?>(null)
    var pointSize by mutableStateOf(2.5f)
    var pointOpacity by mutableStateOf(0.85f)
    var heightColors by mutableStateOf(true)
    var missionOverlay by mutableStateOf<PointCloudMissionOverlay?>(null)
    var cameraResetToken by mutableStateOf(0)

    val showsPointCloud: Boolean
        get() = mode == PreviewMode.POINT_CLOUD
}
