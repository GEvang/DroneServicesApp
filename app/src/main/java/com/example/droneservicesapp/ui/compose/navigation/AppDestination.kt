package com.example.droneservicesapp.ui.compose.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.example.droneservicesapp.R
import com.example.droneservicesapp.ui.home.MissionMapFragment

sealed class AppDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    @IdRes val legacyContainerId: Int,
    val showInDrawer: Boolean = true,
    val drawerGesturesEnabled: Boolean = showInDrawer,
    val createFragment: (() -> Fragment)? = null,
) {
    data object Home : AppDestination(
        route = "home",
        labelRes = R.string.menu_maps_home,
        iconRes = R.drawable.ic_menu_map,
        legacyContainerId = R.id.legacy_host_home,
        drawerGesturesEnabled = false,
        createFragment = ::MissionMapFragment,
    )

    data object Settings : AppDestination(
        route = "settings",
        labelRes = R.string.menu_settings,
        iconRes = R.drawable.ic_menu_settings,
        legacyContainerId = R.id.legacy_host_settings,
    )

    data object MavlinkNetwork : AppDestination(
        route = "mavlink-network",
        labelRes = R.string.menu_mavlink_network,
        iconRes = R.drawable.ic_menu_mavlink_network,
        legacyContainerId = R.id.legacy_host_mavlink,
    )

    data object Datasets : AppDestination(
        route = "datasets",
        labelRes = R.string.menu_datasets,
        iconRes = R.drawable.ic_folder_24,
        legacyContainerId = R.id.legacy_host_datasets,
    )

    data object Rtk : AppDestination(
        route = "rtk",
        labelRes = R.string.menu_rtk,
        iconRes = R.drawable.ic_status_rtk_24,
        legacyContainerId = R.id.legacy_host_rtk,
    )

    data object GeoAwareness : AppDestination(
        route = "geo-awareness",
        labelRes = R.string.menu_geo_awareness,
        iconRes = R.drawable.ic_status_warning_24,
        legacyContainerId = R.id.legacy_host_geo_awareness,
    )

    data object Parameters : AppDestination(
        route = "parameters",
        labelRes = R.string.menu_parameters,
        iconRes = R.drawable.ic_menu_tune,
        legacyContainerId = R.id.legacy_host_parameters,
    )

    data object PointCloud : AppDestination(
        route = "point-cloud",
        labelRes = R.string.menu_point_cloud,
        iconRes = R.drawable.ic_menu_map,
        legacyContainerId = R.id.legacy_host_point_cloud,
        showInDrawer = false,
    )

    data object OrthoPreview : AppDestination(
        route = "ortho-preview",
        labelRes = R.string.menu_ortho_preview,
        iconRes = R.drawable.ic_menu_map,
        legacyContainerId = R.id.legacy_host_ortho_preview,
        showInDrawer = false,
    )

    data object Debug : AppDestination(
        route = "debug",
        labelRes = R.string.menu_debug,
        iconRes = R.drawable.ic_menu_bug,
        legacyContainerId = R.id.legacy_host_debug,
    )

    companion object {
        val all: List<AppDestination>
            get() = listOf(
                Home,
                Settings,
                MavlinkNetwork,
                Datasets,
                Rtk,
                GeoAwareness,
                Parameters,
                PointCloud,
                OrthoPreview,
                Debug,
            )

        val drawerDestinations: List<AppDestination>
            get() = all.filter { it.showInDrawer }

        fun fromRoute(route: String?): AppDestination =
            all.firstOrNull { it.route == route } ?: Home
    }
}
