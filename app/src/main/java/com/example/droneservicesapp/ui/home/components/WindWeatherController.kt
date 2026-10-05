package com.example.droneservicesapp.ui.home.components

import android.content.Context
import android.content.res.ColorStateList
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.weather.OpenMeteoWindRepository
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.weather.WindWeather
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Owns weather-panel visibility, loading, and rendering. */
class WindWeatherController(
    private val context: Context,
    private val rootView: View,
    private val lifecycleOwner: LifecycleOwner,
    private val repository: OpenMeteoWindRepository,
    private val locationProvider: () -> LatLon,
    private val internetNetworkProvider: () -> android.net.Network?,
) {
    private var visible = false
    private var requestJob: Job? = null

    fun bind() {
        rootView.findViewById<MaterialButton?>(R.id.right_panel_weather_button)
            ?.setOnClickListener { toggle() }
        renderVisibility()
    }

    fun dispose() {
        requestJob?.cancel()
        requestJob = null
    }

    private fun toggle() {
        visible = !visible
        renderVisibility()
        if (visible) refresh()
    }

    private fun renderVisibility() {
        rootView.findViewById<View?>(R.id.wind_weather_panel_include)?.visibility =
            if (visible) View.VISIBLE else View.GONE
        rootView.findViewById<MaterialButton?>(R.id.right_panel_weather_button)?.apply {
            val selectedColor = ContextCompat.getColor(
                context,
                if (visible) R.color.ds_color_shell_selected_surface else R.color.ds_color_shell_overlay_strong,
            )
            val contentColor = ContextCompat.getColor(
                context,
                if (visible) R.color.ds_color_shell_active else R.color.ds_color_text_primary,
            )
            backgroundTintList = ColorStateList.valueOf(selectedColor)
            setTextColor(contentColor)
            iconTint = ColorStateList.valueOf(contentColor)
        }
    }

    private fun refresh() {
        renderLoading()
        requestJob?.cancel()
        requestJob = lifecycleOwner.lifecycleScope.launch {
            runCatching {
                val location = locationProvider()
                repository.currentWind(
                    latitude = location.lat,
                    longitude = location.lon,
                    internetNetwork = internetNetworkProvider(),
                )
            }.onSuccess(::renderWeather).onFailure {
                Log.w(LOG_TAG, "Current wind request failed", it)
                renderUnavailable()
            }
        }
    }

    private fun renderLoading() {
        rootView.findViewById<TextView?>(R.id.wind_weather_status)?.setText(R.string.weather_loading)
        rootView.findViewById<TextView?>(R.id.wind_weather_direction)?.setText(R.string.weather_loading)
        rootView.findViewById<TextView?>(R.id.wind_weather_speed)?.text = "--"
        rootView.findViewById<ImageView?>(R.id.wind_weather_direction_arrow)?.rotation = 0f
    }

    private fun renderWeather(wind: WindWeather) {
        if (!visible) return
        rootView.findViewById<TextView?>(R.id.wind_weather_status)?.setText(R.string.weather_live)
        rootView.findViewById<TextView?>(R.id.wind_weather_direction)?.text = context.getString(
            R.string.weather_wind_from_format,
            wind.directionFromCompass,
        )
        rootView.findViewById<TextView?>(R.id.wind_weather_speed)?.text = context.getString(
            R.string.weather_wind_speed_format,
            wind.speedKilometersPerHour,
        )
        rootView.findViewById<ImageView?>(R.id.wind_weather_direction_arrow)?.rotation = wind.directionToDegrees
    }

    private fun renderUnavailable() {
        if (!visible) return
        rootView.findViewById<TextView?>(R.id.wind_weather_status)?.setText(R.string.weather_unavailable)
        rootView.findViewById<TextView?>(R.id.wind_weather_direction)?.setText(R.string.weather_unavailable)
        rootView.findViewById<TextView?>(R.id.wind_weather_speed)?.text = "--"
    }

    companion object {
        private const val LOG_TAG = "WindWeather"
    }
}
