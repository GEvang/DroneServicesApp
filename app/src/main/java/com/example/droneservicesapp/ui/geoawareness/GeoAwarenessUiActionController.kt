package com.example.droneservicesapp.ui.geoawareness

import android.view.View
import androidx.core.view.isVisible
import com.example.droneservicesapp.databinding.FragmentGeoAwarenessBinding

/** Owns screen action binding and section visibility without accessing repositories or ViewModels. */
class GeoAwarenessUiActionController {
    private var binding: FragmentGeoAwarenessBinding? = null

    fun bind(binding: FragmentGeoAwarenessBinding, dispatch: (GeoAwarenessAction) -> Unit) {
        this.binding = binding
        configureSections(binding)
        binding.geoAwarenessOverlaySwitch.setOnCheckedChangeListener { _, checked ->
            dispatch(GeoAwarenessAction.SetOverlayVisibility(checked))
        }
        binding.geoAwarenessImportDatasetButton.setOnClickListener { dispatch(GeoAwarenessAction.ImportDataset) }
        binding.geoAwarenessResetDatasetButton.setOnClickListener { dispatch(GeoAwarenessAction.RemoveAllDatasets) }
        binding.geoAwarenessRefreshStatusButton.setOnClickListener { dispatch(GeoAwarenessAction.RefreshStatus) }
        binding.geoAwarenessValidationDetailsButton.setOnClickListener { dispatch(GeoAwarenessAction.ShowValidation) }
        binding.geoAwarenessExportLogsButton.setOnClickListener { dispatch(GeoAwarenessAction.ExportLogs) }
        binding.geoAwarenessViewDetailedLogsButton.setOnClickListener { dispatch(GeoAwarenessAction.ShowDetailedLogs) }
        binding.geoAwarenessExportEvidenceButton.setOnClickListener { dispatch(GeoAwarenessAction.ExportEvidence) }
        binding.geoAwarenessExportEncryptedIncidentsButton.setOnClickListener {
            dispatch(GeoAwarenessAction.ExportEncryptedIncidents)
        }
        binding.geoAwarenessInternalSection.setOnClickListener { dispatch(GeoAwarenessAction.ShowVerification) }
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
