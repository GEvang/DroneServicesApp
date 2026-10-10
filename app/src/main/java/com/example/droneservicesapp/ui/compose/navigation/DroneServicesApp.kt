package com.example.droneservicesapp.ui.compose.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.droneservicesapp.R
import com.example.droneservicesapp.ui.compose.components.AppButton
import com.example.droneservicesapp.ui.compose.components.AppButtonStyle
import com.example.droneservicesapp.ui.compose.components.NavigationItem
import com.example.droneservicesapp.ui.compose.theme.DroneServicesSizes
import com.example.droneservicesapp.ui.compose.theme.DroneServicesThemeTokens
import com.example.droneservicesapp.ui.home.model.HomeTelemetryUiState
import com.example.droneservicesapp.ui.shell.BottomMissionAction
import com.example.droneservicesapp.ui.shell.model.BottomActionBarUiState
import com.example.droneservicesapp.ui.shell.model.ShellLiveGeoStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

const val SHELL_ROOT_TAG = "compose-shell-root"
const val SHELL_NAVIGATION_BUTTON_TAG = "compose-shell-navigation-button"
const val SHELL_DESTINATION_TITLE_TAG = "compose-shell-destination-title"
const val SHELL_DRAWER_TAG = "compose-shell-drawer"
const val SHELL_BOTTOM_ACTIONS_TAG = "compose-shell-bottom-actions"
const val SHELL_DESTINATION_CONTENT_TAG = "compose-shell-destination-content"
const val SHELL_DRAW_ACTION_TAG = "compose-shell-action-draw"
const val SHELL_LOAD_ACTION_TAG = "compose-shell-action-load"

private enum class TelemetryDialog { GPS, RTK }

