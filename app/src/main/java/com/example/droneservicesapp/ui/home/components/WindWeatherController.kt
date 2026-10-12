package com.example.droneservicesapp.ui.home.components

import android.util.Log
import com.example.droneservicesapp.data.weather.OpenMeteoWindRepository
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.ui.home.WeatherPanelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Owns weather-panel visibility and loading; the panel itself renders [WeatherPanelState]. */
class WindWeatherController(
    private val state: WeatherPanelState,
    private val scope: CoroutineScope,
    private val repository: OpenMeteoWindRepository,
    private val locationProvider: () -> LatLon,
    private val internetNetworkProvider: () -> android.net.Network?,
) {
    private var requestJob: Job? = null

    fun toggle() {
        state.visible = !state.visible
        if (state.visible) refresh()
    }

    fun dispose() {
        requestJob?.cancel()
        requestJob = null
    }

    private fun refresh() {
        state.status = WeatherPanelState.Status.LOADING
        state.wind = null
        requestJob?.cancel()
        requestJob = scope.launch {
            runCatching {
                val location = locationProvider()
                repository.currentWind(
                    latitude = location.lat,
                    longitude = location.lon,
                    internetNetwork = internetNetworkProvider(),
                )
            }.onSuccess { wind ->
                if (!state.visible) return@onSuccess
                state.wind = wind
                state.status = WeatherPanelState.Status.LIVE
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                Log.w(LOG_TAG, "Current wind request failed", it)
                if (!state.visible) return@onFailure
                state.wind = null
                state.status = WeatherPanelState.Status.UNAVAILABLE
            }
        }
    }

    private companion object {
        const val LOG_TAG = "WindWeather"
    }
}
