package com.example.droneservicesapp.ui.home.geoawareness

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction

class LiveGeoAwarenessStatusViewBinder(
    private val context: Context,
    private val statusView: TextView
) {

    fun bindClear() {
        applyStyle(
            text = "LIVE GEO: CLEAR",
            backgroundColor = "#2E7D32".toColorInt(),
            textColor = Color.WHITE
        )
    }

    fun bindInside(zone: GeoZone) {
        bindInsideMultiple(listOf(zone))
    }

    fun bindInsideMultiple(zones: List<GeoZone>) {
        val highest = zones.firstOrNull()?.restriction ?: GeoZoneRestriction.UNKNOWN
        when (highest) {
            GeoZoneRestriction.PROHIBITED -> applyStyle(
                text = "LIVE GEO: PROHIBITED",
                backgroundColor = "#C62828".toColorInt(),
                textColor = Color.WHITE
            )
            GeoZoneRestriction.REQ_AUTHORISATION -> applyStyle(
                text = "LIVE GEO: AUTH ZONE",
                backgroundColor = "#EF6C00".toColorInt(),
                textColor = Color.WHITE
            )
            GeoZoneRestriction.CONDITIONAL -> applyStyle(
                text = "LIVE GEO: CONDITIONAL",
                backgroundColor = "#F9A825".toColorInt(),
                textColor = "#212121".toColorInt()
            )
            GeoZoneRestriction.INFORMATION -> applyStyle(
                text = "LIVE GEO: INFO",
                backgroundColor = "#1565C0".toColorInt(),
                textColor = Color.WHITE
            )
            GeoZoneRestriction.UNKNOWN -> bindUnknown("Unknown")
        }
    }

    fun bindNear(zone: GeoZone, distanceMeters: Double) {
        val roundedMeters = distanceMeters.toInt().coerceAtLeast(0)
        when (zone.restriction) {
            GeoZoneRestriction.PROHIBITED -> applyStyle(
                text = "LIVE GEO: NEAR PROHIBITED — ${roundedMeters} m",
                backgroundColor = "#E65100".toColorInt(),
                textColor = Color.WHITE
            )
            GeoZoneRestriction.REQ_AUTHORISATION -> applyStyle(
                text = "LIVE GEO: NEAR AUTH ZONE — ${roundedMeters} m",
                backgroundColor = "#EF6C00".toColorInt(),
                textColor = Color.WHITE
            )
            GeoZoneRestriction.CONDITIONAL -> applyStyle(
                text = "LIVE GEO: NEAR CONDITIONAL — ${roundedMeters} m",
                backgroundColor = "#F9A825".toColorInt(),
                textColor = "#212121".toColorInt()
            )
            GeoZoneRestriction.UNKNOWN -> applyStyle(
                text = "LIVE GEO: NEAR UNKNOWN — ${roundedMeters} m",
                backgroundColor = "#8D6E63".toColorInt(),
                textColor = Color.WHITE
            )
            GeoZoneRestriction.INFORMATION -> bindClear()
        }
    }

    fun bindVerticalNear(zone: GeoZone, verticalDistanceMeters: Double?) {
        val roundedMeters = verticalDistanceMeters?.toInt()?.coerceAtLeast(0)
        val suffix = roundedMeters?.let { " - ${it} m" }.orEmpty()
        when (zone.restriction) {
            GeoZoneRestriction.PROHIBITED -> applyStyle(
                text = "LIVE GEO: VERTICAL PROHIBITED$suffix",
                backgroundColor = "#E65100".toColorInt(),
                textColor = Color.WHITE
            )
            GeoZoneRestriction.REQ_AUTHORISATION -> applyStyle(
                text = "LIVE GEO: VERTICAL AUTH$suffix",
                backgroundColor = "#EF6C00".toColorInt(),
                textColor = Color.WHITE
            )
            GeoZoneRestriction.CONDITIONAL -> applyStyle(
                text = "LIVE GEO: VERTICAL CONDITIONAL$suffix",
                backgroundColor = "#F9A825".toColorInt(),
                textColor = "#212121".toColorInt()
            )
            GeoZoneRestriction.UNKNOWN -> applyStyle(
                text = "LIVE GEO: VERTICAL UNKNOWN$suffix",
                backgroundColor = "#8D6E63".toColorInt(),
                textColor = Color.WHITE
            )
            GeoZoneRestriction.INFORMATION -> bindClear()
        }
    }

    fun bindDegraded(message: String) {
        applyStyle(
            text = "LIVE GEO: DEGRADED",
            backgroundColor = "#6D4C41".toColorInt(),
            textColor = Color.WHITE
        )
        statusView.contentDescription = message
    }

    fun bindUnknown(message: String) {
        applyStyle(
            text = "LIVE GEO: NO POS",
            backgroundColor = "#757575".toColorInt(),
            textColor = Color.WHITE
        )
        statusView.contentDescription = message
    }

    fun setOnClickListener(listener: View.OnClickListener?) {
        statusView.setOnClickListener(listener)
    }

    private fun applyStyle(
        text: String,
        backgroundColor: Int,
        textColor: Int
    ) {
        val useTabletNeutralGlass =
            context.resources.getBoolean(R.bool.config_tablet_planning_dock) &&
                (text == "LIVE GEO: NO POS" || text == "LIVE GEO: CLEAR")
        statusView.text = text
        statusView.setTextColor(textColor)
        statusView.visibility = View.VISIBLE
        statusView.contentDescription = text

        val horizontalPadding = dpToPx(16)
        val verticalPadding = dpToPx(10)
        statusView.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        statusView.compoundDrawablePadding = dpToPx(8)
        if (useTabletNeutralGlass) {
            statusView.setCompoundDrawables(createStatusDot(), null, null, null)
        } else {
            statusView.setCompoundDrawables(null, null, null, null)
        }

        statusView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(18).toFloat()
            setColor(if (useTabletNeutralGlass) "#D0101820".toColorInt() else backgroundColor)
            setStroke(
                dpToPx(1),
                if (useTabletNeutralGlass) "#3DFFFFFF".toColorInt() else "#33FFFFFF".toColorInt()
            )
        }
    }

    private fun createStatusDot(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor("#31F7A2".toColorInt())
            setSize(dpToPx(10), dpToPx(10))
            setBounds(0, 0, dpToPx(10), dpToPx(10))
        }
    }

    private fun dpToPx(value: Int): Int {
        val density = context.resources.displayMetrics.density
        return (value * density).toInt()
    }
}
