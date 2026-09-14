package com.example.droneservicesapp.ui.shell.binders

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.R
import com.example.droneservicesapp.databinding.ActivityMainBinding
import com.example.droneservicesapp.ui.home.binders.HomeMapTelemetryBinder
import com.example.droneservicesapp.ui.home.model.HomeTelemetryUiState
import com.example.droneservicesapp.ui.home.model.HomeTelemetryViewModel

/** Uses the same current telemetry strip on More screens as the map screen. */
class ShellToolbarBinder(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val homeTelemetryViewModel: HomeTelemetryViewModel,
) {
    private val toolbar by lazy { binding.appBarMain.customToolbar }
    private val telemetryBinder by lazy { HomeMapTelemetryBinder(toolbar) }
    private var latestState: HomeTelemetryUiState? = null

    fun bind(lifecycleOwner: LifecycleOwner) {
        activity.setSupportActionBar(toolbar)
        toolbar.findViewById<View?>(R.id.top_live_geo_card)?.visibility = View.GONE
        val initial = homeTelemetryViewModel.homeTelemetryUiState.value ?: HomeTelemetryUiState(
            connectionText = activity.getString(R.string.shell_status_disconnected),
            armedText = activity.getString(R.string.disarmed),
        )
        render(initial)
        homeTelemetryViewModel.homeTelemetryUiState.observe(lifecycleOwner) { state ->
            latestState = state
            if (toolbar.visibility == View.VISIBLE) render(state)
        }
    }

    fun renderLatest() {
        if (toolbar.visibility != View.VISIBLE) return
        render(latestState ?: homeTelemetryViewModel.homeTelemetryUiState.value ?: return)
    }

    private fun render(state: HomeTelemetryUiState) {
        telemetryBinder.render(state)
        // The controls themselves live on the map; More shows their live state only.
        toolbar.findViewById<View?>(R.id.top_armed_card)?.isClickable = false
        toolbar.findViewById<View?>(R.id.top_flight_mode_card)?.isClickable = false
    }
}
