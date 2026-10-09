package com.example.droneservicesapp.ui.geoawareness

import android.view.View
import androidx.core.view.isVisible
import com.example.droneservicesapp.databinding.FragmentGeoAwarenessBinding

/** Owns screen action binding and section visibility without accessing repositories or ViewModels. */
class GeoAwarenessUiActionController {
    data class Actions(
        val overlayVisibilityChanged: (Boolean) -> Unit,
        val importDataset: () -> Unit,
        val removeAllDatasets: () -> Unit,
        val refreshStatus: () -> Unit,
        val showValidation: () -> Unit,
        val exportLogs: () -> Unit,
        val showDetailedLogs: () -> Unit,
        val exportEvidence: () -> Unit,
        val exportEncryptedIncidents: () -> Unit,
        val showVerification: () -> Unit,
    )

    private var binding: FragmentGeoAwarenessBinding? = null

    fun bind(binding: FragmentGeoAwarenessBinding, actions: Actions) {
        this.binding = binding
        configureSections(binding)
        binding.geoAwarenessOverlaySwitch.setOnCheckedChangeListener { _, checked ->
            actions.overlayVisibilityChanged(checked)
        }
        binding.geoAwarenessImportDatasetButton.setOnClickListener { actions.importDataset() }
        binding.geoAwarenessResetDatasetButton.setOnClickListener { actions.removeAllDatasets() }
        binding.geoAwarenessRefreshStatusButton.setOnClickListener { actions.refreshStatus() }
        binding.geoAwarenessValidationDetailsButton.setOnClickListener { actions.showValidation() }
        binding.geoAwarenessExportLogsButton.setOnClickListener { actions.exportLogs() }
        binding.geoAwarenessViewDetailedLogsButton.setOnClickListener { actions.showDetailedLogs() }
        binding.geoAwarenessExportEvidenceButton.setOnClickListener { actions.exportEvidence() }
        binding.geoAwarenessExportEncryptedIncidentsButton.setOnClickListener { actions.exportEncryptedIncidents() }
        binding.geoAwarenessInternalSection.setOnClickListener { actions.showVerification() }
    }

    fun clear() {
        binding?.apply {
            geoAwarenessOverlaySwitch.setOnCheckedChangeListener(null)
            geoAwarenessImportDatasetButton.setOnClickListener(null)
            geoAwarenessResetDatasetButton.setOnClickListener(null)
            geoAwarenessRefreshStatusButton.setOnClickListener(null)
            geoAwarenessValidationDetailsButton.setOnClickListener(null)
            geoAwarenessExportLogsButton.setOnClickListener(null)
            geoAwarenessViewDetailedLogsButton.setOnClickListener(null)
            geoAwarenessExportEvidenceButton.setOnClickListener(null)
            geoAwarenessExportEncryptedIncidentsButton.setOnClickListener(null)
            geoAwarenessInternalSection.setOnClickListener(null)
        }
        binding = null
    }

    private fun configureSections(binding: FragmentGeoAwarenessBinding) {
        binding.geoAwarenessFlightLogsSectionTitle.isVisible = false
        binding.geoAwarenessFlightLogsSection.isVisible = false
        binding.geoAwarenessLogsSectionTitle.isVisible = false
        binding.geoAwarenessLogsSection.isVisible = false
        binding.geoAwarenessClearLogsButton.isVisible = false
        binding.geoAwarenessInternalSectionTitle.isVisible = true
        binding.geoAwarenessInternalSection.isVisible = true
        binding.geoAwarenessNoticeSectionTitle.isVisible = false
        binding.geoAwarenessValidationSection.isVisible = false
        binding.geoAwarenessLoadingOverlay.visibility = View.GONE
    }
}
