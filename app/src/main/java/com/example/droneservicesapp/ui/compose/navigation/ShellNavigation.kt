package com.example.droneservicesapp.ui.compose.navigation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface ShellNavigator {
    fun navigateTo(destination: AppDestination)
    fun returnToHome()
    fun openNavigation()
}

sealed interface ShellNavigationRequest {
    data class Navigate(val destination: AppDestination) : ShellNavigationRequest
    data object ReturnToHome : ShellNavigationRequest
    data object OpenDrawer : ShellNavigationRequest
}

class ShellNavigationViewModel : ViewModel(), ShellNavigator {
    private val _requests = MutableSharedFlow<ShellNavigationRequest>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val requests = _requests.asSharedFlow()

    override fun navigateTo(destination: AppDestination) {
        _requests.tryEmit(ShellNavigationRequest.Navigate(destination))
    }

    override fun returnToHome() {
        _requests.tryEmit(ShellNavigationRequest.ReturnToHome)
    }

    override fun openNavigation() {
        _requests.tryEmit(ShellNavigationRequest.OpenDrawer)
    }
}
