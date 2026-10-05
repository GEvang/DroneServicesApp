package com.example.droneservicesapp.ui.home.components

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.example.droneservicesapp.ui.home.components.OsmdroidObstacleEditor.Mode

/** Owns workflow selection controls and their presentation state. */
class PlanningWorkflowUiController(
    private val context: Context,
    private val root: View,
    private val viewModel: MainActivityViewModel,
) {
    fun bind(lifecycleOwner: LifecycleOwner, onWorkflowChanged: (PlanningWorkflow) -> Unit) {
        val areaButton = root.findViewById<TextView>(R.id.right_panel_area_button)
        val pointsButton = root.findViewById<TextView>(R.id.right_panel_points_button)
        listOf(areaButton, pointsButton).forEach { button ->
            button.includeFontPadding = false
            button.gravity = Gravity.CENTER
        }
        areaButton.setOnClickListener { select(PlanningWorkflow.AREA) }
        pointsButton.setOnClickListener { select(PlanningWorkflow.POINTS) }
        viewModel.activePlanningWorkflow.observe(lifecycleOwner) { workflow ->
            render(workflow)
            onWorkflowChanged(workflow)
        }
    }

    fun render(workflow: PlanningWorkflow) {
        val areaButton = root.findViewById<TextView>(R.id.right_panel_area_button)
        val pointsButton = root.findViewById<TextView>(R.id.right_panel_points_button)
        val drawButton = root.findViewById<TextView>(R.id.right_panel_draw_area_button)
        val isPoints = workflow == PlanningWorkflow.POINTS
        val selectedTextColor = if (context.resources.getBoolean(R.bool.config_tablet_planning_dock)) {
            R.color.ds_color_shell_active
        } else {
            R.color.ds_color_shell_selected_content
        }
        style(areaButton, !isPoints, selectedTextColor)
        style(pointsButton, isPoints, selectedTextColor)
        areaButton.setText(R.string.area)
        areaButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_draw_area_24, 0, 0, 0)
        pointsButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_points_24, 0, 0, 0)
        listOf(areaButton, pointsButton).forEach {
            it.compoundDrawablePadding = context.resources.getDimensionPixelSize(R.dimen.ds_space_sm)
            it.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        tintIcon(areaButton, !isPoints, selectedTextColor)
        tintIcon(pointsButton, isPoints, selectedTextColor)
        drawButton.visibility = View.VISIBLE
        root.findViewById<View?>(R.id.right_panel_add_obstacle_button)?.visibility = View.VISIBLE
        root.findViewById<View?>(R.id.right_panel_obstacle_mode_row)?.visibility = View.VISIBLE
        root.findViewById<View?>(R.id.right_panel_obstacle_radius_label)?.visibility = View.VISIBLE
        root.findViewById<View?>(R.id.right_panel_obstacle_radius_seekbar)?.visibility = View.VISIBLE
        root.findViewById<View?>(R.id.right_panel_clear_obstacles_button)?.visibility = View.VISIBLE
        root.findViewById<View?>(R.id.right_panel_route_summary)?.visibility = if (isPoints) View.VISIBLE else View.GONE
        updateGeometryActionState()
    }

    fun updateGeometryActionState() {
        val workflow = viewModel.activePlanningWorkflow.value ?: PlanningWorkflow.AREA
        val hasGeometry = when (workflow) {
            PlanningWorkflow.AREA -> viewModel.missionArea.value?.vertices.orEmpty().isNotEmpty() ||
                viewModel.surveyPath.value.orEmpty().isNotEmpty()
            PlanningWorkflow.POINTS -> viewModel.routeWaypoints.value.orEmpty().isNotEmpty() ||
                viewModel.plannedRoutePath.value.orEmpty().isNotEmpty() ||
                viewModel.terrainRouteWaypoints.value.orEmpty().isNotEmpty()
        }
        root.findViewById<TextView?>(R.id.right_panel_draw_area_button)?.setText(
            when (workflow) {
                PlanningWorkflow.AREA -> if (hasGeometry) R.string.redraw_area else R.string.draw_area
                PlanningWorkflow.POINTS -> if (hasGeometry) R.string.redraw_route else R.string.draw_route
            }
        )
        root.findViewById<View?>(R.id.right_panel_clear_area_button)?.apply {
            isEnabled = hasGeometry
            alpha = if (hasGeometry) 1f else 0.45f
        }
    }

    fun renderObstacleControls(mode: Mode, placementActive: Boolean, radiusMeters: Int) {
        val circle = root.findViewById<TextView?>(R.id.right_panel_obstacle_circle_button) ?: return
        val polygon = root.findViewById<TextView?>(R.id.right_panel_obstacle_polygon_button) ?: return
        val add = root.findViewById<TextView?>(R.id.right_panel_add_obstacle_button)
        val radiusLabel = root.findViewById<TextView?>(R.id.right_panel_obstacle_radius_label)
        val radiusSeekbar = root.findViewById<SeekBar?>(R.id.right_panel_obstacle_radius_seekbar)
        val polygonSelected = mode == Mode.POLYGON
        styleObstacleButton(circle, !polygonSelected)
        styleObstacleButton(polygon, polygonSelected)
        val radius = radiusMeters.coerceIn(2, 100)
        radiusLabel?.text = context.getString(R.string.obstacle_radius_format, radius)
        radiusLabel?.visibility = if (polygonSelected) View.GONE else View.VISIBLE
        radiusSeekbar?.visibility = if (polygonSelected) View.GONE else View.VISIBLE
        if (radiusSeekbar != null && radiusSeekbar.progress != radius - 2) radiusSeekbar.progress = radius - 2
        add?.text = context.getString(
            if (polygonSelected && placementActive) R.string.finish_forbidden_polygon
            else R.string.add_forbidden_area
        )
    }

    private fun select(workflow: PlanningWorkflow) {
        viewModel.setPlanningWorkflow(workflow)
        viewModel.mapState.value = MainActivityViewModel.MapState.Idle
    }

    private fun style(button: TextView, selected: Boolean, selectedTextColor: Int) {
        button.setBackgroundResource(if (selected) R.drawable.bg_ds_panel_pill_active else R.drawable.bg_ds_panel_pill_inactive)
        button.setTextColor(ContextCompat.getColor(context, if (selected) selectedTextColor else R.color.ds_color_text_primary))
        button.setTypeface(button.typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
        button.includeFontPadding = false
    }

    private fun tintIcon(button: TextView, selected: Boolean, selectedTextColor: Int) {
        val color = ContextCompat.getColor(context, if (selected) selectedTextColor else R.color.ds_color_text_primary)
        button.compoundDrawables.forEach { drawable -> drawable?.mutate()?.setTint(color) }
    }

    private fun styleObstacleButton(button: TextView, selected: Boolean) {
        button.setBackgroundResource(if (selected) R.drawable.bg_ds_panel_pill_active else R.drawable.bg_ds_panel_pill_inactive)
        button.setTextColor(ContextCompat.getColor(
            context,
            if (selected) R.color.ds_color_shell_selected_content else R.color.ds_color_text_primary,
        ))
        button.setTypeface(button.typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
    }
}
