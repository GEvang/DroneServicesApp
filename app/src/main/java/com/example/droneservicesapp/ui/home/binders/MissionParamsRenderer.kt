package com.example.droneservicesapp.ui.home.binders

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.ui.home.model.MissionParamsUiState
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

class MissionParamsRenderer(
    private val views: MissionParamsViews,
    private val lifecycleOwner: LifecycleOwner,
    private val activityViewModel: MainActivityViewModel,
    private val stateMapper: MissionParamsStateMapper,
) {
    private var missionParamsUiState = MissionParamsUiState(
        angle = 1,
        lineDistance = 1,
        altitude = 2,
        sprayerIntensity = 0,
        flightSpeed = 1,
        estimatedFlightMinutes = 1,
        altitudeReferenceMode = AltitudeReferenceMode.RELATIVE
    )

    fun bind() {
        missionParamsUiState = stateMapper.currentUiState()
        renderFlightSummary(missionParamsUiState)
        bindAltitudeReferenceSelector()
        renderAltitudeReference(missionParamsUiState.altitudeReferenceMode)

        activityViewModel.flightSpeed.observe(lifecycleOwner) { flightSpeed ->
            missionParamsUiState = missionParamsUiState.copy(flightSpeed = flightSpeed.toInt())
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
    }

    private fun renderFlightSummary(state: MissionParamsUiState) {
        views.flightSpeedValue.text = state.flightSpeed.toString()
        views.flightTimeValue.text = state.estimatedFlightMinutes.toString()
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
}
