package com.example.droneservicesapp.ui.compose.screens.missionmap

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.survey.SprayFlowCalibration
import com.example.droneservicesapp.domain.terrain.PointCloudCoverage
import com.example.droneservicesapp.domain.terrain.TerrainCoveragePlanner
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.mavserver.TerrainProvisioningState
import com.example.droneservicesapp.ui.home.binders.MissionParamsStateMapper
import com.example.droneservicesapp.ui.home.model.MissionParamsPresentation
import com.example.droneservicesapp.ui.home.model.MissionParamsPresenter
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import java.util.Locale

const val MISSION_PARAMS_PANEL_TAG = "mission-map-params-panel"

/** Observes the planning inputs the parameter panel and workflow terrain controls depend on. */
@Composable
internal fun rememberMissionParamsPresentation(
    activityViewModel: MainActivityViewModel,
    droneViewModel: DroneViewModel,
): MissionParamsPresentation {
    // Reading each LiveData keeps the presentation current; values are re-read through the mapper.
    val operationMode by activityViewModel.planningOperationMode.observeAsState()
    val workflow by activityViewModel.activePlanningWorkflow.observeAsState(PlanningWorkflow.AREA)
    val coverage by activityViewModel.pointCloudCoverage.observeAsState(PointCloudCoverage.NONE)
    val altitudeReference by activityViewModel.altitudeReferenceMode.observeAsState()
    val terrainState by droneViewModel.terrainProvisioningState.observeAsState(TerrainProvisioningState.Idle)
    val connected by droneViewModel.conStateLiveData.observeAsState(false)
    val surveyPath by activityViewModel.surveyPath.observeAsState(emptyList())
    val plannedRoute by activityViewModel.plannedRoutePath.observeAsState(emptyList())
    val routeWaypoints by activityViewModel.routeWaypoints.observeAsState(emptyList())
    val terrainRoute by activityViewModel.terrainRouteWaypoints.observeAsState(emptyList())
    val mapper = MissionParamsStateMapper(activityViewModel)
    val values = mapper.currentUiState().copy(
        operationMode = operationMode ?: PlanningOperationMode.SURVEY,
        altitudeReferenceMode = altitudeReference ?: AltitudeReferenceMode.TERRAIN,
    )
    val path = when (workflow ?: PlanningWorkflow.AREA) {
        PlanningWorkflow.AREA -> surveyPath.orEmpty().map { LatLon(it.latitude, it.longitude) }
        PlanningWorkflow.POINTS -> plannedRoute.orEmpty().takeIf { it.size >= 2 }?.map { LatLon(it.latitude, it.longitude) }
            ?: routeWaypoints.orEmpty().map { LatLon(it.latitude, it.longitude) }
    }
    val state = terrainState ?: TerrainProvisioningState.Idle
    val sourceReadyMatches = (state as? TerrainProvisioningState.SourceReady)?.let { ready ->
        path.isNotEmpty() &&
            TerrainCoveragePlanner.distanceMeters(TerrainCoveragePlanner.missionCenter(path), ready.center) < 1.0 &&
            ready.radiusMeters == TerrainCoveragePlanner.COVERAGE_RADIUS_METERS
    } ?: false
    return MissionParamsPresenter.present(
        state = values,
        workflow = workflow ?: PlanningWorkflow.AREA,
        pointCloudCoverage = coverage ?: PointCloudCoverage.NONE,
        terrainState = state,
        droneConnected = connected == true,
        sourceReadyMatchesMission = sourceReadyMatches,
        hasTerrainRouteProfile = terrainRoute.orEmpty().isNotEmpty(),
    )
}

