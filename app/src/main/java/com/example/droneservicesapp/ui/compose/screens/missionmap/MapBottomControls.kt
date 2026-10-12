package com.example.droneservicesapp.ui.compose.screens.missionmap

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.droneservicesapp.R
import com.example.droneservicesapp.ui.home.MapDockState
import com.example.droneservicesapp.ui.home.MissionMapController
import com.example.droneservicesapp.ui.home.PreviewPanelState
import com.example.droneservicesapp.ui.home.WaypointEditDockState
import com.example.droneservicesapp.ui.home.model.MapOverlayControlsUiState
import com.example.droneservicesapp.ui.preview.PreviewMode

const val MISSION_BOTTOM_DOCK_TAG = "mission-map-bottom-dock"
const val MISSION_PLANNING_DOCK_BUTTON_TAG = "mission-map-dock-planning"
const val MISSION_DRAW_BAR_TAG = "mission-map-draw-bar"
const val MISSION_PREVIEW_RAIL_TAG = "mission-map-preview-rail"
const val MISSION_WAYPOINT_DOCK_TAG = "mission-map-waypoint-dock"

private data class DockItem(
    @DrawableRes val iconRes: Int,
    val label: String,
    val description: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val tag: String? = null,
)

/**
 * Bottom utility dock. Tablets show labelled items across the width; phones show the compact
 * icon dock with a wider Planning button, as the two XML layouts did.
 */
@Composable
internal fun MapBottomDock(
    tabletLayout: Boolean,
    overlay: MapOverlayControlsUiState,
    dock: MapDockState,
    planningSelected: Boolean,
    controller: MissionMapController,
    modifier: Modifier = Modifier,
) {
    val operator = DockItem(
        R.drawable.ic_baseline_my_location_48, stringResource(R.string.map_dock_operator),
        stringResource(R.string.my_location_button_desc), false, controller::centerOnUser,
    )
    val drone = DockItem(
        R.drawable.drone_marker_36, stringResource(R.string.map_dock_drone),
        stringResource(R.string.drone_location_button), false, controller::centerOnDroneOrShowDisconnected,
    )
    val obstacles = DockItem(
        R.drawable.obstacle, stringResource(R.string.map_dock_obstacles),
        stringResource(R.string.forbidden_areas), dock.obstaclesSelected, controller::toggleObstacles,
    )
    val planning = DockItem(
        R.drawable.mission_planning, stringResource(R.string.planning),
        stringResource(R.string.planning), planningSelected, controller::togglePlanning,
        MISSION_PLANNING_DOCK_BUTTON_TAG,
    )
    val offset = DockItem(
        R.drawable.ic_menu_map, stringResource(R.string.map_dock_offset),
        stringResource(R.string.map_dock_offset), dock.offsetSelected, controller::startDroneOffsetAdjustment,
    )
    val download = DockItem(
        R.drawable.ic_download_24, stringResource(R.string.map_dock_offline),
        stringResource(R.string.home_offline_download), false, controller::downloadCurrentViewOffline,
    )
    val more = DockItem(
        R.drawable.ic_menu_settings, stringResource(R.string.more),
        stringResource(R.string.menu_settings), false, controller::openSettings,
    )

    if (tabletLayout) {
        val items = buildList {
            if (overlay.showMyLocationButton) add(operator)
            if (overlay.showDroneLocationButton) add(drone)
            if (overlay.showDroneLocationButton) add(obstacles)
            add(planning)
            if (overlay.showDroneLocationButton) add(offset)
            if (overlay.showDownloadOfflineButton) add(download)
            add(more)
        }
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(MapChrome.overlay, MapChrome.panelShape)
                .border(1.dp, MapChrome.overlayStroke, MapChrome.panelShape)
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .testTag(MISSION_BOTTOM_DOCK_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    VerticalDivider(Modifier.height(44.dp), thickness = 1.dp, color = MapChrome.shellStroke)
                }
                TabletDockItem(item, Modifier.weight(1f))
            }
        }
    } else {
        Row(
            modifier = modifier
                .background(MapChrome.overlay, MapChrome.panelShape)
                .border(1.dp, MapChrome.overlayStroke, MapChrome.panelShape)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag(MISSION_BOTTOM_DOCK_TAG),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (overlay.showMyLocationButton) PhoneDockIcon(operator)
            if (overlay.showDroneLocationButton) {
                PhoneDockIcon(drone)
                PhoneDockIcon(obstacles)
                PhoneDockIcon(offset)
            }
            PhonePlanningButton(planning)
            if (overlay.showDownloadOfflineButton) PhoneDockIcon(download)
            PhoneDockIcon(more)
        }
    }
}

