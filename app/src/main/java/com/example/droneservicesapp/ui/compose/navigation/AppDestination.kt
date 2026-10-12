package com.example.droneservicesapp.ui.compose.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.droneservicesapp.R

sealed class AppDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val showInDrawer: Boolean = true,
    val drawerGesturesEnabled: Boolean = showInDrawer,
) {
    data object Home : AppDestination(
        route = "home",
        labelRes = R.string.menu_maps_home,
        iconRes = R.drawable.ic_menu_map,
        drawerGesturesEnabled = false,
    )

    data object Settings : AppDestination(
        route = "settings",
        labelRes = R.string.menu_settings,
        iconRes = R.drawable.ic_menu_settings,
    )

    data object MavlinkNetwork : AppDestination(
        route = "mavlink-network",
        labelRes = R.string.menu_mavlink_network,
        iconRes = R.drawable.ic_menu_mavlink_network,
    )

    data object Datasets : AppDestination(
        route = "datasets",
        labelRes = R.string.menu_datasets,
        iconRes = R.drawable.ic_folder_24,
    )

    data object Rtk : AppDestination(
        route = "rtk",
        labelRes = R.string.menu_rtk,
        iconRes = R.drawable.ic_status_rtk_24,
    )

    data object GeoAwareness : AppDestination(
        route = "geo-awareness",
        labelRes = R.string.menu_geo_awareness,
        iconRes = R.drawable.ic_status_warning_24,
    )

    data object Parameters : AppDestination(
        route = "parameters",
        labelRes = R.string.menu_parameters,
        iconRes = R.drawable.ic_menu_tune,
    )

    data object PointCloud : AppDestination(
        route = "point-cloud",
        labelRes = R.string.menu_point_cloud,
        iconRes = R.drawable.ic_menu_map,
        showInDrawer = false,
    )

    data object OrthoPreview : AppDestination(
        route = "ortho-preview",
        labelRes = R.string.menu_ortho_preview,
        iconRes = R.drawable.ic_menu_map,
        showInDrawer = false,
    )

    data object Debug : AppDestination(
        route = "debug",
        labelRes = R.string.menu_debug,
        iconRes = R.drawable.ic_menu_bug,
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
