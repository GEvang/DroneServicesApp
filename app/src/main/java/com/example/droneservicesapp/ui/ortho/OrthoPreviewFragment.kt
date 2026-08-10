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
import com.example.droneservicesapp.ui.preview.PreviewMapFocus
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

class OrthoPreviewFragment : Fragment() {

    private var _binding: FragmentOrthoPreviewBinding? = null
    private val binding get() = _binding!!
    private val previewAssetsViewModel: PreviewAssetsViewModel by activityViewModels()
    private val tiffDecoder = SimpleTiffDecoder()
    private val worldFileParser = WorldFileParser()
    private var bitmap: Bitmap? = null
    private var bitmapFileName: String? = null
    private var sourceImageWidth: Int? = null
    private var sourceImageHeight: Int? = null
    private var bounds: OrthoBounds? = null
    private var worldFileName: String? = null
    private var overlay: OrthoImageOverlay? = null
    private var orthoLoadJob: Job? = null
    private var isOpeningFilePicker = false

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
        } else {
            renderStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        isOpeningFilePicker = false
        binding.orthoMap.onResume()
    }

    override fun onPause() {
        if (!isOpeningFilePicker) {
            previewAssetsViewModel.requestMapFocus(PreviewMapFocus.ORTHO)
        }
        binding.orthoMap.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        orthoLoadJob?.cancel()
        orthoLoadJob = null
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
        isOpeningFilePicker = true
        startActivityForResult(intent, requestCode)
    }

    private fun loadTiff(uri: Uri) {
        val fileName = queryDisplayName(uri) ?: getString(R.string.ortho_unknown_image)
        if (!fileName.lowercase(Locale.US).endsWith(".tif") && !fileName.lowercase(Locale.US).endsWith(".tiff")) {
            binding.orthoStatusText.text = getString(R.string.ortho_select_tif)
            return
        }
        setLoading(true, getString(R.string.ortho_loading_image, fileName))
        orthoLoadJob?.cancel()
        orthoLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    decodeDisplayBitmap(uri)
                }
            }
            val currentBinding = _binding ?: return@launch
            setLoading(false)
            result.onSuccess { decoded ->
                overlay?.let { currentBinding.orthoMap.overlays.remove(it) }
                overlay = null
                bitmap = decoded.bitmap
                bitmapFileName = fileName
                sourceImageWidth = decoded.sourceWidth
                sourceImageHeight = decoded.sourceHeight
                bounds = null
                worldFileName = null
                previewAssetsViewModel.setOrthoImage(
                    bitmap = decoded.bitmap,
                    bitmapFileName = fileName,
                    bitmapUri = uri,
                    sourceWidth = decoded.sourceWidth,
                    sourceHeight = decoded.sourceHeight
                )
                saveOrthoImageReference(uri, fileName)
                currentBinding.orthoMap.invalidate()
                currentBinding.orthoStatusText.text = getString(R.string.ortho_load_world_next)
                renderStatus()
            }.onFailure {
                currentBinding.orthoStatusText.text = getString(R.string.ortho_load_failed, it.message ?: it.javaClass.simpleName)
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
        val parseWidth = sourceImageWidth ?: image.width
        val parseHeight = sourceImageHeight ?: image.height
        setLoading(true, getString(R.string.ortho_loading_world, fileName))
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                        worldFileParser.parse(stream, parseWidth, parseHeight)
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
        zoomMapToBounds(imageBounds)
        binding.orthoMap.invalidate()
    }

    private fun zoomToOrthoBounds() {
        val imageBounds = bounds
        if (imageBounds == null) {
            binding.orthoStatusText.text = getString(R.string.ortho_load_world_next)
            return
        }
        zoomMapToBounds(imageBounds)
        binding.orthoMap.invalidate()
    }

    private fun zoomMapToBounds(imageBounds: OrthoBounds) {
        val centerLat = (imageBounds.minLat + imageBounds.maxLat) / 2.0
        val centerLon = (imageBounds.minLon + imageBounds.maxLon) / 2.0
        binding.orthoMap.controller.setZoom(calculateSafeBoundsZoom(imageBounds))
        binding.orthoMap.controller.setCenter(GeoPoint(centerLat, centerLon))
    }

    private fun calculateSafeBoundsZoom(imageBounds: OrthoBounds): Double {
        val lonSpan = (imageBounds.maxLon - imageBounds.minLon).coerceAtLeast(MIN_BOUNDS_SPAN_DEGREES)
        val mercatorSpan = abs(mercatorY(imageBounds.maxLat) - mercatorY(imageBounds.minLat))
            .coerceAtLeast(MIN_MERCATOR_SPAN)
        val mapWidth = max(binding.orthoMap.width - MAP_FIT_PADDING_PX * 2, MIN_MAP_VIEWPORT_PX)
        val mapHeight = max(binding.orthoMap.height - MAP_FIT_PADDING_PX * 2, MIN_MAP_VIEWPORT_PX)
        val lonZoom = log2(mapWidth * 360.0 / (TILE_SIZE_PX * lonSpan))
        val latZoom = log2(mapHeight * 2.0 * PI / (TILE_SIZE_PX * mercatorSpan))
        return min(lonZoom, latZoom).coerceIn(MIN_ORTHO_ZOOM, MAX_ORTHO_ZOOM)
    }

    private fun mercatorY(latitude: Double): Double {
        val radians = Math.toRadians(latitude.coerceIn(MIN_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE))
        return ln(tan(PI / 4.0 + radians / 2.0))
    }

    private fun log2(value: Double): Double = ln(value) / ln(2.0)

    private fun setBackgroundTilesEnabled(isEnabled: Boolean) {
        binding.orthoMap.overlayManager.tilesOverlay?.isEnabled = isEnabled
        binding.orthoMap.invalidate()
    }

    private fun clearOverlay() {
        overlay?.let { binding.orthoMap.overlays.remove(it) }
        overlay = null
        bitmap = null
        sourceImageWidth = null
        sourceImageHeight = null
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
        sourceImageWidth = asset.sourceWidth
        sourceImageHeight = asset.sourceHeight
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
        orthoLoadJob?.cancel()
        orthoLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val decoded = decodeDisplayBitmap(imageUri)
                    val decodedBounds = if (worldUri != null) {
                        requireContext().contentResolver.openInputStream(worldUri)?.use { stream ->
                            worldFileParser.parse(stream, decoded.sourceWidth, decoded.sourceHeight)
                        }
                    } else {
                        null
                    }
                    decoded to decodedBounds
                }
            }
            if (_binding == null) return@launch
            setLoading(false)
            result.onSuccess { (decoded, decodedBounds) ->
                bitmap = decoded.bitmap
                bitmapFileName = imageName
                sourceImageWidth = decoded.sourceWidth
                sourceImageHeight = decoded.sourceHeight
                bounds = decodedBounds
                worldFileName = if (decodedBounds != null) savedWorldName else null
                previewAssetsViewModel.setOrthoImage(
                    bitmap = decoded.bitmap,
                    bitmapFileName = imageName,
                    bitmapUri = imageUri,
                    sourceWidth = decoded.sourceWidth,
                    sourceHeight = decoded.sourceHeight
                )
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

    private fun decodeDisplayBitmap(uri: Uri): DecodedOrthoBitmap {
        val decoded = requireContext().contentResolver.openInputStream(uri)?.use { stream ->
            tiffDecoder.decodePreview(stream, MAX_ORTHO_PREVIEW_DIMENSION_PX)
        }
            ?: error("Could not open image file.")
        return DecodedOrthoBitmap(decoded.bitmap, decoded.sourceWidth, decoded.sourceHeight)
    }

    private data class DecodedOrthoBitmap(
        val bitmap: Bitmap,
        val sourceWidth: Int,
        val sourceHeight: Int
    )

    private fun renderStatus() {
        val image = bitmap
        val imageName = bitmapFileName
        val worldName = worldFileName
        val imageBounds = bounds
        val displayWidth = sourceImageWidth ?: image?.width
        val displayHeight = sourceImageHeight ?: image?.height
        binding.orthoStatusText.text = when {
            image == null -> getString(R.string.ortho_empty_state)
            imageBounds == null -> getString(R.string.ortho_image_loaded_no_world, imageName, displayWidth ?: 0, displayHeight ?: 0)
            else -> getString(R.string.ortho_loaded, imageName, worldName)
        }
        binding.orthoStatsText.text = if (image != null && imageBounds != null) {
            getString(
                R.string.ortho_stats,
                displayWidth ?: image.width,
                displayHeight ?: image.height,
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
        private const val MAX_ORTHO_PREVIEW_DIMENSION_PX = 2048
        private const val TILE_SIZE_PX = 256.0
        private const val MIN_MAP_VIEWPORT_PX = 320
        private const val MIN_BOUNDS_SPAN_DEGREES = 0.000001
        private const val MIN_MERCATOR_SPAN = 0.000001
        private const val MIN_MERCATOR_LATITUDE = -85.05112878
        private const val MAX_MERCATOR_LATITUDE = 85.05112878
        private const val MIN_ORTHO_ZOOM = 2.0
        private const val MAX_ORTHO_ZOOM = 21.0
    }
}
