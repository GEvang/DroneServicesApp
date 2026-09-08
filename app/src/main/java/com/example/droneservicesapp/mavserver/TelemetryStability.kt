package com.example.droneservicesapp.mavserver

import kotlin.math.roundToInt

/** Prevents sensor noise at an integer boundary from making the displayed percentage flicker. */
internal class BatteryPercentageStabilizer(
    private val hysteresisPercent: Float = 0.25f,
    private val confirmationSamples: Int = 3
) {
    private var displayedPercent: Int? = null
    private var pendingPercent: Int? = null
    private var pendingSamples: Int = 0

    @Synchronized
    fun update(fraction: Float?): Int? {
        if (fraction == null || !fraction.isFinite() || fraction < 0f) return null
        val rawPercent = (fraction.coerceIn(0f, 1f) * 100f)
        var next = displayedPercent ?: rawPercent.roundToInt().coerceIn(0, 100)

        while (next < 100 && rawPercent >= next + 0.5f + hysteresisPercent) {
            next++
        }
        while (next > 0 && rawPercent <= next - 0.5f - hysteresisPercent) {
            next--
        }

        val current = displayedPercent
        if (current == null || next == current || kotlin.math.abs(next - current) > 1) {
            displayedPercent = next
            pendingPercent = null
            pendingSamples = 0
            return next
        }

        if (pendingPercent == next) {
            pendingSamples++
        } else {
            pendingPercent = next
            pendingSamples = 1
        }
        if (pendingSamples >= confirmationSamples) {
            displayedPercent = next
            pendingPercent = null
            pendingSamples = 0
        }
        return displayedPercent
    }

    @Synchronized
    fun reset() {
        displayedPercent = null
        pendingPercent = null
        pendingSamples = 0
    }
}

internal fun isAutopilotLinkHealthy(
    lastAutopilotHeartbeatMs: Long,
    nowMs: Long,
    staleAfterMs: Long
): Boolean {
    if (lastAutopilotHeartbeatMs <= 0L) return false
    val ageMs = nowMs - lastAutopilotHeartbeatMs
    return ageMs >= 0L && ageMs < staleAfterMs
}

internal fun isAircraftHeartbeat(isGcs: Boolean, hasAutopilot: Boolean): Boolean =
    !isGcs && hasAutopilot
