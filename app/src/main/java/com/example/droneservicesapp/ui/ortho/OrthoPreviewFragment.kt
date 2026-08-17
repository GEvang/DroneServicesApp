package com.example.droneservicesapp.ui.ortho

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.ortho.OrthoBounds
import com.example.droneservicesapp.data.ortho.SimpleTiffDecoder
import com.example.droneservicesapp.data.ortho.WorldFileParser
import com.example.droneservicesapp.databinding.FragmentOrthoPreviewBinding
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.home.binders.MissionParamsController
import com.example.droneservicesapp.ui.home.components.EsriWorldImageryTileSource
import com.example.droneservicesapp.ui.preview.MissionPreviewPathSynchronizer
import com.example.droneservicesapp.ui.preview.PreviewMapFocus
import com.example.droneservicesapp.ui.preview.PreviewAssetsViewModel
import com.example.droneservicesapp.ui.preview.buildSurveyDirectionSegments
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

class OrthoPreviewFragment : Fragment() {

    private var _binding: FragmentOrthoPreviewBinding? = null
    private val binding get() = _binding!!
    private val previewAssetsViewModel: PreviewAssetsViewModel by activityViewModels()
    private val activityViewModel: MainActivityViewModel by activityViewModels()
    private val droneViewModel: DroneViewModel by activityViewModels()
    private val tiffDecoder = SimpleTiffDecoder()
    private val worldFileParser = WorldFileParser()
    private var bitmap: Bitmap? = null
    private var bitmapFileName: String? = null
    private var sourceImageWidth: Int? = null
    private var sourceImageHeight: Int? = null
    private var bounds: OrthoBounds? = null
    private var worldFileName: String? = null
    private var overlay: OrthoImageOverlay? = null
    private var missionAreaOverlay: Polygon? = null
    private var missionPathOverlay: Polyline? = null
    private var routePathOverlay: Polyline? = null
    private val missionDirectionMarkers = mutableListOf<Marker>()
    private val missionWaypointMarkers = mutableListOf<Marker>()
    private val missionInfoMarkers = mutableListOf<Marker>()
    private var directionArrowIcon: BitmapDrawable? = null
    private var surveyWaypointIcon: BitmapDrawable? = null
    private var orthoLoadJob: Job? = null
    private var isOpeningFilePicker = false
    private lateinit var missionParamsController: MissionParamsController
    private var missionPathSynchronizer: MissionPreviewPathSynchronizer? = null

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
        setupMissionOverlayObservers()
        if (restorePreviewAsset()) {
            renderStatus()
        } else if (!restorePersistedPreviewAsset()) {
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
        missionPathSynchronizer?.clear()
        missionPathSynchronizer = null
        overlay?.let { binding.orthoMap.overlays.remove(it) }
        overlay = null
        clearMissionOverlays()
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

    private fun setupMissionOverlayObservers() {
        activityViewModel.missionArea.observe(viewLifecycleOwner) {
            updateMissionPanelVisibility()
            renderMissionOverlays()
        }
        activityViewModel.surveyPath.observe(viewLifecycleOwner) { renderMissionOverlays() }
        activityViewModel.routeWaypoints.observe(viewLifecycleOwner) { renderMissionOverlays() }
        activityViewModel.activePlanningWorkflow.observe(viewLifecycleOwner) {
            updateMissionPanelVisibility()
            renderMissionOverlays()
        }
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
            val bottomDockHeight = binding.orthoBottomUtilityDock.height
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

    private fun renderMissionOverlays() {
        val currentBinding = _binding ?: return
        clearMissionOverlays()

        val polygonPoints = activityViewModel.missionArea.value?.vertices.orEmpty()
            .map { GeoPoint(it.latitude, it.longitude) }
        if (polygonPoints.size >= 3) {
            missionAreaOverlay = Polygon(currentBinding.orthoMap).apply {
                points = polygonPoints
                outlinePaint.color = MISSION_AREA_COLOR
                outlinePaint.strokeWidth = MISSION_AREA_STROKE_WIDTH
                fillPaint.color = MISSION_AREA_FILL_COLOR
            }
            currentBinding.orthoMap.overlays.add(missionAreaOverlay)
        }

        val surveyPoints = activityViewModel.surveyPath.value.orEmpty()
        if (surveyPoints.size >= 2) {
            missionPathOverlay = createPathOverlay(surveyPoints, SURVEY_PATH_COLOR)
            currentBinding.orthoMap.overlays.add(missionPathOverlay)
            renderSurveyWaypointMarkers(surveyPoints)
            renderSurveyDirectionMarkers(surveyPoints)
        }

        val routePoints = activityViewModel.routeWaypoints.value.orEmpty().map {
            LatLng(it.latitude, it.longitude)
        }
        if (routePoints.size >= 2) {
            routePathOverlay = createPathOverlay(routePoints, ROUTE_PATH_COLOR)
            currentBinding.orthoMap.overlays.add(routePathOverlay)
        }

        renderSurveyInfoMarkers(activityViewModel.missionArea.value?.vertices.orEmpty())
        currentBinding.orthoMap.invalidate()
    }

    private fun createPathOverlay(points: List<LatLng>, color: Int): Polyline {
        return Polyline(binding.orthoMap).apply {
            setPoints(points.map { GeoPoint(it.latitude, it.longitude) })
            outlinePaint.color = color
            outlinePaint.strokeWidth = MISSION_PATH_STROKE_WIDTH
        }
    }

    private fun renderSurveyDirectionMarkers(path: List<LatLng>) {
        val map = _binding?.orthoMap ?: return
        buildSurveyDirectionSegments(path, MAX_SURVEY_DIRECTION_MARKERS).forEach { segment ->
            val from = GeoPoint(segment.from.latitude, segment.from.longitude)
            val to = GeoPoint(segment.to.latitude, segment.to.longitude)
            val marker = Marker(map).apply {
                position = GeoPoint(
                    (from.latitude + to.latitude) / 2.0,
                    (from.longitude + to.longitude) / 2.0
                )
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = directionArrowIcon ?: createDirectionArrowIcon().also { directionArrowIcon = it }
                rotation = screenVectorRotationDegrees(from, to)
            }
            missionDirectionMarkers += marker
            map.overlays.add(marker)
        }
    }

    private fun renderSurveyWaypointMarkers(path: List<LatLng>) {
        val map = _binding?.orthoMap ?: return
        path.forEach { point ->
            val marker = Marker(map).apply {
                position = GeoPoint(point.latitude, point.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = surveyWaypointIcon ?: createSurveyWaypointIcon().also { surveyWaypointIcon = it }
            }
            missionWaypointMarkers += marker
            map.overlays.add(marker)
        }
    }

    private fun renderSurveyInfoMarkers(areaVertices: List<LatLng>) {
        val map = _binding?.orthoMap ?: return
        perimeterSegments(areaVertices).forEach { segment ->
            val from = segment[0]
            val to = segment[1]
            val marker = Marker(map).apply {
                position = GeoPoint(
                    (from.latitude + to.latitude) / 2.0,
                    (from.longitude + to.longitude) / 2.0
                )
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createTextMarkerIcon(formatDistanceLabel(SphericalUtil.computeDistanceBetween(from, to)))
            }
            missionInfoMarkers += marker
            map.overlays.add(marker)
        }

        if (areaVertices.size >= 3) {
            val marker = Marker(map).apply {
                position = GeoPoint(
                    areaVertices.map { it.latitude }.average(),
                    areaVertices.map { it.longitude }.average()
                )
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createTextMarkerIcon(
                    text = "Area ${formatAreaLabel(SphericalUtil.computeArea(areaVertices))}",
                    emphasized = true
                )
            }
            missionInfoMarkers += marker
            map.overlays.add(marker)
        }
    }

    private fun clearMissionOverlays() {
        val map = _binding?.orthoMap ?: return
        listOfNotNull(missionAreaOverlay, missionPathOverlay, routePathOverlay).forEach { overlay ->
            map.overlays.remove(overlay)
        }
        map.overlays.removeAll(missionDirectionMarkers)
        map.overlays.removeAll(missionWaypointMarkers)
        map.overlays.removeAll(missionInfoMarkers)
        missionDirectionMarkers.clear()
        missionWaypointMarkers.clear()
        missionInfoMarkers.clear()
        missionAreaOverlay = null
        missionPathOverlay = null
        routePathOverlay = null
    }

    private fun createDirectionArrowIcon(): BitmapDrawable {
        val density = resources.displayMetrics.density
        val size = (26 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 0, 0, 0)
            style = Paint.Style.FILL
        }
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SURVEY_PATH_COLOR
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 1.2f * density
            style = Paint.Style.STROKE
        }
        val path = Path().apply {
            moveTo(size - 3f * density, center)
            lineTo(5f * density, size - 5f * density)
            lineTo(10f * density, center)
            lineTo(5f * density, 5f * density)
            close()
        }
        canvas.drawCircle(center, center, center - 2f * density, shadowPaint)
        canvas.drawPath(path, arrowPaint)
        canvas.drawPath(path, strokePaint)
        return BitmapDrawable(resources, bitmap)
    }

    private fun createSurveyWaypointIcon(): BitmapDrawable {
        val density = resources.displayMetrics.density
        val size = (10f * density).toInt().coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(requireContext(), R.color.ds_color_shell_warning)
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 10, 15, 20)
            strokeWidth = 1.2f * density
            style = Paint.Style.STROKE
        }
        canvas.drawCircle(center, center, center - 1.5f * density, fillPaint)
        canvas.drawCircle(center, center, center - 1.5f * density, strokePaint)
        return BitmapDrawable(resources, bitmap)
    }

    private fun createTextMarkerIcon(text: String, emphasized: Boolean = false): BitmapDrawable {
        val density = resources.displayMetrics.density
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f * density
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)
        val horizontalPadding = (10f * density).toInt()
        val verticalPadding = (6f * density).toInt()
        val width = max(bounds.width() + horizontalPadding * 2, (60f * density).toInt())
        val height = bounds.height() + verticalPadding * 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (emphasized) MISSION_AREA_INFO_COLOR else Color.argb(228, 12, 21, 29)
            style = Paint.Style.FILL
        }
        val radius = 10f * density
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, backgroundPaint)
        val baseline = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, horizontalPadding.toFloat(), baseline, textPaint)
        return BitmapDrawable(resources, bitmap)
    }

    private fun perimeterSegments(vertices: List<LatLng>): List<List<LatLng>> {
        if (vertices.size < 2) return emptyList()
        val openSegments = vertices.zipWithNext().map { (from, to) -> listOf(from, to) }
        return if (vertices.size >= 3) {
            openSegments + listOf(listOf(vertices.last(), vertices.first()))
        } else {
            openSegments
        }
    }

    private fun screenVectorRotationDegrees(from: GeoPoint, to: GeoPoint): Float {
        val projection = binding.orthoMap.projection
        val fromPixel = projection.toPixels(from, null)
        val toPixel = projection.toPixels(to, null)
        val dx = (toPixel.x - fromPixel.x).toDouble()
        val dy = (toPixel.y - fromPixel.y).toDouble()
        if (dx == 0.0 && dy == 0.0) return 0f
        return ((-Math.toDegrees(atan2(dy, dx)) + 360.0) % 360.0).toFloat()
    }

    private fun formatDistanceLabel(distanceMeters: Double): String {
        return if (distanceMeters >= 1000.0) {
            String.format(Locale.US, "%.1f km", distanceMeters / 1000.0)
        } else {
            "${distanceMeters.roundToInt().coerceAtLeast(0)} m"
        }
    }

    private fun formatAreaLabel(areaSquareMeters: Double): String {
        return if (areaSquareMeters >= 10_000.0) {
            String.format(Locale.US, "%.2f ha", areaSquareMeters / 10_000.0)
        } else {
            "${areaSquareMeters.roundToInt().coerceAtLeast(0)} m\u00b2"
        }
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
        private const val MISSION_AREA_COLOR = 0xFF50C8FF.toInt()
        private const val MISSION_AREA_FILL_COLOR = 0x3C50C8FF
        private const val SURVEY_PATH_COLOR = 0xFF29E6DA.toInt()
        private const val ROUTE_PATH_COLOR = 0xFF4DFF59.toInt()
        private const val MISSION_AREA_INFO_COLOR = 0xFF22BDB5.toInt()
        private const val MISSION_AREA_STROKE_WIDTH = 4f
        private const val MISSION_PATH_STROKE_WIDTH = 6f
        private const val MAX_SURVEY_DIRECTION_MARKERS = 80
        private const val MIN_PREVIEW_PANEL_WIDTH_PX = 220
        private const val MIN_PREVIEW_PANEL_HEIGHT_PX = 180
    }
}
