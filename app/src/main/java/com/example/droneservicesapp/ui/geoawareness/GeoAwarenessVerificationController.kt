package com.example.droneservicesapp.ui.geoawareness

import android.content.Context
import com.example.droneservicesapp.data.geoawareness.GeoZoneRepository
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventLogger
import com.example.droneservicesapp.data.geoawareness.logging.GeoAwarenessEventType
import com.example.droneservicesapp.data.geoawareness.verification.GeoAwarenessVerificationStatusStore
import com.example.droneservicesapp.domain.geoawareness.testing.GeoAwarenessTestRunResult
import com.example.droneservicesapp.domain.geoawareness.testing.GeoAwarenessTestRunner
import com.example.droneservicesapp.domain.geoawareness.verification.GeoAwarenessVerificationCase
import com.example.droneservicesapp.domain.geoawareness.verification.GeoAwarenessVerificationChecklist
import com.example.droneservicesapp.domain.geoawareness.verification.GeoAwarenessVerificationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns persisted manual verification state and independent automated diagnostic runs. */
class GeoAwarenessVerificationController(
    context: Context,
    private val scope: CoroutineScope,
    private val repositoryProvider: () -> GeoZoneRepository,
    private val eventLogger: GeoAwarenessEventLogger,
    private val onAuditLogChanged: () -> Unit,
) {
    data class CaseState(val definition: GeoAwarenessVerificationCase, val status: GeoAwarenessVerificationStatus)
    data class State(
        val cases: List<CaseState>,
        val testRun: GeoAwarenessTestRunResult?,
        val testRunning: Boolean,
    )

    private val store = GeoAwarenessVerificationStatusStore(context.applicationContext)
    private val appContext = context.applicationContext
    private var testJob: Job? = null
    private var active = true
    private var stateListener: ((State) -> Unit)? = null

    var lastTestRunResult: GeoAwarenessTestRunResult? = null
        private set

    val state: State
        get() = buildState()

    fun statusStoreForEvidenceExport(): GeoAwarenessVerificationStatusStore = store

    fun setStateListener(listener: ((State) -> Unit)?) {
        stateListener = listener
        if (listener != null && active) listener(buildState())
    }

    fun updateStatus(case: GeoAwarenessVerificationCase, status: GeoAwarenessVerificationStatus) {
        if (!active) return
        val previous = store.getStatus(case.id)
        if (previous == status) return
        store.setStatus(case.id, status)
        eventLogger.logSimple(
            type = GeoAwarenessEventType.VERIFICATION_CASE_STATUS_CHANGED,
            severity = "INFO",
            message = "Geo-awareness verification case status changed",
            details = mapOf(
                "caseId" to case.id,
                "caseTitle" to case.title,
                "previousStatus" to previous.name,
                "newStatus" to status.name,
            ),
        )
        onAuditLogChanged()
        publish()
    }

    fun reset() {
        if (!active) return
        store.resetAll()
        eventLogger.logSimple(
            type = GeoAwarenessEventType.VERIFICATION_CHECKLIST_RESET,
            severity = "INFO",
            message = "Geo-awareness verification checklist statuses reset",
        )
        onAuditLogChanged()
        publish()
    }

    fun runTests() {
        if (!active || testJob?.isActive == true) return
        publish(testRunning = true)
        testJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    GeoAwarenessTestRunner(appContext, repositoryProvider(), eventLogger).runAllTests()
                }
                if (!active) return@launch
                lastTestRunResult = result
                onAuditLogChanged()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                testJob = null
                if (active) publish(testRunning = false)
            }
        }
    }

    fun clear() {
        active = false
        stateListener = null
        testJob?.cancel()
        testJob = null
    }

    private fun publish(testRunning: Boolean = testJob?.isActive == true) {
        stateListener?.invoke(buildState(testRunning))
    }

    private fun buildState(testRunning: Boolean = testJob?.isActive == true): State {
        val statuses = store.getAllStatuses()
        return State(
            cases = GeoAwarenessVerificationChecklist.cases.map { CaseState(it, statuses[it.id] ?: GeoAwarenessVerificationStatus.NOT_RUN) },
            testRun = lastTestRunResult,
            testRunning = testRunning,
        )
    }
}
