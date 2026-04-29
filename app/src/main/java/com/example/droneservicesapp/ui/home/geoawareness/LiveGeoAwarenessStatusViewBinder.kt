package com.example.droneservicesapp.ui.home.geoawareness

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
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
        statusView.text = text
        statusView.setTextColor(textColor)
        statusView.visibility = View.VISIBLE
        statusView.contentDescription = text

        val horizontalPadding = dpToPx(14)
        val verticalPadding = dpToPx(8)
        statusView.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

        statusView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(18).toFloat()
            setColor(backgroundColor)
            setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
        }
    }

    private fun dpToPx(value: Int): Int {
        val density = context.resources.displayMetrics.density
        return (value * density).toInt()
    }
}
