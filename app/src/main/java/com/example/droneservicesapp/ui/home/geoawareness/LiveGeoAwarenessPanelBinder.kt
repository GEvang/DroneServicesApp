package com.example.droneservicesapp.ui.home.geoawareness

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction

class LiveGeoAwarenessPanelBinder(
    private val rootView: View
) {
    private val dotView: View = rootView.findViewById(R.id.live_geo_panel_dot)
    private val centerCore: View = rootView.findViewById(R.id.live_geo_center_core)
    private val centerValue: TextView = rootView.findViewById(R.id.live_geo_center_value)
    private val centerIcon: ImageView = rootView.findViewById(R.id.live_geo_center_icon)
    private val verticalMarker: View = rootView.findViewById(R.id.live_geo_vertical_marker)
    private val verticalTopLabel: TextView = rootView.findViewById(R.id.live_geo_vertical_top_label)
    private val verticalTopValue: TextView = rootView.findViewById(R.id.live_geo_vertical_top_value)
    private val verticalBottomLabel: TextView = rootView.findViewById(R.id.live_geo_vertical_bottom_label)
    private val verticalBottomValue: TextView = rootView.findViewById(R.id.live_geo_vertical_bottom_value)
    private val horizontalLine: TextView = rootView.findViewById(R.id.live_geo_horizontal_line)
    private val verticalLine: TextView = rootView.findViewById(R.id.live_geo_vertical_line)

    fun bindClear() {
        applyBaseState(
            dotColor = "#48D26D",
            centerCoreColor = "#1C2833",
            centerText = "CLEAR",
            centerTextColor = "#FFFFFF",
            horizontalText = "H: CLEAR",
            horizontalColor = "#48D26D",
            verticalText = "V: CLEAR",
            verticalColor = "#48D26D",
            topLabel = "ABOVE",
            topValue = "CLEAR",
            topColor = "#6B7B90",
            bottomLabel = "BELOW",
            bottomValue = "CLEAR",
            bottomColor = "#48D26D",
            markerColor = "#2D3A49"
        )
    }

    fun bindInsideMultiple(zones: List<GeoZone>) {
        val highest = zones.firstOrNull()?.restriction ?: GeoZoneRestriction.UNKNOWN
        val label = restrictionLabel(highest)
        val color = restrictionColor(highest)
        applyBaseState(
            dotColor = color,
            centerCoreColor = darken(color),
            centerText = "IN",
            centerTextColor = "#FFFFFF",
            horizontalText = "H: ${label}",
            horizontalColor = color,
            verticalText = "V: IN VOLUME",
            verticalColor = color,
            topLabel = "ABOVE",
            topValue = "ACTIVE",
            topColor = color,
            bottomLabel = "BELOW",
            bottomValue = "ACTIVE",
            bottomColor = color,
            markerColor = color
        )
    }

    fun bindNear(zone: GeoZone, distanceMeters: Double, directionLabel: String?) {
        val label = "NEAR ${restrictionLabel(zone.restriction)}"
        val color = restrictionColor(zone.restriction)
        val roundedMeters = distanceMeters.toInt().coerceAtLeast(0)
        val suffix = directionLabel?.let { " ($it)" }.orEmpty()
        applyBaseState(
            dotColor = color,
            centerCoreColor = darken(color),
            centerText = "${roundedMeters} m",
            centerTextColor = "#FFFFFF",
            horizontalText = "H: $label$suffix",
            horizontalColor = color,
            verticalText = "V: CLEAR",
            verticalColor = "#48D26D",
            topLabel = "ABOVE",
            topValue = "CLEAR",
            topColor = "#6B7B90",
            bottomLabel = "BELOW",
            bottomValue = "CLEAR",
            bottomColor = "#48D26D",
            markerColor = color
        )
    }

    fun bindVerticalNear(zone: GeoZone, verticalDistanceMeters: Double?, relationLabel: String) {
        val color = restrictionColor(zone.restriction)
        val roundedMeters = verticalDistanceMeters?.toInt()?.coerceAtLeast(0)?.let { "$it m" } ?: "ALERT"
        val isAbove = relationLabel == "ABOVE"
        applyBaseState(
            dotColor = color,
            centerCoreColor = darken(color),
            centerText = roundedMeters,
            centerTextColor = "#FFFFFF",
            horizontalText = "H: CLEAR",
            horizontalColor = "#48D26D",
            verticalText = "V: $relationLabel $roundedMeters",
            verticalColor = color,
            topLabel = "ABOVE",
            topValue = if (isAbove) roundedMeters else "CLEAR",
            topColor = if (isAbove) color else "#6B7B90",
            bottomLabel = "BELOW",
            bottomValue = if (isAbove) "CLEAR" else roundedMeters,
            bottomColor = if (isAbove) "#48D26D" else color,
            markerColor = color
        )
    }

    fun bindDegraded(message: String) {
        applyBaseState(
            dotColor = "#C58F5A",
            centerCoreColor = "#473127",
            centerText = "DEG",
            centerTextColor = "#FFFFFF",
            horizontalText = "H: DEGRADED",
            horizontalColor = "#FFB26B",
            verticalText = "V: $message",
            verticalColor = "#C5D0E6",
            topLabel = "ABOVE",
            topValue = "CHECK",
            topColor = "#FFB26B",
            bottomLabel = "BELOW",
            bottomValue = "LINK",
            bottomColor = "#FFB26B",
            markerColor = "#FFB26B"
        )
        rootView.contentDescription = message
    }

    fun bindUnknown(message: String) {
        applyBaseState(
            dotColor = "#9099A8",
            centerCoreColor = "#2B3440",
            centerText = "NO POS",
            centerTextColor = "#FFFFFF",
            horizontalText = "H: UNKNOWN",
            horizontalColor = "#C5D0E6",
            verticalText = "V: UNKNOWN",
            verticalColor = "#C5D0E6",
            topLabel = "ABOVE",
            topValue = "CLEAR",
            topColor = "#6B7B90",
            bottomLabel = "BELOW",
            bottomValue = "CLEAR",
            bottomColor = "#6B7B90",
            markerColor = "#485565"
        )
        rootView.contentDescription = message
    }

    fun setOnClickListener(listener: View.OnClickListener?) {
        rootView.setOnClickListener(listener)
    }

    private fun applyBaseState(
        dotColor: String,
        centerCoreColor: String,
        centerText: String,
        centerTextColor: String,
        horizontalText: String,
        horizontalColor: String,
        verticalText: String,
        verticalColor: String,
        topLabel: String,
        topValue: String,
        topColor: String,
        bottomLabel: String,
        bottomValue: String,
        bottomColor: String,
        markerColor: String
    ) {
        rootView.visibility = View.VISIBLE
        tintShape(dotView.background, dotColor)
        tintShape(centerCore.background, centerCoreColor)
        centerValue.text = centerText
        centerValue.setTextColor(Color.parseColor(centerTextColor))
        centerIcon.setColorFilter(Color.parseColor("#FFFFFF"))
        horizontalLine.text = horizontalText
        horizontalLine.setTextColor(Color.parseColor(horizontalColor))
        verticalLine.text = verticalText
        verticalLine.setTextColor(Color.parseColor(verticalColor))
        verticalTopLabel.text = topLabel
        verticalTopValue.text = topValue
        verticalTopLabel.setTextColor(Color.parseColor(topColor))
        verticalTopValue.setTextColor(Color.parseColor(topColor))
        verticalBottomLabel.text = bottomLabel
        verticalBottomValue.text = bottomValue
        verticalBottomLabel.setTextColor(Color.parseColor(bottomColor))
        verticalBottomValue.setTextColor(Color.parseColor(bottomColor))
        tintShape(verticalMarker.background, markerColor)
        rootView.contentDescription = "$horizontalText. $verticalText."
    }

    private fun restrictionLabel(restriction: GeoZoneRestriction): String {
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED -> "PROHIBITED"
            GeoZoneRestriction.REQ_AUTHORISATION -> "AUTH ZONE"
            GeoZoneRestriction.CONDITIONAL -> "CONDITIONAL"
            GeoZoneRestriction.INFORMATION -> "INFO"
            GeoZoneRestriction.UNKNOWN -> "UNKNOWN"
        }
    }

    private fun restrictionColor(restriction: GeoZoneRestriction): String {
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED -> "#FF5D51"
            GeoZoneRestriction.REQ_AUTHORISATION -> "#FF9A3D"
            GeoZoneRestriction.CONDITIONAL -> "#F4C24D"
            GeoZoneRestriction.INFORMATION -> "#4C9DFF"
            GeoZoneRestriction.UNKNOWN -> "#8D6E63"
        }
    }

    private fun darken(color: String): String {
        return when (color) {
            "#FF5D51" -> "#40201D"
            "#FF9A3D" -> "#433221"
            "#F4C24D" -> "#45391D"
            "#4C9DFF" -> "#1C3147"
            else -> "#2B3440"
        }
    }

    private fun tintShape(background: android.graphics.drawable.Drawable?, color: String) {
        (background as? GradientDrawable)?.setColor(Color.parseColor(color))
    }
}
