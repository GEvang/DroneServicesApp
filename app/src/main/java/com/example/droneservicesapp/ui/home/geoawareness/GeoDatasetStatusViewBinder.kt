package com.example.droneservicesapp.ui.home.geoawareness

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo

class GeoDatasetStatusViewBinder(
    private val context: Context,
    private val statusView: TextView
) {

    fun bind(info: GeoZoneDatasetInfo?) {
        when {
            info == null || info.zoneCount == 0 -> bindUnavailable("Geo Data: Unavailable")
            info.isOfficial -> applyStyle(
                text = "Geo Data: Official",
                backgroundColor = Color.parseColor("#2E7D32"),
                textColor = Color.WHITE
            )
            else -> applyStyle(
                text = "Geo Data: Unofficial",
                backgroundColor = Color.parseColor("#EF6C00"),
                textColor = Color.WHITE
            )
        }
    }

    fun bindUnavailable(message: String) {
        applyStyle(
            text = message,
            backgroundColor = Color.parseColor("#757575"),
            textColor = Color.WHITE
        )
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
