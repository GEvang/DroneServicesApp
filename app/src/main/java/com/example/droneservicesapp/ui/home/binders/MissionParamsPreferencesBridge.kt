package com.example.droneservicesapp.ui.home.binders

import android.content.Context
import androidx.preference.PreferenceManager
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
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
        savePreference(
            R.string.flight_speed_pref,
            activityViewModel.flightSpeed.value?.toInt() ?: 1
        )
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

    private fun saveAltitudeReferencePreference(mode: AltitudeReferenceMode) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        prefs.edit()
            .putString(context.getString(R.string.survey_altitude_reference_pref), mode.name)
            .apply()
    }
}
