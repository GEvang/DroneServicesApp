package com.example.droneservicesapp.ui.home.geoawareness

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
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
    val verticalArrowText: String = "",
    val radialDistanceRatio: Float = 1f,
    val bearingDegrees: Double? = null,
    val verticalIndicator: VerticalIndicator = VerticalIndicator.NONE,
    val showCompassMarker: Boolean = true,
    val isInsideZone: Boolean = false
)

enum class VerticalIndicator { NONE, UP, DOWN }

class LiveGeoAwarenessPanelBinder(
    private val rootView: View
) {
    private val statusBadge: TextView = rootView.findViewById(R.id.live_geo_status_badge)
    private val topDivider: View = rootView.findViewById(R.id.live_geo_top_divider)
    private val compassHighlight: View = rootView.findViewById(R.id.live_geo_compass_highlight)
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
            container = rootView.findViewById(R.id.live_geo_row_1),
            dot = rootView.findViewById(R.id.live_geo_row_1_dot),
            label = rootView.findViewById(R.id.live_geo_row_1_label),
            direction = rootView.findViewById(R.id.live_geo_row_1_direction),
            distance = rootView.findViewById(R.id.live_geo_row_1_distance),
            altitude = rootView.findViewById(R.id.live_geo_row_1_alt),
            verticalArrow = rootView.findViewById(R.id.live_geo_row_1_vertical_arrow),
            divider = rootView.findViewById(R.id.live_geo_row_1_divider)
        ),
        RowBinding(
            container = rootView.findViewById(R.id.live_geo_row_2),
            dot = rootView.findViewById(R.id.live_geo_row_2_dot),
            label = rootView.findViewById(R.id.live_geo_row_2_label),
            direction = rootView.findViewById(R.id.live_geo_row_2_direction),
            distance = rootView.findViewById(R.id.live_geo_row_2_distance),
            altitude = rootView.findViewById(R.id.live_geo_row_2_alt),
            verticalArrow = rootView.findViewById(R.id.live_geo_row_2_vertical_arrow),
            divider = rootView.findViewById(R.id.live_geo_row_2_divider)
        ),
        RowBinding(
            container = rootView.findViewById(R.id.live_geo_row_3),
            dot = rootView.findViewById(R.id.live_geo_row_3_dot),
            label = rootView.findViewById(R.id.live_geo_row_3_label),
            direction = rootView.findViewById(R.id.live_geo_row_3_direction),
            distance = rootView.findViewById(R.id.live_geo_row_3_distance),
            altitude = rootView.findViewById(R.id.live_geo_row_3_alt),
            verticalArrow = rootView.findViewById(R.id.live_geo_row_3_vertical_arrow),
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
            headingDegrees = null,
            borderColor = "#00000000"
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
                distanceText = "H: IN",
                altitudeText = "V: --",
                radialDistanceRatio = 0.18f,
                showCompassMarker = false,
                isInsideZone = true
            )
        }
        bindThreatSummary(
            statusLabel = restrictionBadgeLabel(highest),
            statusColor = color,
            threats = rows,
            remainingCount = (zones.size - rows.size).coerceAtLeast(0),
            headingDegrees = null,
            borderColor = color
        )
    }

    fun bindThreatSummary(
        statusLabel: String,
        statusColor: String,
        threats: List<LiveGeoThreatUiModel>,
        remainingCount: Int = 0,
        headingDegrees: Double? = null,
        borderColor: String = "#00000000"
    ) {
        rootView.visibility = View.VISIBLE
        setThreatContentVisible(threats.isNotEmpty())
        statusBadge.text = statusLabel
        statusBadge.setTextColor(statusColor.toColorInt())
        tintShape(statusBadge.background, statusColor, 0.22f)
        tintStroke(compassHighlight.background, borderColor)
        tintShape(centerCore.background, darken(statusColor))
        centerIcon.setColorFilter("#FFFFFF".toColorInt())

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

    fun bindDegraded(@Suppress("UNUSED_PARAMETER") message: String) {
        bindThreatSummary(
            statusLabel = "DEGRADED",
            statusColor = "#FFB26B",
            threats = emptyList(),
            headingDegrees = null,
            borderColor = "#00000000"
        )
    }

    fun bindUnknown(@Suppress("UNUSED_PARAMETER") message: String) {
        bindThreatSummary(
            statusLabel = "UNKNOWN",
            statusColor = "#AAB5C6",
            threats = emptyList(),
            headingDegrees = null,
            borderColor = "#00000000"
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
            if (!threat.showCompassMarker) {
                marker.visibility = View.INVISIBLE
                return@forEachIndexed
            }
            marker.visibility = View.VISIBLE
            tintShape(marker.background, threat.colorHex)
            marker.text = when (threat.verticalIndicator) {
                VerticalIndicator.UP -> "\u2191"
                VerticalIndicator.DOWN -> "\u2193"
                VerticalIndicator.NONE -> ""
            }
            when {
                threat.bearingDegrees != null -> placeBearingMarker(marker, threat.bearingDegrees, threat.radialDistanceRatio)
                threat.verticalIndicator != VerticalIndicator.NONE -> placeVerticalMarker(marker, threat.verticalIndicator)
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
            forwardMarker.x = centerX + radius * cos(radians).toFloat() - forwardMarker.width / 2f
            forwardMarker.y = centerY + radius * sin(radians).toFloat() - forwardMarker.height / 2f
            forwardMarker.rotation = headingDegrees.toFloat()
        }
    }

    private fun placeBearingMarker(marker: TextView, bearingDegrees: Double, radialDistanceRatio: Float) {
        compassFrame.post {
            val centerX = compassFrame.width / 2f
            val centerY = compassFrame.height / 2f
            val maxRadius = (compassFrame.width.coerceAtMost(compassFrame.height) / 2f) - 24f
            val minRadius = 32f
            val clampedRatio = radialDistanceRatio.coerceIn(0.18f, 1f)
            val radius = minRadius + (maxRadius - minRadius) * clampedRatio
            val radians = Math.toRadians(bearingDegrees - 90.0)
            marker.x = centerX + radius * cos(radians).toFloat() - marker.width / 2f
            marker.y = centerY + radius * sin(radians).toFloat() - marker.height / 2f
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
            GeoZoneRestriction.REQ_AUTHORISATION -> "AUTH REQUIRED"
            GeoZoneRestriction.CONDITIONAL -> "CONDITIONAL"
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
        val parsed = color.toColorInt()
        val tinted = Color.argb(
            (255 * alpha).toInt().coerceIn(0, 255),
            Color.red(parsed),
            Color.green(parsed),
            Color.blue(parsed)
        )
        (background as? GradientDrawable)?.setColor(tinted)
    }

    private fun tintStroke(background: android.graphics.drawable.Drawable?, color: String) {
        val parsedColor = color.toColorInt()
        val strokeWidthPx = if (Color.alpha(parsedColor) == 0) 0 else 6
        (background as? GradientDrawable)?.setStroke(strokeWidthPx, parsedColor)
    }

    private data class RowBinding(
        val container: View,
        val dot: View,
        val label: TextView,
        val direction: TextView,
        val distance: TextView,
        val altitude: TextView,
        val verticalArrow: TextView,
        val divider: View?
    ) {
        fun bind(threat: LiveGeoThreatUiModel) {
            dot.visibility = View.VISIBLE
            label.visibility = View.VISIBLE
            direction.visibility = View.VISIBLE
            distance.visibility = View.VISIBLE
            altitude.visibility = View.VISIBLE
            verticalArrow.visibility = View.VISIBLE
            divider?.visibility = View.VISIBLE
            (dot.background as? GradientDrawable)?.setColor(threat.colorHex.toColorInt())
            container.background = insideHighlight(threat.colorHex.takeIf { threat.isInsideZone })
            label.text = threat.label
            label.setTextColor(threat.colorHex.toColorInt())
            direction.text = threat.directionText
            distance.text = threat.distanceText
            altitude.text = threat.altitudeText
            verticalArrow.text = threat.verticalArrowText
        }

        fun bindPlaceholder(keepVisible: Boolean) {
            val visibility = if (keepVisible) View.VISIBLE else View.GONE
            dot.visibility = visibility
            label.visibility = visibility
            direction.visibility = visibility
            distance.visibility = visibility
            altitude.visibility = visibility
            verticalArrow.visibility = visibility
            divider?.visibility = if (keepVisible) View.VISIBLE else View.GONE
            container.background = null
            if (keepVisible) {
                label.text = "CLEAR"
                label.setTextColor("#48D26D".toColorInt())
                direction.text = "--"
                distance.text = "H: --"
                altitude.text = "V: --"
                verticalArrow.text = ""
                (dot.background as? GradientDrawable)?.setColor("#48D26D".toColorInt())
            }
        }

        private fun insideHighlight(colorHex: String?): GradientDrawable? {
            if (colorHex.isNullOrBlank()) return null
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f
                setColor(Color.TRANSPARENT)
                setStroke(3, colorHex.toColorInt())
            }
        }
    }

    companion object {
        private const val MAX_ROWS = 3
    }
}
