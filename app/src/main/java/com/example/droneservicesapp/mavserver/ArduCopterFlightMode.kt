package com.example.droneservicesapp.mavserver

/** Operator-facing ArduCopter modes supported by the map screen. */
enum class ArduCopterFlightMode(
    val customMode: Int,
    val holdDurationMs: Long = 0L,
    val requiresUploadedMission: Boolean = false,
) {
    AUTO(customMode = 3, requiresUploadedMission = true),
    GUIDED(customMode = 4),
    LOITER(customMode = 5),
    RTL(customMode = 6, holdDurationMs = 2_000L),
    LAND(customMode = 9, holdDurationMs = 2_000L),
    BRAKE(customMode = 17, holdDurationMs = 800L);

    companion object {
        fun fromCustomMode(customMode: Int?): ArduCopterFlightMode? =
            values().firstOrNull { it.customMode == customMode }

        fun displayName(customMode: Int?): String = when (customMode) {
            null -> "—"
            0 -> "STABILIZE"
            1 -> "ACRO"
            2 -> "ALT HOLD"
            3 -> "AUTO"
            4 -> "GUIDED"
            5 -> "LOITER"
            6 -> "RTL"
            7 -> "CIRCLE"
            9 -> "LAND"
            11 -> "DRIFT"
            13 -> "SPORT"
            14 -> "AUTOTUNE"
            16 -> "POSHOLD"
            17 -> "BRAKE"
            18 -> "THROW"
            19 -> "AVOID ADS-B"
            20 -> "GUIDED NO GPS"
            21 -> "SMART RTL"
            22 -> "FLOW HOLD"
            23 -> "FOLLOW"
            24 -> "ZIGZAG"
            25 -> "SYSID"
            26 -> "AUTOROTATE"
            27 -> "AUTO RTL"
            28 -> "TURTLE"
            else -> "MODE $customMode"
        }
    }
}

sealed class FlightModeCommandState {
    object Idle : FlightModeCommandState()

    data class Pending(
        val requestedMode: ArduCopterFlightMode,
        val acknowledged: Boolean,
    ) : FlightModeCommandState()

    data class Succeeded(val mode: ArduCopterFlightMode) : FlightModeCommandState()

    data class Failed(
        val requestedMode: ArduCopterFlightMode,
        val reason: String,
    ) : FlightModeCommandState()
}

sealed class FlightModeRequestResult {
    object Sent : FlightModeRequestResult()
    object Disconnected : FlightModeRequestResult()
    object TargetUnavailable : FlightModeRequestResult()
    object AlreadyPending : FlightModeRequestResult()
}
