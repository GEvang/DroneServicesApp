package com.example.droneservicesapp.ui.home.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Coordinates the one-time initial map focus and its bounded location retries. */
class InitialMapViewportController(
    private val scope: CoroutineScope,
    private val isActive: () -> Boolean,
    private val hasDronePosition: () -> Boolean,
    private val hasPendingPreviewFocus: () -> Boolean,
    private val focusPendingPreview: () -> Boolean,
    private val centerOnDrone: () -> Boolean,
    private val centerOnUser: () -> Boolean,
    private val maxDroneAttempts: Int,
    private val maxUserLocationAttempts: Int = 10,
    private val retryDelayMillis: Long = 1_000L,
) {
    private var centeredToDrone = false
    private var centeredInitialViewport = false
    private var droneAttempts = 0
    private var userLocationAttempts = 0
    private var retryJob: Job? = null

    fun centerIfNeeded() {
        if (!isActive()) return
        if (centeredToDrone) return
        if (hasPendingPreviewFocus() && focusPendingPreview()) return

        if (hasDronePosition() && centerOnDrone()) {
            centeredToDrone = true
            centeredInitialViewport = true
            return
        }

        if (droneAttempts < maxDroneAttempts) {
            droneAttempts += 1
            retry()
            return
        }

        if (centeredInitialViewport) return
        if (centerOnUser()) {
            centeredInitialViewport = true
        } else if (userLocationAttempts < maxUserLocationAttempts) {
            userLocationAttempts += 1
            retry()
        }
    }

    /** Skips the automatic first focus, for example when a previous camera was restored. */
    fun markInitialViewportRestored() {
        centeredInitialViewport = true
    }

    fun markCenteredToDrone() {
        centeredToDrone = true
    }

    fun reset() {
        retryJob?.cancel()
        centeredToDrone = false
        centeredInitialViewport = false
        droneAttempts = 0
        userLocationAttempts = 0
    }

    private fun retry() {
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            delay(retryDelayMillis)
            retryJob = null
            centerIfNeeded()
        }
    }
}
