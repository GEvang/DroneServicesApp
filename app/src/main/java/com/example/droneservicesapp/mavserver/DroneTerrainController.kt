package com.example.droneservicesapp.mavserver

import androidx.lifecycle.MutableLiveData
import com.example.droneservicesapp.data.diagnostics.DiagnosticLog
import com.example.droneservicesapp.data.mavlink.MavlinkClient
import com.example.droneservicesapp.data.terrain.TerrainElevationSource
import com.example.droneservicesapp.data.terrain.TerrainPreview
import com.example.droneservicesapp.data.terrain.TerrainPreviewBuilder
import com.example.droneservicesapp.data.terrain.TerrainSourceRepository
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.terrain.TerrainCoveragePlan
import com.example.droneservicesapp.domain.terrain.TerrainCoveragePlanner
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.TerrainCheck
import io.dronefleet.mavlink.common.TerrainData
import io.dronefleet.mavlink.common.TerrainReport
import io.dronefleet.mavlink.common.TerrainRequest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DroneTerrainController(
    private val mavlinkClient: MavlinkClient,
    private val sourceRepository: TerrainSourceRepository,
    private val scope: CoroutineScope,
    private val state: MutableLiveData<TerrainProvisioningState>,
    private val preview: MutableLiveData<TerrainPreview?>,
    private val isConnected: () -> Boolean,
    private val targetSystemId: () -> Int,
    private val previewDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        private const val GCS_COMPONENT_ID = 190
        private const val REQUIRED_GRID_SPACING = 30
        private const val VERIFY_SAMPLE_STRIDE = 4 // 4 x 30 m: one check per MAVLink terrain sub-grid.
        private const val CHECK_RETRY_MS = 750L
        private const val CHECK_RETRIES = 12
    }

    private var source: TerrainElevationSource? = null
    private var preparationJob: Job? = null
    private var verificationJob: Job? = null
    private var preparationGeneration = 0
    private val blocksSent = AtomicInteger(0)
    private var missionUploaded = false
    private var latestPending: Int? = null
    private var expectedCheck: LatLon? = null
    private var expectedCheckConfirmed = false
    @Volatile private var readyForAuto = false

    @Synchronized
    fun prepare(plan: TerrainCoveragePlan, onComplete: (Boolean) -> Unit) {
        prepareSource(plan, requireConnection = true, onComplete = onComplete)
    }

    @Synchronized
    fun download(plan: TerrainCoveragePlan, onComplete: (Boolean) -> Unit) {
        prepareSource(plan, requireConnection = false, onComplete = onComplete)
    }

    @Synchronized
    private fun prepareSource(
        plan: TerrainCoveragePlan,
        requireConnection: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        cancel(closeSource = true)
        val generation = preparationGeneration
        preview.postValue(null)
        if (requireConnection && !isConnected()) {
            state.postValue(TerrainProvisioningState.Failed(TerrainFailure.DISCONNECTED))
            onComplete(false)
            return
        }
        blocksSent.set(0)
        readyForAuto = false
        missionUploaded = false
        preparationJob = scope.launch {
            try {
                val prepared = sourceRepository.prepare(plan) { progress ->
                    if (synchronized(this@DroneTerrainController) { generation == preparationGeneration }) {
                        state.postValue(
                            TerrainProvisioningState.PreparingSource(
                                progress.completedTiles,
                                progress.totalTiles,
                                progress.currentTile?.baseName,
                                progress.overallPercent,
                            )
                        )
                    }
                }
                val accepted = synchronized(this@DroneTerrainController) {
                    if (generation == preparationGeneration) {
                        source = prepared
                        true
                    } else {
                        false
                    }
                }
                if (!accepted) {
                    prepared.close()
                    return@launch
                }
                val terrainPreview = try {
                    withContext(previewDispatcher) { TerrainPreviewBuilder.build(prepared) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    DiagnosticLog.event(
                        "terrain",
                        "preview_build_failed",
                        "WARN",
                        mapOf("reason" to error.message),
                    )
                    null
                }
                if (synchronized(this@DroneTerrainController) { generation != preparationGeneration }) {
                    return@launch
                }
                preview.postValue(terrainPreview)
                state.postValue(
                    if (requireConnection) {
                        TerrainProvisioningState.WaitingForMissionUpload
                    } else {
                        TerrainProvisioningState.SourceReady(
                            tileCount = plan.sourceTiles.size,
                            center = plan.center,
                            radiusMeters = plan.radiusMeters,
                        )
                    }
                )
                if (!requireConnection) {
                    DiagnosticLog.event(
                        "terrain",
                        "source_downloaded_to_tablet",
                        data = mapOf(
                            "tiles" to plan.sourceTiles.size,
                            "centerLat" to plan.center.lat,
                            "centerLon" to plan.center.lon,
                            "radiusMeters" to plan.radiusMeters,
                        ),
                    )
                }
                onComplete(true)
            } catch (_: CancellationException) {
                // A newer mission preparation or clear() owns the current UI state and callback.
            } catch (error: Throwable) {
                if (synchronized(this@DroneTerrainController) { generation != preparationGeneration }) return@launch
                state.postValue(
                    TerrainProvisioningState.Failed(
                        TerrainFailure.SOURCE_DOWNLOAD_FAILED,
                        error.message,
                    )
                )
                DiagnosticLog.event("terrain", "source_preparation_failed", "ERROR", mapOf("reason" to error.message))
                onComplete(false)
            }
        }
    }

    @Synchronized
    fun onMissionUploadStarted() {
        if (source != null) state.postValue(TerrainProvisioningState.Serving(blocksSent.get(), latestPending))
    }

    @Synchronized
    fun onMissionUploadSucceeded() {
        if (source == null) return
        missionUploaded = true
        beginVerification()
    }

    fun handle(message: MavlinkMessage<*>) {
        handle(message.originSystemId, message.payload)
    }

    internal fun handle(originSystemId: Int, payload: Any) {
        if (originSystemId != targetSystemId()) return
        when (payload) {
            is TerrainRequest -> handleRequest(payload)
            is TerrainReport -> handleReport(payload)
        }
    }

    @Synchronized
    fun isReadyForAuto(): Boolean = readyForAuto

    fun markFailed(failure: TerrainFailure, detail: String? = null) = fail(failure, detail)

    @Synchronized
    fun onDisconnected() {
        // A tablet-only download is intentionally valid without an aircraft connection.
        // Preserve its confirmation and open source so a later connected preparation can
        // reuse the repository cache. Only an active aircraft transfer becomes a failure.
        if (source != null && state.value !is TerrainProvisioningState.SourceReady) {
            state.postValue(TerrainProvisioningState.Failed(TerrainFailure.DISCONNECTED))
        }
        verificationJob?.cancel()
        readyForAuto = false
        verificationJob = null
        expectedCheck = null
    }

    @Synchronized
    fun clear() {
        cancel(closeSource = true)
        preview.postValue(null)
        state.postValue(TerrainProvisioningState.Idle)
    }

    private fun handleRequest(request: TerrainRequest) {
        val prepared = synchronized(this) { source } ?: return
        if (request.gridSpacing() != REQUIRED_GRID_SPACING) {
            fail(TerrainFailure.PARAMETER_REJECTED, "Aircraft requested ${request.gridSpacing()} m terrain")
            return
        }
        for (bit in 0 until 56) {
            if (!request.mask().testBit(bit)) continue
            val elevations = buildGrid(prepared, request, bit)
            if (elevations == null) {
                fail(TerrainFailure.MISSING_ELEVATION, "Missing terrain for request bit $bit")
                return
            }
            val response = TerrainData.builder()
                .lat(request.lat())
                .lon(request.lon())
                .gridSpacing(request.gridSpacing())
                .gridbit(bit)
                .data(elevations)
                .build()
            mavlinkClient.send2(mavlinkClient.gcsSystemId, GCS_COMPONENT_ID, response)
            blocksSent.incrementAndGet()
        }
        state.postValue(TerrainProvisioningState.Serving(blocksSent.get(), latestPending))
    }

    private fun buildGrid(
        prepared: TerrainElevationSource,
        request: TerrainRequest,
        bit: Int,
    ): List<Int>? {
        val requestOrigin = LatLon(request.lat() / 1e7, request.lon() / 1e7)
        val eastBlock = bit % 8
        val northBlock = bit / 8
        val values = ArrayList<Int>(16)
        for (localNorth in 0 until 4) {
            for (localEast in 0 until 4) {
                val northMeters = (northBlock * 4 + localNorth) * request.gridSpacing().toDouble()
                val eastMeters = (eastBlock * 4 + localEast) * request.gridSpacing().toDouble()
                val point = TerrainCoveragePlanner.offset(requestOrigin, northMeters, eastMeters)
                val elevation = prepared.elevationMeters(point.lat, point.lon) ?: return null
                values += elevation.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            }
        }
        return values
    }

    @Synchronized
    private fun handleReport(report: TerrainReport) {
        latestPending = report.pending()
        val expected = expectedCheck
        if (expected != null && report.spacing() == REQUIRED_GRID_SPACING &&
            report.lat() == expected.toE7Lat() && report.lon() == expected.toE7Lon()
        ) {
            expectedCheckConfirmed = true
        }
        if (!missionUploaded || state.value is TerrainProvisioningState.Verifying) return
        state.postValue(TerrainProvisioningState.Serving(blocksSent.get(), report.pending()))
    }

    @Synchronized
    private fun beginVerification() {
        verificationJob?.cancel()
        val prepared = source ?: return
        val checks = prepared.plan.requiredPathSamples
            .filterIndexed { index, _ -> index % VERIFY_SAMPLE_STRIDE == 0 }
            .toMutableList()
            .apply {
                prepared.plan.requiredPathSamples.lastOrNull()?.let { if (lastOrNull() != it) add(it) }
            }
            .distinct()
        verificationJob = scope.launch {
            delay(1_000L)
            for ((index, point) in checks.withIndex()) {
                state.postValue(TerrainProvisioningState.Verifying(index, checks.size))
                var confirmed = false
                for (attempt in 0 until CHECK_RETRIES) {
                    synchronized(this@DroneTerrainController) {
                        expectedCheck = point
                        expectedCheckConfirmed = false
                    }
                    sendCheck(point)
                    delay(CHECK_RETRY_MS)
                    if (synchronized(this@DroneTerrainController) { expectedCheckConfirmed }) {
                        confirmed = true
                        break
                    }
                }
                if (!confirmed) {
                    fail(TerrainFailure.VERIFICATION_TIMEOUT, "No terrain report for ${point.lat}, ${point.lon}")
                    return@launch
                }
            }
            synchronized(this@DroneTerrainController) {
                expectedCheck = null
                expectedCheckConfirmed = false
            }
            state.postValue(TerrainProvisioningState.Ready(blocksSent.get()))
            readyForAuto = true
            DiagnosticLog.event("terrain", "mission_terrain_ready", data = mapOf("blocksSent" to blocksSent.get(), "checks" to checks.size))
        }
    }

    private fun sendCheck(point: LatLon) {
        val check = TerrainCheck.builder().lat(point.toE7Lat()).lon(point.toE7Lon()).build()
        mavlinkClient.send2(mavlinkClient.gcsSystemId, GCS_COMPONENT_ID, check)
    }

    @Synchronized
    private fun fail(failure: TerrainFailure, detail: String?) {
        verificationJob?.cancel()
        readyForAuto = false
        verificationJob = null
        state.postValue(TerrainProvisioningState.Failed(failure, detail))
        DiagnosticLog.event("terrain", "terrain_provisioning_failed", "ERROR", mapOf("failure" to failure.name, "detail" to detail))
    }

    @Synchronized
    private fun cancel(closeSource: Boolean) {
        preparationGeneration += 1
        preparationJob?.cancel()
        preparationJob = null
        verificationJob?.cancel()
        verificationJob = null
        expectedCheck = null
        readyForAuto = false
        if (closeSource) source?.close()
        source = null
    }
}
