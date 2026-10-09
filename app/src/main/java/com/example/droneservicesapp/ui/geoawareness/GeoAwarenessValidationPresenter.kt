package com.example.droneservicesapp.ui.geoawareness

import android.content.Context
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationSeverity

/** Formats validation state without creating or retaining Android views. */
class GeoAwarenessValidationPresenter(private val context: Context) {
    data class Summary(val errorCount: Int, val warningCount: Int, val infoCount: Int, val text: String)

    fun combinedSummary(result: GeoZoneValidationResult?): Summary {
        val resolved = result ?: GeoZoneValidationResult.ok()
        return Summary(
            resolved.errorCount,
            resolved.warningCount,
            resolved.infoCount,
            context.getString(R.string.geo_validation_counts, resolved.errorCount, resolved.warningCount, resolved.infoCount),
        )
    }

    fun details(result: GeoZoneValidationResult, issueLimit: Int = 30): String {
        if (result.issues.isEmpty()) return context.getString(R.string.geo_dataset_validation_passed)
        val visible = result.issues.take(issueLimit)
        val remaining = result.issues.size - visible.size
        return buildString {
            GeoZoneValidationSeverity.values().forEach { severity ->
                val issues = visible.filter { it.severity == severity }
                if (issues.isEmpty()) return@forEach
                appendLine(severity.name)
                issues.forEach { issue ->
                    append("- [${issue.code}] ${issue.message}")
                    if (!issue.zoneId.isNullOrBlank()) {
                        append(" (zoneId=${issue.zoneId}")
                        if (!issue.field.isNullOrBlank()) append(", field=${issue.field}")
                        append(")")
                    } else if (!issue.field.isNullOrBlank()) append(" (field=${issue.field})")
                    appendLine()
                }
                appendLine()
            }
            if (remaining > 0) append("...and $remaining more.")
        }
    }

    fun datasetDetails(record: GeoZoneDatasetRecord): String = details(record.validationResult)

    fun failureSummary(error: Throwable, result: GeoZoneValidationResult?, fallback: Int): String {
        val issueLines = result?.issues?.filter { it.severity == GeoZoneValidationSeverity.ERROR }?.take(10)
            ?.joinToString("\n") { "- [${it.code}] ${it.message}" }.orEmpty()
        return buildString {
            appendLine(error.message ?: context.getString(fallback))
            result?.let { appendLine(); appendLine(combinedSummary(it).text) }
            if (issueLines.isNotBlank()) { appendLine(); append(issueLines) }
        }.trim()
    }
}
