package com.example.droneservicesapp.ui.preview

import com.google.android.gms.maps.model.LatLng
import io.dronefleet.mavlink.common.MavCmd
import io.dronefleet.mavlink.common.MissionItemInt

/** Extracts drawable navigation geometry from a mission downloaded from the aircraft. */
fun downloadedMissionPath(items: List<MissionItemInt>): List<LatLng> = items
    .sortedBy { it.seq() }
    .filter { item ->
        item.command().entry() == MavCmd.MAV_CMD_NAV_WAYPOINT ||
            item.command().entry() == MavCmd.MAV_CMD_NAV_SPLINE_WAYPOINT
    }
    .mapNotNull { item ->
        val latitude = item.x().toDouble() * 1e-7
        val longitude = item.y().toDouble() * 1e-7
        if (
            latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            (latitude != 0.0 || longitude != 0.0)
        ) {
            LatLng(latitude, longitude)
        } else {
            null
        }
    }
