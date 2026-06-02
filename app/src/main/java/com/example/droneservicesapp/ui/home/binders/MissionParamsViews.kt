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

    val altitudeReferenceRelativeButton: TextView =
        panelRoot.findViewById(R.id.altitude_reference_relative_button)
    val altitudeReferenceTerrainButton: TextView =
        panelRoot.findViewById(R.id.altitude_reference_terrain_button)
    val altitudeReferenceWarning: TextView =
        panelRoot.findViewById(R.id.altitude_reference_warning)

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

    val uploadMissionButton: Button = rootView.findViewById(R.id.uploadMission)
    val saveMissionButton: Button = rootView.findViewById(R.id.save_mission)
}
