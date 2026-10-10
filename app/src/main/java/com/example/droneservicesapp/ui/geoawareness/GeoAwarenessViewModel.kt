package com.example.droneservicesapp.ui.geoawareness

import androidx.lifecycle.ViewModel

/** Screen state owner independent of Fragment, View Binding, and Compose. */
class GeoAwarenessViewModel : ViewModel() {
    private val holder = GeoAwarenessUiStateHolder()
    val uiState = holder.state
    val effects = holder.effects

    fun dispatch(action: GeoAwarenessAction) = holder.dispatch(action)
}
