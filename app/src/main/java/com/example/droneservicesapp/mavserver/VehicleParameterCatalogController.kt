package com.example.droneservicesapp.mavserver

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.droneservicesapp.data.diagnostics.DiagnosticLog
import com.example.droneservicesapp.data.mavlink.MavlinkClient
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.ParamRequestList
import io.dronefleet.mavlink.common.ParamRequestRead
import io.dronefleet.mavlink.common.ParamValue

data class VehicleParameter(
    val name: String,
    val value: Float,
    val type: String,
    val index: Int,
)

data class VehicleParameterCatalogState(
    val parameters: List<VehicleParameter> = emptyList(),
    val receivedCount: Int = 0,
    val expectedCount: Int? = null,
    val loading: Boolean = false,
    val partial: Boolean = false,
    val error: String? = null,
)

/** Discovers the complete classic MAVLink parameter table advertised by the vehicle. */
internal class VehicleParameterCatalogController(
    private val mavlinkClient: MavlinkClient,
    private val isConnected: () -> Boolean,
    private val targetSystemId: () -> Int,
    private val targetComponentId: () -> Int,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    companion object {
        private const val GCS_COMPONENT_ID = 190
        private const val INACTIVITY_MS = 2_500L
        private const val FINAL_TIMEOUT_MS = 30_000L
        private const val MAX_MISSING_REQUESTS = 120
        private const val UI_UPDATE_INTERVAL_MS = 200L
    }

    private val mutableState = MutableLiveData(VehicleParameterCatalogState())
    val state: LiveData<VehicleParameterCatalogState> = mutableState
    private val parametersByIndex = linkedMapOf<Int, VehicleParameter>()
    private var generation = 0
    private var expectedCount: Int? = null
    private var recoveryAttempted = false
    private var disconnected = true
    private var publishQueued = false
    private var inactivityRunnable: Runnable? = null

    fun refresh(): Boolean {
        if (!isConnected() || targetSystemId() < 0 || targetComponentId() < 0) {
            mutableState.value = VehicleParameterCatalogState(error = "No active aircraft link")
            return false
        }
        generation += 1
        disconnected = false
        val currentGeneration = generation
        parametersByIndex.clear()
        expectedCount = null
        recoveryAttempted = false
        publishQueued = false
        mutableState.value = VehicleParameterCatalogState(loading = true)
        mavlinkClient.send2(
            mavlinkClient.gcsSystemId,
            GCS_COMPONENT_ID,
            ParamRequestList.builder()
                .targetSystem(targetSystemId())
                .targetComponent(targetComponentId())
                .build()
        )
        scheduleInactivity(currentGeneration)
        handler.postDelayed({ finishIfCurrent(currentGeneration, timedOut = true) }, FINAL_TIMEOUT_MS)
        DiagnosticLog.event("mavlink", "parameter_catalog_requested")
        return true
    }

    fun handle(message: MavlinkMessage<*>) {
        val payload = message.payload as? ParamValue ?: return
        if (message.originSystemId != targetSystemId() ||
            message.originComponentId != targetComponentId() ||
            !isConnected()
        ) return
        val currentState = mutableState.value ?: return
        if (!currentState.loading && parametersByIndex.isEmpty()) return
        val name = payload.paramId().trimEnd('\u0000').trim()
        val index = payload.paramIndex()
        if (name.isBlank() || index < 0) return
        expectedCount = payload.paramCount().takeIf { it > 0 } ?: expectedCount
        parametersByIndex[index] = VehicleParameter(
            name = name,
            value = payload.paramValue(),
            type = payload.paramType().entry()?.name?.removePrefix("MAV_PARAM_TYPE_")
                ?: payload.paramType().value().toString(),
            index = index,
        )
        if (!currentState.loading) {
            publish(loading = false, partial = currentState.partial)
            return
        }
        schedulePublish()
        val currentGeneration = generation
        if (expectedCount != null && parametersByIndex.size >= expectedCount!!) {
            finishIfCurrent(currentGeneration, timedOut = false)
        } else {
            scheduleInactivity(currentGeneration)
        }
    }

    fun onDisconnected() {
        if (disconnected) return
        disconnected = true
        generation += 1
        handler.removeCallbacksAndMessages(null)
        parametersByIndex.clear()
        expectedCount = null
        publishQueued = false
        mutableState.value = VehicleParameterCatalogState(error = "No active aircraft link")
    }

    fun clear() = handler.removeCallbacksAndMessages(null)

    private fun scheduleInactivity(currentGeneration: Int) {
        inactivityRunnable?.let(handler::removeCallbacks)
        inactivityRunnable = Runnable inactivity@{
            if (currentGeneration != generation || mutableState.value?.loading != true) return@inactivity
            val total = expectedCount
            if (!recoveryAttempted && total != null) {
                recoveryAttempted = true
                (0 until total)
                    .filterNot(parametersByIndex::containsKey)
                    .take(MAX_MISSING_REQUESTS)
                    .forEach(::requestByIndex)
                handler.postDelayed({ finishIfCurrent(currentGeneration, timedOut = true) }, INACTIVITY_MS * 2)
            } else {
                finishIfCurrent(currentGeneration, timedOut = true)
            }
        }
        handler.postDelayed(inactivityRunnable!!, INACTIVITY_MS)
    }

    private fun requestByIndex(index: Int) {
        mavlinkClient.send2(
            mavlinkClient.gcsSystemId,
            GCS_COMPONENT_ID,
            ParamRequestRead.builder()
                .targetSystem(targetSystemId())
                .targetComponent(targetComponentId())
                .paramId("")
                .paramIndex(index)
                .build()
        )
    }

    private fun finishIfCurrent(currentGeneration: Int, timedOut: Boolean) {
        if (currentGeneration != generation || mutableState.value?.loading != true) return
        publishQueued = false
        val partial = expectedCount?.let { parametersByIndex.size < it } ?: timedOut
        publish(
            loading = false,
            partial = partial,
            error = if (parametersByIndex.isEmpty()) "The vehicle did not provide a parameter list" else null,
        )
        DiagnosticLog.event(
            "mavlink",
            "parameter_catalog_finished",
            if (partial) "WARN" else "INFO",
            mapOf("received" to parametersByIndex.size, "expected" to expectedCount, "partial" to partial)
        )
    }

    private fun publish(loading: Boolean, partial: Boolean = false, error: String? = null) {
        mutableState.value = VehicleParameterCatalogState(
            parameters = parametersByIndex.values.sortedBy { it.name },
            receivedCount = parametersByIndex.size,
            expectedCount = expectedCount,
            loading = loading,
            partial = partial,
            error = error,
        )
    }

    private fun schedulePublish() {
        if (publishQueued) return
        publishQueued = true
        val currentGeneration = generation
        handler.postDelayed({
            publishQueued = false
            if (currentGeneration == generation && mutableState.value?.loading == true) {
                publish(loading = true)
            }
        }, UI_UPDATE_INTERVAL_MS)
    }
}
