package com.example.droneservicesapp.ui.geoawareness

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.geoawareness.evidence.GeoAwarenessEvidencePackageExporter
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEvent
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.databinding.FragmentGeoAwarenessBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Loads and renders recent geo-awareness audit events without retaining a binding. */
class GeoAwarenessEventLogPresenter(
    private val context: Context,
    private val eventLogger: GeoAwarenessEventLogger,
) {
    data class EventRow(
        val title: String,
        val message: String,
        val messageColor: Int,
        val metadata: String?,
    )

    private val density = context.resources.displayMetrics.density

    fun refresh(binding: FragmentGeoAwarenessBinding) {
        val events = eventLogger.readEvents(maxLines = Int.MAX_VALUE)
        binding.geoAwarenessLogCount.text = context.getString(R.string.geo_awareness_event_count) + " " + events.size
        renderRecent(binding.geoAwarenessFlightLogContainer, events)
    }

    fun detailedPreview(maxEvents: Int = 50): String {
        val events = eventLogger.readEvents(maxLines = maxEvents).sortedByDescending { it.timestampMillis }
        return if (events.isEmpty()) {
            "No detailed geo-awareness events recorded yet."
        } else {
            events.joinToString("\n") { "${it.timestampIsoUtc} | ${it.type.name} | ${it.message}" }
        }
    }

    fun rows(events: List<GeoAwarenessEvent>): List<EventRow> = events
        .filter { it.type in GeoAwarenessEvidencePackageExporter.IMPORTANT_FLIGHT_EVENTS }
        .sortedByDescending { it.timestampMillis }
        .take(50)
        .map { event ->
            EventRow(
                title = "${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.timestampMillis))}  ${friendlyLabel(event)}",
                message = event.message,
                messageColor = severityColor(event.severity),
                metadata = buildMetadata(event),
            )
        }

    private fun renderRecent(container: LinearLayout, events: List<GeoAwarenessEvent>) {
        container.removeAllViews()
        val rows = rows(events)
        if (rows.isEmpty()) {
            container.addView(panelText(context.getString(R.string.geo_awareness_flight_log_empty)))
            return
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) container.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                    topMargin = dp(10); bottomMargin = dp(10)
                }
                setBackgroundColor(Color.parseColor("#1F2A44"))
            })
            container.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                addView(statusValue(row.title))
                addView(panelText(row.message, row.messageColor))
                row.metadata?.let { addView(panelText(it)) }
            })
        }
    }

    private fun friendlyLabel(event: GeoAwarenessEvent): String = event.type.name.lowercase()
        .split('_').joinToString(" ") { it.replaceFirstChar { char -> char.titlecase(Locale.getDefault()) } }

    private fun severityColor(severity: String): Int = when (severity.uppercase(Locale.getDefault())) {
        "ERROR", "BLOCKED" -> Color.parseColor("#FF8A80")
        "WARNING" -> Color.parseColor("#FFB74D")
        else -> Color.parseColor("#C5D0E6")
    }

    private fun buildMetadata(event: GeoAwarenessEvent): String? = buildString {
        event.zoneNames.firstOrNull()?.let { append("Zone: $it") }
        event.restriction?.let { if (isNotEmpty()) append(" | "); append("Restriction: $it") }
        event.flightMode?.let { if (isNotEmpty()) append(" | "); append("Mode: $it") }
    }.takeIf(String::isNotBlank)

    private fun statusValue(value: String) = TextView(context).apply {
        text = value; setTextColor(Color.parseColor("#EAF1FF")); textSize = 15f; setTypeface(typeface, Typeface.BOLD)
    }

    private fun panelText(value: String, color: Int = Color.parseColor("#C5D0E6")) = TextView(context).apply {
        text = value; setTextColor(color); textSize = 13f; setPadding(0, dp(4), 0, 0)
    }

    private fun dp(value: Int) = (value * density).toInt()
}
