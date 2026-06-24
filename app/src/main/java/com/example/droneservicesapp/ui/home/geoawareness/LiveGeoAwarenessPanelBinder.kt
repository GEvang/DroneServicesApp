package com.example.droneservicesapp.ui.home.geoawareness

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction
import kotlin.math.cos
import kotlin.math.sin

data class LiveGeoThreatUiModel(
    val label: String,
    val colorHex: String,
    val directionText: String,
    val distanceText: String,
    val altitudeText: String,
    val bearingDegrees: Double? = null,
    val verticalIndicator: VerticalIndicator = VerticalIndicator.NONE
)

enum class VerticalIndicator { NONE, UP, DOWN }

class LiveGeoAwarenessPanelBinder(
    private val rootView: View
) {
    private val statusBadge: TextView = rootView.findViewById(R.id.live_geo_status_badge)
    private val topDivider: View = rootView.findViewById(R.id.live_geo_top_divider)
    private val centerCore: View = rootView.findViewById(R.id.live_geo_center_core)
    private val centerIcon: ImageView = rootView.findViewById(R.id.live_geo_center_icon)
    private val compassFrame: FrameLayout = rootView.findViewById(R.id.live_geo_compass_frame)
    private val threatCard: View = rootView.findViewById(R.id.live_geo_threat_card)
    private val forwardMarker: TextView = rootView.findViewById(R.id.live_geo_forward_marker)
    private val markerViews: List<TextView> = listOf(
        rootView.findViewById(R.id.live_geo_direction_marker_1),
        rootView.findViewById(R.id.live_geo_direction_marker_2),
        rootView.findViewById(R.id.live_geo_direction_marker_3)
    )
    private val rowBindings: List<RowBinding> = listOf(
        RowBinding(
            dot = rootView.findViewById(R.id.live_geo_row_1_dot),
            label = rootView.findViewById(R.id.live_geo_row_1_label),
            direction = rootView.findViewById(R.id.live_geo_row_1_direction),
            distance = rootView.findViewById(R.id.live_geo_row_1_distance),
            altitude = rootView.findViewById(R.id.live_geo_row_1_alt),
            divider = rootView.findViewById(R.id.live_geo_row_1_divider)
        ),
        RowBinding(
            dot = rootView.findViewById(R.id.live_geo_row_2_dot),
            label = rootView.findViewById(R.id.live_geo_row_2_label),
            direction = rootView.findViewById(R.id.live_geo_row_2_direction),
            distance = rootView.findViewById(R.id.live_geo_row_2_distance),
            altitude = rootView.findViewById(R.id.live_geo_row_2_alt),
            divider = rootView.findViewById(R.id.live_geo_row_2_divider)
        ),
        RowBinding(
            dot = rootView.findViewById(R.id.live_geo_row_3_dot),
            label = rootView.findViewById(R.id.live_geo_row_3_label),
            direction = rootView.findViewById(R.id.live_geo_row_3_direction),
            distance = rootView.findViewById(R.id.live_geo_row_3_distance),
            altitude = rootView.findViewById(R.id.live_geo_row_3_alt),
            divider = null
        )
    )
    private val moreLabel: TextView = rootView.findViewById(R.id.live_geo_more_label)

    fun bindClear() {
        setThreatContentVisible(false)
        bindThreatSummary(
            statusLabel = "CLEAR",
            statusColor = "#48D26D",
            threats = emptyList(),
            headingDegrees = null
        )
    }

    fun bindInsideMultiple(zones: List<GeoZone>) {
        val highest = zones.firstOrNull()?.restriction ?: GeoZoneRestriction.UNKNOWN
        val color = restrictionColor(highest)
        val rows = zones.take(MAX_ROWS).map { zone ->
            LiveGeoThreatUiModel(
                label = restrictionShortLabel(zone.restriction),
                colorHex = restrictionColor(zone.restriction),
                directionText = "IN",
                distanceText = "INSIDE",
                altitudeText = "--",
                bearingDegrees = null
            )
        }
        bindThreatSummary(
            statusLabel = restrictionBadgeLabel(highest),
            statusColor = color,
            threats = rows,
            remainingCount = (zones.size - rows.size).coerceAtLeast(0),
            headingDegrees = null
        )
    }

    fun bindThreatSummary(
        statusLabel: String,
        statusColor: String,
        threats: List<LiveGeoThreatUiModel>,
        remainingCount: Int = 0,
        headingDegrees: Double? = null
    ) {
        rootView.visibility = View.VISIBLE
        setThreatContentVisible(threats.isNotEmpty())
        statusBadge.text = statusLabel
        statusBadge.setTextColor(Color.parseColor(statusColor))
        tintShape(statusBadge.background, statusColor, 0.22f)
        tintShape(centerCore.background, darken(statusColor))
        centerIcon.setColorFilter(Color.parseColor("#FFFFFF"))

        val visibleThreats = threats.take(MAX_ROWS)
        rowBindings.forEachIndexed { index, row ->
            val threat = visibleThreats.getOrNull(index)
            if (threat == null) {
                row.bindPlaceholder(index == 0)
            } else {
                row.bind(threat)
            }
        }

        val hiddenCount = remainingCount + (threats.size - visibleThreats.size).coerceAtLeast(0)
        if (hiddenCount > 0) {
            moreLabel.visibility = View.VISIBLE
            moreLabel.text = "+$hiddenCount more"
        } else {
            moreLabel.visibility = View.GONE
        }

        updateCompassMarkers(visibleThreats, headingDegrees)
        rootView.contentDescription = statusLabel
    }

    fun bindDegraded(message: String) {
        bindThreatSummary(
            statusLabel = "DEGRADED",
            statusColor = "#FFB26B",
            threats = emptyList(),
            headingDegrees = null
        )
    }

    fun bindUnknown(message: String) {
        bindThreatSummary(
            statusLabel = "UNKNOWN",
            statusColor = "#AAB5C6",
            threats = emptyList(),
            headingDegrees = null
        )
    }

    fun setOnClickListener(listener: View.OnClickListener?) {
        rootView.setOnClickListener(listener)
    }

    private fun updateCompassMarkers(threats: List<LiveGeoThreatUiModel>, headingDegrees: Double?) {
        updateForwardMarker(headingDegrees)
        markerViews.forEach { it.visibility = View.INVISIBLE }
        threats.take(markerViews.size).forEachIndexed { index, threat ->
            val marker = markerViews[index]
            marker.visibility = View.VISIBLE
            tintShape(marker.background, threat.colorHex)
            val verticalGlyph = when (threat.verticalIndicator) {
                VerticalIndicator.UP -> "↑"
                VerticalIndicator.DOWN -> "↓"
                VerticalIndicator.NONE -> ""
            }
            marker.text = verticalGlyph
            when {
                threat.verticalIndicator != VerticalIndicator.NONE -> placeVerticalMarker(marker, threat.verticalIndicator)
                threat.bearingDegrees != null -> placeBearingMarker(marker, threat.bearingDegrees)
                else -> marker.visibility = View.INVISIBLE
            }
        }
    }

    private fun updateForwardMarker(headingDegrees: Double?) {
        if (headingDegrees == null) {
            forwardMarker.visibility = View.GONE
            return
        }
        forwardMarker.visibility = View.VISIBLE
        tintShape(forwardMarker.background, "#D7E0EF")
        compassFrame.post {
            val centerX = compassFrame.width / 2f
            val centerY = compassFrame.height / 2f
            val radius = 18f
            val radians = Math.toRadians(headingDegrees - 90.0)
            val x = centerX + radius * cos(radians).toFloat() - forwardMarker.width / 2f
            val y = centerY + radius * sin(radians).toFloat() - forwardMarker.height / 2f
            forwardMarker.x = x
            forwardMarker.y = y
            forwardMarker.rotation = headingDegrees.toFloat()
        }
    }

    private fun placeBearingMarker(marker: TextView, bearingDegrees: Double) {
        compassFrame.post {
            val centerX = compassFrame.width / 2f
            val centerY = compassFrame.height / 2f
            val radius = (compassFrame.width.coerceAtMost(compassFrame.height) / 2f) - 24f
            val radians = Math.toRadians(bearingDegrees - 90.0)
            val x = centerX + radius * cos(radians).toFloat() - marker.width / 2f
            val y = centerY + radius * sin(radians).toFloat() - marker.height / 2f
            marker.x = x
            marker.y = y
        }
    }

    private fun placeVerticalMarker(marker: TextView, indicator: VerticalIndicator) {
        compassFrame.post {
            val centerX = compassFrame.width / 2f
            val centerY = compassFrame.height / 2f
            val radius = 54f
            val yOffset = if (indicator == VerticalIndicator.UP) -radius else radius
            marker.x = centerX - marker.width / 2f
            marker.y = centerY + yOffset - marker.height / 2f
        }
    }

    private fun setThreatContentVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        topDivider.visibility = visibility
        compassFrame.visibility = visibility
        threatCard.visibility = visibility
    }

    private fun restrictionShortLabel(restriction: GeoZoneRestriction): String {
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED -> "PROHIBITED"
            GeoZoneRestriction.REQ_AUTHORISATION -> "AUTH"
            GeoZoneRestriction.CONDITIONAL -> "COND"
            GeoZoneRestriction.INFORMATION -> "INFO"
            GeoZoneRestriction.UNKNOWN -> "UNKNOWN"
        }
    }

    private fun restrictionBadgeLabel(restriction: GeoZoneRestriction): String {
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED -> "PROHIBITED"
            GeoZoneRestriction.REQ_AUTHORISATION -> "AUTH REQUIRED"
            GeoZoneRestriction.CONDITIONAL -> "CONDITIONAL"
            GeoZoneRestriction.INFORMATION -> "INFO"
            GeoZoneRestriction.UNKNOWN -> "UNKNOWN"
        }
    }

    private fun restrictionColor(restriction: GeoZoneRestriction): String {
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED -> "#FF4F45"
            GeoZoneRestriction.REQ_AUTHORISATION -> "#FF972E"
            GeoZoneRestriction.CONDITIONAL -> "#F4C73D"
            GeoZoneRestriction.INFORMATION -> "#4C9DFF"
            GeoZoneRestriction.UNKNOWN -> "#8D6E63"
        }
    }

    private fun darken(color: String): String {
        return when (color) {
            "#FF4F45" -> "#40201D"
            "#FF972E" -> "#433221"
            "#F4C73D" -> "#45391D"
            "#4C9DFF" -> "#1C3147"
            "#FFB26B" -> "#473127"
            else -> "#2B3440"
        }
    }

    private fun tintShape(background: android.graphics.drawable.Drawable?, color: String, alpha: Float = 1f) {
        val parsed = Color.parseColor(color)
        val tinted = Color.argb(
            (255 * alpha).toInt().coerceIn(0, 255),
            Color.red(parsed),
            Color.green(parsed),
            Color.blue(parsed)
        )
        (background as? GradientDrawable)?.setColor(tinted)
    }

    private data class RowBinding(
        val dot: View,
        val label: TextView,
        val direction: TextView,
        val distance: TextView,
        val altitude: TextView,
        val divider: View?
    ) {
        fun bind(threat: LiveGeoThreatUiModel) {
            dot.visibility = View.VISIBLE
            label.visibility = View.VISIBLE
            direction.visibility = View.VISIBLE
            distance.visibility = View.VISIBLE
            altitude.visibility = View.VISIBLE
            divider?.visibility = View.VISIBLE
            (dot.background as? GradientDrawable)?.setColor(Color.parseColor(threat.colorHex))
            label.text = threat.label
            label.setTextColor(Color.parseColor(threat.colorHex))
            direction.text = threat.directionText
            distance.text = threat.distanceText
            altitude.text = threat.altitudeText
        }

        fun bindPlaceholder(keepVisible: Boolean) {
            val visibility = if (keepVisible) View.VISIBLE else View.GONE
            dot.visibility = visibility
            label.visibility = visibility
            direction.visibility = visibility
            distance.visibility = visibility
            altitude.visibility = visibility
            divider?.visibility = if (keepVisible) View.VISIBLE else View.GONE
            if (keepVisible) {
                label.text = "CLEAR"
                label.setTextColor(Color.parseColor("#48D26D"))
                direction.text = "--"
                distance.text = "--"
                altitude.text = "--"
                (dot.background as? GradientDrawable)?.setColor(Color.parseColor("#48D26D"))
            }
        }
    }

    companion object {
        private const val MAX_ROWS = 3
    }
}
