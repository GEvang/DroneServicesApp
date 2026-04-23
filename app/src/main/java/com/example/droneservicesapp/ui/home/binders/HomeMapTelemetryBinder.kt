package com.example.droneservicesapp.ui.home.binders

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import com.example.droneservicesapp.R

class HomeMapTelemetryBinder(
    private val activity: Activity,
) {
    companion object {
        private const val MIN_DISTANCE_VALUE = 5
        private const val MAX_DISTANCE_VALUE = 15
    }

    fun renderFrontDistance(distance: Int) {
        renderDistance(
            logTag = "frontDistance",
            distance = distance,
            textViewId = R.id.front_dist,
            colorIndex = 0
        )
    }

    fun renderBackDistance(distance: Int) {
        renderDistance(
            logTag = "backDistance",
            distance = distance,
            textViewId = R.id.back_dist,
            colorIndex = 2
        )
    }

    private fun renderDistance(
        logTag: String,
        distance: Int,
        textViewId: Int,
        colorIndex: Int,
    ) {
        Log.i(logTag, "------")
        Log.i(logTag, "$logTag: $distance")

        val color = getColor(distance)
        Log.i(logTag, "distance: $distance    color: $color")

        activity.findViewById<TextView>(textViewId)?.text = "$distance m"

        val compassImageView = activity.findViewById<ImageView>(R.id.avoidance_compass)
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
