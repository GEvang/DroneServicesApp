package com.example.droneservicesapp.ui.pointcloud

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.pointcloud.PlyPointCloudParser
import com.example.droneservicesapp.data.pointcloud.PointCloudData
import com.example.droneservicesapp.databinding.FragmentPointCloudViewerBinding
import com.example.droneservicesapp.ui.preview.PreviewMapFocus
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class PointCloudViewerFragment : Fragment() {

    private var _binding: FragmentPointCloudViewerBinding? = null
    private val binding get() = _binding!!
    private val previewAssetsViewModel: PreviewAssetsViewModel by activityViewModels()
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
            previewAssetsViewModel.requestMapFocus(PreviewMapFocus.POINT_CLOUD)
            findNavController().navigate(R.id.nav_maps_home)
        }
        binding.pointCloudLoadButton.setOnClickListener { openPointCloudPicker() }
        binding.pointCloudResetButton.setOnClickListener { binding.pointCloudGlView.resetCamera() }
        binding.pointCloudSizeSlider.addOnChangeListener { _, value, _ ->
            binding.pointCloudGlView.setPointSize(value)
        }
        restorePreviewAsset()
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
            data?.data?.let { persistReadPermission(it, data.flags) }
            data?.data?.let(::loadPointCloud)
        }
    }

    private fun openPointCloudPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
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
            val currentBinding = _binding ?: return@launch
            currentBinding.pointCloudProgress.visibility = View.GONE
            currentBinding.pointCloudLoadButton.isEnabled = true
            result.onSuccess { pointCloud ->
                previewAssetsViewModel.setPointCloud(pointCloud, fileName, uri)
                savePointCloudReference(uri, fileName)
                currentBinding.pointCloudGlView.setPointCloud(pointCloud)
                renderLoadedState(fileName, pointCloud)
            }.onFailure { error ->
                currentBinding.pointCloudStatusText.text = getString(
                    R.string.point_cloud_load_failed,
                    error.message ?: error.javaClass.simpleName
                )
            }
        }
    }

    private fun restorePreviewAsset() {
        val asset = previewAssetsViewModel.pointCloudAsset
        if (asset == null) {
            if (!restorePersistedPreviewAsset()) {
                renderEmptyState()
            }
            return
        }
        binding.pointCloudGlView.setPointCloud(asset.pointCloud)
        renderLoadedState(asset.fileName, asset.pointCloud)
    }

    private fun restorePersistedPreviewAsset(): Boolean {
        val preferences = previewPreferences()
        val uri = preferences.getString(KEY_POINT_CLOUD_URI, null)?.let(Uri::parse) ?: return false
        val fileName = preferences.getString(KEY_POINT_CLOUD_NAME, null) ?: getString(R.string.point_cloud_unknown_file)

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
            val currentBinding = _binding ?: return@launch
            currentBinding.pointCloudProgress.visibility = View.GONE
            currentBinding.pointCloudLoadButton.isEnabled = true
            result.onSuccess { pointCloud ->
                previewAssetsViewModel.setPointCloud(pointCloud, fileName, uri)
                currentBinding.pointCloudGlView.setPointCloud(pointCloud)
                renderLoadedState(fileName, pointCloud)
            }.onFailure { error ->
                currentBinding.pointCloudStatusText.text = getString(
                    R.string.point_cloud_load_failed,
                    error.message ?: error.javaClass.simpleName
                )
            }
        }
        return true
    }

    private fun renderEmptyState() {
        val currentBinding = _binding ?: return
        currentBinding.pointCloudStatusText.text = getString(R.string.point_cloud_empty_state)
        currentBinding.pointCloudStatsText.text = getString(R.string.point_cloud_stats_empty)
    }

    private fun renderLoadedState(fileName: String, pointCloud: PointCloudData) {
        val currentBinding = _binding ?: return
        currentBinding.pointCloudStatusText.text = getString(R.string.point_cloud_loaded, fileName)
        currentBinding.pointCloudStatsText.text = getString(
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

    private fun persistReadPermission(uri: Uri, flags: Int) {
        val readFlags = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (readFlags == 0) return
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(uri, readFlags)
        }
    }

    private fun savePointCloudReference(uri: Uri, fileName: String) {
        previewPreferences().edit()
            .putString(KEY_POINT_CLOUD_URI, uri.toString())
            .putString(KEY_POINT_CLOUD_NAME, fileName)
            .apply()
    }

    private fun previewPreferences() = requireContext().getSharedPreferences(PREVIEW_PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val PREVIEW_PREFS = "preview_assets"
        private const val KEY_POINT_CLOUD_URI = "point_cloud_uri"
        private const val KEY_POINT_CLOUD_NAME = "point_cloud_name"
        private const val REQUEST_OPEN_PLY = 2201
    }
}
