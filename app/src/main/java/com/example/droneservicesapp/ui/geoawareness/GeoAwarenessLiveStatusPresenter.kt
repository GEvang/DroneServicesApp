package com.example.droneservicesapp.ui.geoawareness

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.example.droneservicesapp.R
import com.example.droneservicesapp.ui.home.geoawareness.LiveGeoAwarenessStatusViewBinder
import java.util.Locale

/** Binds dedicated-screen live status and owns its matching details dialog. */
class GeoAwarenessLiveStatusPresenter(
    private val context: Context,
    statusBinder: LiveGeoAwarenessStatusViewBinder,
) {
    private var binder: LiveGeoAwarenessStatusViewBinder? = statusBinder
    private var latestState: GeoAwarenessLiveStatusController.State? = null
    private var dialog: AlertDialog? = null

    init {
        binder?.setOnClickListener(android.view.View.OnClickListener { showDetails() })
    }

    fun present(state: GeoAwarenessLiveStatusController.State) {
        latestState = state
        when (state.kind) {
            GeoAwarenessLiveStatusController.Kind.UNKNOWN -> binder?.bindUnknown(state.message ?: context.getString(R.string.geo_awareness_live_no_position))
            GeoAwarenessLiveStatusController.Kind.DEGRADED -> binder?.bindDegraded(state.message ?: "Geo-awareness degraded")
            GeoAwarenessLiveStatusController.Kind.CLEAR -> binder?.bindClear()
            GeoAwarenessLiveStatusController.Kind.INSIDE -> binder?.bindInsideMultiple(state.insideZones)
            GeoAwarenessLiveStatusController.Kind.NEAR -> state.proximity?.let {
                binder?.bindNear(it.nearestZone, it.distanceMeters)
            }
            GeoAwarenessLiveStatusController.Kind.VERTICAL_NEAR -> state.proximity?.let {
                binder?.bindVerticalNear(it.nearestZone, it.verticalDistanceMeters)
            }
        }
    }

    fun showInitialUnknown() {
        binder?.bindUnknown(context.getString(R.string.geo_awareness_live_no_position))
    }

    fun clear() {
        binder?.setOnClickListener(null)
        binder = null
        latestState = null
        dialog?.dismiss()
        dialog = null
    }

    private fun showDetails() {
        val state = latestState
        val title = when (state?.kind) {
            GeoAwarenessLiveStatusController.Kind.INSIDE -> "Live geo-awareness warning"
            GeoAwarenessLiveStatusController.Kind.NEAR, GeoAwarenessLiveStatusController.Kind.VERTICAL_NEAR -> "Nearby geo-zone"
            else -> context.getString(R.string.geo_awareness_title)
        }
        val message = when (state?.kind) {
            null, GeoAwarenessLiveStatusController.Kind.UNKNOWN -> state?.message ?: context.getString(R.string.geo_awareness_live_no_position)
            GeoAwarenessLiveStatusController.Kind.DEGRADED -> buildTelemetryText(state, state.message ?: "Geo-awareness degraded")
            GeoAwarenessLiveStatusController.Kind.CLEAR -> buildTelemetryText(state, context.getString(R.string.geo_awareness_live_clear))
            GeoAwarenessLiveStatusController.Kind.INSIDE -> insideDetails(state)
            GeoAwarenessLiveStatusController.Kind.NEAR, GeoAwarenessLiveStatusController.Kind.VERTICAL_NEAR -> proximityDetails(state)
        }
        dialog?.dismiss()
        dialog = AlertDialog.Builder(context, R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(title).setMessage(message).setPositiveButton(android.R.string.ok, null).show()
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#212121"))
    }

    private fun insideDetails(state: GeoAwarenessLiveStatusController.State): String {
        val visible = state.insideZones.take(5)
        val remaining = state.insideZones.size - visible.size
        return buildString {
            appendLine("Drone is inside loaded geo-zone(s):"); appendLine()
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
            val dataset = state.datasetInfo
            dataset?.title?.takeIf(String::isNotBlank)?.let {
                appendLine("Dataset: $it (${dataset.version ?: "N/A"})")
            }
            proximity.nearestZone.message?.takeIf(String::isNotBlank)?.let { appendLine("Message: $it") }
            appendLine(); appendLine("The drone is outside this zone but within the near-zone warning threshold.")
            appendTelemetry(state)
        }.trim()
    }

    private fun buildTelemetryText(state: GeoAwarenessLiveStatusController.State, leading: String) = buildString {
        appendLine(leading)
        appendTelemetry(state)
    }.trim()

    private fun StringBuilder.appendTelemetry(state: GeoAwarenessLiveStatusController.State) {
        val telemetry = state.telemetry ?: return
        appendLine(); appendLine("Telemetry:")
        telemetry.altitudeAglMeters?.let { appendLine("Altitude AGL: ${decimal(it)} m") }
        telemetry.altitudeAmslMeters?.let { appendLine("Altitude AMSL: ${decimal(it)} m") }
        telemetry.groundSpeedMetersPerSecond?.let { appendLine("Ground speed: ${decimal(it)} m/s") }
        telemetry.verticalSpeedMetersPerSecond?.let { appendLine("Vertical speed: ${decimal(it)} m/s") }
        telemetry.headingDegrees?.let { appendLine("Heading: ${decimal(it)}°") }
    }

    private fun decimal(value: Double) = "%.2f".format(Locale.US, value)
}
