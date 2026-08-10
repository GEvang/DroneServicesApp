package com.example.droneservicesapp.ui.ortho

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.ortho.OrthoBounds
import com.example.droneservicesapp.data.ortho.SimpleTiffDecoder
import com.example.droneservicesapp.data.ortho.WorldFileParser
import com.example.droneservicesapp.databinding.FragmentOrthoPreviewBinding
import com.example.droneservicesapp.ui.home.components.EsriWorldImageryTileSource
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import java.util.Locale

class OrthoPreviewFragment : Fragment() {

    private var _binding: FragmentOrthoPreviewBinding? = null
    private val binding get() = _binding!!
    private val previewAssetsViewModel: PreviewAssetsViewModel by activityViewModels()
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

        binding.orthoPreviewCycleButton.setOnClickListener {
            findNavController().navigate(R.id.nav_point_cloud)
        }
        binding.orthoLoadImageButton.setOnClickListener { openFilePicker(REQUEST_OPEN_TIFF) }
        binding.orthoLoadWorldButton.setOnClickListener { openFilePicker(REQUEST_OPEN_WORLD) }
        binding.orthoClearButton.setOnClickListener { clearOverlay() }
        binding.orthoZoomToButton.setOnClickListener { zoomToOrthoBounds() }
        binding.orthoBackgroundSwitch.setOnCheckedChangeListener { _, isChecked ->
            setBackgroundTilesEnabled(isChecked)
        }
        binding.orthoOpacitySlider.addOnChangeListener { _, value, _ ->
            overlay?.opacity = value
            binding.orthoMap.invalidate()
        }
        if (restorePreviewAsset()) {
            renderStatus()
        } else if (!restorePersistedPreviewAsset()) {
            renderStatus()
        }
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
        persistReadPermission(uri, data.flags)
        when (requestCode) {
            REQUEST_OPEN_TIFF -> loadTiff(uri)
            REQUEST_OPEN_WORLD -> loadWorldFile(uri)
        }
    }

    private fun openFilePicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
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
                previewAssetsViewModel.setOrthoImage(it, fileName, uri)
                saveOrthoImageReference(uri, fileName)
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
                previewAssetsViewModel.setOrthoBounds(it, fileName, uri)
                saveOrthoWorldReference(uri, fileName)
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

    private fun zoomToOrthoBounds() {
        val imageBounds = bounds
        if (imageBounds == null) {
            binding.orthoStatusText.text = getString(R.string.ortho_load_world_next)
            return
        }
        binding.orthoMap.zoomToBoundingBox(
            BoundingBox(imageBounds.maxLat, imageBounds.maxLon, imageBounds.minLat, imageBounds.minLon),
            true,
            MAP_FIT_PADDING_PX
        )
        binding.orthoMap.invalidate()
    }

    private fun setBackgroundTilesEnabled(isEnabled: Boolean) {
        binding.orthoMap.overlayManager.tilesOverlay?.isEnabled = isEnabled
        binding.orthoMap.invalidate()
    }

    private fun clearOverlay() {
        overlay?.let { binding.orthoMap.overlays.remove(it) }
        overlay = null
        bitmap = null
        bounds = null
        bitmapFileName = null
        worldFileName = null
        previewAssetsViewModel.clearOrtho()
        clearPersistedOrthoReference()
        binding.orthoMap.invalidate()
        renderStatus()
    }

    private fun restorePreviewAsset(): Boolean {
        val asset = previewAssetsViewModel.orthoAsset ?: return false
        bitmap = asset.bitmap
        bitmapFileName = asset.bitmapFileName
        bounds = asset.bounds
        worldFileName = asset.worldFileName
        renderOverlayIfReady()
        return true
    }

    private fun restorePersistedPreviewAsset(): Boolean {
        val preferences = previewPreferences()
        val imageUri = preferences.getString(KEY_ORTHO_IMAGE_URI, null)?.let(Uri::parse) ?: return false
        val imageName = preferences.getString(KEY_ORTHO_IMAGE_NAME, null) ?: getString(R.string.ortho_unknown_image)
        val worldUri = preferences.getString(KEY_ORTHO_WORLD_URI, null)?.let(Uri::parse)
        val savedWorldName = preferences.getString(KEY_ORTHO_WORLD_NAME, null)

        setLoading(true, getString(R.string.ortho_loading_image, imageName))
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val decodedBitmap = requireContext().contentResolver.openInputStream(imageUri)?.use(tiffDecoder::decode)
                        ?: error("Could not open image file.")
                    val decodedBounds = if (worldUri != null) {
                        requireContext().contentResolver.openInputStream(worldUri)?.use { stream ->
                            worldFileParser.parse(stream, decodedBitmap.width, decodedBitmap.height)
                        }
                    } else {
                        null
                    }
                    decodedBitmap to decodedBounds
                }
            }
            if (_binding == null) return@launch
            setLoading(false)
            result.onSuccess { (decodedBitmap, decodedBounds) ->
                bitmap = decodedBitmap
                bitmapFileName = imageName
                bounds = decodedBounds
                worldFileName = if (decodedBounds != null) savedWorldName else null
                previewAssetsViewModel.setOrthoImage(decodedBitmap, imageName, imageUri)
                if (decodedBounds != null && worldUri != null && savedWorldName != null) {
                    previewAssetsViewModel.setOrthoBounds(decodedBounds, savedWorldName, worldUri)
                }
                renderOverlayIfReady()
                renderStatus()
            }.onFailure {
                _binding?.orthoStatusText?.text = getString(R.string.ortho_load_failed, it.message ?: it.javaClass.simpleName)
            }
        }
        return true
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

    private fun persistReadPermission(uri: Uri, flags: Int) {
        val readFlags = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (readFlags == 0) return
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(uri, readFlags)
        }
    }

    private fun saveOrthoImageReference(uri: Uri, fileName: String) {
        previewPreferences().edit()
            .putString(KEY_ORTHO_IMAGE_URI, uri.toString())
            .putString(KEY_ORTHO_IMAGE_NAME, fileName)
            .apply()
    }

    private fun saveOrthoWorldReference(uri: Uri, fileName: String) {
        previewPreferences().edit()
            .putString(KEY_ORTHO_WORLD_URI, uri.toString())
            .putString(KEY_ORTHO_WORLD_NAME, fileName)
            .apply()
    }

    private fun clearPersistedOrthoReference() {
        previewPreferences().edit()
            .remove(KEY_ORTHO_IMAGE_URI)
            .remove(KEY_ORTHO_IMAGE_NAME)
            .remove(KEY_ORTHO_WORLD_URI)
            .remove(KEY_ORTHO_WORLD_NAME)
            .apply()
    }

    private fun previewPreferences() = requireContext().getSharedPreferences(PREVIEW_PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val PREVIEW_PREFS = "preview_assets"
        private const val KEY_ORTHO_IMAGE_URI = "ortho_image_uri"
        private const val KEY_ORTHO_IMAGE_NAME = "ortho_image_name"
        private const val KEY_ORTHO_WORLD_URI = "ortho_world_uri"
        private const val KEY_ORTHO_WORLD_NAME = "ortho_world_name"
        private const val REQUEST_OPEN_TIFF = 2301
        private const val REQUEST_OPEN_WORLD = 2302
        private const val DEFAULT_LAT = 35.35824717735152
        private const val DEFAULT_LON = 24.62254619945714
        private const val DEFAULT_ZOOM = 18.0
        private const val MAP_FIT_PADDING_PX = 80
    }
}
