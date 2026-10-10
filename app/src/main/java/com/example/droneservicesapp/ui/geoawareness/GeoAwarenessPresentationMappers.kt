package com.example.droneservicesapp.ui.geoawareness

import com.example.droneservicesapp.data.geoawareness.evidence.GeoAwarenessEvidencePackageExporter
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEvent
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthState
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class GeoAwarenessStatusTone { SUCCESS, WARNING, ERROR }

data class GeoAwarenessSourcePresentation(
    val importedActive: Boolean,
    val loadedDatasetCount: Int,
    val totalZoneCount: Int,
    val validationErrorCount: Int,
    val validationWarningCount: Int,
)

data class GeoAwarenessValidationPresentation(
    val result: GeoZoneValidationResult,
    val tone: GeoAwarenessStatusTone,
)

data class GeoAwarenessHealthPresentation(
    val health: GeoAwarenessHealth,
    val tone: GeoAwarenessStatusTone,
)

/** Pure mapping used by Compose; no Context, View, binding, or widget types. */
object GeoAwarenessStatusPresentationMapper {
    fun source(
        importedActive: Boolean,
        datasetInfo: GeoZoneDatasetInfo?,
        records: List<GeoZoneDatasetRecord>,
        validation: GeoZoneValidationResult?,
    ): GeoAwarenessSourcePresentation {
        val resolved = validation ?: GeoZoneValidationResult.ok()
        return GeoAwarenessSourcePresentation(
            importedActive = importedActive,
            loadedDatasetCount = records.size,
            totalZoneCount = datasetInfo?.zoneCount ?: records.sumOf { it.zoneCount },
            validationErrorCount = resolved.errorCount,
            validationWarningCount = resolved.warningCount,
        )
    }

    fun validation(result: GeoZoneValidationResult?): GeoAwarenessValidationPresentation {
        val resolved = result ?: GeoZoneValidationResult.ok()
        return GeoAwarenessValidationPresentation(
            result = resolved,
            tone = when {
                resolved.hasErrors -> GeoAwarenessStatusTone.ERROR
                resolved.hasWarnings -> GeoAwarenessStatusTone.WARNING
                else -> GeoAwarenessStatusTone.SUCCESS
            },
        )
    }

    fun health(health: GeoAwarenessHealth) = GeoAwarenessHealthPresentation(
        health = health,
        tone = when (health.state) {
            GeoAwarenessHealthState.AVAILABLE -> GeoAwarenessStatusTone.SUCCESS
            GeoAwarenessHealthState.DEGRADED,
            GeoAwarenessHealthState.STALE -> GeoAwarenessStatusTone.WARNING
            GeoAwarenessHealthState.UNAVAILABLE -> GeoAwarenessStatusTone.ERROR
        },
    )
}

data class GeoAwarenessEventRowPresentation(
    val title: String,
    val message: String,
    val tone: GeoAwarenessEventTone,
    val metadata: String?,
)

enum class GeoAwarenessEventTone { NORMAL, WARNING, ERROR }

/** Pure event-log mapping used by both list and dialog composables. */
object GeoAwarenessEventLogPresentationMapper {
    fun detailedPreview(events: List<GeoAwarenessEvent>): String {
        val sorted = events.sortedByDescending { it.timestampMillis }
        return if (sorted.isEmpty()) {
            "No detailed geo-awareness events recorded yet."
        } else {
            sorted.joinToString("\n") { "${it.timestampIsoUtc} | ${it.type.name} | ${it.message}" }
        }
    }

    fun rows(events: List<GeoAwarenessEvent>): List<GeoAwarenessEventRowPresentation> = events
        .filter { it.type in GeoAwarenessEvidencePackageExporter.IMPORTANT_FLIGHT_EVENTS }
        .sortedByDescending { it.timestampMillis }
        .take(50)
        .map { event ->
            GeoAwarenessEventRowPresentation(
                title = "${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.timestampMillis))}  ${friendlyLabel(event)}",
                message = event.message,
                tone = when (event.severity.uppercase(Locale.getDefault())) {
                    "ERROR", "BLOCKED" -> GeoAwarenessEventTone.ERROR
                    "WARNING" -> GeoAwarenessEventTone.WARNING
                    else -> GeoAwarenessEventTone.NORMAL
                },
                metadata = buildString {
                    event.zoneNames.firstOrNull()?.let { append("Zone: $it") }
                    event.restriction?.let { if (isNotEmpty()) append(" | "); append("Restriction: $it") }
                    event.flightMode?.let { if (isNotEmpty()) append(" | "); append("Mode: $it") }
                }.takeIf(String::isNotBlank),
            )
        }

    private fun friendlyLabel(event: GeoAwarenessEvent): String = event.type.name.lowercase()
        .split('_').joinToString(" ") { it.replaceFirstChar { char -> char.titlecase(Locale.getDefault()) } }
}
