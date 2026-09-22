package com.example.droneservicesapp.ui.preview

import com.google.android.gms.maps.model.LatLng
import io.dronefleet.mavlink.common.MavCmd
import io.dronefleet.mavlink.common.MissionItemInt

data class DownloadedMissionWaypoint(
    val position: LatLng,
    val altitudeMeters: Float,
)

/** Extracts drawable navigation geometry from a mission downloaded from the aircraft. */
fun downloadedMissionWaypoints(items: List<MissionItemInt>): List<DownloadedMissionWaypoint> = items
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
            DownloadedMissionWaypoint(
                position = LatLng(latitude, longitude),
                altitudeMeters = item.z().takeIf { it.isFinite() } ?: 0f,
            )
        } else {
            null
        }
    }

fun downloadedMissionPath(items: List<MissionItemInt>): List<LatLng> =
    downloadedMissionWaypoints(items).map(DownloadedMissionWaypoint::position)
