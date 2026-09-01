package com.example.droneservicesapp.data.geoawareness.verification

import android.content.Context
import androidx.core.content.edit
import com.example.droneservicesapp.domain.geoawareness.verification.GeoAwarenessVerificationChecklist
import com.example.droneservicesapp.domain.geoawareness.verification.GeoAwarenessVerificationStatus

class GeoAwarenessVerificationStatusStore(context: Context) {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getStatus(caseId: String): GeoAwarenessVerificationStatus {
        val raw = preferences.getString(statusKey(caseId), null)
        return raw?.let {
            runCatching { GeoAwarenessVerificationStatus.valueOf(it) }.getOrNull()
        } ?: GeoAwarenessVerificationStatus.NOT_RUN
    }

    fun setStatus(caseId: String, status: GeoAwarenessVerificationStatus) {
        preferences.edit {
            putString(statusKey(caseId), status.name)
        }
    }

    fun getAllStatuses(): Map<String, GeoAwarenessVerificationStatus> {
        return GeoAwarenessVerificationChecklist.cases.associate { verificationCase ->
            verificationCase.id to getStatus(verificationCase.id)
        }
    }

    fun resetAll() {
        preferences.edit {
            GeoAwarenessVerificationChecklist.cases.forEach { verificationCase ->
                remove(statusKey(verificationCase.id))
            }
        }
    }

    private fun statusKey(caseId: String): String = "geo_verification_status_$caseId"

    companion object {
        private const val PREFS_NAME = "geo_awareness_verification"
    }
}
