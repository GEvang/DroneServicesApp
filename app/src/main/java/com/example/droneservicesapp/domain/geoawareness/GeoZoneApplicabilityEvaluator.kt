package com.example.droneservicesapp.domain.geoawareness

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object GeoZoneApplicabilityEvaluator {

    fun isActiveNow(zone: GeoZone, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val applicability = zone.applicability
        if (applicability.isEmpty()) {
            return true
        }

        return applicability.any { window -> isWindowActive(window, nowMillis) }
    }

    private fun isWindowActive(window: GeoZoneApplicability, nowMillis: Long): Boolean {
        if (window.permanent) {
            return true
        }

        val startMillis = parseUtcMillis(window.startDateTime)
        val endMillis = parseUtcMillis(window.endDateTime)

        // Conservative fallback: do not suppress a warning for an unknown schedule format.
        if (startMillis == null && !window.startDateTime.isNullOrBlank()) {
            return true
        }
        if (endMillis == null && !window.endDateTime.isNullOrBlank()) {
            return true
        }

        val startsBeforeNow = startMillis?.let { nowMillis >= it } ?: true
        val endsAfterNow = endMillis?.let { nowMillis <= it } ?: true
        return startsBeforeNow && endsAfterNow
    }

    private fun parseUtcMillis(value: String?): Long? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return DATE_FORMATS.firstNotNullOfOrNull { format ->
            runCatching { requireNotNull(format.get()).parse(text)?.time }.getOrNull()
        }
    }

    private val DATE_FORMATS = listOf(
        threadLocalFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX"),
        threadLocalFormat("yyyy-MM-dd'T'HH:mm:ssX"),
        threadLocalFormat("yyyy-MM-dd'T'HH:mmX"),
        threadLocalFormat("yyyy-MM-dd")
    )

    private fun threadLocalFormat(pattern: String): ThreadLocal<SimpleDateFormat> {
        return object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }
            }
        }
    }
}
