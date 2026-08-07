package com.example.droneservicesapp.ui.pointcloud

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.pointcloud.PlyPointCloudParser
import com.example.droneservicesapp.data.pointcloud.PointCloudData
import com.example.droneservicesapp.databinding.FragmentPointCloudViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class PointCloudViewerFragment : Fragment() {

    private var _binding: FragmentPointCloudViewerBinding? = null
    private val binding get() = _binding!!
    private val parser = PlyPointCloudParser()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPointCloudViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pointCloudPreviewCycleButton.setOnClickListener {
            findNavController().navigate(R.id.nav_maps_home)
        }
        binding.pointCloudLoadButton.setOnClickListener { openPointCloudPicker() }
        binding.pointCloudResetButton.setOnClickListener { binding.pointCloudGlView.resetCamera() }
        binding.pointCloudSizeSlider.addOnChangeListener { _, value, _ ->
            binding.pointCloudGlView.setPointSize(value)
        }
        renderEmptyState()
    }

    override fun onResume() {
        super.onResume()
        binding.pointCloudGlView.onResume()
    }

    override fun onPause() {
        binding.pointCloudGlView.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OPEN_PLY && resultCode == Activity.RESULT_OK) {
            data?.data?.let(::loadPointCloud)
        }
    }

    private fun openPointCloudPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "text/plain"))
        }
        startActivityForResult(intent, REQUEST_OPEN_PLY)
    }

    private fun loadPointCloud(uri: Uri) {
        val fileName = queryDisplayName(uri) ?: getString(R.string.point_cloud_unknown_file)
        if (!fileName.lowercase(Locale.US).endsWith(".ply")) {
            Toast.makeText(requireContext(), R.string.point_cloud_select_ply, Toast.LENGTH_SHORT).show()
            return
        }
        binding.pointCloudProgress.visibility = View.VISIBLE
        binding.pointCloudLoadButton.isEnabled = false
        binding.pointCloudStatusText.text = getString(R.string.point_cloud_loading, fileName)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                        parser.parse(stream)
                    } ?: error("Could not open file.")
                }
            }
            binding.pointCloudProgress.visibility = View.GONE
            binding.pointCloudLoadButton.isEnabled = true
            result.onSuccess { pointCloud ->
                binding.pointCloudGlView.setPointCloud(pointCloud)
                renderLoadedState(fileName, pointCloud)
            }.onFailure { error ->
                binding.pointCloudStatusText.text = getString(
                    R.string.point_cloud_load_failed,
                    error.message ?: error.javaClass.simpleName
                )
            }
        }
    }

    private fun renderEmptyState() {
        binding.pointCloudStatusText.text = getString(R.string.point_cloud_empty_state)
        binding.pointCloudStatsText.text = getString(R.string.point_cloud_stats_empty)
    }

    private fun renderLoadedState(fileName: String, pointCloud: PointCloudData) {
        binding.pointCloudStatusText.text = getString(R.string.point_cloud_loaded, fileName)
        binding.pointCloudStatsText.text = getString(
            R.string.point_cloud_stats,
            pointCloud.displayedPointCount,
            pointCloud.totalPointCount,
            pointCloud.bounds.spanX,
            pointCloud.bounds.spanY,
            pointCloud.bounds.spanZ,
            if (pointCloud.hasRgb) getString(R.string.point_cloud_color_rgb) else getString(R.string.point_cloud_color_height)
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        return requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    }

    companion object {
        private const val REQUEST_OPEN_PLY = 2201
    }
}
