package com.example.droneservicesapp.ui.home.binders

import android.graphics.Typeface
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.survey.SprayPresets
import com.example.droneservicesapp.ui.home.model.MissionParamsUiState
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import java.util.Locale

class MissionParamsRenderer(
    private val views: MissionParamsViews,
    private val lifecycleOwner: LifecycleOwner,
    private val activityViewModel: MainActivityViewModel,
    private val stateMapper: MissionParamsStateMapper,
) {
    private var missionParamsUiState = MissionParamsUiState(
        operationMode = PlanningOperationMode.SURVEY,
        angle = 1,
        lineDistance = 1,
        altitude = 2,
        sprayerIntensity = 0,
        surveyStripSpacing = 8,
        surveyHeightAboveTerrain = 5,
        surveyOverlap = 20,
        surveyGridAngle = 0,
        surveyTerrainSegment = 2.5,
        surveyCanopySmoothing = 5,
        flightSpeed = 1.0,
        estimatedFlightMinutes = 1,
        altitudeReferenceMode = AltitudeReferenceMode.RELATIVE
    )

    fun bind() {
        missionParamsUiState = stateMapper.currentUiState()
        renderFlightSummary(missionParamsUiState)
        renderPreset(activityViewModel.selectedSprayPresetId.value)
        renderMode(
            missionParamsUiState.operationMode,
            activityViewModel.pointCloudCoversMissionArea.value == true
        )
        renderSurveyValues(missionParamsUiState)
        bindPresetSelector()
        bindAltitudeReferenceSelector()
        renderAltitudeReference(missionParamsUiState.altitudeReferenceMode)

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
        }

        activityViewModel.selectedSprayPresetId.observe(lifecycleOwner) { presetId ->
            renderPreset(presetId)
        }

        activityViewModel.planningOperationMode.observe(lifecycleOwner) { mode ->
            val operationMode = mode ?: PlanningOperationMode.SURVEY
            missionParamsUiState = missionParamsUiState.copy(operationMode = operationMode)
            renderMode(operationMode, activityViewModel.pointCloudCoversMissionArea.value == true)
        }

        activityViewModel.pointCloudCoversMissionArea.observe(lifecycleOwner) { covered ->
            renderMode(missionParamsUiState.operationMode, covered == true)
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

    private fun renderMode(mode: PlanningOperationMode, hasPointCloudInArea: Boolean) {
        val isSurvey = mode == PlanningOperationMode.SURVEY
        val isThreeDimensionalSpray = !isSurvey && hasPointCloudInArea
        views.surveyModeSection.isVisible = isSurvey
        views.sprayModeSection.isVisible = !isSurvey
        views.presetSelector.isVisible = false
        views.altitudeReferenceSection.isVisible = isSurvey
        views.surveyModeSection.isVisible = isSurvey || isThreeDimensionalSpray
        views.surveyGridSectionLabel.isVisible = isSurvey
        views.surveyGridSectionDivider.isVisible = isSurvey
        views.surveyStripSpacingField.isVisible = isSurvey
        views.surveyHeightField.isVisible = isSurvey
        views.surveyOverlapField.isVisible = isSurvey
        views.surveyGridAngleField.isVisible = isSurvey
        views.surveyTerrainSegmentField.isVisible = isThreeDimensionalSpray
        views.surveyCanopySmoothingField.isVisible = isThreeDimensionalSpray
        views.flightTimeLabel.isVisible = true
        views.speedTimeRow.isVisible = true
        views.flightTimeValue.isVisible = false
        views.flightTimeUnit.isVisible = false
        views.sprayAltitudeModeStatus.setText(
            if (isThreeDimensionalSpray) R.string.spray_mode_relative_point_cloud
            else R.string.spray_mode_terrain_rangefinder
        )

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
