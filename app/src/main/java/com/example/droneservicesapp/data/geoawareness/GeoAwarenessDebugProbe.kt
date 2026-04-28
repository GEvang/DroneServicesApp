package com.example.droneservicesapp.data.geoawareness

import android.content.Context
import android.util.Log
import com.example.droneservicesapp.domain.geoawareness.GeoZoneGeometry
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction

object GeoAwarenessDebugProbe {

    fun logDummyRethymnoZones(context: Context) {
        try {
            val repository = GeoZoneRepository(
                GeoZoneAssetDataSource(context.applicationContext)
            )
            val zones = repository.loadDummyRethymnoZones()
            val circles = zones.sumOf { zone -> zone.geometries.count { it is GeoZoneGeometry.Circle } }
            val polygons = zones.sumOf { zone -> zone.geometries.count { it is GeoZoneGeometry.Polygon } }
            val prohibited = zones.count { it.restriction == GeoZoneRestriction.PROHIBITED }
            val auth = zones.count { it.restriction == GeoZoneRestriction.REQ_AUTHORISATION }
            val conditional = zones.count { it.restriction == GeoZoneRestriction.CONDITIONAL }
            val information = zones.count { it.restriction == GeoZoneRestriction.INFORMATION }
            val unknown = zones.count { it.restriction == GeoZoneRestriction.UNKNOWN }

            Log.d(
                TAG,
                "Geo-awareness dummy validation: zones=${zones.size} circles=$circles polygons=$polygons prohibited=$prohibited auth=$auth conditional=$conditional information=$information unknown=$unknown"
            )

            zones.forEach { zone ->
                Log.d(
                    TAG,
                    "${zone.id} | ${zone.name} | ${zone.restriction} | geometries=${zone.geometries.size}"
                )
            }
        } catch (error: Exception) {
            Log.e(TAG, "Geo-awareness dummy validation failed", error)
        }
    }

    private const val TAG = "GeoAwarenessDebug"
}
