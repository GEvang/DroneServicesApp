package com.example.droneservicesapp.ui.geoawareness

import android.content.Context
import com.example.droneservicesapp.R
import java.util.Locale

/** Converts live geo-awareness state to text without creating or retaining UI objects. */
class GeoAwarenessLiveStatusFormatter(private val context: Context) {
    fun label(state: GeoAwarenessLiveStatusController.State?): String = when (state?.kind) {
        null, GeoAwarenessLiveStatusController.Kind.UNKNOWN -> "UNKNOWN"
        GeoAwarenessLiveStatusController.Kind.DEGRADED -> "DEGRADED"
        GeoAwarenessLiveStatusController.Kind.CLEAR -> "CLEAR"
        GeoAwarenessLiveStatusController.Kind.INSIDE ->
            if (state.insideZones.size > 1) "IN ${state.insideZones.size} ZONES" else "IN ZONE"
        GeoAwarenessLiveStatusController.Kind.NEAR -> "NEAR ZONE"
        GeoAwarenessLiveStatusController.Kind.VERTICAL_NEAR -> "VERTICAL NEAR"
    }

    fun tone(state: GeoAwarenessLiveStatusController.State?): GeoAwarenessStatusTone = when (state?.kind) {
        GeoAwarenessLiveStatusController.Kind.CLEAR -> GeoAwarenessStatusTone.SUCCESS
        null, GeoAwarenessLiveStatusController.Kind.UNKNOWN,
        GeoAwarenessLiveStatusController.Kind.DEGRADED,
        GeoAwarenessLiveStatusController.Kind.NEAR,
        GeoAwarenessLiveStatusController.Kind.VERTICAL_NEAR -> GeoAwarenessStatusTone.WARNING
        GeoAwarenessLiveStatusController.Kind.INSIDE -> GeoAwarenessStatusTone.ERROR
    }

    fun title(state: GeoAwarenessLiveStatusController.State?): String = when (state?.kind) {
        GeoAwarenessLiveStatusController.Kind.INSIDE -> "Live geo-awareness warning"
        GeoAwarenessLiveStatusController.Kind.NEAR,
        GeoAwarenessLiveStatusController.Kind.VERTICAL_NEAR -> "Nearby geo-zone"
        else -> context.getString(R.string.geo_awareness_title)
    }

    fun details(state: GeoAwarenessLiveStatusController.State?): String = when (state?.kind) {
        null, GeoAwarenessLiveStatusController.Kind.UNKNOWN ->
            state?.message ?: context.getString(R.string.geo_awareness_live_no_position)
        GeoAwarenessLiveStatusController.Kind.DEGRADED -> telemetryText(state, state.message ?: "Geo-awareness degraded")
        GeoAwarenessLiveStatusController.Kind.CLEAR -> telemetryText(state, context.getString(R.string.geo_awareness_live_clear))
        GeoAwarenessLiveStatusController.Kind.INSIDE -> insideDetails(state)
        GeoAwarenessLiveStatusController.Kind.NEAR,
        GeoAwarenessLiveStatusController.Kind.VERTICAL_NEAR -> proximityDetails(state)
    }

    private fun insideDetails(state: GeoAwarenessLiveStatusController.State): String {
        val visible = state.insideZones.take(5)
        val remaining = state.insideZones.size - visible.size
        return buildString {
            appendLine("Drone is inside loaded geo-zone(s):")
            appendLine()
            visible.forEach { zone ->
                appendLine("- ${zone.name}")
                appendLine("  Restriction: ${zone.restriction}")
                appendLine("  Message: ${zone.message ?: "No message"}")
            }
            if (remaining > 0) appendLine("...and $remaining more.")
            appendLine("Verify restrictions with the responsible authority before flight.")
            appendTelemetry(state)
        }.trim()
    }

    private fun proximityDetails(state: GeoAwarenessLiveStatusController.State): String {
        val proximity = state.proximity ?: return state.message ?: "Nearby geo-zone information unavailable"
        return buildString {
            appendLine("Nearest zone: ${proximity.nearestZone.name}")
            appendLine("Restriction: ${proximity.restriction}")
            appendLine("Distance: ${proximity.distanceMeters.toInt().coerceAtLeast(0)} m")
            appendLine("Configured threshold: ${proximity.configuredThresholdMeters.toInt()} m")
            appendLine("Effective threshold: ${proximity.effectiveThresholdMeters.toInt()} m")
            appendLine("Required warning time: ${proximity.requiredWarningSeconds} s")
            proximity.groundSpeedMetersPerSecond?.let { appendLine("Ground speed: ${decimal(it)} m/s") }
            proximity.closingSpeedMetersPerSecond?.let { appendLine("Closing speed: ${decimal(it)} m/s") }
            proximity.timeToBoundarySeconds?.let { appendLine("Time to boundary: ${decimal(it)} s") }
            appendLine("Warning mode: ${proximity.warningMode}")
            state.datasetInfo?.title?.takeIf(String::isNotBlank)?.let {
                appendLine("Dataset: $it (${state.datasetInfo.version ?: "N/A"})")
            }
            proximity.nearestZone.message?.takeIf(String::isNotBlank)?.let { appendLine("Message: $it") }
            appendLine()
            appendLine("The drone is outside this zone but within the near-zone warning threshold.")
            appendTelemetry(state)
        }.trim()
    }

    private fun telemetryText(state: GeoAwarenessLiveStatusController.State, leading: String) = buildString {
        appendLine(leading)
        appendTelemetry(state)
    }.trim()

    private fun StringBuilder.appendTelemetry(state: GeoAwarenessLiveStatusController.State) {
        val telemetry = state.telemetry ?: return
        appendLine()
        appendLine("Telemetry:")
        telemetry.altitudeAglMeters?.let { appendLine("Altitude AGL: ${decimal(it)} m") }
        telemetry.altitudeAmslMeters?.let { appendLine("Altitude AMSL: ${decimal(it)} m") }
        telemetry.groundSpeedMetersPerSecond?.let { appendLine("Ground speed: ${decimal(it)} m/s") }
        telemetry.verticalSpeedMetersPerSecond?.let { appendLine("Vertical speed: ${decimal(it)} m/s") }
        telemetry.headingDegrees?.let { appendLine("Heading: ${decimal(it)}°") }
    }

    private fun decimal(value: Double) = "%.2f".format(Locale.US, value)
}
