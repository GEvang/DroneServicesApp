package com.example.droneservicesapp.ui.geoawareness

import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.domain.geoawareness.GeoAltitudeContext
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.LiveGeoAwarenessChecker
import com.example.droneservicesapp.domain.geoawareness.LiveGeoAwarenessProximityResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Evaluates dedicated-screen live geo status with cancellation and stale-result protection. */
class GeoAwarenessLiveStatusController(
    private val scope: CoroutineScope,
    private val eventLogger: GeoAwarenessEventLogger,
    private val onState: (State) -> Unit,
    private val onAuditLogChanged: () -> Unit,
) {
    enum class Kind { UNKNOWN, DEGRADED, CLEAR, INSIDE, NEAR, VERTICAL_NEAR }

    data class State(
        val kind: Kind,
        val telemetry: GeoAwarenessTelemetryObserver.Snapshot?,
        val datasetInfo: com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo? = null,
        val insideZones: List<GeoZone> = emptyList(),
        val proximity: LiveGeoAwarenessProximityResult? = null,
        val message: String? = null,
    )

    private val checker = LiveGeoAwarenessChecker()
    private var dataset: GeoAwarenessDatasetStateCoordinator.Snapshot? = null
    private var telemetry: GeoAwarenessTelemetryObserver.Snapshot? = null
    private var calculationJob: Job? = null
    private var generation = 0L
    private var active = true
    private var previousState: State? = null

    fun updateDataset(snapshot: GeoAwarenessDatasetStateCoordinator.Snapshot) {
        dataset = snapshot
        evaluate()
    }

    fun updateTelemetry(snapshot: GeoAwarenessTelemetryObserver.Snapshot) {
        telemetry = snapshot
        evaluate()
    }

    fun clear() {
        active = false
        generation++
        calculationJob?.cancel()
        calculationJob = null
    }

    private fun evaluate() {
        if (!active) return
        val currentTelemetry = telemetry
        val currentDataset = dataset
        calculationJob?.cancel()
        val request = ++generation
        val immediate = immediateState(currentTelemetry, currentDataset)
        if (immediate != null) {
            publish(immediate)
            return
        }
        val usableTelemetry = requireNotNull(currentTelemetry)
        val usableDataset = requireNotNull(currentDataset)
        calculationJob = scope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val position = requireNotNull(usableTelemetry.position)
                    val altitude = GeoAltitudeContext(
                        aglMeters = usableTelemetry.altitudeAglMeters,
                        amslMeters = usableTelemetry.altitudeAmslMeters,
                    )
                    val inside = checker.checkDronePosition(
                        dronePosition = position,
                        altitudeContext = altitude,
                        zones = usableDataset.zones,
                    )
                    val proximity = if (inside.isEmpty()) checker.findNearestZoneWithinThreshold(
                        position = position,
                        zones = usableDataset.zones,
                        thresholdMeters = NEAR_ZONE_THRESHOLD_METERS,
                        altitudeContext = altitude,
                        groundSpeedMetersPerSecond = usableTelemetry.groundSpeedMetersPerSecond,
                        headingDegrees = usableTelemetry.headingDegrees,
                        verticalSpeedMetersPerSecond = usableTelemetry.verticalSpeedMetersPerSecond,
                    ) else null
                    inside to proximity
                }
                if (!active || request != generation) return@launch
                val (inside, proximity) = result
                publish(State(
                    kind = when {
                        inside.isNotEmpty() -> Kind.INSIDE
                        proximity?.warningMode?.startsWith("VERTICAL") == true -> Kind.VERTICAL_NEAR
                        proximity != null -> Kind.NEAR
                        else -> Kind.CLEAR
                    },
                    telemetry = usableTelemetry,
                    datasetInfo = usableDataset.datasetInfo,
                    insideZones = inside,
                    proximity = proximity,
                ))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                if (request == generation) calculationJob = null
            }
        }
    }

    private fun immediateState(
        telemetry: GeoAwarenessTelemetryObserver.Snapshot?,
        dataset: GeoAwarenessDatasetStateCoordinator.Snapshot?,
    ): State? = when {
        telemetry == null || telemetry.position == null -> State(Kind.UNKNOWN, telemetry, dataset?.datasetInfo, message = "Drone position unavailable")
        !telemetry.connected -> State(Kind.DEGRADED, telemetry, dataset?.datasetInfo, message = "Drone disconnected")
        !telemetry.hasUsableGpsFix -> State(Kind.DEGRADED, telemetry, dataset?.datasetInfo, message = "GPS fix unavailable")
        dataset == null || dataset.zones.isEmpty() -> State(Kind.DEGRADED, telemetry, dataset?.datasetInfo, message = "Geo-awareness dataset unavailable")
        dataset.loadError != null -> State(Kind.DEGRADED, telemetry, dataset.datasetInfo, message = dataset.loadError.message ?: "Geo-awareness dataset degraded")
        else -> null
    }

    private fun publish(state: State) {
        if (!active) return
        logTransitions(previousState, state)
        previousState = state
        onState(state)
    }

    private fun logTransitions(previous: State?, current: State) {
        val previousIds = previous?.insideZones.orEmpty().mapTo(linkedSetOf()) { it.id }
        val currentById = current.insideZones.associateBy { it.id }
        val currentIds = currentById.keys
        (currentIds - previousIds).forEach { id -> logZoneTransition(GeoAwarenessEventType.LIVE_ZONE_ENTERED, currentById.getValue(id), current) }
        (previousIds - currentIds).forEach { id ->
            val zone = previous?.insideZones?.firstOrNull { it.id == id } ?: return@forEach
            logZoneTransition(GeoAwarenessEventType.LIVE_ZONE_EXITED, zone, current)
        }
        val signature = "${current.kind}:${currentIds.sorted().joinToString(",")}:${current.proximity?.nearestZone?.id.orEmpty()}"
        val previousSignature = previous?.let { "${it.kind}:${it.insideZones.map(GeoZone::id).sorted().joinToString(",")}:${it.proximity?.nearestZone?.id.orEmpty()}" }
        if (signature != previousSignature) {
            eventLogger.logSimple(
                type = GeoAwarenessEventType.LIVE_STATUS_CHANGED,
                severity = if (current.kind in setOf(Kind.INSIDE, Kind.NEAR, Kind.VERTICAL_NEAR, Kind.DEGRADED)) "WARNING" else "INFO",
                message = "Live geo-awareness status changed to ${current.kind.name}",
                datasetTitle = current.datasetInfo?.title,
                datasetVersion = current.datasetInfo?.version,
                zoneIds = current.insideZones.map { it.id },
                zoneNames = current.insideZones.map { it.name },
                latitude = current.telemetry?.position?.lat,
                longitude = current.telemetry?.position?.lon,
                altitudeMeters = current.telemetry?.altitudeAglMeters,
                speedMetersPerSecond = current.telemetry?.groundSpeedMetersPerSecond,
                headingDegrees = current.telemetry?.headingDegrees,
                details = mapOf("status" to current.kind.name),
            )
            onAuditLogChanged()
        }
    }

    private fun logZoneTransition(type: GeoAwarenessEventType, zone: GeoZone, state: State) {
        eventLogger.logSimple(
            type = type,
            severity = "WARNING",
            message = if (type == GeoAwarenessEventType.LIVE_ZONE_ENTERED) "Drone entered geo-zone" else "Drone exited geo-zone",
            datasetTitle = state.datasetInfo?.title,
            datasetVersion = state.datasetInfo?.version,
            zoneIds = listOf(zone.id),
            zoneNames = listOf(zone.name),
            restriction = zone.restriction.name,
            latitude = state.telemetry?.position?.lat,
            longitude = state.telemetry?.position?.lon,
            altitudeMeters = state.telemetry?.altitudeAglMeters,
        )
    }

    private companion object {
        const val NEAR_ZONE_THRESHOLD_METERS = 100.0
    }
}
