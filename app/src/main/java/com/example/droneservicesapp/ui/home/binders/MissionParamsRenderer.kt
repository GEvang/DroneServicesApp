package com.example.droneservicesapp.ui.home.binders

import android.graphics.Typeface
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.survey.SprayPresets
import com.example.droneservicesapp.domain.terrain.PointCloudCoverage
import com.example.droneservicesapp.domain.terrain.TerrainCoveragePlanner
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.mavserver.TerrainProvisioningState
import com.example.droneservicesapp.ui.home.model.MissionParamsUiState
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import java.util.Locale

class MissionParamsRenderer(
    private val views: MissionParamsViews,
    private val lifecycleOwner: LifecycleOwner,
    private val activityViewModel: MainActivityViewModel,
    private val droneViewModel: DroneViewModel,
    private val stateMapper: MissionParamsStateMapper,
) {
    // Point-cloud coverage is refreshed while mission parameters are edited. Keep that
    // refresh from causing an otherwise identical settings layout to be rebuilt.
    private var lastLayoutSignature: Triple<Boolean, Boolean, Boolean>? = null
    private var currentTerrainState: TerrainProvisioningState = TerrainProvisioningState.Idle

    private var missionParamsUiState = MissionParamsUiState(
        operationMode = PlanningOperationMode.SURVEY,
        angle = 90,
        lineDistance = 5,
        altitude = 0,
        takeoffHeight = 5,
        sprayerIntensity = 75,
        surveyStripSpacing = 70,
        surveyHeightAboveTerrain = 50,
        surveyOverlap = 80,
        surveyGridAngle = 90,
        surveyTerrainSegment = 2.5,
        surveyCanopySmoothing = 5,
        flightSpeed = 5.0,
        estimatedFlightMinutes = 1,
        altitudeReferenceMode = AltitudeReferenceMode.TERRAIN
    )

    fun bind() {
        missionParamsUiState = stateMapper.currentUiState()
        renderFlightSummary(missionParamsUiState)
        renderPreset(activityViewModel.selectedSprayPresetId.value)
        renderMode(
            missionParamsUiState.operationMode,
            activityViewModel.pointCloudCoverage.value ?: PointCloudCoverage.NONE
        )
        renderSurveyValues(missionParamsUiState)
        bindPresetSelector()
        bindAltitudeReferenceSelector()
        renderAltitudeReference(missionParamsUiState.altitudeReferenceMode)
        droneViewModel.terrainProvisioningState.observe(lifecycleOwner, ::renderTerrainProvisioning)

        activityViewModel.flightSpeed.observe(lifecycleOwner) { flightSpeed ->
            missionParamsUiState = missionParamsUiState.copy(flightSpeed = flightSpeed)
            renderFlightSummary(missionParamsUiState)
        }

        activityViewModel.estimatedFlightMinutes.observe(lifecycleOwner) { minutes ->
            missionParamsUiState = missionParamsUiState.copy(estimatedFlightMinutes = minutes)
            renderFlightSummary(missionParamsUiState)
        }

        activityViewModel.altitudeReferenceMode.observe(lifecycleOwner) { mode ->
            val selectedMode = mode ?: AltitudeReferenceMode.RELATIVE
            missionParamsUiState = missionParamsUiState.copy(altitudeReferenceMode = selectedMode)
            renderAltitudeReference(selectedMode)
            renderTerrainProvisioning(currentTerrainState)
        }

        activityViewModel.selectedSprayPresetId.observe(lifecycleOwner) { presetId ->
            renderPreset(presetId)
        }

        activityViewModel.planningOperationMode.observe(lifecycleOwner) { mode ->
            val operationMode = mode ?: PlanningOperationMode.SURVEY
            missionParamsUiState = missionParamsUiState.copy(operationMode = operationMode)
            renderMode(
                operationMode,
                activityViewModel.pointCloudCoverage.value ?: PointCloudCoverage.NONE,
            )
            renderTerrainProvisioning(currentTerrainState)
        }

        activityViewModel.activePlanningWorkflow.observe(lifecycleOwner) {
            renderMode(
                missionParamsUiState.operationMode,
                activityViewModel.pointCloudCoverage.value ?: PointCloudCoverage.NONE
            )
            renderTerrainProvisioning(currentTerrainState)
        }

        activityViewModel.pointCloudCoverage.observe(lifecycleOwner) { coverage ->
            renderMode(
                missionParamsUiState.operationMode,
                coverage ?: PointCloudCoverage.NONE,
            )
            renderTerrainProvisioning(currentTerrainState)
        }

        droneViewModel.conStateLiveData.observe(lifecycleOwner) {
            renderTerrainProvisioning(currentTerrainState)
        }
        activityViewModel.surveyPath.observe(lifecycleOwner) {
            renderTerrainProvisioning(currentTerrainState)
        }
        activityViewModel.plannedRoutePath.observe(lifecycleOwner) {
            renderTerrainProvisioning(currentTerrainState)
        }
        activityViewModel.routeWaypoints.observe(lifecycleOwner) {
            renderTerrainProvisioning(currentTerrainState)
        }
        activityViewModel.terrainRouteWaypoints.observe(lifecycleOwner) {
            renderTerrainProvisioning(currentTerrainState)
        }

        bindSurveyField(activityViewModel.surveyStripSpacing) { value ->
            missionParamsUiState = missionParamsUiState.copy(surveyStripSpacing = value)
            views.surveyStripSpacingValue.setText(value.toString())
        }
        bindSurveyField(activityViewModel.surveyHeightAboveTerrain) { value ->
            missionParamsUiState = missionParamsUiState.copy(surveyHeightAboveTerrain = value)
            views.surveyHeightValue.setText(value.toString())
        }
        bindSurveyField(activityViewModel.surveyOverlapPercent) { value ->
            missionParamsUiState = missionParamsUiState.copy(surveyOverlap = value)
            views.surveyOverlapValue.setText(value.toString())
        }
        bindSurveyField(activityViewModel.surveyGridAngle) { value ->
            missionParamsUiState = missionParamsUiState.copy(surveyGridAngle = value)
            views.surveyGridAngleValue.setText(value.toString())
        }
        activityViewModel.surveyTerrainSegment.observe(lifecycleOwner) { value ->
            val segment = value ?: 2.5
            missionParamsUiState = missionParamsUiState.copy(surveyTerrainSegment = segment)
            views.surveyTerrainSegmentValue.setText(formatDecimal(segment))
        }
        bindSurveyField(activityViewModel.surveyCanopySmoothing) { value ->
            missionParamsUiState = missionParamsUiState.copy(surveyCanopySmoothing = value)
            views.surveyCanopySmoothingValue.setText(value.toString())
        }
    }

    private fun renderTerrainProvisioning(state: TerrainProvisioningState?) {
        currentTerrainState = state ?: TerrainProvisioningState.Idle
        val current = if (usesFlightControllerTerrain()) {
            currentTerrainState
        } else {
            TerrainProvisioningState.Idle
        }
        val sourceReadyForCurrentMission = current is TerrainProvisioningState.SourceReady &&
            sourceReadyMatchesCurrentMission(current)
        val text = when (current) {
            TerrainProvisioningState.Idle -> null
            is TerrainProvisioningState.PreparingSource -> views.panelRoot.context.getString(
                R.string.terrain_preparing_tile,
                current.tileName ?: "…",
                current.completedTiles,
                current.totalTiles,
            )
            is TerrainProvisioningState.SourceReady -> if (sourceReadyForCurrentMission) {
                views.panelRoot.context.getString(R.string.terrain_downloaded, current.tileCount)
            } else {
                null
            }
            TerrainProvisioningState.WaitingForMissionUpload ->
                views.panelRoot.context.getString(R.string.terrain_waiting_for_upload)
            is TerrainProvisioningState.Serving -> if (current.pending != null) {
                views.panelRoot.context.getString(R.string.terrain_sending_blocks_pending, current.blocksSent, current.pending)
            } else {
                views.panelRoot.context.getString(R.string.terrain_sending_blocks, current.blocksSent)
            }
            is TerrainProvisioningState.Verifying -> views.panelRoot.context.getString(
                R.string.terrain_verifying_progress,
                current.checked,
                current.total,
            )
            is TerrainProvisioningState.Ready ->
                views.panelRoot.context.getString(R.string.terrain_ready_blocks, current.blocksSent)
            is TerrainProvisioningState.Failed -> views.panelRoot.context.getString(
                R.string.terrain_failed_detail,
                current.detail ?: current.failure.name,
            )
        }
        views.terrainProvisioningStatus?.isVisible = text != null
        views.terrainProvisioningStatus?.text = text.orEmpty()
        val progress = when (current) {
            is TerrainProvisioningState.PreparingSource -> current.progressPercent.coerceIn(0, 100)
            is TerrainProvisioningState.SourceReady -> if (sourceReadyForCurrentMission) 100 else null
            TerrainProvisioningState.WaitingForMissionUpload,
            is TerrainProvisioningState.Serving,
            is TerrainProvisioningState.Verifying,
            is TerrainProvisioningState.Ready -> 100
            TerrainProvisioningState.Idle,
            is TerrainProvisioningState.Failed -> null
        }
        views.terrainDownloadProgress?.isVisible = progress != null
        if (progress != null) views.terrainDownloadProgress?.progress = progress
        val downloadBusy = current is TerrainProvisioningState.PreparingSource
        // Offline terrain provisioning belongs to survey terrain missions only. Spray terrain
        // missions retain their rangefinder-backed behavior and must not offer an SRTM download.
        val showDownload = missionParamsUiState.operationMode == PlanningOperationMode.SURVEY &&
            usesFlightControllerTerrain() && droneViewModel.conStateLiveData.value != true &&
            !sourceReadyForCurrentMission
        views.terrainDownloadButton?.isVisible = showDownload
        views.terrainDownloadButton?.isEnabled = showDownload && !downloadBusy
        views.uploadMissionButton?.isEnabled = current is TerrainProvisioningState.Idle ||
            current is TerrainProvisioningState.SourceReady ||
            current is TerrainProvisioningState.Ready || current is TerrainProvisioningState.Failed
    }

    private fun sourceReadyMatchesCurrentMission(state: TerrainProvisioningState.SourceReady): Boolean {
        val path = currentTerrainPath()
        if (path.isEmpty()) return false
        val center = TerrainCoveragePlanner.missionCenter(path)
        return TerrainCoveragePlanner.distanceMeters(center, state.center) < 1.0 &&
            state.radiusMeters == TerrainCoveragePlanner.COVERAGE_RADIUS_METERS
    }

    private fun usesFlightControllerTerrain(): Boolean {
        val hasPointCloudProfile =
            activityViewModel.pointCloudCoverage.value == PointCloudCoverage.COMPLETE ||
                activityViewModel.terrainRouteWaypoints.value.orEmpty().isNotEmpty()
        return missionParamsUiState.operationMode == PlanningOperationMode.SURVEY &&
            !hasPointCloudProfile &&
            missionParamsUiState.altitudeReferenceMode == AltitudeReferenceMode.TERRAIN
    }

    private fun currentTerrainPath(): List<LatLon> {
        return when (activityViewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA) {
            PlanningWorkflow.AREA -> activityViewModel.surveyPath.value.orEmpty().map {
                LatLon(it.latitude, it.longitude)
            }
            PlanningWorkflow.POINTS -> activityViewModel.plannedRoutePath.value.orEmpty()
                .takeIf { it.size >= 2 }
                ?.map { LatLon(it.latitude, it.longitude) }
                ?: activityViewModel.routeWaypoints.value.orEmpty().map {
                    LatLon(it.latitude, it.longitude)
                }
        }
    }

    private fun renderFlightSummary(state: MissionParamsUiState) {
        views.flightSpeedValue.text = formatSpeed(state.flightSpeed)
        views.flightTimeValue.text = state.estimatedFlightMinutes.toString()
    }

    private fun formatSpeed(speed: Double): String {
        return if (speed % 1.0 == 0.0) {
            speed.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", speed)
        }
    }

    private fun bindAltitudeReferenceSelector() {
        listOf(views.altitudeReferenceRelativeButton, views.altitudeReferenceTerrainButton).forEach { button ->
            button.includeFontPadding = false
            button.gravity = Gravity.CENTER
            button.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
        }
        views.altitudeReferenceRelativeButton.setOnClickListener {
            activityViewModel.setAltitudeReferenceMode(AltitudeReferenceMode.RELATIVE)
        }
        views.altitudeReferenceTerrainButton.setOnClickListener {
            activityViewModel.setAltitudeReferenceMode(AltitudeReferenceMode.TERRAIN)
        }
    }

    private fun bindPresetSelector() {
        views.presetSelector.setOnClickListener {
            val presets = SprayPresets.all
            val labels = presets.map { it.label }.toTypedArray()
            val selectedId = activityViewModel.selectedSprayPresetId.value
            val selectedIndex = presets.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)

            AlertDialog.Builder(
                ContextThemeWrapper(views.panelRoot.context, R.style.Theme_DroneServicesApp_AlertDialog)
            )
                .setTitle(R.string.spray_preset_title)
                .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                    activityViewModel.applySprayPreset(presets[which].id)
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun renderPreset(presetId: String?) {
        views.presetSelector.text = SprayPresets.byId(presetId).label
    }

    private fun renderMode(mode: PlanningOperationMode, coverage: PointCloudCoverage) {
        val isSurvey = mode == PlanningOperationMode.SURVEY
        val isPointRoute = activityViewModel.activePlanningWorkflow.value ==
            com.example.droneservicesapp.domain.model.PlanningWorkflow.POINTS
        val isThreeDimensionalSpray = !isSurvey && coverage == PointCloudCoverage.COMPLETE
        val layoutSignature = Triple(isSurvey, isPointRoute, isThreeDimensionalSpray)

        // The status text may change from CHECKING to COMPLETE during a recalculation,
        // but that alone must not detach and reattach the terrain controls.
        renderPointCloudStatus(mode, coverage)
        if (layoutSignature == lastLayoutSignature) return
        lastLayoutSignature = layoutSignature

        arrangeParameterFields(isSurvey, isPointRoute)
        views.surveyModeSection.isVisible = isSurvey
        views.sprayModeSection.isVisible = !isSurvey
        views.presetSelector.isVisible = false
        views.altitudeReferenceSection.isVisible = isSurvey
        views.surveyModeSection.isVisible = isSurvey
        views.surveyGridSectionLabel.isVisible = isSurvey
        views.surveyGridSectionDivider.isVisible = isSurvey
        views.surveyStripSpacingField.isVisible = isSurvey
        views.surveyHeightField.isVisible = isSurvey
        views.surveyOverlapField.isVisible = isSurvey
        views.surveyGridAngleField.isVisible = isSurvey
        views.surveyTerrainSegmentField.isVisible = isThreeDimensionalSpray || isPointRoute
        views.surveyCanopySmoothingField.isVisible = isThreeDimensionalSpray || isPointRoute
        views.flightTimeLabel.isVisible = true
        views.speedTimeRow.isVisible = true
        views.flightTimeValue.isVisible = false
        views.flightTimeUnit.isVisible = false
        if (!isSurvey) {
            val requiredMode = if (isThreeDimensionalSpray) {
                AltitudeReferenceMode.RELATIVE
            } else {
                AltitudeReferenceMode.TERRAIN
            }
            if (activityViewModel.altitudeReferenceMode.value != requiredMode) {
                activityViewModel.setAltitudeReferenceMode(requiredMode)
            }
        }
    }

    private fun renderPointCloudStatus(
        mode: PlanningOperationMode,
        coverage: PointCloudCoverage,
    ) {
        if (mode == PlanningOperationMode.SURVEY) return
        val label = when (coverage) {
            PointCloudCoverage.CHECKING -> R.string.spray_mode_point_cloud_validating
            PointCloudCoverage.COMPLETE -> R.string.spray_mode_point_cloud_validated
            PointCloudCoverage.PARTIAL -> R.string.spray_mode_point_cloud_partial
            PointCloudCoverage.NONE -> R.string.spray_mode_terrain_rangefinder
        }
        views.sprayAltitudeModeStatus.setText(label)
        val color = when (coverage) {
            PointCloudCoverage.PARTIAL -> R.color.ds_color_shell_danger
            PointCloudCoverage.CHECKING -> R.color.ds_color_shell_warning
            PointCloudCoverage.NONE,
            PointCloudCoverage.COMPLETE -> R.color.ds_color_shell_active
        }
        views.sprayAltitudeModeStatus.setTextColor(
            ContextCompat.getColor(views.sprayAltitudeModeStatus.context, color)
        )
    }

    private fun arrangeParameterFields(isSurvey: Boolean, isPointRoute: Boolean) {
        val spraySection = views.sprayModeSection as ViewGroup
        val surveySection = views.surveyModeSection as ViewGroup

        val surveyFields = mutableListOf(
                views.surveyOverlapField,
                views.surveyStripSpacingField,
                views.surveyHeightField,
                views.surveyGridAngleField
            )
        if (isSurvey && isPointRoute) {
            surveyFields += views.surveyTerrainSegmentField
            surveyFields += views.surveyCanopySmoothingField
        }
        moveToEnd(surveySection, surveyFields)

        val sprayFields = mutableListOf(
            views.sprayStripSpacingField,
            views.sprayAltitudeField,
            views.sprayAngleField
        )
        if (!isSurvey) {
            sprayFields += views.flightTimeLabel
            sprayFields += views.speedTimeRow
        }
        sprayFields += views.sprayLitersField
        if (!isSurvey || !isPointRoute) {
            sprayFields += views.surveyTerrainSegmentField
            sprayFields += views.surveyCanopySmoothingField
        }
        moveToEnd(spraySection, sprayFields)

        if (isSurvey) {
            moveToEnd(surveySection, listOf(views.flightTimeLabel, views.speedTimeRow))
        }
    }

    private fun moveToEnd(parent: ViewGroup, children: List<View>) {
        val firstExpectedIndex = parent.childCount - children.size
        val alreadyInPlace = firstExpectedIndex >= 0 && children.withIndex().all { (index, child) ->
            child.parent === parent && parent.indexOfChild(child) == firstExpectedIndex + index
        }
        if (alreadyInPlace) return

        children.forEach { child ->
            (child.parent as? ViewGroup)?.removeView(child)
            parent.addView(child)
        }
    }

    private fun renderSurveyValues(state: MissionParamsUiState) {
        views.surveyStripSpacingValue.setText(state.surveyStripSpacing.toString())
        views.surveyHeightValue.setText(state.surveyHeightAboveTerrain.toString())
        views.surveyOverlapValue.setText(state.surveyOverlap.toString())
        views.surveyGridAngleValue.setText(state.surveyGridAngle.toString())
        views.surveyTerrainSegmentValue.setText(formatDecimal(state.surveyTerrainSegment))
        views.surveyCanopySmoothingValue.setText(state.surveyCanopySmoothing.toString())
    }

    private fun renderAltitudeReference(mode: AltitudeReferenceMode) {
        val selectedTextColor = if (views.panelRoot.resources.getBoolean(R.bool.config_tablet_planning_dock)) {
            R.color.ds_color_shell_active
        } else {
            R.color.ds_color_shell_selected_content
        }
        applyAltitudeReferenceButton(
            button = views.altitudeReferenceRelativeButton,
            selected = mode == AltitudeReferenceMode.RELATIVE,
            selectedTextColor = selectedTextColor
        )
        applyAltitudeReferenceButton(
            button = views.altitudeReferenceTerrainButton,
            selected = mode == AltitudeReferenceMode.TERRAIN,
            selectedTextColor = selectedTextColor
        )
        views.altitudeReferenceWarning.visibility = if (mode == AltitudeReferenceMode.TERRAIN) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun applyAltitudeReferenceButton(
        button: TextView,
        selected: Boolean,
        selectedTextColor: Int,
    ) {
        button.setBackgroundResource(
            if (selected) R.drawable.bg_ds_panel_pill_active else R.drawable.bg_ds_panel_pill_inactive
        )
        button.setTextColor(
            ContextCompat.getColor(
                views.panelRoot.context,
                if (selected) selectedTextColor else R.color.ds_color_text_primary
            )
        )
    }

    private fun bindSurveyField(
        liveData: androidx.lifecycle.LiveData<Double>,
        onChanged: (Int) -> Unit,
    ) {
        liveData.observe(lifecycleOwner) { value ->
            onChanged(value?.toInt() ?: 0)
        }
    }

    private fun formatDecimal(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }
}
