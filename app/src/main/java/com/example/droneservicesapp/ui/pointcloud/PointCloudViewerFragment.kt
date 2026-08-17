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
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.pointcloud.PlyPointCloudParser
import com.example.droneservicesapp.data.pointcloud.PointCloudData
import com.example.droneservicesapp.databinding.FragmentPointCloudViewerBinding
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.home.binders.MissionParamsController
import com.example.droneservicesapp.ui.preview.MissionPreviewPathSynchronizer
import com.example.droneservicesapp.ui.preview.PreviewMapFocus
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.preview.buildSurveyDirectionSegments
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class PointCloudViewerFragment : Fragment() {

    private var _binding: FragmentPointCloudViewerBinding? = null
    private val binding get() = _binding!!
    private val previewAssetsViewModel: PreviewAssetsViewModel by activityViewModels()
    private val activityViewModel: MainActivityViewModel by activityViewModels()
    private val droneViewModel: DroneViewModel by activityViewModels()
    private val parser = PlyPointCloudParser()
    private var missionOverlayLineCount = 0
    private var missionOverlayPointCount = 0
    private lateinit var missionParamsController: MissionParamsController
    private var missionPathSynchronizer: MissionPreviewPathSynchronizer? = null

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
        configureMissionPanelBounds()
        updateMissionPanelVisibility()
        missionParamsController = MissionParamsController(
            context = requireContext(),
            rootView = binding.root,
            lifecycleOwner = viewLifecycleOwner,
            activityViewModel = activityViewModel,
            droneViewModel = droneViewModel,
            loadPreferencesOnShow = false
        )
        missionParamsController.show()
        missionPathSynchronizer = MissionPreviewPathSynchronizer(
            lifecycleOwner = viewLifecycleOwner,
            activityViewModel = activityViewModel,
            previewAssetsViewModel = previewAssetsViewModel
        ).also { it.bind() }
        binding.pointCloudPreviewCycleButton.setOnClickListener {
            previewAssetsViewModel.requestMapFocus(PreviewMapFocus.POINT_CLOUD)
            val navController = findNavController()
            if (!navController.popBackStack(R.id.nav_maps_home, false)) {
                navController.navigate(R.id.nav_maps_home)
            }
        }
        binding.pointCloudBottomUtilityDock.bringToFront()
        binding.pointCloudLoadButton.setOnClickListener { openPointCloudPicker() }
        binding.pointCloudResetButton.setOnClickListener { binding.pointCloudGlView.resetCamera() }
        binding.pointCloudSizeSlider.addOnChangeListener { _, value, _ ->
            binding.pointCloudGlView.setPointSize(value)
        }
        setupMissionOverlayObservers()
        restorePreviewAsset()
    }

    override fun onResume() {
        super.onResume()
        binding.pointCloudGlView.onResume()
        binding.pointCloudBottomUtilityDock.bringToFront()
    }

    override fun onPause() {
        binding.pointCloudGlView.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        missionPathSynchronizer?.clear()
        missionPathSynchronizer = null
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
                updateMissionOverlay()
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
        updateMissionOverlay()
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
                updateMissionOverlay()
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

    private fun setupMissionOverlayObservers() {
        activityViewModel.missionArea.observe(viewLifecycleOwner) {
            updateMissionPanelVisibility()
            updateMissionOverlay()
        }
        activityViewModel.surveyPath.observe(viewLifecycleOwner) { updateMissionOverlay() }
        activityViewModel.terrainSurveyWaypoints.observe(viewLifecycleOwner) { updateMissionOverlay() }
        activityViewModel.routeWaypoints.observe(viewLifecycleOwner) { updateMissionOverlay() }
    }

    private fun updateMissionPanelVisibility() {
        val hasArea = (activityViewModel.missionArea.value?.vertices?.size ?: 0) >= 3
        binding.missionParamsSideView.isVisible = hasArea
    }

    private fun configureMissionPanelBounds() {
        binding.root.post {
            val rootWidth = binding.root.width
            val rootHeight = binding.root.height
            if (rootWidth <= 0 || rootHeight <= 0) return@post
            val margin = resources.getDimensionPixelSize(R.dimen.phone_map_overlay_card_margin)
            val desiredWidth = resources.getDimensionPixelSize(R.dimen.phone_map_panel_left_width)
            val maxHeight = resources.getDimensionPixelSize(R.dimen.phone_map_panel_max_height)
            val bottomDockHeight = binding.pointCloudBottomUtilityDock.height
            val availableWidth = (rootWidth - margin * 2).coerceAtLeast(MIN_PREVIEW_PANEL_WIDTH_PX)
            val availableHeight = (
                rootHeight - margin * 3 - bottomDockHeight - resources.getDimensionPixelSize(R.dimen.preview_mode_rail_bottom_margin)
                ).coerceAtLeast(MIN_PREVIEW_PANEL_HEIGHT_PX)
            val layoutParams = binding.missionParamsSideView.layoutParams as MarginLayoutParams
            val width = min(desiredWidth, availableWidth)
            val height = min(maxHeight, availableHeight)
            if (layoutParams.width != width || layoutParams.height != height) {
                layoutParams.width = width
                layoutParams.height = height
                binding.missionParamsSideView.layoutParams = layoutParams
            }
        }
    }

    private fun updateMissionOverlay() {
        val currentBinding = _binding ?: return
        val pointCloud = previewAssetsViewModel.pointCloudAsset?.pointCloud
        val frame = pointCloud?.coordinateFrame
        if (pointCloud == null || frame == null) {
            missionOverlayLineCount = 0
            missionOverlayPointCount = 0
            currentBinding.pointCloudGlView.setMissionOverlay(null)
            updateOverlayStatusText()
            return
        }

        val centeredMaxZ = pointCloud.bounds.maxZ - pointCloud.bounds.centerZ
        val overlayZ = centeredMaxZ + max(pointCloud.bounds.maxSpan * 0.02f, MIN_MISSION_OVERLAY_Z_OFFSET)
        val vertices = ArrayList<Float>()
        val colors = ArrayList<Float>()
        val pointVertices = ArrayList<Float>()
        val pointColors = ArrayList<Float>()

        addClosedLineStrip(
            source = activityViewModel.missionArea.value?.vertices.orEmpty(),
            z = overlayZ,
            color = POLYGON_COLOR,
            vertices = vertices,
            colors = colors
        ) { point ->
            frame.latLonToLocal(point.latitude, point.longitude)
        }

        val surveyPoints = activityViewModel.surveyPath.value.orEmpty()
        val surveyZValues = pointCloudSurveyZValues(surveyPoints)
        addOpenLineStrip(
            source = surveyPoints,
            z = overlayZ + MISSION_LAYER_Z_STEP,
            zValues = surveyZValues,
            color = SURVEY_PATH_COLOR,
            vertices = vertices,
            colors = colors
        ) { point ->
            frame.latLonToLocal(point.latitude, point.longitude)
        }
        addSurveyWaypointPoints(
            source = surveyPoints,
            z = overlayZ + MISSION_LAYER_Z_STEP * 1.5f,
            zValues = surveyZValues,
            vertices = pointVertices,
            colors = pointColors
        ) { point ->
            frame.latLonToLocal(point.latitude, point.longitude)
        }
        addSurveyDirectionArrows(
            source = surveyPoints,
            z = overlayZ + MISSION_LAYER_Z_STEP * 1.7f,
            zValues = surveyZValues,
            arrowSizeMeters = max(pointCloud.bounds.maxSpan * 0.012f, MIN_MISSION_ARROW_SIZE_METERS),
            vertices = vertices,
            colors = colors
        ) { point ->
            frame.latLonToLocal(point.latitude, point.longitude)
        }

        val routePoints = activityViewModel.routeWaypoints.value.orEmpty().map { waypoint ->
            LatLng(waypoint.latitude, waypoint.longitude)
        }
        addOpenLineStrip(
            source = routePoints,
            z = overlayZ + MISSION_LAYER_Z_STEP * 2f,
            color = ROUTE_COLOR,
            vertices = vertices,
            colors = colors
        ) { point ->
            frame.latLonToLocal(point.latitude, point.longitude)
        }

        if (vertices.isEmpty() && pointVertices.isEmpty()) {
            missionOverlayLineCount = 0
            missionOverlayPointCount = 0
            currentBinding.pointCloudGlView.setMissionOverlay(null)
            updateOverlayStatusText()
            return
        }
        missionOverlayLineCount = vertices.size / (VALUES_PER_VERTEX * VERTICES_PER_LINE)
        missionOverlayPointCount = pointVertices.size / VALUES_PER_VERTEX
        currentBinding.pointCloudGlView.setMissionOverlay(
            PointCloudMissionOverlay(
                vertices = vertices.toFloatArray(),
                colors = colors.toFloatArray(),
                lineVertexCount = vertices.size / VALUES_PER_VERTEX,
                pointVertices = pointVertices.toFloatArray(),
                pointColors = pointColors.toFloatArray(),
                pointVertexCount = pointVertices.size / VALUES_PER_VERTEX
            )
        )
        updateOverlayStatusText()
    }

    private fun addClosedLineStrip(
        source: List<LatLng>,
        z: Float,
        color: FloatArray,
        vertices: MutableList<Float>,
        colors: MutableList<Float>,
        convert: (LatLng) -> Pair<Double, Double>
    ) {
        if (source.size < 3) return
        addOpenLineStrip(
            source = source + source.first(),
            z = z,
            color = color,
            vertices = vertices,
            colors = colors,
            convert = convert
        )
    }

    private fun addOpenLineStrip(
        source: List<LatLng>,
        z: Float,
        zValues: List<Float>? = null,
        color: FloatArray,
        vertices: MutableList<Float>,
        colors: MutableList<Float>,
        convert: (LatLng) -> Pair<Double, Double>
    ) {
        if (source.size < 2) return
        source.zipWithNext().forEachIndexed { index, (from, to) ->
            addMissionVertex(from, zValues?.getOrNull(index) ?: z, color, vertices, colors, convert)
            addMissionVertex(to, zValues?.getOrNull(index + 1) ?: z, color, vertices, colors, convert)
        }
    }

    private fun addMissionVertex(
        point: LatLng,
        z: Float,
        color: FloatArray,
        vertices: MutableList<Float>,
        colors: MutableList<Float>,
        convert: (LatLng) -> Pair<Double, Double>
    ) {
        val (x, y) = convert(point)
        vertices += x.toFloat()
        vertices += y.toFloat()
        vertices += z
        colors += color[0]
        colors += color[1]
        colors += color[2]
    }

    private fun addSurveyWaypointPoints(
        source: List<LatLng>,
        z: Float,
        zValues: List<Float>? = null,
        vertices: MutableList<Float>,
        colors: MutableList<Float>,
        convert: (LatLng) -> Pair<Double, Double>
    ) {
        source.forEachIndexed { index, point ->
            addMissionVertex(point, zValues?.getOrNull(index) ?: z, SURVEY_POINT_COLOR, vertices, colors, convert)
        }
    }

    private fun addSurveyDirectionArrows(
        source: List<LatLng>,
        z: Float,
        zValues: List<Float>? = null,
        arrowSizeMeters: Float,
        vertices: MutableList<Float>,
        colors: MutableList<Float>,
        convert: (LatLng) -> Pair<Double, Double>
    ) {
        buildSurveyDirectionSegments(source, MAX_MISSION_DIRECTION_ARROWS).forEach { segment ->
            val (fromX, fromY) = convert(segment.from)
            val (toX, toY) = convert(segment.to)
            val dx = toX - fromX
            val dy = toY - fromY
            val length = sqrt(dx * dx + dy * dy)
            if (length <= 0.001) return@forEach

            val unitX = dx / length
            val unitY = dy / length
            val arrowLength = min(arrowSizeMeters.toDouble(), length * 0.35)
            val arrowWidth = arrowLength * 0.55
            val midX = (fromX + toX) / 2.0
            val midY = (fromY + toY) / 2.0
            val fromZ = zValues?.getOrNull(source.indexOf(segment.from)) ?: z
            val toZ = zValues?.getOrNull(source.indexOf(segment.to)) ?: z
            val arrowZ = (fromZ + toZ) / 2f
            val tipX = midX + unitX * arrowLength * 0.5
            val tipY = midY + unitY * arrowLength * 0.5
            val baseX = midX - unitX * arrowLength * 0.5
            val baseY = midY - unitY * arrowLength * 0.5
            val perpX = -unitY
            val perpY = unitX
            val leftX = baseX + perpX * arrowWidth * 0.5
            val leftY = baseY + perpY * arrowWidth * 0.5
            val rightX = baseX - perpX * arrowWidth * 0.5
            val rightY = baseY - perpY * arrowWidth * 0.5

            addLocalMissionVertex(tipX, tipY, arrowZ, SURVEY_PATH_COLOR, vertices, colors)
            addLocalMissionVertex(leftX, leftY, arrowZ, SURVEY_PATH_COLOR, vertices, colors)
            addLocalMissionVertex(tipX, tipY, arrowZ, SURVEY_PATH_COLOR, vertices, colors)
            addLocalMissionVertex(rightX, rightY, arrowZ, SURVEY_PATH_COLOR, vertices, colors)
        }
    }

    private fun pointCloudSurveyZValues(surveyPoints: List<LatLng>): List<Float>? {
        val terrainWaypoints = activityViewModel.terrainSurveyWaypoints.value.orEmpty()
        return terrainWaypoints
            .takeIf { it.size == surveyPoints.size && it.isNotEmpty() }
            ?.map { it.displayAltitudeMeters.toFloat() }
    }

    private fun addLocalMissionVertex(
        x: Double,
        y: Double,
        z: Float,
        color: FloatArray,
        vertices: MutableList<Float>,
        colors: MutableList<Float>
    ) {
        vertices += x.toFloat()
        vertices += y.toFloat()
        vertices += z
        colors += color[0]
        colors += color[1]
        colors += color[2]
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
        updateOverlayStatusText()
    }

    private fun updateOverlayStatusText() {
        val currentBinding = _binding ?: return
        val pointCloud = previewAssetsViewModel.pointCloudAsset?.pointCloud ?: return
        val baseStats = getString(
            R.string.point_cloud_stats,
            pointCloud.displayedPointCount,
            pointCloud.totalPointCount,
            pointCloud.bounds.spanX,
            pointCloud.bounds.spanY,
            pointCloud.bounds.spanZ,
            if (pointCloud.hasRgb) getString(R.string.point_cloud_color_rgb) else getString(R.string.point_cloud_color_height)
        )
        val overlayStatus = when {
            pointCloud.coordinateFrame == null -> "Mission overlay: no georeference"
            missionOverlayLineCount == 0 && missionOverlayPointCount == 0 -> "Mission overlay: no mission lines"
            else -> "Mission overlay: $missionOverlayLineCount lines, $missionOverlayPointCount photo points"
        }
        val areaVertices = activityViewModel.missionArea.value?.vertices.orEmpty()
        val areaStatus = if (areaVertices.size >= 3) {
            "Area: ${formatAreaLabel(SphericalUtil.computeArea(areaVertices))}"
        } else {
            "Area: --"
        }
        currentBinding.pointCloudStatsText.text = "$baseStats\n$overlayStatus\n$areaStatus"
    }

    private fun formatAreaLabel(areaSquareMeters: Double): String {
        return if (areaSquareMeters >= 10_000.0) {
            String.format(Locale.US, "%.2f ha", areaSquareMeters / 10_000.0)
        } else {
            "${areaSquareMeters.toInt().coerceAtLeast(0)} m\u00b2"
        }
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
        private const val VALUES_PER_VERTEX = 3
        private const val VERTICES_PER_LINE = 2
        private const val MIN_MISSION_OVERLAY_Z_OFFSET = 0.5f
        private const val MIN_MISSION_ARROW_SIZE_METERS = 1.0f
        private const val MISSION_LAYER_Z_STEP = 0.2f
        private const val MAX_MISSION_DIRECTION_ARROWS = 80
        private val POLYGON_COLOR = floatArrayOf(0.31f, 0.78f, 1.0f)
        private val SURVEY_PATH_COLOR = floatArrayOf(0.16f, 0.90f, 0.85f)
        private val SURVEY_POINT_COLOR = floatArrayOf(0.89f, 0.65f, 0.25f)
        private val ROUTE_COLOR = floatArrayOf(0.3f, 1.0f, 0.35f)
        private const val MIN_PREVIEW_PANEL_WIDTH_PX = 220
        private const val MIN_PREVIEW_PANEL_HEIGHT_PX = 180
    }
}