@Composable
private fun TabletDockItem(item: DockItem, modifier: Modifier) {
    val shape = RoundedCornerShape(24.dp)
    val color = if (item.selected) MapChrome.active else MapChrome.unselected
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 4.dp)
            .background(if (item.selected) MapChrome.dockItemSelected else Color.Transparent, shape)
            .border(1.dp, if (item.selected) MapChrome.dockItemSelectedStroke else Color.Transparent, shape)
            .clickable(role = Role.Button, onClick = item.onClick)
            .semantics {
                contentDescription = item.description
                selected = item.selected
            }
            .then(item.tag?.let { Modifier.testTag(it) } ?: Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painterResource(item.iconRes),
            null,
            Modifier.size(28.dp),
            colorFilter = ColorFilter.tint(color),
        )
        Text(item.label, color = color, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PhoneDockIcon(item: DockItem) {
    val color = if (item.selected) MapChrome.active else Color.White
    Box(
        modifier = Modifier
            .size(48.dp)
            .padding(4.dp)
            .background(MapChrome.dockButton, CircleShape)
            .border(1.dp, if (item.selected) MapChrome.active else MapChrome.dockButtonStroke, CircleShape)
            .clickable(role = Role.Button, onClick = item.onClick)
            .semantics {
                contentDescription = item.description
                selected = item.selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(item.iconRes),
            null,
            Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(color),
        )
    }
}

@Composable
private fun PhonePlanningButton(item: DockItem) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .height(40.dp)
            .width(132.dp)
            .background(if (item.selected) MapChrome.pillActiveSurface else MapChrome.dockButton, shape)
            .border(1.dp, if (item.selected) MapChrome.pillActiveStroke else MapChrome.dockButtonStroke, shape)
            .clickable(role = Role.Button, onClick = item.onClick)
            .semantics {
                contentDescription = item.description
                selected = item.selected
            }
            .then(item.tag?.let { Modifier.testTag(it) } ?: Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Image(
            painterResource(item.iconRes),
            null,
            Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(if (item.selected) MapChrome.active else Color.White),
        )
        Text(item.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Map / Ortho / 3D segmented switch, plus the 3D terrain status line. */
@Composable
internal fun PreviewModeRail(
    preview: PreviewPanelState,
    onSelect: (PreviewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    MapOverlayPanel(modifier = modifier.testTag(MISSION_PREVIEW_RAIL_TAG), contentPadding = 4.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PreviewModeSegment(R.string.preview_mode_map, R.drawable.ic_menu_map, preview.mode == PreviewMode.MAP, Modifier.weight(1f)) {
                onSelect(PreviewMode.MAP)
            }
            if (!preview.surveyMode) {
                PreviewModeSegment(R.string.preview_mode_ortho, R.drawable.ic_ortho_24, preview.mode == PreviewMode.ORTHO, Modifier.weight(1f)) {
                    onSelect(PreviewMode.ORTHO)
                }
            }
            PreviewModeSegment(R.string.preview_mode_3d, R.drawable.ic_point_cloud_24, preview.mode == PreviewMode.POINT_CLOUD, Modifier.weight(1f)) {
                onSelect(PreviewMode.POINT_CLOUD)
            }
        }
        preview.terrainStatus?.takeIf { preview.mode == PreviewMode.POINT_CLOUD }?.let {
            Text(
                it,
                color = MapChrome.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun PreviewModeSegment(textRes: Int, @DrawableRes iconRes: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    MapPill(
        text = stringResource(textRes),
        selected = selected,
        onClick = onClick,
        iconRes = iconRes,
        modifier = modifier,
        height = 40.dp,
    )
}

/** Decline / Accept bar shown while drawing a mission area or route. */
@Composable
internal fun DrawActionBar(onDecline: () -> Unit, onAccept: () -> Unit, tabletLayout: Boolean, modifier: Modifier = Modifier) {
    val height = if (tabletLayout) 56.dp else 42.dp
    val width = if (tabletLayout) 200.dp else 112.dp
    val fontSize = if (tabletLayout) 18.sp else 14.sp
    Row(
        modifier = modifier
            .background(MapChrome.overlay, MapChrome.panelShape)
            .border(1.dp, MapChrome.overlayStroke, MapChrome.panelShape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag(MISSION_DRAW_BAR_TAG),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onDecline, modifier = Modifier.height(height).width(width)) {
            Text(stringResource(R.string.decline), fontSize = fontSize, color = MapChrome.textPrimary)
        }
        Button(
            onClick = onAccept,
            modifier = Modifier.height(height).width(width),
            colors = ButtonDefaults.buttonColors(containerColor = MapChrome.active, contentColor = MapChrome.onAccent),
        ) {
            Text(stringResource(R.string.accept), fontSize = fontSize, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Height and delete controls for the selected survey or terrain-route waypoint. */
@Composable
internal fun WaypointEditDock(
    state: WaypointEditDockState,
    controller: MissionMapController,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var hadFocus by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .background(MapChrome.overlay, MapChrome.panelShape)
            .border(1.dp, MapChrome.overlayStroke, MapChrome.panelShape)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(MISSION_WAYPOINT_DOCK_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MapRoundIconButton(
                R.drawable.ic_add_24,
                stringResource(R.string.survey_waypoint_height_increase),
                onClick = { controller.changeSelectedWaypointHeight(1.0) },
                size = 36.dp,
                background = MapChrome.dockButton,
                border = MapChrome.dockButtonStroke,
            )
            MapRoundIconButton(
                R.drawable.ic_remove_24,
                stringResource(R.string.survey_waypoint_height_decrease),
                onClick = { controller.changeSelectedWaypointHeight(-1.0) },
                size = 36.dp,
                background = MapChrome.dockButton,
                border = MapChrome.dockButtonStroke,
            )
        }
        BasicTextField(
            value = state.heightText,
            onValueChange = controller::setSelectedWaypointHeightText,
            singleLine = true,
            textStyle = TextStyle(color = MapChrome.textPrimary, fontSize = 16.sp, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(MapChrome.active),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                controller.applySelectedWaypointHeight()
                focusManager.clearFocus()
            }),
            modifier = Modifier
                .width(84.dp)
                .onFocusChanged { focus ->
                    // Apply only when the field loses focus it actually had, as the EditText did.
                    if (hadFocus && !focus.isFocused) controller.applySelectedWaypointHeight()
                    hadFocus = focus.isFocused
                },
        )
        MapRoundIconButton(
            R.drawable.ic_delete_24,
            stringResource(R.string.survey_waypoint_delete),
            onClick = controller::deleteSelectedWaypoint,
            size = 44.dp,
            iconSize = 22.dp,
            background = MapChrome.dockButton,
            border = MapChrome.dockButtonStroke,
        )
        MapRoundIconButton(
            R.drawable.ic_baseline_cancel_24,
            stringResource(R.string.survey_waypoint_cancel),
            onClick = controller::cancelSelectedWaypoint,
            size = 44.dp,
            iconSize = 22.dp,
            background = MapChrome.dockButton,
            border = MapChrome.dockButtonStroke,
        )
    }
}
