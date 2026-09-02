package com.example.droneservicesapp.mavserver

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.droneservicesapp.data.mavlink.MavlinkClient
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.MavParamType
import io.dronefleet.mavlink.common.ParamRequestRead
import io.dronefleet.mavlink.common.ParamSet
import io.dronefleet.mavlink.common.ParamValue
import io.dronefleet.mavlink.util.EnumValue
import kotlin.math.abs

enum class VehicleParameterAvailability {
    DISCONNECTED,
    LOADING,
    SUPPORTED,
    UNSUPPORTED
}

enum class VehicleParameterResult {
    SUCCESS,
    ERROR
}

data class VehicleParameterUiState(
    val availability: VehicleParameterAvailability = VehicleParameterAvailability.DISCONNECTED,
    val value: Int? = null,
    val isWriting: Boolean = false,
    val result: VehicleParameterResult? = null
)

data class VehicleParameterFeedback(
    val parameterName: String,
    val succeeded: Boolean
)

/** A value that is delivered only once, even when an observer is recreated. */
class OneShotEvent<out T>(private val value: T) {
    private var handled = false

    fun getIfNotHandled(): T? {
        if (handled) return null
        handled = true
        return value
    }
}

/**
 * Owns the small PARAM_REQUEST_READ/PARAM_SET transactions used by the Parameters screen.
 * A write is considered successful only after the vehicle echoes the requested value in PARAM_VALUE.
 */
