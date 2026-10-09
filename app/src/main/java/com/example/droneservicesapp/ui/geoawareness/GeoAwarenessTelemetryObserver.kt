package com.example.droneservicesapp.ui.geoawareness

import android.location.Location
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.mavserver.DroneViewModel
import io.dronefleet.mavlink.common.GpsFixType

/** Normalizes drone telemetry into one immutable snapshot without presentation logic. */
class GeoAwarenessTelemetryObserver {
    data class Snapshot(
        val connected: Boolean,
        val gpsFix: GpsFixType?,
        val position: LatLon?,
        val altitudeAglMeters: Double?,
        val altitudeAmslMeters: Double?,
        val groundSpeedMetersPerSecond: Double?,
        val verticalSpeedMetersPerSecond: Double?,
        val headingDegrees: Double?,
    ) {
        val hasUsableGpsFix: Boolean
            get() = gpsFix?.name !in setOf(null, "NO_GPS", "NO_FIX")
    }

    private var onSnapshot: ((Snapshot) -> Unit)? = null

    fun observe(owner: LifecycleOwner, viewModel: DroneViewModel, onSnapshot: (Snapshot) -> Unit) {
        this.onSnapshot = onSnapshot
        val publish = { publish(viewModel) }
        viewModel.droneLocationLiveData.observe(owner) { publish() }
        viewModel.conStateLiveData.observe(owner) { publish() }
        viewModel.gpsFixType.observe(owner) { publish() }
        viewModel.droneAltitudeAmslMeters.observe(owner) { publish() }
        viewModel.droneGroundSpeedMetersPerSecond.observe(owner) { publish() }
        viewModel.droneVerticalSpeedMetersPerSecond.observe(owner) { publish() }
        viewModel.droneHeading.observe(owner) { publish() }
        publish()
    }

    fun clear() {
        onSnapshot = null
    }

    private fun publish(viewModel: DroneViewModel) {
        val location = viewModel.droneLocationLiveData.value?.takeIf(::isUsableLocation)
        onSnapshot?.invoke(
            Snapshot(
                connected = viewModel.conStateLiveData.value == true,
                gpsFix = viewModel.gpsFixType.value,
                position = location?.let { LatLon(it.latitude, it.longitude) },
                altitudeAglMeters = location?.altitude,
                altitudeAmslMeters = viewModel.droneAltitudeAmslMeters.value,
                groundSpeedMetersPerSecond = viewModel.droneGroundSpeedMetersPerSecond.value?.toDouble(),
                verticalSpeedMetersPerSecond = viewModel.droneVerticalSpeedMetersPerSecond.value?.toDouble(),
                headingDegrees = viewModel.droneHeading.value,
            ),
        )
    }

    private fun isUsableLocation(location: Location): Boolean {
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return false
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return false
        return kotlin.math.abs(location.latitude) > 1e-4 || kotlin.math.abs(location.longitude) > 1e-4
    }
}
