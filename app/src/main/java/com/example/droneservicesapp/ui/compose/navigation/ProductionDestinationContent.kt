package com.example.droneservicesapp.ui.compose.navigation

import androidx.compose.runtime.Composable
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.compose.screens.debug.DebugScreen
import com.example.droneservicesapp.ui.compose.screens.datasets.DatasetsScreen
import com.example.droneservicesapp.ui.compose.screens.geoawareness.GeoAwarenessScreen
import com.example.droneservicesapp.ui.compose.screens.mavlink.MavlinkNetworkScreen
import com.example.droneservicesapp.ui.compose.screens.parameters.ParametersScreen
import com.example.droneservicesapp.ui.compose.screens.ortho.OrthoPreviewScreen
import com.example.droneservicesapp.ui.compose.screens.pointcloud.PointCloudViewerScreen
import com.example.droneservicesapp.ui.compose.screens.rtk.RtkScreen
import com.example.droneservicesapp.ui.compose.screens.settings.SettingsScreen
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel

/** Routes migrated destinations directly to Compose and keeps the bridge only for legacy screens. */
@Composable
fun ProductionDestinationContent(
    destination: AppDestination,
    droneViewModel: DroneViewModel,
    activityViewModel: MainActivityViewModel,
    previewAssetsViewModel: PreviewAssetsViewModel,
    navigator: ShellNavigator,
) {
    when (destination) {
        AppDestination.Settings -> SettingsScreen(activityViewModel)
        AppDestination.MavlinkNetwork -> MavlinkNetworkScreen()
        AppDestination.Rtk -> RtkScreen(droneViewModel)
        AppDestination.Parameters -> ParametersScreen(droneViewModel)
        AppDestination.Debug -> DebugScreen(droneViewModel)
        AppDestination.Datasets -> DatasetsScreen(previewAssetsViewModel)
        AppDestination.GeoAwareness -> GeoAwarenessScreen(activityViewModel, droneViewModel)
        AppDestination.OrthoPreview -> OrthoPreviewScreen(
            activityViewModel = activityViewModel,
            droneViewModel = droneViewModel,
            previewAssetsViewModel = previewAssetsViewModel,
            onShowPointCloud = { navigator.navigateTo(AppDestination.PointCloud) },
        )
        AppDestination.PointCloud -> PointCloudViewerScreen(
            activityViewModel = activityViewModel,
            droneViewModel = droneViewModel,
            previewAssetsViewModel = previewAssetsViewModel,
            onReturnToMap = navigator::returnToHome,
        )
        else -> LegacyFragmentHost(destination)
    }
}
