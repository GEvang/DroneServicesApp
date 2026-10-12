package com.example.droneservicesapp.ui.home

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * A lifecycle that follows its host while a composable is on screen and is destroyed when the
 * composable leaves composition. It gives the mission screen the same lifetime the Fragment's
 * view lifecycle used to provide: LiveData observers and `lifecycleScope` jobs end when the
 * operator navigates away, even though the navigation back-stack entry itself stays alive.
 */
class CompositionLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = registry

    /** Follows [hostState], never leaving DESTROYED once reached. */
    fun follow(hostState: Lifecycle.State) {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        if (hostState == Lifecycle.State.DESTROYED) {
            destroy()
            return
        }
        registry.currentState = if (hostState == Lifecycle.State.INITIALIZED) Lifecycle.State.CREATED else hostState
    }

    fun destroy() {
        when (registry.currentState) {
            Lifecycle.State.DESTROYED -> return
            Lifecycle.State.INITIALIZED -> registry.currentState = Lifecycle.State.CREATED
            else -> Unit
        }
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
