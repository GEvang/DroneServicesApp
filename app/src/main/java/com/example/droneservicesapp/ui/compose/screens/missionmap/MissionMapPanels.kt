package com.example.droneservicesapp.ui.compose.screens.missionmap

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.MissionObstacleShape
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.ui.home.MissionMapController
import com.example.droneservicesapp.ui.home.MissionSummaryCardState
import com.example.droneservicesapp.ui.home.ObstaclePanelState
import com.example.droneservicesapp.ui.home.WeatherPanelState
import com.example.droneservicesapp.ui.home.WorkflowPanelState
import com.example.droneservicesapp.ui.home.components.MissionSimulationController
import com.example.droneservicesapp.ui.home.map.MissionMapSceneBuilder
import com.example.droneservicesapp.ui.home.map.ObstacleEditorState
import com.example.droneservicesapp.ui.home.model.MissionParamsPresentation
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlin.math.PI

const val MISSION_WORKFLOW_PANEL_TAG = "mission-map-workflow-panel"
const val MISSION_OBSTACLE_PANEL_TAG = "mission-map-obstacle-panel"
const val MISSION_UPLOAD_BUTTON_TAG = "mission-map-upload-button"
const val MISSION_DRAW_BUTTON_TAG = "mission-map-draw-button"

@Composable
internal fun WorkflowPanel(
    workflow: PlanningWorkflow,
    state: WorkflowPanelState,
    weatherVisible: Boolean,
    params: MissionParamsPresentation,
    controller: MissionMapController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    MapOverlayPanel(modifier = modifier.testTag(MISSION_WORKFLOW_PANEL_TAG)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MapPanelHeader(title = stringResource(R.string.home_panel_top_actions), iconRes = R.drawable.ic_workflow_24) {
                MapRoundIconButton(
                    iconRes = R.drawable.ic_chevron_down_24,
                    contentDescription = stringResource(R.string.collapse_panel),
                    onClick = controller::closePanels,
                    size = 30.dp,
                    background = MapChrome.pillInactive,
                    border = MapChrome.overlayStroke,
                )
            }

            MapSectionLabel(stringResource(R.string.workflow_planning_mode))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                MapPill(
                    text = stringResource(R.string.area),
                    selected = workflow == PlanningWorkflow.AREA,
                    onClick = { controller.selectWorkflow(PlanningWorkflow.AREA) },
                    iconRes = R.drawable.ic_draw_area_24,
                    modifier = Modifier.weight(1f),
                )
                MapPill(
                    text = stringResource(R.string.points),
                    selected = workflow == PlanningWorkflow.POINTS,
                    onClick = { controller.selectWorkflow(PlanningWorkflow.POINTS) },
                    iconRes = R.drawable.ic_points_24,
                    modifier = Modifier.weight(1f),
                )
            }

            MapSectionLabel(stringResource(R.string.workflow_geometry))
            MapPanelButton(
                text = stringResource(
                    when (workflow) {
                        PlanningWorkflow.AREA -> if (state.hasGeometry) R.string.redraw_area else R.string.draw_area
                        PlanningWorkflow.POINTS -> if (state.hasGeometry) R.string.redraw_route else R.string.draw_route
                    },
                ),
                onClick = controller::drawGeometry,
                iconRes = R.drawable.ic_draw_area_24,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MISSION_DRAW_BUTTON_TAG),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                if (state.addHomeVisible) {
                    MapPanelButton(
                        text = stringResource(R.string.set_home),
                        onClick = controller::addPlannedHome,
                        iconRes = R.drawable.ic_home_takeoff_marker,
                        iconTinted = false,
                        modifier = Modifier.weight(1f),
                    )
                }
                MapPanelButton(
                    text = stringResource(R.string.clear_area),
                    onClick = controller::clearGeometry,
                    iconRes = R.drawable.ic_delete_24,
                    enabled = state.hasGeometry,
                    contentColor = MapChrome.danger,
                    modifier = Modifier.weight(1f),
                )
            }
            if (workflow == PlanningWorkflow.POINTS) {
                MapCaption(stringResource(R.string.route_summary_format, state.routeWaypointCount), color = MapChrome.textPrimary)
            }

            MapDivider()
            MapSectionLabel(stringResource(R.string.workflow_mission))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                MapPanelButton(
                    text = stringResource(R.string.save),
                    onClick = controller::saveMission,
                    iconRes = R.drawable.ic_save_24,
                    modifier = Modifier.weight(1f),
                )
                MapPanelButton(
                    text = stringResource(R.string.load),
                    onClick = controller::loadMission,
                    iconRes = R.drawable.ic_folder_24,
                    modifier = Modifier.weight(1f),
                )
            }

            MapSectionLabel(stringResource(R.string.workflow_tools))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                MapPanelButton(
                    text = stringResource(R.string.weather),
                    onClick = controller::toggleWeather,
                    iconRes = R.drawable.ic_weather_wind_24,
                    highlighted = weatherVisible,
                    contentColor = if (weatherVisible) MapChrome.active else MapChrome.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                MapPanelButton(
                    text = stringResource(
                        when (state.simulationState) {
                            MissionSimulationController.State.FLYING -> R.string.stop_mission_simulation
                            MissionSimulationController.State.WAITING_FOR_SERVICE -> R.string.resume_mission
                            else -> R.string.simulate_mission_path
                        },
                    ),
                    onClick = controller::toggleMissionSimulation,
                    iconRes = R.drawable.drone_marker_36,
                    iconTinted = false,
                    enabled = state.simulationEnabled,
                    modifier = Modifier.weight(1f),
                )
            }
            MapPanelButton(
                text = stringResource(R.string.upload_mission),
                onClick = controller::uploadMission,
                iconRes = R.drawable.ic_upload_24,
                enabled = params.missionUploadEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MISSION_UPLOAD_BUTTON_TAG),
            )
            if (params.terrainDownloadVisible) {
                MapPanelButton(
                    text = stringResource(R.string.terrain_download_button),
                    onClick = controller::downloadTerrain,
                    enabled = params.terrainDownloadEnabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            params.terrainProgressPercent?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MapChrome.active,
                    trackColor = MapChrome.sliderPill,
                )
            }
            terrainStatusText(context, params)?.let { MapCaption(it, color = MapChrome.textPrimary, fontSize = 12.sp) }
            if (state.resumeVisible) {
                MapPanelButton(
                    text = state.resumeLabel ?: stringResource(R.string.resume_mission),
                    onClick = controller::resumeServiceMission,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun ObstaclePanel(
    state: ObstaclePanelState,
    obstacles: List<com.example.droneservicesapp.domain.model.MissionObstacle>,
    controller: MissionMapController,
    modifier: Modifier = Modifier,
) {
    MapOverlayPanel(modifier = modifier.testTag(MISSION_OBSTACLE_PANEL_TAG)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.forbidden_areas), color = MapChrome.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            val polygon = state.mode == ObstacleEditorState.Mode.POLYGON
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                MapPill(
                    stringResource(R.string.obstacle_shape_circle),
                    selected = !polygon,
                    onClick = { controller.selectObstacleShape(ObstacleEditorState.Mode.CIRCLE) },
                    modifier = Modifier.weight(1f),
                )
                MapPill(
                    stringResource(R.string.obstacle_shape_polygon),
                    selected = polygon,
                    onClick = { controller.selectObstacleShape(ObstacleEditorState.Mode.POLYGON) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (!polygon) {
                MapSliderField(
                    label = stringResource(R.string.obstacle_radius_format, state.radiusMeters),
                    stepIndex = state.radiusMeters,
                    minStepIndex = 2,
                    maxStepIndex = 100,
                    valueText = state.radiusMeters.toString(),
                    unit = stringResource(R.string.m),
                    onStepChange = controller::setObstacleRadius,
                )
            }
            MapPanelButton(
                text = stringResource(if (polygon && state.placementActive) R.string.finish_forbidden_polygon else R.string.add_forbidden_area),
                onClick = controller::addObstacle,
                highlighted = state.placementActive,
                modifier = Modifier.fillMaxWidth(),
                height = 36.dp,
            )
            obstacles.filter { it.isValid() }.forEachIndexed { index, obstacle ->
                val letter = MissionMapSceneBuilder.obstacleLetter(index)
                val size = when (obstacle.shape) {
                    MissionObstacleShape.CIRCLE -> PI * obstacle.radiusMeters * obstacle.radiusMeters
                    MissionObstacleShape.POLYGON -> SphericalUtil.computeArea(obstacle.vertices.map { LatLng(it.lat, it.lon) })
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (obstacle.shape == MissionObstacleShape.CIRCLE) R.string.obstacle_circle_item_format
                            else R.string.obstacle_polygon_item_format,
                            letter,
                            size,
                        ),
                        color = MapChrome.textPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    val deleteDescription = stringResource(R.string.delete_obstacle_format, letter)
                    Text(
                        "×",
                        color = MapChrome.danger,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .size(36.dp)
                            .clickable(role = Role.Button) { controller.removeObstacle(obstacle.id) }
                            .semantics { contentDescription = deleteDescription }
                            .padding(start = 10.dp),
                    )
                }
            }
            MapPanelButton(
                text = stringResource(R.string.clear_forbidden_areas),
                onClick = controller::clearObstacles,
                modifier = Modifier.fillMaxWidth(),
                height = 36.dp,
            )
        }
    }
}

@Composable
internal fun WeatherPanel(state: WeatherPanelState, modifier: Modifier = Modifier) {
    MapOverlayPanel(modifier = modifier.width(240.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painterResource(R.drawable.ic_weather_wind_24), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(MapChrome.active))
            Text(stringResource(R.string.weather), color = MapChrome.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                stringResource(
                    when (state.status) {
                        WeatherPanelState.Status.LOADING -> R.string.weather_loading
                        WeatherPanelState.Status.LIVE -> R.string.weather_live
                        WeatherPanelState.Status.UNAVAILABLE -> R.string.weather_unavailable
                    },
                ),
                color = MapChrome.textSecondary,
                fontSize = 11.sp,
            )
        }
        val wind = state.wind
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(
                painterResource(R.drawable.ic_wind_direction_48),
                stringResource(R.string.weather_wind_direction),
                Modifier
                    .size(56.dp)
                    .rotate(wind?.directionToDegrees ?: 0f),
                colorFilter = ColorFilter.tint(MapChrome.active),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        wind != null -> stringResource(R.string.weather_wind_from_format, wind.directionFromCompass)
                        state.status == WeatherPanelState.Status.UNAVAILABLE -> stringResource(R.string.weather_unavailable)
                        else -> stringResource(R.string.weather_loading)
                    },
                    color = MapChrome.textPrimary,
                    fontSize = 13.sp,
                )
                Text(
                    wind?.let { stringResource(R.string.weather_wind_speed_format, it.speedKilometersPerHour) } ?: "--",
                    color = MapChrome.active,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
internal fun MissionDownloadChip(percent: Int, modifier: Modifier = Modifier) {
    MapOverlayPanel(modifier = modifier, shape = MapChrome.overlayCardShape, color = MapChrome.overlay, border = MapChrome.shellStroke) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier
                    .width(72.dp)
                    .height(4.dp),
                color = MapChrome.active,
                trackColor = MapChrome.sliderPill,
            )
            Text(stringResource(R.string.mission_downloading_from_drone, percent), color = MapChrome.textPrimary, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun MissionSummaryCard(state: MissionSummaryCardState, modifier: Modifier = Modifier) {
    val text = state.text ?: return
    MapOverlayPanel(
        modifier = modifier.widthIn(min = 220.dp),
        shape = MapChrome.overlayCardShape,
        border = MapChrome.shellStroke,
        contentPadding = 12.dp,
    ) {
        Text(text, color = MapChrome.textPrimary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 2.dp))
    }
}