/** Human-readable terrain provisioning status, or null when there is nothing to report. */
internal fun terrainStatusText(context: Context, presentation: MissionParamsPresentation): String? =
    when (val current = presentation.effectiveTerrainState) {
        TerrainProvisioningState.Idle -> null
        is TerrainProvisioningState.PreparingSource -> context.getString(
            R.string.terrain_preparing_tile,
            current.tileName ?: "…",
            current.completedTiles,
            current.totalTiles,
        )
        is TerrainProvisioningState.SourceReady -> if (presentation.terrainSourceReady) {
            context.getString(R.string.terrain_downloaded, current.tileCount)
        } else null
        TerrainProvisioningState.WaitingForMissionUpload -> context.getString(R.string.terrain_waiting_for_upload)
        is TerrainProvisioningState.Serving -> if (current.pending != null) {
            context.getString(R.string.terrain_sending_blocks_pending, current.blocksSent, current.pending)
        } else {
            context.getString(R.string.terrain_sending_blocks, current.blocksSent)
        }
        is TerrainProvisioningState.Verifying -> context.getString(R.string.terrain_verifying_progress, current.checked, current.total)
        is TerrainProvisioningState.Ready -> context.getString(R.string.terrain_ready_blocks, current.blocksSent)
        is TerrainProvisioningState.Failed -> context.getString(R.string.terrain_failed_detail, current.detail ?: current.failure.name)
    }

