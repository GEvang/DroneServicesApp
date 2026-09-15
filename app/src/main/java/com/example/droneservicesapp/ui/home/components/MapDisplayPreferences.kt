package com.example.droneservicesapp.ui.home.components

import android.content.Context
import androidx.preference.PreferenceManager

object MapDisplayPreferences {
    const val LABELS_ENABLED_KEY = "map_labels_enabled"
    const val LABELS_ENABLED_DEFAULT = true

    fun areLabelsEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .getBoolean(LABELS_ENABLED_KEY, LABELS_ENABLED_DEFAULT)
}