@Composable
fun DroneServicesApp(
    telemetryState: HomeTelemetryUiState,
    liveGeoStatus: ShellLiveGeoStatus = ShellLiveGeoStatus(),
    bottomActionState: BottomActionBarUiState,
    bottomActionBarVisible: Boolean,
    onBottomAction: (BottomMissionAction) -> Unit,
    navigationRequests: Flow<ShellNavigationRequest> = emptyFlow(),
    destinationContent: @Composable (AppDestination) -> Unit = { LegacyFragmentHost(it) },
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = AppDestination.fromRoute(backStackEntry?.destination?.route)

    LaunchedEffect(navController, navigationRequests) {
        navigationRequests.collect { request ->
            when (request) {
                is ShellNavigationRequest.Navigate -> navController.navigateSecondary(request.destination)
                ShellNavigationRequest.ReturnToHome -> navController.returnToHome()
                ShellNavigationRequest.OpenDrawer -> drawerState.open()
            }
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = destination.showInDrawer,
        drawerContent = {
            ShellDrawer(
                selectedDestination = destination,
                onDestinationSelected = { selected ->
                    navController.navigateTopLevel(selected)
                    scope.launch { drawerState.close() }
                },
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag(SHELL_ROOT_TAG),
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (destination != AppDestination.Home) {
                    ShellTelemetryBar(
                        destination = destination,
                        telemetryState = telemetryState,
                        liveGeoStatus = liveGeoStatus,
                        onNavigationClick = {
                            if (destination.showInDrawer) {
                                scope.launch { drawerState.open() }
                            } else {
                                navController.popBackStack()
                            }
                        },
                    )
                }
            },
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = AppDestination.Home.route,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(SHELL_DESTINATION_CONTENT_TAG),
                ) {
                    AppDestination.all.forEach { routeDestination ->
                        composable(routeDestination.route) {
                            destinationContent(routeDestination)
                        }
                    }
                }

                if (destination == AppDestination.Home && bottomActionBarVisible) {
                    ShellBottomActionBar(
                        state = bottomActionState,
                        onAction = onBottomAction,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 72.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShellDrawer(
    selectedDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier
            .width(DroneServicesSizes.drawerWidth)
            .testTag(SHELL_DRAWER_TAG),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(DroneServicesThemeTokens.spacing.large),
            verticalArrangement = Arrangement.spacedBy(DroneServicesThemeTokens.spacing.small),
        ) {
            Image(
                painter = painterResource(R.drawable.new_logo),
                contentDescription = stringResource(R.string.nav_header_desc),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                alignment = Alignment.CenterStart,
                contentScale = ContentScale.Fit,
            )
            Text(
                stringResource(R.string.drone_services_sprayer),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.shell_operator_console),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            AppDestination.drawerDestinations.forEach { destination ->
                NavigationItem(
                    label = stringResource(destination.labelRes),
                    selected = destination == selectedDestination,
                    onClick = { onDestinationSelected(destination) },
                    modifier = Modifier.testTag("drawer-${destination.route}"),
                )
            }
        }
    }
}

@Composable
private fun ShellTelemetryBar(
    destination: AppDestination,
    telemetryState: HomeTelemetryUiState,
    liveGeoStatus: ShellLiveGeoStatus,
    onNavigationClick: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<TelemetryDialog?>(null) }
    val navigationDescription = stringResource(
        if (destination.showInDrawer) R.string.shell_open_navigation else R.string.shell_back
    )
    val destinationLabel = stringResource(destination.labelRes)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag(SHELL_DESTINATION_TITLE_TAG)
                .semantics { contentDescription = destinationLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(
                onClick = onNavigationClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(SHELL_NAVIGATION_BUTTON_TAG)
                    .semantics {
                        role = Role.Button
                        contentDescription = navigationDescription
                    },
            ) {
                Text(if (destination.showInDrawer) "☰" else "‹", style = MaterialTheme.typography.titleLarge)
            }
            MapStyleStatusCard(
                modifier = Modifier.weight(1.04f),
                iconRes = R.drawable.ic_status_signal_24,
                iconTint = colorResource(
                    if (telemetryState.isConnected) R.color.ds_color_shell_active
                    else R.color.ds_color_shell_danger
                ),
            ) {
                StatusValue(
                    telemetryState.connectionText.uppercase(),
                    colorResource(
                        if (telemetryState.isConnected) R.color.ds_color_shell_active
                        else R.color.ds_color_shell_danger
                    ),
                    large = true,
                )
            }
            val gpsColor = gpsStatusColor(telemetryState)
            MapStyleStatusCard(
                modifier = Modifier.weight(1.08f),
                iconRes = R.drawable.ic_status_gps_pin_24,
                iconTint = gpsColor,
                onClick = { dialog = TelemetryDialog.GPS },
                description = "GPS, ${telemetryState.gpsStatusText}",
            ) {
                StatusValue(telemetryState.gpsStatusText, gpsColor)
                StatusSubvalue(telemetryState.gpsDetailText, gpsColor)
            }
            MapStyleStatusCard(
                modifier = Modifier.weight(1.48f),
                iconRes = R.drawable.ic_status_rtk_24,
                iconTint = colorResource(R.color.ds_color_text_primary),
                onClick = { dialog = TelemetryDialog.RTK },
                description = "RTK, ${telemetryState.rtkMountpointText}",
            ) {
                StatusSubvalue("RTK", colorResource(R.color.ds_color_text_primary))
                StatusSubvalue(
                    telemetryState.rtkMountpointText.ifBlank { "--" },
                    colorResource(R.color.ds_color_text_primary),
                )
            }
            val armedColor = colorResource(
                if (telemetryState.isArmed) R.color.ds_color_shell_active
                else R.color.ds_color_shell_warning
            )
            MapStyleStatusCard(
                modifier = Modifier.weight(1.25f),
                iconRes = R.drawable.ic_status_shield_24,
                iconTint = armedColor,
            ) {
                StatusValue(telemetryState.armedText.uppercase(), armedColor, large = true)
            }
            val modeColor = colorResource(
                if (telemetryState.isFlightModeControlEnabled) R.color.ds_color_shell_active
                else R.color.ds_color_shell_unselected
            )
            MapStyleStatusCard(
                modifier = Modifier.weight(1.18f),
                iconRes = R.drawable.ic_flight_mode_24,
                iconTint = modeColor,
            ) {
                StatusSubvalue(stringResource(R.string.flight_mode_short_label).uppercase(), modeColor)
                StatusValue(telemetryState.flightModeText, modeColor)
            }
            MapStyleStatusCard(
                modifier = Modifier.weight(0.98f),
                iconRes = R.drawable.ic_status_speed_24,
            ) {
                StatusMetric(telemetryState.speedText.removePrefix("SPD:").trim().ifBlank { "-- m/s" })
            }
            MapStyleStatusCard(
                modifier = Modifier.weight(0.92f),
                iconRes = R.drawable.ic_status_altitude_24,
            ) {
                StatusMetric(compactAltitude(telemetryState.altitudeText))
            }
            val batteryColor = colorResource(telemetryState.batteryColorRes)
            MapStyleStatusCard(
                modifier = Modifier.weight(1.05f),
                iconRes = telemetryState.batteryIconRes,
                iconTint = batteryColor,
            ) {
                StatusValue(telemetryState.batteryText, batteryColor)
            }
            val liveGeoColor = Color(liveGeoStatus.color)
            MapStyleStatusCard(
                modifier = Modifier.weight(1.22f),
                iconRes = if (liveGeoStatus.isClear) {
                    R.drawable.ic_baseline_check_circle_outline_24
                } else {
                    R.drawable.ic_status_warning_24
                },
                iconTint = liveGeoColor,
            ) {
                StatusSubvalue(stringResource(R.string.top_status_live_geo).uppercase())
                StatusValue(
                    liveGeoStatus.label.ifBlank { stringResource(R.string.live_geo_unknown_status) },
                    liveGeoColor,
                )
            }
        }
    }

    dialog?.let { selectedDialog ->
        val isGps = selectedDialog == TelemetryDialog.GPS
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = {
                Text(stringResource(if (isGps) R.string.telemetry_gps_details_title else R.string.telemetry_rtk_details_title))
            },
            text = { Text(if (isGps) telemetryState.gpsDialogText else telemetryState.rtkDialogText) },
            confirmButton = {
                TextButton(onClick = { dialog = null }) {
                    Text(stringResource(R.string.confirm))
                }
            },
        )
    }
}

