package com.example.droneservicesapp.ui.home.binders

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.example.droneservicesapp.R

class HomeMapTelemetryBinder(
    private val rootView: View,
) {
    companion object {
        private const val MIN_DISTANCE_VALUE = 5
        private const val MAX_DISTANCE_VALUE = 15
    }

    fun renderFrontDistance(distance: Int) {
        renderDistance(
            distance = distance,
            textViewId = R.id.front_dist,
            colorIndex = 0
        )
    }

    fun renderBackDistance(distance: Int) {
        renderDistance(
            distance = distance,
            textViewId = R.id.back_dist,
            colorIndex = 2
        )
    }

    private fun renderDistance(
        distance: Int,
        textViewId: Int,
        colorIndex: Int,
    ) {
        val color = getColor(distance)
        rootView.findViewById<TextView>(textViewId)?.text = "$distance m"

        val compassImageView = rootView.findViewById<ImageView>(R.id.avoidance_compass)
        val drawable = compassImageView?.drawable as? GradientDrawable
        drawable?.colors?.let { colors ->
            val newColors = colors.copyOf()
            newColors[colorIndex] = color
            drawable.colors = newColors
        }
    }

    private fun getColor(inValue: Int): Int {
        var value = when {
            inValue < MIN_DISTANCE_VALUE -> MIN_DISTANCE_VALUE
            inValue > MAX_DISTANCE_VALUE -> MAX_DISTANCE_VALUE
            else -> inValue
        }
        value = MAX_DISTANCE_VALUE + MIN_DISTANCE_VALUE - value

        val hue =
            ((120 * (MAX_DISTANCE_VALUE - value)) / (MAX_DISTANCE_VALUE - MIN_DISTANCE_VALUE)).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
    }
}
