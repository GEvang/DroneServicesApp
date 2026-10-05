package com.example.droneservicesapp.ui.preview

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.pointcloud.PointCloudData
import com.example.droneservicesapp.databinding.FragmentHomeMapsBinding

enum class PreviewMode { MAP, ORTHO, POINT_CLOUD }

/** Applies preview-mode view state; feature data and navigation remain with the Fragment. */
class PreviewModeRenderer(
    private val context: Context,
    private val binding: FragmentHomeMapsBinding,
    private val assetsViewModel: PreviewAssetsViewModel,
    private val renderMapPath: () -> Unit,
    private val renderOrtho: () -> Unit,
    private val clearOrtho: () -> Unit,
    private val renderTerrainStatus: () -> Unit,
    private val renderMissionOverlay: () -> Unit,
    private val updateDockPlacement: () -> Unit,
) {
    fun render(mode: PreviewMode, surveyMode: Boolean, pointCloud: PointCloudData?) {
        renderModeButtons(mode, surveyMode)
        binding.previewAssetPrimaryRow.visibility = View.GONE
        binding.previewAssetSecondaryRow.visibility = View.GONE
        binding.previewAssetTertiaryRow.visibility = View.GONE
        binding.previewColorModeRow.visibility = View.GONE
        binding.previewBackgroundRow.visibility = View.GONE
        binding.previewOpacityRow.visibility = View.GONE
        binding.previewTerrainStatus.visibility = View.GONE
        when (mode) {
            PreviewMode.MAP -> renderMap(surveyMode)
            PreviewMode.ORTHO -> renderOrthoMode()
            PreviewMode.POINT_CLOUD -> renderPointCloud(surveyMode, pointCloud)
        }
        updateDockPlacement()
    }

    private fun renderMap(surveyMode: Boolean) {
        binding.homePointCloudGlView.onPause()
        binding.osmMap.visibility = View.VISIBLE
        binding.homePointCloudGlView.visibility = View.GONE
        clearOrtho()
        val icon = if (surveyMode) R.drawable.ic_point_cloud_24 else R.drawable.ic_ortho_24
        val label = if (surveyMode) R.string.preview_mode_next_3d else R.string.preview_mode_next_ortho
        binding.previewModeCycleButton.setImageResource(icon)
        binding.previewModeCycleButton.contentDescription = context.getString(label)
        binding.previewModeCycleLabel.text = context.getString(label)
        binding.osmMap.overlayManager.tilesOverlay?.isEnabled = true
        renderMapPath()
    }

    private fun renderOrthoMode() {
        binding.homePointCloudGlView.onPause()
        binding.osmMap.visibility = View.VISIBLE
        binding.homePointCloudGlView.visibility = View.GONE
        renderOrtho()
        setCycleAction(R.drawable.ic_point_cloud_24, R.string.preview_mode_next_3d)
        binding.previewAssetPrimaryButton.setImageResource(R.drawable.baseline_load_file_24)
        binding.previewAssetSecondaryButton.setImageResource(R.drawable.ic_menu_map)
        setAssetLabels(R.string.ortho_load_image, R.string.ortho_load_world)
        renderMapPath()
    }

    private fun renderPointCloud(surveyMode: Boolean, pointCloud: PointCloudData?) {
        binding.homePointCloudGlView.onResume()
        binding.osmMap.visibility = View.GONE
        binding.homePointCloudGlView.visibility = View.VISIBLE
        setCycleAction(R.drawable.ic_menu_map, R.string.preview_mode_next_map)
        binding.previewAssetPrimaryButton.setImageResource(R.drawable.baseline_load_file_24)
        binding.previewAssetSecondaryButton.setImageResource(R.drawable.ic_baseline_layers_clear_24)
        setAssetLabels(R.string.point_cloud_load, R.string.point_cloud_reset)
        if (pointCloud == null) {
            binding.homePointCloudGlView.clearPointCloud()
        } else {
            binding.homePointCloudGlView.setPointCloud(pointCloud)
            binding.homePointCloudGlView.setPointSize(
                if (surveyMode) 4f else assetsViewModel.previewSettings.value?.pointCloudPointSize ?: 2.5f
            )
            binding.homePointCloudGlView.setHeightColorModeEnabled(
                surveyMode || (assetsViewModel.previewSettings.value?.heightColorModeEnabled ?: false)
            )
            binding.homePointCloudGlView.setPointCloudOpacity(
                assetsViewModel.previewSettings.value?.orthoOpacity ?: 0.85f
            )
        }
        renderTerrainStatus()
        renderMissionOverlay()
    }

    private fun setCycleAction(icon: Int, label: Int) {
        binding.previewModeCycleButton.setImageResource(icon)
        binding.previewModeCycleButton.contentDescription = context.getString(label)
        binding.previewModeCycleLabel.text = context.getString(label)
    }

    private fun setAssetLabels(primary: Int, secondary: Int) {
        binding.previewAssetPrimaryButton.contentDescription = context.getString(primary)
        binding.previewAssetSecondaryButton.contentDescription = context.getString(secondary)
        binding.previewAssetPrimaryLabel.text = context.getString(primary)
        binding.previewAssetSecondaryLabel.text = context.getString(secondary)
    }

    private fun renderModeButtons(mode: PreviewMode, surveyMode: Boolean) {
        binding.previewModeOrthoButton.visibility = if (surveyMode) View.GONE else View.VISIBLE
        val activeColor = ContextCompat.getColor(context, R.color.ds_color_shell_active)
        val inactiveColor = ContextCompat.getColor(context, R.color.ds_color_text_primary)
        listOf(
            binding.previewModeMapButton to PreviewMode.MAP,
            binding.previewModeOrthoButton to PreviewMode.ORTHO,
            binding.previewMode3dButton to PreviewMode.POINT_CLOUD,
        ).forEach { (button, buttonMode) ->
            val active = mode == buttonMode
            button.alpha = if (active) 1f else 0.88f
            button.strokeWidth = if (active) 1 else 0
            button.cornerRadius = context.resources.getDimensionPixelSize(R.dimen.preview_mode_segment_radius)
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, if (active) R.color.ds_color_shell_selected_surface else android.R.color.transparent)
            )
            button.strokeColor = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, if (active) R.color.ds_color_shell_active else R.color.ds_color_shell_stroke)
            )
            button.setTextColor(if (active) activeColor else inactiveColor)
            button.iconTint = android.content.res.ColorStateList.valueOf(if (active) activeColor else inactiveColor)
        }
    }
}
