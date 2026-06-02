package com.example.droneservicesapp.ui.home.geoawareness

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
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
            backgroundColor = Color.parseColor("#2E7D32"),
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
                backgroundColor = Color.parseColor("#C62828"),
                textColor = Color.WHITE
            )
            GeoZoneRestriction.REQ_AUTHORISATION -> applyStyle(
                text = "LIVE GEO: AUTH ZONE",
                backgroundColor = Color.parseColor("#EF6C00"),
                textColor = Color.WHITE
            )
            GeoZoneRestriction.CONDITIONAL -> applyStyle(
                text = "LIVE GEO: CONDITIONAL",
                backgroundColor = Color.parseColor("#F9A825"),
                textColor = Color.parseColor("#212121")
            )
            GeoZoneRestriction.INFORMATION -> applyStyle(
                text = "LIVE GEO: INFO",
                backgroundColor = Color.parseColor("#1565C0"),
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
                backgroundColor = Color.parseColor("#E65100"),
                textColor = Color.WHITE
            )
            GeoZoneRestriction.REQ_AUTHORISATION -> applyStyle(
                text = "LIVE GEO: NEAR AUTH ZONE — ${roundedMeters} m",
                backgroundColor = Color.parseColor("#EF6C00"),
                textColor = Color.WHITE
            )
            GeoZoneRestriction.CONDITIONAL -> applyStyle(
                text = "LIVE GEO: NEAR CONDITIONAL — ${roundedMeters} m",
                backgroundColor = Color.parseColor("#F9A825"),
                textColor = Color.parseColor("#212121")
            )
            GeoZoneRestriction.UNKNOWN -> applyStyle(
                text = "LIVE GEO: NEAR UNKNOWN — ${roundedMeters} m",
                backgroundColor = Color.parseColor("#8D6E63"),
                textColor = Color.WHITE
            )
            GeoZoneRestriction.INFORMATION -> bindClear()
        }
    }

    fun bindUnknown(message: String) {
        applyStyle(
            text = "LIVE GEO: NO POS",
            backgroundColor = Color.parseColor("#757575"),
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
            setColor(if (useTabletNeutralGlass) Color.parseColor("#D0101820") else backgroundColor)
            setStroke(
                dpToPx(1),
                if (useTabletNeutralGlass) Color.parseColor("#3DFFFFFF") else Color.parseColor("#33FFFFFF")
            )
        }
    }

    private fun createStatusDot(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#31F7A2"))
            setSize(dpToPx(10), dpToPx(10))
            setBounds(0, 0, dpToPx(10), dpToPx(10))
        }
    }

    private fun dpToPx(value: Int): Int {
        val density = context.resources.displayMetrics.density
        return (value * density).toInt()
    }
}
