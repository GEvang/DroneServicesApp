package com.example.droneservicesapp.ui.home.components

import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.droneservicesapp.R
import com.example.droneservicesapp.databinding.FragmentHomeMapsBinding
import com.example.droneservicesapp.ui.preview.PreviewMode
import com.google.android.material.button.MaterialButton

/** Installs map-screen click and value listeners without owning mission behavior. */
class MissionMapUiActionController(
    private val root: View,
    private val binding: FragmentHomeMapsBinding,
) {
    data class Actions(
        val downloadOffline: () -> Unit,
        val centerOnUser: () -> Unit,
        val centerOnDrone: () -> Unit,
        val toggleObstacles: () -> Unit,
        val startDroneOffset: () -> Unit,
        val cyclePreviewMode: () -> Unit,
        val openSettings: () -> Unit,
        val togglePlanning: () -> Unit,
        val loadMission: () -> Unit,
        val addHome: () -> Unit,
        val simulate: () -> Unit,
        val resumeMission: () -> Unit,
        val closePanel: () -> Unit,
        val acceptGeometry: () -> Unit,
        val declineGeometry: () -> Unit,
        val drawGeometry: () -> Unit,
        val clearGeometry: () -> Unit,
        val addObstacle: () -> Unit,
        val clearObstacles: () -> Unit,
        val selectCircleObstacle: () -> Unit,
        val selectPolygonObstacle: () -> Unit,
        val obstacleRadiusChanged: (Int) -> Unit,
        val deleteWaypoint: () -> Unit,
        val cancelWaypoint: () -> Unit,
        val decreaseWaypointHeight: () -> Unit,
        val increaseWaypointHeight: () -> Unit,
        val applyWaypointHeight: () -> Unit,
    )

    data class PreviewActions(
        val selectMode: (PreviewMode) -> Unit,
        val primary: () -> Unit,
        val secondary: () -> Unit,
        val toggleHeightColors: () -> Unit,
        val backgroundChanged: (Boolean) -> Unit,
        val opacityChanged: (Float) -> Unit,
    )

    fun bindChrome(
        chromeBinder: com.example.droneservicesapp.ui.home.binders.HomeMapChromeBinder,
        actions: Actions,
        initialObstacleRadiusMeters: Int,
    ) {
        chromeBinder.bindActions(
            onDownloadOffline = actions.downloadOffline,
            onCenterOnUser = actions.centerOnUser,
            onCenterOnDrone = actions.centerOnDrone,
            onToggleObstacles = actions.toggleObstacles,
            onStartDroneOffset = actions.startDroneOffset,
            onCyclePreviewMode = actions.cyclePreviewMode,
            onOpenSettings = actions.openSettings,
            onTogglePlanning = actions.togglePlanning,
        )
        root.findViewById<MaterialButton>(R.id.right_panel_load_button).setOnClickListener { actions.loadMission() }
        root.findViewById<MaterialButton?>(R.id.right_panel_add_home_button)?.setOnClickListener { actions.addHome() }
        root.findViewById<MaterialButton?>(R.id.right_panel_simulate_button)?.setOnClickListener { actions.simulate() }
        root.findViewById<MaterialButton?>(R.id.right_panel_resume_mission_button)?.setOnClickListener { actions.resumeMission() }
        root.findViewById<MaterialButton?>(R.id.right_panel_close_button)?.setOnClickListener { actions.closePanel() }
        binding.homeDrawAcceptButton.setOnClickListener { actions.acceptGeometry() }
        binding.homeDrawDeclineButton.setOnClickListener { actions.declineGeometry() }
        root.findViewById<TextView>(R.id.right_panel_draw_area_button).setOnClickListener { actions.drawGeometry() }
        root.findViewById<TextView>(R.id.right_panel_clear_area_button).setOnClickListener { actions.clearGeometry() }
        root.findViewById<View?>(R.id.right_panel_add_obstacle_button)?.setOnClickListener { actions.addObstacle() }
        root.findViewById<View?>(R.id.right_panel_clear_obstacles_button)?.setOnClickListener { actions.clearObstacles() }
        root.findViewById<TextView?>(R.id.right_panel_obstacle_circle_button)?.setOnClickListener { actions.selectCircleObstacle() }
        root.findViewById<TextView?>(R.id.right_panel_obstacle_polygon_button)?.setOnClickListener { actions.selectPolygonObstacle() }
        root.findViewById<SeekBar?>(R.id.right_panel_obstacle_radius_seekbar)?.apply {
            progress = (initialObstacleRadiusMeters - 2).coerceIn(0, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) actions.obstacleRadiusChanged(progress + 2)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        binding.surveyWaypointDeleteButton.setOnClickListener { actions.deleteWaypoint() }
        binding.surveyWaypointCancelButton.setOnClickListener { actions.cancelWaypoint() }
        binding.surveyWaypointHeightMinusButton.setOnClickListener { actions.decreaseWaypointHeight() }
        binding.surveyWaypointHeightPlusButton.setOnClickListener { actions.increaseWaypointHeight() }
        binding.surveyWaypointHeightLabel.setOnEditorActionListener { _, _, _ ->
            actions.applyWaypointHeight()
            binding.surveyWaypointHeightLabel.clearFocus()
            true
        }
        binding.surveyWaypointHeightLabel.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) actions.applyWaypointHeight()
        }
    }

    fun bindPreview(actions: PreviewActions) {
        binding.previewModeMapButton.setOnClickListener { actions.selectMode(PreviewMode.MAP) }
        binding.previewModeOrthoButton.setOnClickListener { actions.selectMode(PreviewMode.ORTHO) }
        binding.previewMode3dButton.setOnClickListener { actions.selectMode(PreviewMode.POINT_CLOUD) }
        binding.previewAssetPrimaryButton.setOnClickListener { actions.primary() }
        binding.previewAssetSecondaryButton.setOnClickListener { actions.secondary() }
        binding.previewAssetTertiaryButton.setOnClickListener { }
        binding.previewColorModeButton.setOnClickListener { actions.toggleHeightColors() }
        binding.previewBackgroundSwitch.setOnCheckedChangeListener { _, checked -> actions.backgroundChanged(checked) }
        binding.previewOpacitySlider.addOnChangeListener { _, value, _ -> actions.opacityChanged(value) }
    }

    fun applyMapInsets() {
        val dockMargin = (binding.homeBottomUtilityDock.layoutParams as MarginLayoutParams).bottomMargin
        val drawMargin = (binding.homeDrawActionBar.layoutParams as MarginLayoutParams).bottomMargin
        val labelMargin = (binding.homeBottomPlanningLabel.layoutParams as MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bottom = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            ).bottom
            updateBottomMargin(binding.homeBottomUtilityDock, dockMargin + bottom)
            updateBottomMargin(binding.homeDrawActionBar, drawMargin + bottom)
            updateBottomMargin(binding.homeBottomPlanningLabel, labelMargin + bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    fun setDockButtonSelected(buttonId: Int, selected: Boolean) {
        val button = root.findViewById<View?>(buttonId) ?: return
        button.isSelected = selected
        val color = ContextCompat.getColor(
            root.context,
            if (selected) R.color.ds_color_shell_active else R.color.ds_color_shell_unselected,
        )
        when (button) {
            is ImageView -> button.setColorFilter(color)
            is ViewGroup -> for (index in 0 until button.childCount) {
                when (val child = button.getChildAt(index)) {
                    is ImageView -> child.setColorFilter(color)
                    is TextView -> child.setTextColor(color)
                }
            }
        }
    }

    private fun updateBottomMargin(view: View, marginBottom: Int) {
        val params = view.layoutParams as? MarginLayoutParams ?: return
        if (params.bottomMargin == marginBottom) return
        params.bottomMargin = marginBottom
        view.layoutParams = params
    }
}
