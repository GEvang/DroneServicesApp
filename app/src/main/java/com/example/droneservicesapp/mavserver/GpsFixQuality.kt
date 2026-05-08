package com.example.droneservicesapp.mavserver

enum class GpsFixQuality {
    DISCONNECTED,
    NO_GPS,
    FIX_2D,
    FIX_3D,
    DGPS,
    RTK_FLOAT,
    RTK_FIXED,
    UNKNOWN
}