@Composable
private fun MapStyleStatusCard(
    modifier: Modifier,
    iconRes: Int,
    iconTint: Color = colorResource(R.color.ds_color_text_primary),
    onClick: (() -> Unit)? = null,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardModifier = modifier
        .height(54.dp)
        .then(if (description == null) Modifier else Modifier.semantics { contentDescription = description })
    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(iconTint),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                content = content,
            )
        }
    }
    if (onClick == null) {
        Surface(
            modifier = cardModifier,
            color = Color(0xDD091117),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            content = cardContent,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = cardModifier,
            color = Color(0xDD091117),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            content = cardContent,
        )
    }
}

@Composable
private fun StatusValue(text: String, color: Color, large: Boolean = false) {
    Text(
        text = text,
        color = color,
        fontSize = if (large) 14.sp else 13.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = if (large) 16.sp else 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StatusSubvalue(
    text: String,
    color: Color = colorResource(R.color.ds_color_shell_unselected),
) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StatusMetric(text: String) {
    Text(
        text = text,
        color = colorResource(R.color.ds_color_text_primary),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun gpsStatusColor(state: HomeTelemetryUiState): Color = colorResource(
    when (state.gpsFixQuality) {
        com.example.droneservicesapp.mavserver.GpsFixQuality.DISCONNECTED,
        com.example.droneservicesapp.mavserver.GpsFixQuality.NO_GPS -> R.color.ds_color_shell_danger
        com.example.droneservicesapp.mavserver.GpsFixQuality.RTK_FLOAT,
        com.example.droneservicesapp.mavserver.GpsFixQuality.RTK_FIXED -> R.color.ds_color_shell_active
        com.example.droneservicesapp.mavserver.GpsFixQuality.FIX_2D -> R.color.gps_2d_orange
        com.example.droneservicesapp.mavserver.GpsFixQuality.UNKNOWN -> R.color.gps_unknown_gray
        else -> R.color.ds_color_shell_warning
    }
)

private fun compactAltitude(value: String): String = value
    .removePrefix("ALT:")
    .trim()
    .replace(Regex("(-?\\d+(?:\\.\\d+)?)m$"), "$1 m")
    .let { if (it == "--" || it.isBlank()) "-- m" else it }

@Composable
private fun ShellBottomActionBar(
    state: BottomActionBarUiState,
    onAction: (BottomMissionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.preserveExistingMenu) return
    Surface(
        modifier = modifier
            .widthIn(max = 640.dp)
            .testTag(SHELL_BOTTOM_ACTIONS_TAG),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = MaterialTheme.shapes.large,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.showDraw) {
                AppButton(
                    stringResource(R.string.survey),
                    { onAction(BottomMissionAction.DRAW) },
                    modifier = Modifier.testTag(SHELL_DRAW_ACTION_TAG),
                )
            }
            if (state.showLoad) {
                AppButton(
                    stringResource(R.string.load),
                    { onAction(BottomMissionAction.LOAD) },
                    modifier = Modifier.testTag(SHELL_LOAD_ACTION_TAG),
                    style = AppButtonStyle.SECONDARY,
                )
            }
            if (state.showCancel) {
                AppButton(
                    stringResource(R.string.decline),
                    { onAction(BottomMissionAction.CANCEL) },
                    style = AppButtonStyle.SECONDARY,
                )
            }
            if (state.showErase) {
                AppButton(
                    stringResource(R.string.erase),
                    { onAction(BottomMissionAction.ERASE) },
                    style = AppButtonStyle.DANGER,
                )
            }
            if (state.showAccept) {
                AppButton(stringResource(R.string.accept), { onAction(BottomMissionAction.ACCEPT) })
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(destination: AppDestination) {
    navigate(destination.route) {
        popUpTo(AppDestination.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateSecondary(destination: AppDestination) {
    if (destination.showInDrawer) {
        navigateTopLevel(destination)
    } else {
        navigate(destination.route) { launchSingleTop = true }
    }
}

private fun NavHostController.returnToHome() {
    if (!popBackStack(AppDestination.Home.route, inclusive = false)) {
        navigateTopLevel(AppDestination.Home)
    }
}
