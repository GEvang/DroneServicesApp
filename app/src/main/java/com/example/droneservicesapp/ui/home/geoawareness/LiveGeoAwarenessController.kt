package com.example.droneservicesapp.ui.home.geoawareness

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.domain.geoawareness.GeoAltitudeContext
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessResult
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.LiveGeoAwarenessChecker
import com.example.droneservicesapp.domain.geoawareness.LiveGeoAwarenessProximityResult
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.mavserver.GpsFixQuality
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns geo datasets, planning checks, live proximity state, overlays, and presentation. */
class LiveGeoAwarenessController(
    private val context: Context,
    private val root: View,
    private val scope: LifecycleCoroutineScope,
    private val viewModel: MainActivityViewModel,
    private val datasetController: GeoAwarenessDatasetController,
    private val planningController: GeoAwarenessPlanningController,
    private val overlayController: GeoZoneOverlayController,
    private val eventLogger: GeoAwarenessEventLogger,
    private val eventTracker: GeoAwarenessEventTracker,
    private val dialogController: GeoAwarenessDialogController,
    private val telemetry: () -> Telemetry,
    private val isActive: () -> Boolean,
) {
    data class Telemetry(
        val position: LatLon?,
        val altitudeAglMeters: Double?,
        val altitudeAmslMeters: Double?,
        val groundSpeedMetersPerSecond: Float?,
        val verticalSpeedMetersPerSecond: Float?,
        val headingDegrees: Double?,
        val connected: Boolean,
        val gpsFixQuality: GpsFixQuality,
    )

    private val checker = LiveGeoAwarenessChecker()
    private val threatPresenter = LiveGeoThreatPresenter(context)
    private val statusPanel = root.findViewById<View?>(R.id.liveGeoAwarenessPanel)
    private var planningJob: Job? = null
    private var liveUpdateJob: Job? = null
    private var reloadInProgress = false
    private var latestLiveDronePosition: LatLon? = null
    private var latestInsideZones: List<GeoZone> = emptyList()
    private var latestProximity: LiveGeoAwarenessProximityResult? = null
    private var lastLiveUpdateUptimeMs = 0L
    private var lastTopStatusSignature: String? = null

    var latestPlanningResult: GeoAwarenessResult = GeoAwarenessResult.clear()
        private set

    val datasetInfo: GeoZoneDatasetInfo?
        get() = datasetController.snapshot.datasetInfo

    val health: GeoAwarenessHealth?
        get() = datasetController.snapshot.health

    fun initialize() {
        // The compact top-bar status is the map screen's only live-geo indicator.
        statusPanel?.visibility = View.GONE
        statusPanel?.setOnClickListener(null)
        updateTopStatus(context.getString(R.string.live_geo_unknown_status), UNKNOWN_COLOR)
        loadZonesIfNeeded()
        renderLayerIfVisible()
        updatePlanningStatus()
    }

    fun loadZonesIfNeeded(): Boolean {
        val snapshot = datasetController.snapshot
        if (snapshot.zones.isNotEmpty()) {
            if (snapshot.health == null) publishHealth(datasetController.ensureHealth(viewModel.geoZoneDatasetRecords.value.orEmpty()))
            return true
        }
        if (datasetController.loadAttempted) return snapshot.loadError == null
        return try {
            val outcome = datasetController.loadCurrent()
            applyLoadOutcome(outcome)
            eventTracker.datasetLoaded(outcome.result.datasetInfo)
            eventTracker.datasetValidation(outcome.result.validationResult, outcome.result.datasetInfo)
            eventTracker.multiDatasetLoaded(outcome.result)
            health?.let(eventTracker::healthEvaluation)
            true
        } catch (error: Exception) {
            Log.e(LAYER_TAG, "Failed to load geo-awareness zones", error)
            applyFailure(error, emptyList())
            eventTracker.datasetLoadFailed(error)
            health?.let(eventTracker::healthEvaluation)
            false
        }
    }

    fun reloadCurrentDataset() {
        if (reloadInProgress) return
        reloadInProgress = true
        scope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) { datasetController.reloadCurrent() }
                if (!isActive()) return@launch
                applyLoadOutcome(outcome)
                eventTracker.multiDatasetLoaded(outcome.result)
                renderLayerIfVisible()
                updatePlanningStatus()
                updateLiveFromActiveSource()
                health?.let(eventTracker::healthEvaluation)
            } catch (error: Exception) {
                Log.e(LAYER_TAG, "Failed to reload geo-awareness dataset", error)
                applyFailure(error, viewModel.geoZoneDatasetRecords.value.orEmpty())
                health?.let(eventTracker::healthEvaluation)
            } finally {
                reloadInProgress = false
            }
        }
    }

    fun renderLayerIfVisible() {
        if (viewModel.geoAwarenessLayerVisible.value != true) {
            overlayController.clear()
            logLayer(GeoAwarenessEventType.GEO_LAYER_HIDDEN, "Geo-awareness layer hidden")
            return
        }
        if (!loadZonesIfNeeded()) return
        overlayController.renderZones(datasetController.snapshot.zones)
        logLayer(GeoAwarenessEventType.GEO_LAYER_SHOWN, "Geo-awareness layer shown")
    }

    fun updatePlanningStatus() {
        if (!isActive()) return
        latestPlanningResult = evaluatePlanningResult()
        ensureHealth()
        eventTracker.planningStatus(latestPlanningResult)
        logPlanning(latestPlanningResult)
    }

    fun schedulePlanningStatusUpdate() {
        if (!isActive()) return
        val input = planningInput()
        if (!input.hasGeometry) {
            planningJob?.cancel()
            latestPlanningResult = GeoAwarenessResult.clear()
            return
        }
        if (!loadZonesIfNeeded()) return
        val snapshot = input.copy(zones = datasetController.snapshot.zones.toList())
        planningJob?.cancel()
        planningJob = scope.launch {
            val result = withContext(Dispatchers.Default) { planningController.evaluate(snapshot) }
            if (!isActive()) return@launch
            latestPlanningResult = result
            ensureHealth()
            eventTracker.planningStatus(result)
            logPlanning(result)
        }
    }

    fun evaluatePlanningResult(): GeoAwarenessResult {
        val input = planningInput()
        latestPlanningResult = if (!input.hasGeometry || !loadZonesIfNeeded()) GeoAwarenessResult.clear()
        else planningController.evaluate(input.copy(zones = datasetController.snapshot.zones))
        return latestPlanningResult
    }

    fun ensureHealth(): GeoAwarenessHealth {
        val snapshot = datasetController.snapshot
        if (snapshot.zones.isEmpty() && snapshot.datasetInfo == null && snapshot.loadError == null) loadZonesIfNeeded()
        return datasetController.ensureHealth(viewModel.geoZoneDatasetRecords.value.orEmpty()).also(::publishHealth)
    }

    fun updateLiveFromActiveSource() {
        if (!isActive()) return
        val elapsed = SystemClock.uptimeMillis() - lastLiveUpdateUptimeMs
        if (elapsed >= LIVE_UPDATE_INTERVAL_MS && liveUpdateJob == null) {
            lastLiveUpdateUptimeMs = SystemClock.uptimeMillis()
            updateLiveStatus(telemetry())
            return
        }
        if (liveUpdateJob != null) return
        liveUpdateJob = scope.launch {
            delay((LIVE_UPDATE_INTERVAL_MS - elapsed).coerceAtLeast(0L))
            liveUpdateJob = null
            if (!isActive()) return@launch
            lastLiveUpdateUptimeMs = SystemClock.uptimeMillis()
            updateLiveStatus(telemetry())
        }
    }

    fun showDetails() {
        val (title, message) = buildDetails()
        dialogController.showMessage(title, message)
    }

    fun setZoneDetailsEnabled(enabled: Boolean) = overlayController.setZoneDetailsEnabled(enabled)

    fun dispose() {
        planningJob?.cancel()
        liveUpdateJob?.cancel()
        overlayController.clear()
    }

    private fun updateLiveStatus(live: Telemetry) {
        latestLiveDronePosition = live.position
        if (degradedReason(live) != null) {
            clearLiveState()
            updateTopStatus(context.getString(R.string.live_geo_degraded), DEGRADED_COLOR)
            return
        }
        val position = live.position
        if (position == null) {
            bindUnknown()
            return
        }
        if (!loadZonesIfNeeded() || datasetController.snapshot.zones.isEmpty()) {
            bindUnknown()
            return
        }
        val altitudeContext = GeoAltitudeContext(live.altitudeAglMeters, live.altitudeAmslMeters)
        val zones = datasetController.snapshot.zones
        val insideZones = checker.checkDronePosition(position, altitudeContext, zones)
        eventTracker.liveStatus(insideZones, position.lat, position.lon, live.altitudeAglMeters)
        latestInsideZones = insideZones
        val nearThreats = checker.findZonesWithinThreshold(
            position = position,
            zones = zones,
            thresholdMeters = NEAR_ZONE_THRESHOLD_METERS,
            altitudeContext = altitudeContext,
            groundSpeedMetersPerSecond = live.groundSpeedMetersPerSecond?.toDouble(),
            headingDegrees = live.headingDegrees,
            verticalSpeedMetersPerSecond = live.verticalSpeedMetersPerSecond?.toDouble(),
        )
        latestProximity = nearThreats.firstOrNull()
        if (insideZones.isEmpty() && latestProximity == null) {
            updateTopStatus(context.getString(R.string.live_geo_clear_status), CLEAR_COLOR)
        } else {
            renderThreats(position, live, insideZones, nearThreats)
        }
        Log.d(LIVE_TAG, "Live geo-awareness updated: inside=${insideZones.size} highest=${insideZones.firstOrNull()?.restriction}")
    }

    private fun renderThreats(
        position: LatLon,
        live: Telemetry,
        insideZones: List<GeoZone>,
        nearThreats: List<LiveGeoAwarenessProximityResult>,
    ) {
        latestProximity?.let { eventTracker.liveProximity(it, position.lat, position.lon, live.altitudeAglMeters) }
        val insideRows = insideZones.map { threatPresenter.inside(it, position) }
        val nearby = nearThreats.filterNot { proximity -> insideZones.any { it.id == proximity.nearestZone.id } }
        val altitudes = LiveGeoThreatPresenter.Altitudes(live.altitudeAglMeters, live.altitudeAmslMeters)
        val rows = (insideRows + nearby.map { threatPresenter.proximity(it, position, altitudes) }).take(MAX_THREAT_ROWS)
        val highestInside = insideZones.maxByOrNull { threatPresenter.restrictionPriority(it.restriction) }
        val restriction = highestInside?.restriction ?: latestProximity?.restriction
        val label = when {
            highestInside != null -> context.getString(R.string.live_geo_inside_status, threatPresenter.restrictionLabel(highestInside.restriction))
            rows.size > 1 -> context.getString(R.string.live_geo_multiple)
            restriction != null -> threatPresenter.nearRestrictionLabel(restriction)
            else -> context.getString(R.string.live_geo_clear_status)
        }
        val color = restriction?.let(threatPresenter::restrictionColor) ?: CLEAR_COLOR
        updateTopStatus(label, color)
    }

    private fun buildDetails(): Pair<String, String> = when {
        latestLiveDronePosition == null -> context.getString(R.string.live_geo_details_title) to context.getString(R.string.live_geo_no_position)
        latestInsideZones.isNotEmpty() -> {
            val visible = latestInsideZones.take(MAX_DETAIL_ZONES)
            context.getString(R.string.live_geo_warning_title) to buildString {
                appendLine(context.getString(R.string.live_geo_inside_loaded)); appendLine()
                visible.forEach { zone ->
                    appendLine("- ${zone.name}")
                    appendLine("  ${context.getString(R.string.live_geo_restriction, zone.restriction)}")
                    appendLine("  ${context.getString(R.string.live_geo_message, zone.message ?: context.getString(R.string.geo_summary_no_message))}")
                }
                val remaining = latestInsideZones.size - visible.size
                if (remaining > 0) appendLine(context.getString(R.string.geo_summary_more, remaining))
                append(context.getString(R.string.live_geo_verify_authority))
            }
        }
        latestProximity != null -> context.getString(R.string.live_geo_nearby_title) to proximityDetails(latestProximity!!)
        else -> context.getString(R.string.live_geo_details_title) to buildString {
            appendLine(context.getString(R.string.live_geo_outside_all))
            append(context.getString(R.string.live_geo_verify_before_flight))
        }
    }

    private fun proximityDetails(proximity: LiveGeoAwarenessProximityResult) = buildString {
        appendLine(context.getString(R.string.live_geo_nearest_zone, proximity.nearestZone.name))
        appendLine(context.getString(R.string.live_geo_restriction, proximity.restriction))
        appendLine(context.getString(R.string.live_geo_distance, proximity.distanceMeters.toInt().coerceAtLeast(0)))
        appendLine(context.getString(R.string.live_geo_configured_threshold, proximity.configuredThresholdMeters.toInt()))
        appendLine(context.getString(R.string.live_geo_effective_threshold, proximity.effectiveThresholdMeters.toInt()))
        appendLine(context.getString(R.string.live_geo_warning_time, proximity.requiredWarningSeconds))
        proximity.groundSpeedMetersPerSecond?.let { appendLine(context.getString(R.string.live_geo_ground_speed, decimal(it))) }
        proximity.closingSpeedMetersPerSecond?.let { appendLine(context.getString(R.string.live_geo_closing_speed, decimal(it))) }
        proximity.timeToBoundarySeconds?.let { appendLine(context.getString(R.string.live_geo_boundary_time, decimal(it))) }
        proximity.verticalDistanceMeters?.let { appendLine(context.getString(R.string.live_geo_vertical_distance, decimal(it))) }
        proximity.verticalClosingSpeedMetersPerSecond?.let { appendLine(context.getString(R.string.live_geo_vertical_speed, decimal(it))) }
        proximity.verticalTimeToBoundarySeconds?.let { appendLine(context.getString(R.string.live_geo_vertical_time, decimal(it))) }
        appendLine(context.getString(R.string.live_geo_warning_mode, proximity.warningMode))
        datasetInfo?.let { appendLine(context.getString(R.string.live_geo_dataset, it.title, it.version)) }
        proximity.nearestZone.message?.takeIf { it.isNotBlank() }?.let { appendLine(context.getString(R.string.live_geo_message, it)) }
        appendLine(); append(context.getString(R.string.live_geo_outside_near))
    }

    private fun planningInput(): GeoAwarenessPlanningController.Input {
        val missionPolygon = viewModel.missionArea.value?.vertices?.takeIf { it.isNotEmpty() }
            ?.map { LatLon(it.latitude, it.longitude) }
        val surveyPath = viewModel.surveyPath.value.orEmpty().map { LatLon(it.latitude, it.longitude) }
        val pointPath = viewModel.plannedRoutePath.value.orEmpty().takeIf { it.isNotEmpty() }
            ?.map { LatLon(it.latitude, it.longitude) }
            ?: viewModel.routeWaypoints.value.orEmpty().map { LatLon(it.latitude, it.longitude) }
        return GeoAwarenessPlanningController.Input(
            missionPolygon = missionPolygon,
            planningPath = if (viewModel.activePlanningWorkflow.value == PlanningWorkflow.POINTS) pointPath else surveyPath,
            aglAltitudeMeters = viewModel.flightAltProgress.value,
            zones = datasetController.snapshot.zones,
        )
    }

    private fun applyLoadOutcome(outcome: GeoAwarenessDatasetController.LoadOutcome) {
        val result = outcome.result
        viewModel.geoZoneDatasetInfo.value = result.datasetInfo
        viewModel.geoZoneValidationResult.value = result.validationResult
        viewModel.geoZoneDatasetRecords.value = result.datasetRecords
        viewModel.geoZoneImportedActive.value = outcome.snapshot.importedActive
        viewModel.geoAwarenessHealth.value = outcome.snapshot.health
    }

    private fun applyFailure(error: Exception, records: List<com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord>) {
        val snapshot = datasetController.recordFailure(error, records)
        viewModel.geoZoneDatasetInfo.value = snapshot.datasetInfo
        viewModel.geoZoneValidationResult.value = snapshot.validationResult
        viewModel.geoZoneDatasetRecords.value = records
        viewModel.geoZoneImportedActive.value = snapshot.importedActive
        viewModel.geoAwarenessHealth.value = snapshot.health
    }

    private fun publishHealth(value: GeoAwarenessHealth) {
        viewModel.geoAwarenessHealth.value = value
        eventTracker.healthEvaluation(value)
    }

    private fun logLayer(type: GeoAwarenessEventType, message: String) {
        eventLogger.logSimple(
            type = type, severity = "INFO", message = message,
            datasetTitle = datasetInfo?.title, datasetVersion = datasetInfo?.version,
            healthState = health?.state?.name,
        )
    }

    private fun logPlanning(result: GeoAwarenessResult) {
        Log.d(PLANNING_TAG, "Planning geo-awareness updated: conflicts=${result.conflicts.size} highest=${result.highestRestriction} canUpload=${result.canUpload}")
    }

    private fun bindUnknown() {
        clearLiveState()
        updateTopStatus(context.getString(R.string.live_geo_unknown_status), UNKNOWN_COLOR)
    }

    private fun clearLiveState() {
        latestInsideZones = emptyList()
        latestProximity = null
    }

    private fun degradedReason(live: Telemetry): String? {
        if (!live.connected) return context.getString(R.string.live_geo_degraded_no_link)
        return when (live.gpsFixQuality) {
            GpsFixQuality.DISCONNECTED, GpsFixQuality.NO_GPS, GpsFixQuality.UNKNOWN -> context.getString(R.string.live_geo_degraded_gps)
            GpsFixQuality.FIX_2D, GpsFixQuality.FIX_3D, GpsFixQuality.DGPS,
            GpsFixQuality.RTK_FLOAT, GpsFixQuality.RTK_FIXED -> null
        }
    }

    private fun updateTopStatus(label: String, colorHex: String) {
        val signature = "$label|$colorHex"
        if (lastTopStatusSignature == signature) return
        lastTopStatusSignature = signature
        val status = root.findViewById<TextView?>(R.id.top_live_geo_status_text) ?: return
        val color = Color.parseColor(colorHex)
        status.text = label
        status.setTextColor(color)
        root.findViewById<ImageView?>(R.id.top_live_geo_icon)?.apply {
            setImageResource(if (label == context.getString(R.string.live_geo_clear_status)) R.drawable.ic_baseline_check_circle_outline_24 else R.drawable.ic_status_warning_24)
            setColorFilter(color)
        }
    }

    private fun decimal(value: Double) = String.format(Locale.US, "%.2f", value)

    companion object {
        private const val LAYER_TAG = "GeoZoneToggle"
        private const val PLANNING_TAG = "GeoPlanningStatus"
        private const val LIVE_TAG = "LiveGeoAwareness"
        private const val LIVE_UPDATE_INTERVAL_MS = 500L
        private const val NEAR_ZONE_THRESHOLD_METERS = 100.0
        private const val MAX_THREAT_ROWS = 3
        private const val MAX_DETAIL_ZONES = 5
        private const val CLEAR_COLOR = "#48D26D"
        private const val DEGRADED_COLOR = "#FFB26B"
        private const val UNKNOWN_COLOR = "#AAB5C6"
    }
}
