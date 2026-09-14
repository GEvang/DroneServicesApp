package com.example.droneservicesapp.ui.home.binders

import androidx.appcompat.app.AppCompatActivity
import android.location.Location
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.rtk.RtkForwardingState
import com.example.droneservicesapp.data.rtk.RtkMountpoint
import com.example.droneservicesapp.mavserver.GpsFixQuality
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.mavserver.TelemetryMapping
import com.example.droneservicesapp.mavserver.ArduCopterFlightMode
import com.example.droneservicesapp.mavserver.FlightModeCommandState
import com.example.droneservicesapp.mavserver.ArmCommandState
import com.example.droneservicesapp.ui.home.model.HomeTelemetryUiState
import com.example.droneservicesapp.ui.home.model.HomeTelemetryViewModel
import java.util.Locale
import kotlin.math.roundToInt

class HomeTelemetryCoordinator(
    private val activity: AppCompatActivity,
    private val droneViewModel: DroneViewModel,
    private val homeTelemetryViewModel: HomeTelemetryViewModel,
) {
    companion object {
        private const val TAG = "RtkTelemetryUi"
    }

    private var lastLoggedGpsQuality: GpsFixQuality? = null
    private var lastLoggedMountpointSummary: String? = null

    fun bind(lifecycleOwner: LifecycleOwner) {
        renderCurrent()

        droneViewModel.conStateLiveData.observe(lifecycleOwner) { connState ->
            update { state ->
                if (connState) {
                    state.copy(
                        isConnected = true,
                        isFlightModeControlEnabled = true,
                        flightModeCustomMode = droneViewModel.droneFlightMode.value,
                        flightModeText = formatFlightMode(droneViewModel.droneFlightMode.value),
                        connectionText = activity.getString(R.string.shell_status_connected),
                        gpsStatusText = formatGpsStatus(isConnected = true),
                        gpsFixQuality = formatGpsQuality(isConnected = true),
                        rtkMountpointText = formatRtkMountpointText()
                    )
                } else {
                    disconnectedState(state)
                }
            }
        }

        droneViewModel.droneBatteryPercentage.observe(lifecycleOwner) { batteryPercentage ->
            if (droneViewModel.conStateLiveData.value != true) return@observe
            updateBatteryTelemetry(batteryPercentage, droneViewModel.droneBatteryVoltage.value)
        }

        droneViewModel.droneBatteryVoltage.observe(lifecycleOwner) { batteryVoltage ->
            if (droneViewModel.conStateLiveData.value != true) return@observe
            updateBatteryTelemetry(droneViewModel.droneBatteryPercentage.value, batteryVoltage)
        }

        droneViewModel.droneLocationLiveData.observe(lifecycleOwner) { location ->
            if (droneViewModel.conStateLiveData.value != true) return@observe
            update { state ->
                state.copy(
                    altitudeText = "${location.altitude.toInt()}m",
                    gpsStatusText = formatGpsStatus(isConnected = true),
                    gpsFixQuality = formatGpsQuality(isConnected = true),
                    rtkMountpointText = formatRtkMountpointText()
                )
            }
        }

        droneViewModel.droneGroundSpeedMetersPerSecond.observe(lifecycleOwner) { speed ->
            if (droneViewModel.conStateLiveData.value != true) return@observe
            update { state ->
                state.copy(speedText = formatSpeedText(speed))
            }
        }

        droneViewModel.gpsFixType.observe(lifecycleOwner) {
            update { state ->
                state.copy(
                    gpsStatusText = formatGpsStatus(isConnected = state.isConnected),
                    gpsFixQuality = formatGpsQuality(isConnected = state.isConnected)
                )
            }
        }

        droneViewModel.gpsSatellitesVisible.observe(lifecycleOwner) { updateGpsDetails() }
        droneViewModel.gpsHdop.observe(lifecycleOwner) { updateGpsDetails() }
        droneViewModel.gpsVdop.observe(lifecycleOwner) { updateGpsDetails() }
        droneViewModel.droneHeading.observe(lifecycleOwner) { update { it } }

        droneViewModel.liquidLevel.observe(lifecycleOwner) { liquidLevel ->
            update { state ->
                state.copy(sprayerText = formatSprayerText(liquidLevel))
            }
        }

        droneViewModel.armedState.observe(lifecycleOwner) { armedState ->
            update { state ->
                state.copy(
                    isArmed = armedState,
                    armedText = activity.getString(if (armedState) R.string.armed else R.string.disarmed)
                )
            }
        }

        droneViewModel.uploadProgressPercent.observe(lifecycleOwner) { percent ->
            update { state ->
                when {
                    percent in 1..99 -> state.copy(
                        showUploadProgress = true,
                        uploadProgressText = activity.getString(R.string.telemetry_uploading, percent)
                    )
                    else -> state.copy(showUploadProgress = false)
                }
            }
        }

        droneViewModel.droneFrontDistance.observe(lifecycleOwner) { frontDistance ->
            update { state ->
                state.copy(frontDistanceMeters = frontDistance)
            }
        }

        droneViewModel.droneBackDistance.observe(lifecycleOwner) { backDistance ->
            update { state ->
                state.copy(backDistanceMeters = backDistance)
            }
        }

        droneViewModel.rtkForwardingState.observe(lifecycleOwner) {
            update { state ->
                state.copy(
                    gpsStatusText = formatGpsStatus(isConnected = state.isConnected),
                    gpsFixQuality = formatGpsQuality(isConnected = state.isConnected),
                    rtkMountpointText = formatRtkMountpointText()
                )
            }
        }

        droneViewModel.armCommandState.observe(lifecycleOwner) { commandState ->
            update { state -> state.copy(armCommandState = commandState) }
        }

        droneViewModel.droneFlightMode.observe(lifecycleOwner) { customMode ->
            update { state ->
                state.copy(
                    flightModeCustomMode = customMode,
                    flightModeText = if (state.isConnected) {
                        formatFlightMode(customMode)
                    } else {
                        activity.getString(R.string.shell_status_disconnected)
                    }
                )
            }
        }

        droneViewModel.flightModeCommandState.observe(lifecycleOwner) { commandState ->
            update { state -> state.copy(flightModeCommandState = commandState) }
        }

        droneViewModel.selectedRtkMountpoint.observe(lifecycleOwner) {
            update { state ->
                state.copy(rtkMountpointText = formatRtkMountpointText())
            }
        }
    }

    private fun renderCurrent() {
        val isConnected = droneViewModel.conStateLiveData.value == true
        val armed = droneViewModel.armedState.value == true
        val batteryPercentage = droneViewModel.droneBatteryPercentage.value
        val batteryIconRes = resolveBatteryIconRes(batteryPercentage)
        val batteryText = formatBatteryText(droneViewModel.droneBatteryVoltage.value, batteryPercentage)
        val uploadProgress = droneViewModel.uploadProgressPercent.value ?: 0
        val batteryColorRes = resolveBatteryColorRes(batteryPercentage)
        val currentLocation = droneViewModel.droneLocationLiveData.value

        homeTelemetryViewModel.homeTelemetryUiState.value = HomeTelemetryUiState(
            isConnected = isConnected,
            connectionText = activity.getString(
                if (isConnected) R.string.shell_status_connected else R.string.shell_status_disconnected
            ),
            gpsStatusText = formatGpsStatus(isConnected = isConnected),
            gpsFixQuality = formatGpsQuality(isConnected = isConnected),
            gpsDetailText = formatGpsDetails(isConnected),
            gpsDialogText = formatGpsDialogDetails(isConnected),
            rtkMountpointText = formatRtkMountpointText(),
            rtkDialogText = formatRtkDialogDetails(),
            batteryText = batteryText,
            batteryIconRes = batteryIconRes,
            batteryColorRes = batteryColorRes,
            altitudeText = currentLocation?.altitude?.toInt()?.let { "${it}m" } ?: "--",
            speedText = formatSpeedText(droneViewModel.droneGroundSpeedMetersPerSecond.value),
            sprayerText = formatSprayerText(droneViewModel.liquidLevel.value),
            armedText = activity.getString(if (armed) R.string.armed else R.string.disarmed),
            isArmed = armed,
            armCommandState = droneViewModel.armCommandState.value ?: ArmCommandState.Idle,
            flightModeCustomMode = droneViewModel.droneFlightMode.value,
            flightModeText = if (isConnected) {
                formatFlightMode(droneViewModel.droneFlightMode.value)
            } else {
                activity.getString(R.string.shell_status_disconnected)
            },
            flightModeCommandState = droneViewModel.flightModeCommandState.value
                ?: FlightModeCommandState.Idle,
            isFlightModeControlEnabled = isConnected,
            uploadProgressText = activity.getString(R.string.telemetry_uploading, uploadProgress),
            showUploadProgress = uploadProgress in 1..99,
            frontDistanceMeters = droneViewModel.droneFrontDistance.value,
            backDistanceMeters = droneViewModel.droneBackDistance.value
        )
    }

    private fun update(transform: (HomeTelemetryUiState) -> HomeTelemetryUiState) {
        val current = homeTelemetryViewModel.homeTelemetryUiState.value
            ?: HomeTelemetryUiState(
                connectionText = activity.getString(R.string.shell_status_disconnected),
                armedText = activity.getString(R.string.disarmed)
            )
        val next = transform(current)
        homeTelemetryViewModel.homeTelemetryUiState.value = next.copy(
            gpsDialogText = formatGpsDialogDetails(next.isConnected),
            rtkDialogText = formatRtkDialogDetails(),
        )
    }

    private fun disconnectedState(current: HomeTelemetryUiState): HomeTelemetryUiState {
        return current.copy(
            isConnected = false,
            connectionText = activity.getString(R.string.shell_status_disconnected),
            gpsStatusText = activity.getString(R.string.top_status_no_gps),
            gpsFixQuality = GpsFixQuality.DISCONNECTED,
            gpsDetailText = activity.getString(R.string.top_status_gps_details_unknown),
            gpsDialogText = formatGpsDialogDetails(false),
            rtkMountpointText = activity.getString(R.string.top_status_rtk_not_connected),
            rtkDialogText = formatRtkDialogDetails(),
            batteryText = "--.-V --%",
            batteryIconRes = R.drawable.ic_baseline_battery_alert_24,
            batteryColorRes = R.color.ds_color_shell_unselected,
            altitudeText = "--",
            speedText = "0.0 m/s",
            sprayerText = "--.-L",
            armedText = activity.getString(R.string.disarmed),
            isArmed = false,
            flightModeText = activity.getString(R.string.shell_status_disconnected),
            flightModeCustomMode = null,
            isFlightModeControlEnabled = false,
            showUploadProgress = false,
            frontDistanceMeters = null,
            backDistanceMeters = null
        )
    }

    private fun resolveBatteryColorRes(batteryPercentage: Float?): Int {
        if (batteryPercentage == null || batteryPercentage < 0f) {
            return R.color.ds_color_shell_unselected
        }
        return when {
            batteryPercentage <= 0.2f -> R.color.ds_color_shell_danger
            batteryPercentage <= 0.5f -> R.color.ds_color_shell_warning
            else -> R.color.ds_color_shell_active
        }
    }

    private fun formatGpsStatus(isConnected: Boolean): String {
        if (!isConnected) return activity.getString(R.string.top_status_no_gps)
        return when (TelemetryMapping.gpsFixQuality(droneViewModel.gpsFixType.value, true)) {
            GpsFixQuality.FIX_2D -> activity.getString(R.string.gps_fix_2d)
            GpsFixQuality.FIX_3D -> activity.getString(R.string.gps_fix_3d)
            GpsFixQuality.DGPS -> activity.getString(R.string.gps_fix_dgps)
            GpsFixQuality.RTK_FLOAT -> activity.getString(R.string.gps_fix_rtk_float)
            GpsFixQuality.RTK_FIXED -> activity.getString(R.string.gps_fix_rtk_fixed)
            else -> activity.getString(R.string.top_status_no_gps)
        }
    }

    private fun formatGpsQuality(isConnected: Boolean): GpsFixQuality {
        val quality = TelemetryMapping.gpsFixQuality(droneViewModel.gpsFixType.value, isConnected)
        if (lastLoggedGpsQuality != quality) {
            lastLoggedGpsQuality = quality
            Log.d(TAG, "gps quality changed quality=$quality label=${TelemetryMapping.gpsFixLabel(droneViewModel.gpsFixType.value)}")
        }
        return quality
    }

    private fun updateGpsDetails() {
        update { state -> state.copy(gpsDetailText = formatGpsDetails(state.isConnected)) }
    }

    private fun formatGpsDetails(isConnected: Boolean): String {
        if (!isConnected) return activity.getString(R.string.top_status_gps_details_unknown)
        val satellites = droneViewModel.gpsSatellitesVisible.value?.toString() ?: "--"
        val hdop = droneViewModel.gpsHdop.value?.let { String.format(Locale.US, "%.2f", it) } ?: "--"
        val vdop = droneViewModel.gpsVdop.value?.let { String.format(Locale.US, "%.2f", it) } ?: "--"
        return activity.getString(R.string.top_status_gps_details, satellites, hdop, vdop)
    }

    private fun formatGpsDialogDetails(isConnected: Boolean): String {
        val location = if (isConnected) {
            droneViewModel.droneLocationLiveData.value?.takeIf(::isUsableLocation)
        } else {
            null
        }
        val unknown = "--"
        return activity.getString(
            R.string.telemetry_gps_dialog_body,
            activity.getString(if (isConnected) R.string.shell_status_connected else R.string.shell_status_disconnected),
            formatGpsStatus(isConnected),
            droneViewModel.gpsSatellitesVisible.value?.toString() ?: unknown,
            droneViewModel.gpsHdop.value?.let { String.format(Locale.US, "%.2f", it) } ?: unknown,
            droneViewModel.gpsVdop.value?.let { String.format(Locale.US, "%.2f", it) } ?: unknown,
            location?.let { String.format(Locale.US, "%.7f", it.latitude) } ?: unknown,
            location?.let { String.format(Locale.US, "%.7f", it.longitude) } ?: unknown,
            location?.let { String.format(Locale.US, "%.1f m", it.altitude) } ?: unknown,
            droneViewModel.droneGroundSpeedMetersPerSecond.value?.takeIf { isConnected }
                ?.let { String.format(Locale.US, "%.1f m/s", it) } ?: unknown,
            droneViewModel.droneHeading.value?.takeIf { isConnected }
                ?.let { String.format(Locale.US, "%.1f°", it) } ?: unknown,
        )
    }

    private fun formatRtkDialogDetails(): String {
        val mountpoint = droneViewModel.selectedRtkMountpoint.value
        val unknown = "--"
        val distance = mountpoint?.takeIf { it.hasCoordinates }
            ?.let(::currentDistanceToMountpoint)
            ?.let(::formatDistance)
            ?: unknown
        return activity.getString(
            R.string.telemetry_rtk_dialog_body,
            formatRtkState(droneViewModel.rtkForwardingState.value),
            mountpoint?.name ?: unknown,
            mountpoint?.latitude?.let { String.format(Locale.US, "%.7f", it) } ?: unknown,
            mountpoint?.longitude?.let { String.format(Locale.US, "%.7f", it) } ?: unknown,
            distance,
        )
    }

    private fun formatRtkState(state: RtkForwardingState?): String = when (state) {
        null, RtkForwardingState.Idle, RtkForwardingState.Stopped -> activity.getString(R.string.rtk_status_idle)
        RtkForwardingState.WaitingForMountpoint -> activity.getString(R.string.telemetry_rtk_wait_mountpoint)
        RtkForwardingState.WaitingForInternet -> activity.getString(R.string.telemetry_rtk_wait_internet)
        RtkForwardingState.WaitingForDrone -> activity.getString(R.string.telemetry_rtk_wait_drone)
        RtkForwardingState.WaitingForGps -> activity.getString(R.string.telemetry_rtk_wait_gps)
        RtkForwardingState.ConnectingToCaster -> activity.getString(R.string.telemetry_rtk_connecting)
        RtkForwardingState.Streaming -> activity.getString(R.string.telemetry_rtk_streaming)
        is RtkForwardingState.Reconnecting -> activity.getString(R.string.telemetry_rtk_reconnecting, state.message)
        is RtkForwardingState.InvalidConfig -> activity.getString(R.string.rtk_status_invalid_config, state.message)
        is RtkForwardingState.AuthFailed -> activity.getString(R.string.rtk_status_auth_failed)
        is RtkForwardingState.MountpointInvalid -> activity.getString(R.string.rtk_status_mountpoint_not_found)
        is RtkForwardingState.NetworkError -> activity.getString(R.string.rtk_status_network_failed, state.message)
        is RtkForwardingState.ProtocolError -> activity.getString(R.string.telemetry_rtk_protocol_error, state.message)
    }

    private fun formatRtkMountpointText(): String {
        val rtkState = droneViewModel.rtkForwardingState.value
        val mountpoint = droneViewModel.selectedRtkMountpoint.value
        val text = when {
            rtkState !is RtkForwardingState.Streaming || mountpoint == null -> {
                activity.getString(R.string.top_status_rtk_not_connected)
            }
            !mountpoint.hasCoordinates -> {
                activity.getString(R.string.top_status_rtk_distance_unknown)
            }
            else -> {
                val distanceMeters = currentDistanceToMountpoint(mountpoint)
                if (distanceMeters == null) {
                    activity.getString(R.string.top_status_rtk_distance_unknown)
                } else {
                    activity.getString(R.string.top_status_rtk_distance, formatDistance(distanceMeters))
                }
            }
        }
        logMountpointTextIfChanged(text, mountpoint)
        return text
    }

    private fun currentDistanceToMountpoint(mountpoint: RtkMountpoint): Double? {
        val location = droneViewModel.droneLocationLiveData.value?.takeIf(::isUsableLocation) ?: return null
        val latitude = mountpoint.latitude ?: return null
        val longitude = mountpoint.longitude ?: return null
        return TelemetryMapping.haversineDistanceMeters(
            location.latitude,
            location.longitude,
            latitude,
            longitude
        )
    }

    private fun formatDistance(distanceMeters: Double): String {
        return if (distanceMeters < 1000.0) {
            "${distanceMeters.roundToInt()} m"
        } else {
            "${String.format(Locale.US, "%.1f", distanceMeters / 1000.0)} km"
        }
    }

    private fun isUsableLocation(location: Location): Boolean {
        return !location.latitude.isNaN() &&
            !location.longitude.isNaN() &&
            location.latitude in -90.0..90.0 &&
            location.longitude in -180.0..180.0 &&
            !(location.latitude == 0.0 && location.longitude == 0.0)
    }

    private fun logMountpointTextIfChanged(text: String, mountpoint: RtkMountpoint?) {
        val summary = "mountpoint=${mountpoint?.name ?: "--"} text=$text"
        if (lastLoggedMountpointSummary == summary) return
        lastLoggedMountpointSummary = summary
        Log.d(TAG, summary)
    }

    private fun formatBatteryText(batteryVoltage: Float?, batteryPercentage: Float?): String {
        return TelemetryMapping.formatBatteryText(batteryVoltage, batteryPercentage)
    }

    private fun formatSprayerText(sprayerPercentage: Float?): String {
        return TelemetryMapping.placeholderSprayLiters(sprayerPercentage)
            ?.let { String.format(Locale.US, "%.1fL", it) }
            ?: "--.-L"
    }

    private fun updateBatteryTelemetry(batteryPercentage: Float?, batteryVoltage: Float?) {
        val iconRes = resolveBatteryIconRes(batteryPercentage)
        update { state ->
            state.copy(
                batteryText = formatBatteryText(batteryVoltage, batteryPercentage),
                batteryIconRes = iconRes,
                batteryColorRes = resolveBatteryColorRes(batteryPercentage)
            )
        }
    }

    private fun resolveBatteryIconRes(batteryPercentage: Float?): Int {
        return when {
            batteryPercentage == null || batteryPercentage < 0.0F -> R.drawable.ic_baseline_battery_alert_24
            batteryPercentage >= 1.0F -> R.drawable.ic_baseline_battery_full_24
            batteryPercentage >= 0.7F -> R.drawable.ic_baseline_battery_6_bar_24
            batteryPercentage >= 0.4F -> R.drawable.ic_baseline_battery_4_bar_24
            batteryPercentage >= 0.25F -> R.drawable.ic_baseline_battery_3_bar_24
            else -> R.drawable.ic_baseline_battery_2_bar_24
        }
    }

    private fun formatSpeedText(speedMetersPerSecond: Float?): String {
        val speed = speedMetersPerSecond
            ?.takeIf { TelemetryMapping.isValidGroundSpeedMetersPerSecond(it) }
            ?: 0f
        return "${String.format(Locale.US, "%.1f", speed)} m/s"
    }

    private fun formatFlightMode(customMode: Int?): String = when (customMode) {
        ArduCopterFlightMode.AUTO.customMode -> activity.getString(R.string.flight_mode_auto)
        ArduCopterFlightMode.GUIDED.customMode -> activity.getString(R.string.flight_mode_guided)
        ArduCopterFlightMode.LOITER.customMode -> activity.getString(R.string.flight_mode_loiter)
        ArduCopterFlightMode.RTL.customMode -> activity.getString(R.string.flight_mode_rtl)
        ArduCopterFlightMode.LAND.customMode -> activity.getString(R.string.flight_mode_land)
        ArduCopterFlightMode.BRAKE.customMode -> activity.getString(R.string.flight_mode_brake)
        else -> ArduCopterFlightMode.displayName(customMode)
    }
}
