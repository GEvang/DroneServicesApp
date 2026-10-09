package com.example.droneservicesapp.ui.geoawareness

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.geoawareness.testing.GeoAwarenessTestStatus
import com.example.droneservicesapp.domain.geoawareness.verification.GeoAwarenessVerificationCase
import com.example.droneservicesapp.domain.geoawareness.verification.GeoAwarenessVerificationStatus
import com.google.android.material.button.MaterialButton

/** Renders and live-updates the verification checklist and automated test results. */
class GeoAwarenessVerificationDialogController(
    private val context: Context,
    private val controller: GeoAwarenessVerificationController,
) {
    private val density = context.resources.displayMetrics.density
    private var openDialog: AlertDialog? = null

    fun show() {
        if (openDialog?.isShowing == true) return
        val summary = text(14f, "#21304A")
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val resetButton = outlinedButton(context.getString(R.string.geo_awareness_verification_reset)) { confirmReset() }
        val runButton = outlinedButton("Run automated geo-awareness tests") { controller.runTests() }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(summary)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(12); bottomMargin = dp(12)
                }
                addView(resetButton, LinearLayout.LayoutParams(0, dp(44), 1f))
                addView(runButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(8) })
            })
            addView(ScrollView(context).apply {
                isFillViewport = true
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420))
                addView(content)
            })
        }
        openDialog = AlertDialog.Builder(context, R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(R.string.geo_awareness_verification_checklist)
            .setView(root)
            .setPositiveButton(android.R.string.ok, null)
            .create().also { dialog ->
                dialog.setOnDismissListener {
                    controller.setStateListener(null)
                    openDialog = null
                }
                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#212121"))
            }
        controller.setStateListener { state -> render(state, summary, content, runButton) }
    }

    fun dismiss() {
        controller.setStateListener(null)
        openDialog?.dismiss()
        openDialog = null
    }

    private fun render(
        state: GeoAwarenessVerificationController.State,
        summary: TextView,
        content: LinearLayout,
        runButton: MaterialButton,
    ) {
        val statuses = state.cases.map { it.status }
        summary.text = buildString {
            appendLine("${context.getString(R.string.geo_awareness_verification_total)} ${state.cases.size}")
            appendLine("${context.getString(R.string.geo_awareness_verification_passed)} ${statuses.count { it == GeoAwarenessVerificationStatus.PASS }}")
            appendLine("${context.getString(R.string.geo_awareness_verification_failed)} ${statuses.count { it == GeoAwarenessVerificationStatus.FAIL }}")
            appendLine("${context.getString(R.string.geo_awareness_verification_blocked)} ${statuses.count { it == GeoAwarenessVerificationStatus.BLOCKED }}")
            append("${context.getString(R.string.geo_awareness_verification_not_run)} ${statuses.count { it == GeoAwarenessVerificationStatus.NOT_RUN }}")
        }
        runButton.isEnabled = !state.testRunning
        runButton.text = if (state.testRunning) "Running tests..." else "Run automated geo-awareness tests"
        content.removeAllViews()
        state.cases.groupBy { it.definition.category }.forEach { (category, cases) ->
            content.addView(text(15f, "#21304A", bold = true).apply { text = category })
            cases.forEach { content.addView(caseCard(it)) }
        }
        state.testRun?.let { result ->
            content.addView(text(16f, "#21304A", bold = true).apply {
                text = "Automated tests — ${result.overallStatus.name} (${result.passCount} passed, ${result.failCount} failed, ${result.warningCount} warnings)"
                setPadding(0, dp(18), 0, dp(6))
            })
            result.results.forEach { test ->
                content.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(8), 0, dp(8))
                    addView(text(15f, "#21304A", bold = true).apply { text = "${test.id}  ${test.name}" })
                    addView(statusChip(test.status.name, testColor(test.status)))
                    addView(text(13f, "#42536F").apply { text = test.message; setPadding(0, dp(4), 0, 0) })
                })
            }
        }
    }

    private fun caseCard(caseState: GeoAwarenessVerificationController.CaseState) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = rounded("#EEF3FB", "#CCD8EA")
        addView(text(15f, "#21304A", bold = true).apply { text = "${caseState.definition.id}  ${caseState.definition.title}" })
        addView(statusChip(statusLabel(caseState.status), statusColor(caseState.status)))
        addView(text(13f, "#42536F").apply {
            text = context.getString(R.string.geo_current_status, statusLabel(caseState.status)); setPadding(0, dp(4), 0, 0)
        })
        addView(outlinedButton(context.getString(R.string.geo_awareness_verification_details)) {
            showCaseDetails(caseState.definition, caseState.status)
        }.apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { topMargin = dp(10) } })
        addView(statusRow(GeoAwarenessVerificationStatus.NOT_RUN, GeoAwarenessVerificationStatus.PASS, caseState))
        addView(statusRow(GeoAwarenessVerificationStatus.FAIL, GeoAwarenessVerificationStatus.BLOCKED, caseState))
    }

    private fun statusRow(first: GeoAwarenessVerificationStatus, second: GeoAwarenessVerificationStatus, state: GeoAwarenessVerificationController.CaseState) =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
            addView(statusButton(first, state), LinearLayout.LayoutParams(0, dp(40), 1f))
            addView(statusButton(second, state), LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(10) })
        }

    private fun statusButton(status: GeoAwarenessVerificationStatus, state: GeoAwarenessVerificationController.CaseState) =
        outlinedButton(statusLabel(status)) { controller.updateStatus(state.definition, status) }.apply { isEnabled = status != state.status }

    private fun showCaseDetails(case: GeoAwarenessVerificationCase, status: GeoAwarenessVerificationStatus) {
        val message = buildString {
            appendLine(context.getString(R.string.geo_verification_status, statusLabel(status))); appendLine()
            appendLine(context.getString(R.string.geo_verification_purpose)); appendLine(case.purpose); appendLine()
            appendLine(context.getString(R.string.geo_verification_preconditions))
            if (case.preconditions.isEmpty()) appendLine("- ${context.getString(R.string.geo_none)}") else case.preconditions.forEach { appendLine("- $it") }
            appendLine(); appendLine(context.getString(R.string.geo_verification_steps)); case.steps.forEach { appendLine("- $it") }
            appendLine(); appendLine(context.getString(R.string.geo_verification_expected)); appendLine(case.expectedResult)
            appendLine(); appendLine(context.getString(R.string.geo_verification_evidence)); case.evidenceToCapture.forEach { appendLine("- $it") }
        }.trim()
        AlertDialog.Builder(context, R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle("${case.id} ${case.title}").setMessage(message).setPositiveButton(android.R.string.ok, null).show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(context, R.style.Theme_DroneServicesApp_AlertDialog)
            .setTitle(R.string.geo_awareness_verification_checklist).setMessage(R.string.geo_reset_checklist_question)
            .setPositiveButton(R.string.geo_reset) { _, _ -> controller.reset() }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun outlinedButton(label: String, click: () -> Unit) = MaterialButton(context, null, R.attr.materialButtonOutlinedStyle).apply {
        text = label; setOnClickListener { click() }
    }

    private fun statusChip(label: String, color: Int) = text(12f, "#FFFFFF", bold = true).apply {
        text = label; background = rounded(color); setPadding(dp(12), dp(6), dp(12), dp(6))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
    }

    private fun text(size: Float, color: String, bold: Boolean = false) = TextView(context).apply {
        textSize = size; setTextColor(Color.parseColor(color)); if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun rounded(fill: String, stroke: String? = null) = rounded(Color.parseColor(fill), stroke?.let { Color.parseColor(it) })
    private fun rounded(fill: Int, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = 18f * density; setColor(fill); stroke?.let { setStroke(dp(1), it) }
    }

    private fun statusLabel(status: GeoAwarenessVerificationStatus) = context.getString(when (status) {
        GeoAwarenessVerificationStatus.NOT_RUN -> R.string.geo_awareness_verification_status_not_run
        GeoAwarenessVerificationStatus.PASS -> R.string.geo_awareness_verification_status_pass
        GeoAwarenessVerificationStatus.FAIL -> R.string.geo_awareness_verification_status_fail
        GeoAwarenessVerificationStatus.BLOCKED -> R.string.geo_awareness_verification_status_blocked
    })

    private fun statusColor(status: GeoAwarenessVerificationStatus) = Color.parseColor(when (status) {
        GeoAwarenessVerificationStatus.NOT_RUN -> "#616161"
        GeoAwarenessVerificationStatus.PASS -> "#2E7D32"
        GeoAwarenessVerificationStatus.FAIL -> "#B71C1C"
        GeoAwarenessVerificationStatus.BLOCKED -> "#EF6C00"
    })

    private fun testColor(status: GeoAwarenessTestStatus) = Color.parseColor(when (status) {
        GeoAwarenessTestStatus.PASS -> "#2E7D32"
        GeoAwarenessTestStatus.WARNING -> "#EF6C00"
        GeoAwarenessTestStatus.FAIL -> "#B71C1C"
        GeoAwarenessTestStatus.SKIPPED -> "#616161"
    })

    private fun dp(value: Int) = (value * density).toInt()
}
