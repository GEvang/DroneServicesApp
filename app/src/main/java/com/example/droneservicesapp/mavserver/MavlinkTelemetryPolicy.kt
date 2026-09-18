package com.example.droneservicesapp.mavserver

import com.example.droneservicesapp.data.mavlink.MavlinkConfig

internal data class MavlinkMessageIntervalRequest(
    val messageName: String,
    val messageId: Int,
    val intervalUs: Float,
)

/** Additional stream requests needed by transports that start with minimal telemetry. */
internal object MavlinkTelemetryPolicy {
    private val tcpRequests = listOf(
        MavlinkMessageIntervalRequest("GPS_RAW_INT", messageId = 24, intervalUs = 500_000f),
        MavlinkMessageIntervalRequest("GLOBAL_POSITION_INT", messageId = 33, intervalUs = 200_000f),
        MavlinkMessageIntervalRequest("VFR_HUD", messageId = 74, intervalUs = 200_000f),
    )

    fun additionalRequests(
        interfaceType: MavlinkConfig.InterfaceType?,
    ): List<MavlinkMessageIntervalRequest> = when (interfaceType) {
        MavlinkConfig.InterfaceType.TCP -> tcpRequests
        else -> emptyList()
    }
}
