package com.example.droneservicesapp.data.terrain

import android.content.Context

/** Persistent engineering options exposed only through the protected debug screen. */
object TerrainDebugOptions {
    private const val PREFERENCES_NAME = "debug_access"
    private const val KEY_DISABLE_COVERAGE_SAFEGUARD = "disable_terrain_coverage_safeguard"

    fun isCoverageSafeguardDisabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DISABLE_COVERAGE_SAFEGUARD, false)

    fun setCoverageSafeguardDisabled(context: Context, disabled: Boolean) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DISABLE_COVERAGE_SAFEGUARD, disabled)
            .apply()
    }
}
