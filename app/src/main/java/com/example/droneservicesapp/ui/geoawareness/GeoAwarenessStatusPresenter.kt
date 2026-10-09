package com.example.droneservicesapp.ui.geoawareness

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.example.droneservicesapp.R
import com.example.droneservicesapp.databinding.FragmentGeoAwarenessBinding
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealth
import com.example.droneservicesapp.domain.geoawareness.GeoAwarenessHealthState
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetInfo
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult

/** Builds and renders immutable dataset, validation, health, and busy presentation state. */
class GeoAwarenessStatusPresenter(private val context: Context) {
    data class SourceState(
        val importedActive: Boolean,
        val datasetInfo: GeoZoneDatasetInfo?,
        val records: List<GeoZoneDatasetRecord>,
        val validation: GeoZoneValidationResult?,
    )

    fun renderSource(binding: FragmentGeoAwarenessBinding, state: SourceState) {
        binding.geoAwarenessCurrentSource.text = context.getString(R.string.geo_awareness_current_source_label) + " " +
            context.getString(if (state.importedActive) R.string.geo_awareness_current_source_imported else R.string.geo_awareness_current_source_none)
        binding.geoAwarenessLoadedDatasetsSummary.text = context.getString(R.string.geo_awareness_loaded_datasets_label) + " " + state.records.size
        binding.geoAwarenessTotalZonesSummary.text = context.getString(R.string.geo_awareness_total_zones_label) + " " +
            (state.datasetInfo?.zoneCount ?: state.records.sumOf { it.zoneCount })
        val validation = state.validation ?: GeoZoneValidationResult.ok()
        binding.geoAwarenessTotalValidationSummary.text = context.getString(R.string.geo_awareness_total_validation_label) +
            " Errors ${validation.errorCount} | Warnings ${validation.warningCount}"
    }

    fun renderValidation(binding: FragmentGeoAwarenessBinding, result: GeoZoneValidationResult?) {
        val resolved = result ?: GeoZoneValidationResult.ok()
        val (label, color) = when {
            resolved.hasErrors -> context.getString(R.string.geo_awareness_validation_errors) to "#B71C1C"
            resolved.hasWarnings -> context.getString(R.string.geo_awareness_validation_warnings) to "#EF6C00"
            else -> context.getString(R.string.geo_awareness_validation_ok) to "#2E7D32"
        }
        binding.geoAwarenessValidationChip.text = context.getString(R.string.geo_validation_status, label)
        binding.geoAwarenessValidationChip.background = pill(color)
        binding.geoAwarenessValidationCounts.text = context.getString(
            R.string.geo_validation_counts, resolved.errorCount, resolved.warningCount, resolved.infoCount
        )
    }

    fun renderHealth(binding: FragmentGeoAwarenessBinding, health: GeoAwarenessHealth) {
        val (label, color) = when (health.state) {
            GeoAwarenessHealthState.AVAILABLE -> R.string.geo_awareness_health_available to "#2E7D32"
            GeoAwarenessHealthState.DEGRADED -> R.string.geo_awareness_health_degraded to "#E65100"
            GeoAwarenessHealthState.STALE -> R.string.geo_awareness_health_stale to "#EF6C00"
            GeoAwarenessHealthState.UNAVAILABLE -> R.string.geo_awareness_health_unavailable to "#B71C1C"
        }
        binding.geoAwarenessHealthChip.text = context.getString(R.string.geo_awareness_health_label) + " " + context.getString(label)
        binding.geoAwarenessHealthChip.setTextColor(Color.WHITE)
        binding.geoAwarenessHealthChip.background = pill(color)
        binding.geoAwarenessHealthMessage.text = health.message
    }

    fun renderBusy(binding: FragmentGeoAwarenessBinding, busy: Boolean, message: String) {
        binding.geoAwarenessLoadingOverlay.visibility = if (busy) View.VISIBLE else View.GONE
        binding.geoAwarenessLoadingText.text = message
        listOf(binding.geoAwarenessImportDatasetButton, binding.geoAwarenessResetDatasetButton, binding.geoAwarenessRefreshStatusButton).forEach {
            it.isEnabled = !busy; it.alpha = if (busy) 0.6f else 1f
        }
    }

    private fun pill(color: String) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 18 * context.resources.displayMetrics.density
        setColor(Color.parseColor(color))
        setStroke((context.resources.displayMetrics.density).toInt(), Color.parseColor("#33FFFFFF"))
    }
}