@Composable
internal fun MissionParamsPanel(
    activityViewModel: MainActivityViewModel,
    presentation: MissionParamsPresentation,
    modifier: Modifier = Modifier,
) {
    val takeoff by activityViewModel.takeoffHeight.observeAsState()
    val angle by activityViewModel.angleProgress.observeAsState()
    val lineDistance by activityViewModel.lineDistanceProgress.observeAsState()
    val altitude by activityViewModel.flightAltProgress.observeAsState()
    val sprayer by activityViewModel.sprayerProgress.observeAsState()
    val stripSpacing by activityViewModel.surveyStripSpacing.observeAsState()
    val surveyHeight by activityViewModel.surveyHeightAboveTerrain.observeAsState()
    val overlap by activityViewModel.surveyOverlapPercent.observeAsState()
    val gridAngle by activityViewModel.surveyGridAngle.observeAsState()
    val terrainSegment by activityViewModel.surveyTerrainSegment.observeAsState()
    val canopy by activityViewModel.surveyCanopySmoothing.observeAsState()
    val speed by activityViewModel.flightSpeed.observeAsState()
    val coverage by activityViewModel.pointCloudCoverage.observeAsState(PointCloudCoverage.NONE)
    val workflow by activityViewModel.activePlanningWorkflow.observeAsState(PlanningWorkflow.AREA)

    val survey = presentation.showSurveyFields
    val pointRoute = workflow == PlanningWorkflow.POINTS
    val meters = stringResource(R.string.m)
    val degrees = stringResource(R.string.degrees)
    val percent = stringResource(R.string.percentage_symbol)

    @Composable
    fun intField(label: String, value: Double?, min: Int, max: Int, unit: String, update: (Int) -> Unit) {
        val current = value?.toInt() ?: min
        MapSliderField(
            label = label,
            stepIndex = current,
            minStepIndex = min,
            maxStepIndex = max,
            valueText = current.toString(),
            unit = unit,
            onStepChange = update,
        )
    }

    @Composable
    fun terrainSegmentField() {
        val value = terrainSegment ?: 2.5
        MapSliderField(
            label = stringResource(R.string.survey_terrain_segment),
            stepIndex = ((value - 0.5) / 0.5).toInt().coerceAtLeast(0),
            maxStepIndex = 39,
            valueText = formatDecimal(value),
            unit = meters,
            onStepChange = { activityViewModel.updateSurveyTerrainSegment(0.5 + it * 0.5) },
        )
    }

    @Composable
    fun canopyField() = intField(stringResource(R.string.survey_canopy_smoothing), canopy, 0, 30, meters) {
        activityViewModel.updateSurveyCanopySmoothing(it)
    }

    @Composable
    fun speedField() {
        val value = speed ?: 5.0
        MapSliderField(
            label = stringResource(R.string.flight_speed_label),
            stepIndex = ((value - MIN_SPEED) / SPEED_STEP).toInt(),
            maxStepIndex = 18,
            valueText = formatDecimal(value),
            unit = stringResource(R.string.meters_per_second),
            onStepChange = { activityViewModel.updateMissionSpeed(MIN_SPEED + it * SPEED_STEP) },
        )
    }

    MapOverlayPanel(modifier = modifier.testTag(MISSION_PARAMS_PANEL_TAG)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_panel_flight_settings),
                    color = MapChrome.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Image(
                    painterResource(R.drawable.ic_chevron_down_24),
                    null,
                    Modifier.size(28.dp),
                    colorFilter = ColorFilter.tint(MapChrome.textPrimary),
                )
            }

            if (presentation.showAltitudeReference) {
                val reference = presentation.values.altitudeReferenceMode
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    MapPill(
                        stringResource(R.string.relative),
                        selected = reference == AltitudeReferenceMode.RELATIVE,
                        onClick = { activityViewModel.setAltitudeReferenceMode(AltitudeReferenceMode.RELATIVE) },
                        modifier = Modifier.weight(1f),
                    )
                    MapPill(
                        stringResource(R.string.terrain),
                        selected = reference == AltitudeReferenceMode.TERRAIN,
                        onClick = { activityViewModel.setAltitudeReferenceMode(AltitudeReferenceMode.TERRAIN) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (reference == AltitudeReferenceMode.TERRAIN) {
                    MapCaption(stringResource(R.string.terrain_altitude_warning), color = MapChrome.warning)
                }
            }

            intField(stringResource(R.string.takeoff_height), takeoff, 1, 120, meters) {
                activityViewModel.updateTakeoffHeight(it)
            }

            if (presentation.showSprayFields) {
                val (statusRes, statusColor) = sprayStatus(coverage ?: PointCloudCoverage.NONE)
                MapCaption(stringResource(statusRes), color = statusColor, fontSize = 12.sp)
                intField(stringResource(R.string.line_distance), lineDistance, 0, 20, meters) {
                    activityViewModel.updateLineSpacing(it)
                }
                intField(stringResource(R.string.flight_altitude), altitude, 0, 20, meters) {
                    activityViewModel.updateAltitude(it)
                }
                intField(stringResource(R.string.mission_angle), angle, 0, 180, degrees) {
                    activityViewModel.updateMissionAngle(it)
                }
                speedField()
                val flow = SprayFlowCalibration.flowForIntensityPercent(sprayer ?: 0.0)
                MapSliderField(
                    label = stringResource(R.string.spray_flow_rate),
                    stepIndex = SprayFlowCalibration.indexForFlow(flow).coerceIn(0, SprayFlowCalibration.settings.lastIndex),
                    maxStepIndex = SprayFlowCalibration.settings.lastIndex,
                    valueText = String.format(Locale.US, "%.1f", flow),
                    unit = stringResource(R.string.liters_per_minute_unit),
                    onStepChange = {
                        activityViewModel.updateSprayFlowLitersPerMinute(SprayFlowCalibration.settingAt(it).litersPerMinute)
                    },
                )
                if (presentation.showTerrainProfileFields) {
                    terrainSegmentField()
                    canopyField()
                }
            }

            if (survey) {
                MapSectionLabel(stringResource(R.string.survey_grid_title), color = MapChrome.active)
                MapDivider()
                intField(stringResource(R.string.survey_overlap), overlap, 0, 95, percent) {
                    activityViewModel.updateSurveyOverlap(it)
                }
                intField(stringResource(R.string.survey_strip_spacing), stripSpacing, 0, 95, percent) {
                    activityViewModel.updateSurveyStripSpacing(it)
                }
                intField(stringResource(R.string.survey_height_above_terrain), surveyHeight, 0, 120, meters) {
                    activityViewModel.updateSurveyHeightAboveTerrain(it)
                }
                intField(stringResource(R.string.survey_grid_angle), gridAngle, 0, 180, degrees) {
                    activityViewModel.updateSurveyGridAngle(it)
                }
                if (pointRoute) {
                    terrainSegmentField()
                    canopyField()
                }
                speedField()
            }
        }
    }
}

private fun sprayStatus(coverage: PointCloudCoverage): Pair<Int, Color> = when (coverage) {
    PointCloudCoverage.CHECKING -> R.string.spray_mode_point_cloud_validating to MapChrome.warning
    PointCloudCoverage.COMPLETE -> R.string.spray_mode_point_cloud_validated to MapChrome.active
    PointCloudCoverage.PARTIAL -> R.string.spray_mode_point_cloud_partial to MapChrome.danger
    PointCloudCoverage.NONE -> R.string.spray_mode_terrain_rangefinder to MapChrome.active
}

private fun formatDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

private const val MIN_SPEED = 1.0
private const val SPEED_STEP = 0.5
