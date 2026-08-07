package com.example.droneservicesapp.ui.ortho

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.ortho.OrthoBounds
import com.example.droneservicesapp.data.ortho.SimpleTiffDecoder
import com.example.droneservicesapp.data.ortho.WorldFileParser
import com.example.droneservicesapp.databinding.FragmentOrthoPreviewBinding
import com.example.droneservicesapp.ui.home.components.EsriWorldImageryTileSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import java.util.Locale

class OrthoPreviewFragment : Fragment() {

    private var _binding: FragmentOrthoPreviewBinding? = null
    private val binding get() = _binding!!
    private val tiffDecoder = SimpleTiffDecoder()
    private val worldFileParser = WorldFileParser()
    private var bitmap: Bitmap? = null
    private var bitmapFileName: String? = null
    private var bounds: OrthoBounds? = null
    private var worldFileName: String? = null
    private var overlay: OrthoImageOverlay? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrthoPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.orthoMap.setBuiltInZoomControls(false)
        binding.orthoMap.setMultiTouchControls(true)
        binding.orthoMap.setTileSource(EsriWorldImageryTileSource)
        binding.orthoMap.isTilesScaledToDpi = true
        binding.orthoMap.maxZoomLevel = 22.0
        binding.orthoMap.controller.setZoom(DEFAULT_ZOOM)
        binding.orthoMap.controller.setCenter(GeoPoint(DEFAULT_LAT, DEFAULT_LON))

        binding.orthoLoadImageButton.setOnClickListener { openFilePicker(REQUEST_OPEN_TIFF) }
        binding.orthoLoadWorldButton.setOnClickListener { openFilePicker(REQUEST_OPEN_WORLD) }
        binding.orthoClearButton.setOnClickListener { clearOverlay() }
        binding.orthoOpacitySlider.addOnChangeListener { _, value, _ ->
            overlay?.opacity = value
            binding.orthoMap.invalidate()
        }
        renderStatus()
    }

    override fun onResume() {
        super.onResume()
        binding.orthoMap.onResume()
    }

    override fun onPause() {
        binding.orthoMap.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        overlay?.let { binding.orthoMap.overlays.remove(it) }
        overlay = null
        binding.orthoMap.onDetach()
        _binding = null
        super.onDestroyView()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_OPEN_TIFF -> loadTiff(uri)
            REQUEST_OPEN_WORLD -> loadWorldFile(uri)
        }
    }

    private fun openFilePicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, requestCode)
    }

    private fun loadTiff(uri: Uri) {
        val fileName = queryDisplayName(uri) ?: getString(R.string.ortho_unknown_image)
        if (!fileName.lowercase(Locale.US).endsWith(".tif") && !fileName.lowercase(Locale.US).endsWith(".tiff")) {
            binding.orthoStatusText.text = getString(R.string.ortho_select_tif)
            return
        }
        setLoading(true, getString(R.string.ortho_loading_image, fileName))
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use(tiffDecoder::decode)
                        ?: error("Could not open image file.")
                }
            }
            setLoading(false)
            result.onSuccess {
                bitmap = it
                bitmapFileName = fileName
                if (bounds == null) {
                    binding.orthoStatusText.text = getString(R.string.ortho_load_world_next)
                }
                renderOverlayIfReady()
                renderStatus()
            }.onFailure {
                binding.orthoStatusText.text = getString(R.string.ortho_load_failed, it.message ?: it.javaClass.simpleName)
            }
        }
    }

    private fun loadWorldFile(uri: Uri) {
        val fileName = queryDisplayName(uri) ?: getString(R.string.ortho_unknown_world)
        if (!fileName.lowercase(Locale.US).endsWith(".tfw") && !fileName.lowercase(Locale.US).endsWith(".wld")) {
            binding.orthoStatusText.text = getString(R.string.ortho_select_world)
            return
        }
        val image = bitmap
        if (image == null) {
            binding.orthoStatusText.text = getString(R.string.ortho_load_image_first)
            return
        }
        setLoading(true, getString(R.string.ortho_loading_world, fileName))
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                        worldFileParser.parse(stream, image.width, image.height)
                    } ?: error("Could not open world file.")
                }
            }
            setLoading(false)
            result.onSuccess {
                bounds = it
                worldFileName = fileName
                renderOverlayIfReady()
                renderStatus()
            }.onFailure {
                binding.orthoStatusText.text = getString(R.string.ortho_load_failed, it.message ?: it.javaClass.simpleName)
            }
        }
    }

    private fun renderOverlayIfReady() {
        val image = bitmap ?: return
        val imageBounds = bounds ?: return
        overlay?.let { binding.orthoMap.overlays.remove(it) }
        overlay = OrthoImageOverlay(image, imageBounds).apply {
            opacity = binding.orthoOpacitySlider.value
        }
        binding.orthoMap.overlays.add(0, overlay)
        binding.orthoMap.zoomToBoundingBox(
            BoundingBox(imageBounds.maxLat, imageBounds.maxLon, imageBounds.minLat, imageBounds.minLon),
            true,
            MAP_FIT_PADDING_PX
        )
        binding.orthoMap.invalidate()
    }

    private fun clearOverlay() {
        overlay?.let { binding.orthoMap.overlays.remove(it) }
        overlay = null
        bitmap = null
        bounds = null
        bitmapFileName = null
        worldFileName = null
        binding.orthoMap.invalidate()
        renderStatus()
    }

    private fun renderStatus() {
        val image = bitmap
        val imageName = bitmapFileName
        val worldName = worldFileName
        val imageBounds = bounds
        binding.orthoStatusText.text = when {
            image == null -> getString(R.string.ortho_empty_state)
            imageBounds == null -> getString(R.string.ortho_image_loaded_no_world, imageName, image.width, image.height)
            else -> getString(R.string.ortho_loaded, imageName, worldName)
        }
        binding.orthoStatsText.text = if (image != null && imageBounds != null) {
            getString(
                R.string.ortho_stats,
                image.width,
                image.height,
                imageBounds.minLon,
                imageBounds.minLat,
                imageBounds.maxLon,
                imageBounds.maxLat
            )
        } else {
            getString(R.string.ortho_stats_empty)
        }
    }

    private fun setLoading(isLoading: Boolean, status: String? = null) {
        binding.orthoProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.orthoLoadImageButton.isEnabled = !isLoading
        binding.orthoLoadWorldButton.isEnabled = !isLoading
        if (status != null) binding.orthoStatusText.text = status
    }

    private fun queryDisplayName(uri: Uri): String? {
        return requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    }

    companion object {
        private const val REQUEST_OPEN_TIFF = 2301
        private const val REQUEST_OPEN_WORLD = 2302
        private const val DEFAULT_LAT = 35.35824717735152
        private const val DEFAULT_LON = 24.62254619945714
        private const val DEFAULT_ZOOM = 18.0
        private const val MAP_FIT_PADDING_PX = 80
    }
}
