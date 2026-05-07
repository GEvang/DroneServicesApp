package com.example.droneservicesapp.data.geoawareness.incident

import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class GeoIncidentLogger(
    private val store: GeoIncidentEncryptedLogStore
) {

    fun logZoneEntered(
        zones: List<GeoZone>,
        latitude: Double?,
        longitude: Double?,
        altitudeMeters: Double?,
        datasetTitle: String?,
        datasetVersion: String?,
        healthState: String?,
        source: String
    ) {
        if (zones.isEmpty()) return
        store.append(
            buildEvent(
                type = GeoIncidentEventType.GEO_ZONE_ENTERED,
                zones = zones,
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altitudeMeters,
                datasetTitle = datasetTitle,
                datasetVersion = datasetVersion,
                healthState = healthState,
                source = source,
                messagePrefix = "Entered geo-zone"
            )
        )
    }

    fun logZoneExited(
        zones: List<GeoZone>,
        latitude: Double?,
        longitude: Double?,
        altitudeMeters: Double?,
        datasetTitle: String?,
        datasetVersion: String?,
        healthState: String?,
        source: String
    ) {
        if (zones.isEmpty()) return
        store.append(
            buildEvent(
                type = GeoIncidentEventType.GEO_ZONE_EXITED,
                zones = zones,
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altitudeMeters,
                datasetTitle = datasetTitle,
                datasetVersion = datasetVersion,
                healthState = healthState,
                source = source,
                messagePrefix = "Exited geo-zone"
            )
        )
    }

    private fun buildEvent(
        type: GeoIncidentEventType,
        zones: List<GeoZone>,
        latitude: Double?,
        longitude: Double?,
        altitudeMeters: Double?,
        datasetTitle: String?,
        datasetVersion: String?,
        healthState: String?,
        source: String,
        messagePrefix: String
    ): GeoIncidentEvent {
        val timestampMillis = System.currentTimeMillis()
        val highestRestriction = zones.maxByOrNull { restrictionRank(it.restriction) }?.restriction?.name
        val zoneNamesJoined = zones.map { it.name }.distinct().joinToString(", ")
        return GeoIncidentEvent(
            id = UUID.randomUUID().toString(),
            timestampMillis = timestampMillis,
            timestampIsoUtc = isoUtc(timestampMillis),
            type = type,
            zoneIds = zones.map { it.id }.distinct(),
            zoneNames = zones.map { it.name }.distinct(),
            highestRestriction = highestRestriction,
            restrictions = zones.map { it.restriction.name }.distinct(),
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            datasetTitle = datasetTitle,
            datasetVersion = datasetVersion,
            healthState = healthState,
            source = source,
            message = "$messagePrefix: $zoneNamesJoined",
            details = emptyMap()
        )
    }

    private fun restrictionRank(restriction: GeoZoneRestriction): Int {
        return when (restriction) {
            GeoZoneRestriction.PROHIBITED -> 4
            GeoZoneRestriction.REQ_AUTHORISATION -> 3
            GeoZoneRestriction.CONDITIONAL -> 2
            GeoZoneRestriction.INFORMATION -> 1
            GeoZoneRestriction.UNKNOWN -> 0
        }
    }

    private fun isoUtc(timestampMillis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestampMillis))
    }
}