internal class DroneParameterController(
    private val mavlinkClient: MavlinkClient,
    private val isConnected: () -> Boolean,
    private val targetSystemId: () -> Int,
    private val targetComponentId: () -> Int,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    companion object {
        const val WP_RFND_USE = "WP_RFND_USE"
        const val SURFTRAK_MODE = "SURFTRAK_MODE"

        private const val TAG = "DroneParameters"
        private const val GCS_SYSTEM_ID = 255
        private const val GCS_COMPONENT_ID = 190
        private const val READ_RETRY_MS = 1_000L
        private const val READ_TIMEOUT_MS = 3_500L
        private const val WRITE_READBACK_MS = 1_000L
        private const val WRITE_TIMEOUT_MS = 3_500L
        private const val VALUE_TOLERANCE = 0.01f
        private val SUPPORTED_PARAMETERS = setOf(WP_RFND_USE, SURFTRAK_MODE)
    }

    private val mutableStates = SUPPORTED_PARAMETERS.associateWith {
        MutableLiveData(VehicleParameterUiState())
    }
    private val parameterTypes = mutableMapOf<String, EnumValue<MavParamType>>()
    private val readGenerations = mutableMapOf<String, Int>()
    private val writeGenerations = mutableMapOf<String, Int>()
    private val pendingWrites = mutableMapOf<String, Int>()
    @Volatile
    private var disconnected = true

    private val mutableFeedback = MutableLiveData<OneShotEvent<VehicleParameterFeedback>>()
    val feedback: LiveData<OneShotEvent<VehicleParameterFeedback>> = mutableFeedback

    fun state(parameterName: String): LiveData<VehicleParameterUiState> =
        requireNotNull(mutableStates[parameterName]) { "Unsupported UI parameter: $parameterName" }

    fun refreshAll() {
        disconnected = false
        SUPPORTED_PARAMETERS.forEach(::startRead)
    }

    fun setValue(parameterName: String, value: Int) {
        val stateLiveData = mutableStates[parameterName] ?: return
        val current = stateLiveData.value ?: VehicleParameterUiState()
        if (!isReady() || current.availability != VehicleParameterAvailability.SUPPORTED || current.isWriting) {
            publishFailure(parameterName, current)
            return
        }

        val generation = (writeGenerations[parameterName] ?: 0) + 1
        writeGenerations[parameterName] = generation
        pendingWrites[parameterName] = value
        stateLiveData.value = current.copy(isWriting = true, result = null)

        val parameterType = parameterTypes[parameterName]
        val request = ParamSet.builder()
            .targetSystem(targetSystemId())
            .targetComponent(targetComponentId())
            .paramId(parameterName)
            .paramValue(value.toFloat())
            .apply {
                if (parameterType != null) paramType(parameterType)
                else paramType(MavParamType.MAV_PARAM_TYPE_REAL32)
            }
            .build()
        mavlinkClient.send2(GCS_SYSTEM_ID, GCS_COMPONENT_ID, request)
        Log.i(TAG, "PARAM_SET sent name=$parameterName requested=$value")

        handler.postDelayed({
            if (writeGenerations[parameterName] == generation && pendingWrites[parameterName] == value) {
                sendReadRequest(parameterName)
            }
        }, WRITE_READBACK_MS)
        handler.postDelayed({
            if (writeGenerations[parameterName] == generation && pendingWrites.remove(parameterName) != null) {
                val latest = stateLiveData.value ?: current
                stateLiveData.value = latest.copy(isWriting = false, result = VehicleParameterResult.ERROR)
                mutableFeedback.value = OneShotEvent(VehicleParameterFeedback(parameterName, false))
                Log.w(TAG, "PARAM_SET confirmation timed out name=$parameterName requested=$value")
            }
        }, WRITE_TIMEOUT_MS)
    }

    fun handle(message: MavlinkMessage<*>) {
        val payload = message.payload as? ParamValue ?: return
        if (message.originSystemId != targetSystemId()) return

        val parameterName = normalizeParameterId(payload.paramId())
        val stateLiveData = mutableStates[parameterName] ?: return
        parameterTypes[parameterName] = payload.paramType()
        readGenerations[parameterName] = (readGenerations[parameterName] ?: 0) + 1

        val receivedValue = payload.paramValue()
        val roundedValue = receivedValue.toInt()
        val requestedValue = pendingWrites[parameterName]
        if (requestedValue != null) {
            writeGenerations[parameterName] = (writeGenerations[parameterName] ?: 0) + 1
            pendingWrites.remove(parameterName)
            val confirmed = abs(receivedValue - requestedValue.toFloat()) <= VALUE_TOLERANCE
            stateLiveData.value = VehicleParameterUiState(
                availability = VehicleParameterAvailability.SUPPORTED,
                value = roundedValue,
                isWriting = false,
                result = if (confirmed) VehicleParameterResult.SUCCESS else VehicleParameterResult.ERROR
            )
            mutableFeedback.value = OneShotEvent(VehicleParameterFeedback(parameterName, confirmed))
            Log.i(TAG, "PARAM_SET confirmation name=$parameterName requested=$requestedValue received=$receivedValue confirmed=$confirmed")
            return
        }

        stateLiveData.value = VehicleParameterUiState(
            availability = VehicleParameterAvailability.SUPPORTED,
            value = roundedValue
        )
        Log.i(TAG, "PARAM_VALUE read name=$parameterName value=$receivedValue")
    }

    fun onDisconnected() {
        if (disconnected) return
        disconnected = true
        handler.post {
            SUPPORTED_PARAMETERS.forEach { parameterName ->
                readGenerations[parameterName] = (readGenerations[parameterName] ?: 0) + 1
                writeGenerations[parameterName] = (writeGenerations[parameterName] ?: 0) + 1
                pendingWrites.remove(parameterName)
                mutableStates[parameterName]?.value = VehicleParameterUiState()
            }
            parameterTypes.clear()
        }
    }

    fun clear() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun startRead(parameterName: String) {
        val stateLiveData = mutableStates[parameterName] ?: return
        if (!isReady()) {
            stateLiveData.value = VehicleParameterUiState()
            return
        }

        val generation = (readGenerations[parameterName] ?: 0) + 1
        readGenerations[parameterName] = generation
        stateLiveData.value = VehicleParameterUiState(VehicleParameterAvailability.LOADING)
        sendReadRequest(parameterName)
        handler.postDelayed({ retryRead(parameterName, generation) }, READ_RETRY_MS)
        handler.postDelayed({ retryRead(parameterName, generation) }, READ_RETRY_MS * 2)
        handler.postDelayed({
            if (readGenerations[parameterName] == generation &&
                stateLiveData.value?.availability == VehicleParameterAvailability.LOADING
            ) {
                stateLiveData.value = VehicleParameterUiState(VehicleParameterAvailability.UNSUPPORTED)
                Log.w(TAG, "PARAM_REQUEST_READ timed out; parameter unavailable name=$parameterName")
            }
        }, READ_TIMEOUT_MS)
    }

    private fun retryRead(parameterName: String, generation: Int) {
        if (readGenerations[parameterName] == generation &&
            mutableStates[parameterName]?.value?.availability == VehicleParameterAvailability.LOADING
        ) {
            sendReadRequest(parameterName)
        }
    }

    private fun sendReadRequest(parameterName: String) {
        if (!isReady()) return
        val request = ParamRequestRead.builder()
            .targetSystem(targetSystemId())
            .targetComponent(targetComponentId())
            .paramId(parameterName)
            .paramIndex(-1)
            .build()
        mavlinkClient.send2(GCS_SYSTEM_ID, GCS_COMPONENT_ID, request)
        Log.d(TAG, "PARAM_REQUEST_READ sent name=$parameterName")
    }

    private fun publishFailure(parameterName: String, current: VehicleParameterUiState) {
        mutableStates[parameterName]?.value = current.copy(isWriting = false, result = VehicleParameterResult.ERROR)
        mutableFeedback.value = OneShotEvent(VehicleParameterFeedback(parameterName, false))
    }

    private fun isReady(): Boolean =
        isConnected() && targetSystemId() >= 0 && targetComponentId() >= 0

    private fun normalizeParameterId(value: String): String = value.trimEnd('\u0000').trim()
}
