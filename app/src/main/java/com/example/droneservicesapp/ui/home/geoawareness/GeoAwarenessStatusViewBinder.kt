package com.example.droneservicesapp.ui.home.geoawareness

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthState
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessResult
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction

class GeoAwarenessStatusViewBinder(
    private val context: Context,
    private val statusView: TextView
) {

    fun bindResult(result: GeoAwarenessResult) {
        when {
            !result.hasConflicts -> applyStyle(
                text = "GEO: CLEAR",
                backgroundColor = Color.parseColor("#2E7D32"),
                textColor = Color.WHITE
            )
            result.highestRestriction == GeoZoneRestriction.PROHIBITED -> applyStyle(
                text = "GEO: PROHIBITED",
                backgroundColor = Color.parseColor("#C62828"),
                textColor = Color.WHITE
            )
            result.highestRestriction == GeoZoneRestriction.REQ_AUTHORISATION -> applyStyle(
                text = "GEO: AUTH REQUIRED",
                backgroundColor = Color.parseColor("#EF6C00"),
                textColor = Color.WHITE
            )
            result.highestRestriction == GeoZoneRestriction.CONDITIONAL -> applyStyle(
                text = "GEO: CONDITIONAL",
                backgroundColor = Color.parseColor("#F9A825"),
                textColor = Color.parseColor("#212121")
            )
            result.highestRestriction == GeoZoneRestriction.INFORMATION -> applyStyle(
                text = "GEO: INFO",
                backgroundColor = Color.parseColor("#1565C0"),
                textColor = Color.WHITE
            )
            else -> applyStyle(
                text = "GEO: UNKNOWN",
                backgroundColor = Color.parseColor("#757575"),
                textColor = Color.WHITE
            )
        }
    }

    fun clear() {
        applyStyle(
            text = "GEO: CLEAR",
            backgroundColor = Color.parseColor("#2E7D32"),
            textColor = Color.WHITE
        )
    }

    fun bindHealth(health: GeoAwarenessHealth) {
        when (health.state) {
            GeoAwarenessHealthState.AVAILABLE -> clear()
            GeoAwarenessHealthState.DEGRADED -> applyStyle(
                text = "GEO: DEGRADED",
                backgroundColor = Color.parseColor("#E65100"),
                textColor = Color.WHITE
            )
            GeoAwarenessHealthState.STALE -> applyStyle(
                text = "GEO: STALE",
                backgroundColor = Color.parseColor("#EF6C00"),
                textColor = Color.WHITE
            )
            GeoAwarenessHealthState.UNAVAILABLE -> applyStyle(
                text = "GEO: UNAVAILABLE",
                backgroundColor = Color.parseColor("#B71C1C"),
                textColor = Color.WHITE
            )
        }
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
