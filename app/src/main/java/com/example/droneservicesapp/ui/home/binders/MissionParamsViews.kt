package com.example.droneservicesapp.ui.home.binders

import android.view.View
import android.widget.LinearLayout
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import com.example.droneservicesapp.R

class MissionParamsViews(
    rootView: View,
) {
    val panelRoot: View = rootView.findViewById(R.id.mission_params_side_view)
    val sprayModeSection: View = panelRoot.findViewById(R.id.spray_mode_section)
    val surveyModeSection: View = panelRoot.findViewById(R.id.survey_mode_section)

    val altitudeReferenceRelativeButton: TextView =
        panelRoot.findViewById(R.id.altitude_reference_relative_button)
    val altitudeReferenceTerrainButton: TextView =
        panelRoot.findViewById(R.id.altitude_reference_terrain_button)
    val altitudeReferenceWarning: TextView =
        panelRoot.findViewById(R.id.altitude_reference_warning)

    val presetSelector: TextView = rootView.findViewById(R.id.spray_preset_selector)

    val surveyStripSpacingValue: EditText = rootView.findViewById(R.id.survey_strip_spacing_value)
    val surveyStripSpacingSeekbar: SeekBar = rootView.findViewById(R.id.survey_strip_spacing_seekbar)
    val surveyStripSpacingSliderRow: LinearLayout = rootView.findViewById(R.id.survey_strip_spacing_slider_row)
    val surveyStripSpacingMinusButton: View = panelRoot.findViewById(R.id.btn_survey_strip_spacing_minus)
    val surveyStripSpacingPlusButton: View = panelRoot.findViewById(R.id.btn_survey_strip_spacing_plus)

    val surveyHeightValue: EditText = rootView.findViewById(R.id.survey_height_value)
    val surveyHeightSeekbar: SeekBar = rootView.findViewById(R.id.survey_height_seekbar)
    val surveyHeightSliderRow: LinearLayout = rootView.findViewById(R.id.survey_height_slider_row)
    val surveyHeightMinusButton: View = panelRoot.findViewById(R.id.btn_survey_height_minus)
    val surveyHeightPlusButton: View = panelRoot.findViewById(R.id.btn_survey_height_plus)

    val surveyOverlapValue: EditText = rootView.findViewById(R.id.survey_overlap_value)
    val surveyOverlapSeekbar: SeekBar = rootView.findViewById(R.id.survey_overlap_seekbar)
    val surveyOverlapSliderRow: LinearLayout = rootView.findViewById(R.id.survey_overlap_slider_row)
    val surveyOverlapMinusButton: View = panelRoot.findViewById(R.id.btn_survey_overlap_minus)
    val surveyOverlapPlusButton: View = panelRoot.findViewById(R.id.btn_survey_overlap_plus)

    val surveyGridAngleValue: EditText = rootView.findViewById(R.id.survey_grid_angle_value)
    val surveyGridAngleSeekbar: SeekBar = rootView.findViewById(R.id.survey_grid_angle_seekbar)
    val surveyGridAngleSliderRow: LinearLayout = rootView.findViewById(R.id.survey_grid_angle_slider_row)
    val surveyGridAngleMinusButton: View = panelRoot.findViewById(R.id.btn_survey_grid_angle_minus)
    val surveyGridAnglePlusButton: View = panelRoot.findViewById(R.id.btn_survey_grid_angle_plus)

    val surveyTerrainSegmentValue: EditText = rootView.findViewById(R.id.survey_terrain_segment_value)
    val surveyTerrainSegmentSeekbar: SeekBar = rootView.findViewById(R.id.survey_terrain_segment_seekbar)
    val surveyTerrainSegmentSliderRow: LinearLayout = rootView.findViewById(R.id.survey_terrain_segment_slider_row)
    val surveyTerrainSegmentMinusButton: View = panelRoot.findViewById(R.id.btn_survey_terrain_segment_minus)
    val surveyTerrainSegmentPlusButton: View = panelRoot.findViewById(R.id.btn_survey_terrain_segment_plus)

    val surveyCanopySmoothingValue: EditText = rootView.findViewById(R.id.survey_canopy_smoothing_value)
    val surveyCanopySmoothingSeekbar: SeekBar = rootView.findViewById(R.id.survey_canopy_smoothing_seekbar)
    val surveyCanopySmoothingSliderRow: LinearLayout = rootView.findViewById(R.id.survey_canopy_smoothing_slider_row)
    val surveyCanopySmoothingMinusButton: View = panelRoot.findViewById(R.id.btn_survey_canopy_smoothing_minus)
    val surveyCanopySmoothingPlusButton: View = panelRoot.findViewById(R.id.btn_survey_canopy_smoothing_plus)

    val angleValue: EditText = rootView.findViewById(R.id.line_angle_value)
    val angleSeekbar: SeekBar = rootView.findViewById(R.id.line_angle_seekbar)
    val angleSliderRow: LinearLayout = rootView.findViewById(R.id.angle_slider_row)
    val angleMinusButton: View = panelRoot.findViewById(R.id.btn_angle_minus)
    val anglePlusButton: View = panelRoot.findViewById(R.id.btn_angle_plus)

    val lineDistanceValue: EditText = rootView.findViewById(R.id.line_distance_value)
    val lineDistanceSeekbar: SeekBar = rootView.findViewById(R.id.line_distance_seekbar)
    val lineDistanceSliderRow: LinearLayout = rootView.findViewById(R.id.line_distance_slider_row)
    val lineDistanceMinusButton: View = panelRoot.findViewById(R.id.btn_distance_minus)
    val lineDistancePlusButton: View = panelRoot.findViewById(R.id.btn_distance_plus)

    val altitudeValue: EditText = rootView.findViewById(R.id.altitude_value)
    val altitudeSeekbar: SeekBar = rootView.findViewById(R.id.altitude_seekbar)
    val altitudeSliderRow: LinearLayout = rootView.findViewById(R.id.altitude_slider_row)
    val altitudeMinusButton: View = panelRoot.findViewById(R.id.btn_alt_minus)
    val altitudePlusButton: View = panelRoot.findViewById(R.id.btn_alt_plus)

    val sprayerValue: EditText = rootView.findViewById(R.id.sprayer_seekbar_value)
    val sprayerSeekbar: SeekBar = rootView.findViewById(R.id.sprayer_seekbar)
    val sprayerSliderRow: LinearLayout = rootView.findViewById(R.id.sprayer_slider_row)
    val sprayerMinusButton: View = panelRoot.findViewById(R.id.btn_sprayer_minus)
    val sprayerPlusButton: View = panelRoot.findViewById(R.id.btn_sprayer_plus)

    val flightSpeedValue: TextView = rootView.findViewById(R.id.flight_speed)
    val flightTimeValue: TextView = rootView.findViewById(R.id.flight_time)
    val speedMinusButton: ImageButton = rootView.findViewById(R.id.minus_button)
    val speedPlusButton: ImageButton = rootView.findViewById(R.id.plus_button)
    val flightTimeLabel: TextView = rootView.findViewById(R.id.flight_time_label)
    val speedTimeRow: View = rootView.findViewById(R.id.speed_time_row)

    val uploadMissionButton: Button = rootView.findViewById(R.id.uploadMission)
    val saveMissionButton: Button = rootView.findViewById(R.id.save_mission)
}
