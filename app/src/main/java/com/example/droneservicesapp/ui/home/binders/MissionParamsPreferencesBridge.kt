package com.example.droneservicesapp.ui.home.binders

import android.content.Context
import androidx.preference.PreferenceManager
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.survey.SprayPresets
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

class MissionParamsPreferencesBridge(
    private val context: Context,
    private val activityViewModel: MainActivityViewModel,
) {
    fun loadIntoViewModel() {
        loadPreference(R.string.survey_angle_pref, activityViewModel.angleProgress, "0")
        loadPreference(
            R.string.survey_line_distance_pref,
            activityViewModel.lineDistanceProgress,
            "1"
        )
        loadPreference(R.string.survey_altitude_pref, activityViewModel.flightAltProgress, "2")
        loadAltitudeReferencePreference()
        loadPreference(
            R.string.survey_sprayer_intensity_pref,
            activityViewModel.sprayerProgress,
            "0"
        )
        loadPreference(R.string.flight_speed_pref, activityViewModel.flightSpeed, "1")
        loadSelectedPresetPreference()
        loadSurveyGridPreferences()
    }

    fun saveFromViewModel() {
        savePreference(
            R.string.survey_angle_pref,
            activityViewModel.angleProgress.value?.toInt() ?: 0
        )
        savePreference(
            R.string.survey_line_distance_pref,
            activityViewModel.lineDistanceProgress.value?.toInt() ?: 1
        )
        savePreference(
            R.string.survey_altitude_pref,
            activityViewModel.flightAltProgress.value?.toInt() ?: 2
        )
        saveAltitudeReferencePreference(
            activityViewModel.altitudeReferenceMode.value ?: AltitudeReferenceMode.RELATIVE
        )
        savePreference(
            R.string.survey_sprayer_intensity_pref,
            activityViewModel.sprayerProgress.value?.toInt() ?: 0
        )
        saveDoublePreference(R.string.flight_speed_pref, activityViewModel.flightSpeed.value ?: 1.0)
        saveSelectedPresetPreference()
        saveSurveyGridPreferences()
    }

    private fun loadPreference(
        stringResourceId: Int,
        target: androidx.lifecycle.MutableLiveData<Double>,
        defaultValue: String,
    ) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        target.value = prefs
            .getString(context.getString(stringResourceId), defaultValue)
            ?.toDouble()
    }

    private fun loadAltitudeReferencePreference() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val storedValue = prefs.getString(
            context.getString(R.string.survey_altitude_reference_pref),
            AltitudeReferenceMode.RELATIVE.name
        )
        activityViewModel.setAltitudeReferenceMode(
            AltitudeReferenceMode.fromStorageValue(storedValue)
        )
    }

    private fun savePreference(stringResourceId: Int, value: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        prefs.edit()
            .putString(context.getString(stringResourceId), value.toString())
            .apply()
    }

    private fun saveDoublePreference(stringResourceId: Int, value: Double) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        prefs.edit()
            .putString(context.getString(stringResourceId), value.toString())
            .apply()
    }

    private fun saveAltitudeReferencePreference(mode: AltitudeReferenceMode) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        prefs.edit()
            .putString(context.getString(R.string.survey_altitude_reference_pref), mode.name)
            .apply()
    }

    private fun loadSelectedPresetPreference() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        activityViewModel.selectedSprayPresetId.value = SprayPresets.byId(
            prefs.getString(context.getString(R.string.survey_spray_preset_pref), SprayPresets.CUSTOM_ID)
        ).id
    }

    private fun saveSelectedPresetPreference() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        prefs.edit()
            .putString(
                context.getString(R.string.survey_spray_preset_pref),
                SprayPresets.byId(activityViewModel.selectedSprayPresetId.value).id
            )
            .apply()
    }

    private fun loadSurveyGridPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        activityViewModel.updateSurveyStripSpacing(
            prefs.getString(context.getString(R.string.survey_strip_spacing_pref), "8")
                ?.toIntOrNull() ?: 8
        )
        activityViewModel.updateSurveyHeightAboveTerrain(
            prefs.getString(context.getString(R.string.survey_height_above_terrain_pref), "5")
                ?.toIntOrNull() ?: 5
        )
        activityViewModel.updateSurveyOverlap(
            prefs.getString(context.getString(R.string.survey_overlap_pref), "20")
                ?.toIntOrNull() ?: 20
        )
        activityViewModel.updateSurveyGridAngle(
            prefs.getString(context.getString(R.string.survey_grid_angle_pref), "0")
                ?.toIntOrNull() ?: 0
        )
        activityViewModel.updateSurveyTerrainSegment(
            prefs.getString(context.getString(R.string.survey_terrain_segment_pref), "2.5")
                ?.toDoubleOrNull() ?: 2.5
        )
        activityViewModel.updateSurveyCanopySmoothing(
            prefs.getString(context.getString(R.string.survey_canopy_smoothing_pref), "5")
                ?.toIntOrNull() ?: 5
        )
    }

    private fun saveSurveyGridPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        prefs.edit()
            .putString(
                context.getString(R.string.survey_strip_spacing_pref),
                (activityViewModel.surveyStripSpacing.value?.toInt() ?: 8).toString()
            )
            .putString(
                context.getString(R.string.survey_height_above_terrain_pref),
                (activityViewModel.surveyHeightAboveTerrain.value?.toInt() ?: 5).toString()
            )
            .putString(
                context.getString(R.string.survey_overlap_pref),
                (activityViewModel.surveyOverlapPercent.value?.toInt() ?: 20).toString()
            )
            .putString(
                context.getString(R.string.survey_grid_angle_pref),
                (activityViewModel.surveyGridAngle.value?.toInt() ?: 0).toString()
            )
            .putString(
                context.getString(R.string.survey_terrain_segment_pref),
                (activityViewModel.surveyTerrainSegment.value ?: 2.5).toString()
            )
            .putString(
                context.getString(R.string.survey_canopy_smoothing_pref),
                (activityViewModel.surveyCanopySmoothing.value?.toInt() ?: 5).toString()
            )
            .apply()
    }
}
