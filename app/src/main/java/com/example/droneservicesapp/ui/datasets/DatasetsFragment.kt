package com.example.droneservicesapp.ui.datasets

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.ortho.SimpleTiffDecoder
import com.example.droneservicesapp.data.ortho.WorldFileParser
import com.example.droneservicesapp.data.pointcloud.PlyPointCloudParser
import com.example.droneservicesapp.data.preview.PreviewDatasetRecord
import com.example.droneservicesapp.data.preview.PreviewDatasetStore
import com.example.droneservicesapp.databinding.FragmentDatasetsBinding
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.preview.PreviewMapFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class DatasetsFragment : Fragment() {
    private var _binding: FragmentDatasetsBinding? = null
    private val binding get() = _binding!!

    private val previewAssetsViewModel: PreviewAssetsViewModel by activityViewModels()
    private lateinit var datasetStore: PreviewDatasetStore
    private val tiffDecoder = SimpleTiffDecoder()
    private val worldFileParser = WorldFileParser()
    private val pointCloudParser = PlyPointCloudParser()
    private var loadJob: Job? = null
    private var suppressOptionCallbacks = false
    private var suppressDatasetSelection = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDatasetsBinding.inflate(inflater, container, false)
        datasetStore = PreviewDatasetStore(requireContext().applicationContext)
        ensureActiveDataset()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindActions()
        render()
    }

    override fun onDestroyView() {
        loadJob?.cancel()
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
            REQUEST_OPEN_TIFF -> attachOrthoImage(uri)
            REQUEST_OPEN_WORLD -> attachOrthoWorld(uri)
            REQUEST_OPEN_POINT_CLOUD -> attachPointCloud(uri)
        }
    }

    private fun bindActions() {
        binding.datasetCreateButton.setOnClickListener {
            val index = datasetStore.loadDatasets().size + 1
            val record = PreviewDatasetRecord(
                id = System.currentTimeMillis().toString(),
                name = "Dataset $index"
            )
            datasetStore.upsert(record)
            datasetStore.setActiveDatasetId(record.id)
            applySettings(record)
            Toast.makeText(requireContext(), R.string.dataset_created, Toast.LENGTH_SHORT).show()
            render()
        }
        binding.datasetLoadButton.setOnClickListener {
            datasetStore.activeDataset()?.let { loadDataset(it) }
        }
        binding.datasetUnloadButton.setOnClickListener {
            previewAssetsViewModel.clearAssets()
            datasetStore.setActiveDatasetId(null)
            clearLegacyPreviewReferences()
            Toast.makeText(requireContext(), R.string.dataset_unloaded, Toast.LENGTH_SHORT).show()
            ensureActiveDataset()
            render()
        }
        binding.datasetLoadTifButton.setOnClickListener { openFilePicker(REQUEST_OPEN_TIFF) }
        binding.datasetLoadTfwButton.setOnClickListener { openFilePicker(REQUEST_OPEN_WORLD) }
        binding.datasetLoadPlyButton.setOnClickListener { openFilePicker(REQUEST_OPEN_POINT_CLOUD) }
        binding.datasetOrthoBackgroundSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (suppressOptionCallbacks) return@setOnCheckedChangeListener
            updateActiveDataset { copy(orthoBackgroundEnabled = isChecked) }
        }
        binding.datasetOrthoOpacitySlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || suppressOptionCallbacks) return@addOnChangeListener
            updateActiveDataset { copy(orthoOpacity = value) }
        }
        binding.datasetPointSizeSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || suppressOptionCallbacks) return@addOnChangeListener
            updateActiveDataset { copy(pointCloudPointSize = value) }
        }
        binding.datasetHeightColorsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (suppressOptionCallbacks) return@setOnCheckedChangeListener
            updateActiveDataset { copy(heightColorModeEnabled = isChecked) }
        }
        binding.datasetSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressDatasetSelection) return
                val record = datasetStore.loadDatasets().getOrNull(position) ?: return
                datasetStore.setActiveDatasetId(record.id)
                applySettings(record)
                render()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun ensureActiveDataset(): PreviewDatasetRecord {
        datasetStore.activeDataset()?.let { return it }
        val existing = datasetStore.loadDatasets().firstOrNull()
        if (existing != null) {
            datasetStore.setActiveDatasetId(existing.id)
            return existing
        }
        val created = PreviewDatasetRecord(
            id = System.currentTimeMillis().toString(),
            name = "Dataset 1"
        )
        datasetStore.upsert(created)
        datasetStore.setActiveDatasetId(created.id)
        return created
    }

    private fun activeDataset(): PreviewDatasetRecord = ensureActiveDataset()

    private fun updateActiveDataset(update: PreviewDatasetRecord.() -> PreviewDatasetRecord) {
        val updated = activeDataset().update()
        datasetStore.upsert(updated)
        applySettings(updated)
        render()
    }

    private fun attachOrthoImage(uri: Uri) {
        val fileName = queryDisplayName(uri) ?: getString(R.string.ortho_unknown_image)
        if (!fileName.lowercase(Locale.US).endsWith(".tif") && !fileName.lowercase(Locale.US).endsWith(".tiff")) {
            Toast.makeText(requireContext(), R.string.ortho_select_tif, Toast.LENGTH_SHORT).show()
            return
        }
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                        tiffDecoder.decodePreview(stream, MAX_ORTHO_PREVIEW_DIMENSION_PX)
                    } ?: error("Could not open image file.")
                }
            }
            result.onSuccess { decoded ->
                val updated = activeDataset().copy(
                    orthoImageUri = uri,
                    orthoImageName = fileName,
                    orthoSourceWidth = decoded.sourceWidth,
                    orthoSourceHeight = decoded.sourceHeight,
                    orthoWorldUri = null,
                    orthoWorldName = null,
                )
                datasetStore.upsert(updated)
                previewAssetsViewModel.setOrthoImage(
                    bitmap = decoded.bitmap,
                    bitmapFileName = fileName,
                    bitmapUri = uri,
                    sourceWidth = decoded.sourceWidth,
                    sourceHeight = decoded.sourceHeight,
                    notifyChange = false
                )
                applySettings(updated)
                saveLegacyPreviewReferences(updated)
                Toast.makeText(requireContext(), R.string.ortho_load_world_next, Toast.LENGTH_SHORT).show()
                render()
            }.onFailure { showLoadError(it) }
        }
    }

    private fun attachOrthoWorld(uri: Uri) {
        val fileName = queryDisplayName(uri) ?: getString(R.string.ortho_unknown_world)
        if (!fileName.lowercase(Locale.US).endsWith(".tfw") && !fileName.lowercase(Locale.US).endsWith(".wld")) {
            Toast.makeText(requireContext(), R.string.ortho_select_world, Toast.LENGTH_SHORT).show()
            return
        }
        val asset = previewAssetsViewModel.orthoAsset
        val sourceWidth = asset?.sourceWidth ?: activeDataset().orthoSourceWidth
        val sourceHeight = asset?.sourceHeight ?: activeDataset().orthoSourceHeight
        if (sourceWidth == null || sourceHeight == null) {
            Toast.makeText(requireContext(), R.string.ortho_load_image_first, Toast.LENGTH_SHORT).show()
            return
        }
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                        worldFileParser.parse(stream, sourceWidth, sourceHeight)
                    } ?: error("Could not open world file.")
                }
            }
            result.onSuccess { bounds ->
                val updated = activeDataset().copy(
                    orthoWorldUri = uri,
                    orthoWorldName = fileName
                )
                datasetStore.upsert(updated)
                previewAssetsViewModel.requestMapFocus(PreviewMapFocus.ORTHO)
                previewAssetsViewModel.setOrthoBounds(bounds, fileName, uri)
                applySettings(updated)
                saveLegacyPreviewReferences(updated)
                Toast.makeText(requireContext(), R.string.dataset_loaded, Toast.LENGTH_SHORT).show()
                render()
            }.onFailure { showLoadError(it) }
        }
    }

    private fun attachPointCloud(uri: Uri) {
        val fileName = queryDisplayName(uri) ?: getString(R.string.point_cloud_unknown_file)
        if (!isSupportedPointCloudFile(fileName)) {
            Toast.makeText(requireContext(), R.string.point_cloud_select_ply, Toast.LENGTH_SHORT).show()
            return
        }
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                        pointCloudParser.parse(stream, fileName)
                    } ?: error("Could not open file.")
                }
            }
            result.onSuccess { pointCloud ->
                val updated = activeDataset().copy(
                    pointCloudUri = uri,
                    pointCloudName = fileName
                )
                datasetStore.upsert(updated)
                previewAssetsViewModel.requestMapFocus(PreviewMapFocus.POINT_CLOUD)
                previewAssetsViewModel.setPointCloud(pointCloud, fileName, uri)
                applySettings(updated)
                saveLegacyPreviewReferences(updated)
                Toast.makeText(requireContext(), R.string.dataset_loaded, Toast.LENGTH_SHORT).show()
                render()
            }.onFailure { showLoadError(it) }
        }
    }

    private fun loadDataset(record: PreviewDatasetRecord) {
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            previewAssetsViewModel.clearAssets()
            applySettings(record)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val decoded = record.orthoImageUri?.let { uri ->
                        requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                            tiffDecoder.decodePreview(stream, MAX_ORTHO_PREVIEW_DIMENSION_PX)
                        } ?: error("Could not open image file.")
                    }
                    val bounds = if (decoded != null && record.orthoWorldUri != null) {
                        requireContext().contentResolver.openInputStream(record.orthoWorldUri)?.use { stream ->
                            worldFileParser.parse(stream, decoded.sourceWidth, decoded.sourceHeight)
                        }
                    } else {
                        null
                    }
                    val pointCloud = record.pointCloudUri?.let { uri ->
                        requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                            pointCloudParser.parse(stream, record.pointCloudName ?: getString(R.string.point_cloud_unknown_file))
                        } ?: error("Could not open file.")
                    }
                    Triple(decoded, bounds, pointCloud)
                }
            }
            result.onSuccess { (decoded, bounds, pointCloud) ->
                if (decoded != null && record.orthoImageUri != null) {
                    previewAssetsViewModel.setOrthoImage(
                        bitmap = decoded.bitmap,
                        bitmapFileName = record.orthoImageName ?: getString(R.string.ortho_unknown_image),
                        bitmapUri = record.orthoImageUri,
                        sourceWidth = decoded.sourceWidth,
                        sourceHeight = decoded.sourceHeight,
                        notifyChange = bounds == null
                    )
                    if (bounds != null && record.orthoWorldUri != null) {
                        previewAssetsViewModel.requestMapFocus(PreviewMapFocus.ORTHO)
                        previewAssetsViewModel.setOrthoBounds(
                            bounds,
                            record.orthoWorldName ?: getString(R.string.ortho_unknown_world),
                            record.orthoWorldUri
                        )
                    }
                }
                if (pointCloud != null && record.pointCloudUri != null) {
                    previewAssetsViewModel.requestMapFocus(PreviewMapFocus.POINT_CLOUD)
                    previewAssetsViewModel.setPointCloud(
                        pointCloud,
                        record.pointCloudName ?: getString(R.string.point_cloud_unknown_file),
                        record.pointCloudUri
                    )
                }
                datasetStore.setActiveDatasetId(record.id)
                saveLegacyPreviewReferences(record)
                Toast.makeText(requireContext(), R.string.dataset_loaded, Toast.LENGTH_SHORT).show()
                render()
            }.onFailure { showLoadError(it) }
        }
    }

    private fun applySettings(record: PreviewDatasetRecord) {
        previewAssetsViewModel.updateSettings {
            copy(
                orthoOpacity = record.orthoOpacity,
                orthoBackgroundEnabled = record.orthoBackgroundEnabled,
                pointCloudPointSize = record.pointCloudPointSize,
                heightColorModeEnabled = record.heightColorModeEnabled
            )
        }
    }

    private fun render() {
        val record = activeDataset()
        val datasets = datasetStore.loadDatasets()
        binding.datasetActiveName.text = getString(R.string.dataset_active_name, record.name)
        suppressDatasetSelection = true
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_dataset_spinner,
            datasets.map { it.name }
        )
        adapter.setDropDownViewResource(R.layout.item_dataset_spinner)
        binding.datasetSelector.adapter = adapter
        val selectedIndex = datasets.indexOfFirst { it.id == record.id }.coerceAtLeast(0)
        binding.datasetSelector.setSelection(selectedIndex)
        suppressDatasetSelection = false
        binding.datasetOrthoImageStatus.text = getString(
            R.string.dataset_ortho_image_status,
            record.orthoImageName ?: getString(R.string.dataset_none)
        )
        binding.datasetOrthoWorldStatus.text = getString(
            R.string.dataset_ortho_world_status,
            record.orthoWorldName ?: getString(R.string.dataset_none)
        )
        binding.datasetPointCloudStatus.text = getString(
            R.string.dataset_point_cloud_status,
            record.pointCloudName ?: getString(R.string.dataset_none)
        )
        suppressOptionCallbacks = true
        binding.datasetOrthoBackgroundSwitch.isChecked = record.orthoBackgroundEnabled
        binding.datasetOrthoOpacitySlider.value = record.orthoOpacity.coerceIn(0f, 1f)
        binding.datasetPointSizeSlider.value = record.pointCloudPointSize.coerceIn(1f, 10f)
        binding.datasetHeightColorsSwitch.isChecked = record.heightColorModeEnabled
        suppressOptionCallbacks = false
        binding.datasetOrthoOpacityLabel.text = getString(
            R.string.dataset_ortho_opacity_value,
            (record.orthoOpacity * 100).toInt()
        )
        binding.datasetPointSizeLabel.text = getString(
            R.string.dataset_point_size_value,
            record.pointCloudPointSize
        )
    }

    private fun openFilePicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, requestCode)
    }

    private fun queryDisplayName(uri: Uri): String? {
        return requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }

    private fun persistReadPermission(uri: Uri, flags: Int) {
        val takeFlags = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)
        }
    }

    private fun saveLegacyPreviewReferences(record: PreviewDatasetRecord) {
        requireContext().getSharedPreferences(PREVIEW_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ORTHO_IMAGE_URI, record.orthoImageUri?.toString())
            .putString(KEY_ORTHO_IMAGE_NAME, record.orthoImageName)
            .putString(KEY_ORTHO_WORLD_URI, record.orthoWorldUri?.toString())
            .putString(KEY_ORTHO_WORLD_NAME, record.orthoWorldName)
            .putString(KEY_POINT_CLOUD_URI, record.pointCloudUri?.toString())
            .putString(KEY_POINT_CLOUD_NAME, record.pointCloudName)
            .apply()
    }

    private fun clearLegacyPreviewReferences() {
        requireContext().getSharedPreferences(PREVIEW_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun showLoadError(error: Throwable) {
        Toast.makeText(
            requireContext(),
            getString(R.string.ortho_load_failed, error.message ?: error.javaClass.simpleName),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun isSupportedPointCloudFile(fileName: String): Boolean {
        val lowerName = fileName.lowercase(Locale.US)
        return SUPPORTED_POINT_CLOUD_EXTENSIONS.any { lowerName.endsWith(it) }
    }

    companion object {
        private const val REQUEST_OPEN_TIFF = 4301
        private const val REQUEST_OPEN_WORLD = 4302
        private const val REQUEST_OPEN_POINT_CLOUD = 4303
        private const val MAX_ORTHO_PREVIEW_DIMENSION_PX = 2048
        private const val PREVIEW_PREFS = "preview_assets"
        private const val KEY_ORTHO_IMAGE_URI = "ortho_image_uri"
        private const val KEY_ORTHO_IMAGE_NAME = "ortho_image_name"
        private const val KEY_ORTHO_WORLD_URI = "ortho_world_uri"
        private const val KEY_ORTHO_WORLD_NAME = "ortho_world_name"
        private const val KEY_POINT_CLOUD_URI = "point_cloud_uri"
        private const val KEY_POINT_CLOUD_NAME = "point_cloud_name"
        private val SUPPORTED_POINT_CLOUD_EXTENSIONS = listOf(".ply", ".pcd", ".csv", ".txt", ".xyz")
    }
}
