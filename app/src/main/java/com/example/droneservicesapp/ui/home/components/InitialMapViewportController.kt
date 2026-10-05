package com.example.droneservicesapp.ui.home.components

import android.view.View

/** Coordinates the one-time initial map focus and its bounded location retries. */
class InitialMapViewportController(
    private val rootViewProvider: () -> View?,
    private val mapController: OsmdroidMapController,
    private val hasPendingPreviewFocus: () -> Boolean,
    private val focusPendingPreview: () -> Boolean,
    private val centerOnDrone: () -> Boolean,
    private val maxDroneAttempts: Int,
    private val maxUserLocationAttempts: Int = 10,
    private val retryDelayMillis: Long = 1_000L,
) {
    private var centeredToDrone = false
    private var centeredInitialViewport = false
    private var droneAttempts = 0
    private var userLocationAttempts = 0

    fun centerIfNeeded() {
        val root = rootViewProvider() ?: return
        if (centeredToDrone) return
        if (hasPendingPreviewFocus() && focusPendingPreview()) return

        if (mapController.hasDronePosition()) {
            if (centerOnDrone()) {
                centeredToDrone = true
                centeredInitialViewport = true
                return
            }
        }

        if (droneAttempts < maxDroneAttempts) {
            droneAttempts += 1
            root.postDelayed(::centerIfNeeded, retryDelayMillis)
            return
        }

        if (centeredInitialViewport) return
        if (mapController.centerOnUserIfPermitted(showErrors = false)) {
            centeredInitialViewport = true
        } else if (userLocationAttempts < maxUserLocationAttempts) {
            userLocationAttempts += 1
            root.postDelayed(::centerIfNeeded, retryDelayMillis)
        }
    }

    fun markCenteredToDrone() {
        centeredToDrone = true
    }

    fun reset() {
        centeredToDrone = false
        centeredInitialViewport = false
        droneAttempts = 0
        userLocationAttempts = 0
    }
}
