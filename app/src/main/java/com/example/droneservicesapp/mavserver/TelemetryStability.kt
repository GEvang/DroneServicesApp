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

/** Rejects corrupt one-off voltage samples without hiding a genuine pack change. */
internal class BatteryVoltageStabilizer(
    private val maximumImmediateJumpVolts: Float = 8f,
    private val minorChangeVolts: Float = 0.15f,
    private val confirmationSamples: Int = 2,
    private val largeJumpConfirmationSamples: Int = 4,
) {
    private var displayedVoltage: Float? = null
    private var pendingVoltage: Float? = null
    private var pendingSamples = 0

    @Synchronized
    fun update(voltage: Float?): Float? {
        val candidate = voltage?.takeIf { it.isFinite() && it in 5f..80f } ?: return displayedVoltage
        val current = displayedVoltage
        if (current == null) {
            displayedVoltage = candidate
            pendingVoltage = null
            pendingSamples = 0
            return candidate
        }
        val difference = kotlin.math.abs(candidate - current)
        if (difference <= minorChangeVolts) {
            pendingVoltage = null
            pendingSamples = 0
            return current
        }

        val requiredSamples = if (difference > maximumImmediateJumpVolts) {
            largeJumpConfirmationSamples
        } else {
            confirmationSamples
        }
        if (pendingVoltage != null && kotlin.math.abs(candidate - pendingVoltage!!) <= minorChangeVolts) {
            pendingSamples++
        } else {
            pendingVoltage = candidate
            pendingSamples = 1
        }
        if (pendingSamples >= requiredSamples) {
            displayedVoltage = candidate
            pendingVoltage = null
            pendingSamples = 0
        }
        return displayedVoltage
    }

    @Synchronized
    fun reset() {
        displayedVoltage = null
        pendingVoltage = null
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
