package com.example.droneservicesapp.ui.geoawareness

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetRecord
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetSourceType
import com.example.droneservicesapp.domain.geoawareness.GeoZoneDatasetStalenessPolicy
import com.example.droneservicesapp.domain.geoawareness.validation.GeoZoneValidationResult
import com.google.android.material.button.MaterialButton

/** Builds and renders dataset cards while preserving source ordering and imported-only actions. */
class GeoAwarenessDatasetListRenderer(private val context: Context) {
    data class Actions(
        val update: (GeoZoneDatasetRecord) -> Unit,
        val remove: (GeoZoneDatasetRecord) -> Unit,
        val validationDetails: (GeoZoneDatasetRecord) -> Unit,
    )

    private val density = context.resources.displayMetrics.density
    private val mutationButtons = mutableListOf<MaterialButton>()
    private var busy = false

    fun render(container: LinearLayout, records: List<GeoZoneDatasetRecord>, actions: Actions) {
        mutationButtons.clear()
        container.removeAllViews()
        if (records.isEmpty()) {
            container.addView(panelText(context.getString(R.string.geo_awareness_dataset_list_empty)).apply { setPadding(0, 0, 0, 0) })
            return
        }
        records.forEachIndexed { index, record -> container.addView(card(record, if (index == 0) 0 else 12, actions)) }
        setBusy(busy)
    }

    fun setBusy(value: Boolean) {
        busy = value
        mutationButtons.forEach { button -> button.isEnabled = !value; button.alpha = if (value) 0.6f else 1f }
    }

    fun clear() = mutationButtons.clear()

    fun statusValue(text: String): TextView = TextView(context).apply {
        this.text = text; setTextColor(Color.parseColor("#EAF1FF")); textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
    }

    fun panelText(text: String, textColor: Int = Color.parseColor("#C5D0E6")): TextView = TextView(context).apply {
        this.text = text; setTextColor(textColor); textSize = 13f; setPadding(0, dp(4), 0, 0)
    }

    private fun card(record: GeoZoneDatasetRecord, topMarginDp: Int, actions: Actions): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded("#7010151C", "#2EFFFFFF", 10)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(topMarginDp) }
        val validationLabel = when {
            record.validationResult.hasErrors -> context.getString(R.string.geo_awareness_validation_errors)
            record.validationResult.hasWarnings -> context.getString(R.string.geo_awareness_validation_warnings)
            else -> context.getString(R.string.geo_awareness_validation_ok)
        }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(statusValue(record.displayName).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            addView(validationPill(record.validationResult, validationLabel))
        })
        addView(panelText(context.getString(R.string.geo_source, when (record.sourceType) {
            GeoZoneDatasetSourceType.BUNDLED_ASSET -> context.getString(R.string.geo_awareness_dataset_source_bundled_row)
            GeoZoneDatasetSourceType.IMPORTED_FILE -> context.getString(R.string.geo_awareness_dataset_source_imported_row)
        })).apply { setPadding(0, dp(10), 0, 0) })
        addView(metaGrid(record))
        addView(actionButton(context.getString(R.string.geo_awareness_view_validation_details), false) { actions.validationDetails(record) }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { topMargin = dp(10) }
        })
        if (record.sourceType == GeoZoneDatasetSourceType.IMPORTED_FILE) {
            addView(panelText("${context.getString(R.string.geo_awareness_updated_label)} ${record.ageDescription ?: GeoZoneDatasetStalenessPolicy.ageDescription(record.updatedAtMillis)}"))
            addView(panelText("${context.getString(R.string.geo_awareness_stale_label)} ${context.getString(if (record.isStale) R.string.geo_awareness_yes else R.string.geo_awareness_no)}", if (record.isStale) Color.parseColor("#FFB74D") else Color.parseColor("#C5D0E6")))
        }
        if (record.sourceType == GeoZoneDatasetSourceType.IMPORTED_FILE && record.storageFileName != null) {
            addView(panelText(context.getString(R.string.geo_actions)).apply { setPadding(0, dp(12), 0, 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
                addView(actionButton(context.getString(R.string.geo_awareness_update_dataset), true) { actions.update(record) })
                addView(actionButton(context.getString(R.string.geo_awareness_remove_dataset), true, danger = true) { actions.remove(record) }.apply {
                    (layoutParams as LinearLayout.LayoutParams).marginStart = dp(8)
                })
            })
        }
    }

    private fun metaGrid(record: GeoZoneDatasetRecord) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
        addView(metaRow("Version", record.datasetInfo.version ?: "N/A", "Zones", record.zoneCount.toString()))
        addView(metaRow("Country", record.datasetInfo.country ?: "N/A", "Type", if (record.datasetInfo.isDummy) "Test / dummy" else "Validated JSON"))
        addView(metaRow("Errors", record.validationResult.errorCount.toString(), "Warnings", record.validationResult.warningCount.toString()))
    }

    private fun metaRow(l1: String, v1: String, l2: String, v2: String) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) }
        addView(metaCell(l1, v1).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        addView(metaCell(l2, v2).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) } })
    }

    private fun metaCell(label: String, value: String) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; background = rounded("#661B2430", "#1FFFFFFF", 8)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        addView(TextView(context).apply { text = label; setTextColor(Color.parseColor("#8FA0B8")); textSize = 11f; setTypeface(typeface, Typeface.BOLD) })
        addView(TextView(context).apply { text = value; setTextColor(Color.parseColor("#EAF1FF")); textSize = 13f; maxLines = 2 })
    }

    private fun validationPill(result: GeoZoneValidationResult, label: String) = TextView(context).apply {
        text = label; setTextColor(Color.WHITE); textSize = 12f; setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(16).toFloat(); setColor(Color.parseColor(when { result.hasErrors -> "#B71C1C"; result.hasWarnings -> "#E65100"; else -> "#2E7D32" })) }
    }

    private fun actionButton(textValue: String, mutating: Boolean, danger: Boolean = false, click: () -> Unit) = MaterialButton(context).apply {
        text = textValue; minWidth = 0; insetTop = 0; insetBottom = 0
        backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, if (danger) R.color.ds_color_shell_danger else R.color.ds_color_shell_selected_surface))
        setTextColor(ContextCompat.getColor(context, if (danger) R.color.ds_color_text_primary else R.color.ds_color_shell_selected_content))
        cornerRadius = context.resources.getDimensionPixelSize(R.dimen.ds_radius_round); setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
        if (mutating) mutationButtons += this
    }

    private fun rounded(fill: String, stroke: String, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(radiusDp).toFloat(); setColor(Color.parseColor(fill)); setStroke(dp(1), Color.parseColor(stroke))
    }

    private fun dp(value: Int) = (value * density).toInt()
}
