package com.example.droneservicesapp.data.weather

import android.net.Network
import com.example.droneservicesapp.domain.weather.WindWeather
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Retrieves current 10 m wind conditions without requiring an API key. */
class OpenMeteoWindRepository {
    suspend fun currentWind(
        latitude: Double,
        longitude: Double,
        internetNetwork: Network? = null,
    ): WindWeather = withContext(Dispatchers.IO) {
        val requestUrl = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.6f&longitude=%.6f&current=wind_speed_10m,wind_direction_10m&wind_speed_unit=kmh",
            latitude,
            longitude,
        )
        val url = URL(requestUrl)
        if (internetNetwork == null) {
            fetch(url, network = null)
        } else {
            runCatching { fetch(url, internetNetwork) }
                .getOrElse { fetch(url, network = null) }
        }
    }

    private fun fetch(url: URL, network: Network?): WindWeather {
        val connection = ((network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "DroneServicesApp/1.0")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("Weather service returned HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).getJSONObject("current")
            return WindWeather(
                speedKilometersPerHour = current.getDouble("wind_speed_10m"),
                directionFromDegrees = current.getDouble("wind_direction_10m"),
            )
        } finally {
            connection.disconnect()
        }
    }
}
