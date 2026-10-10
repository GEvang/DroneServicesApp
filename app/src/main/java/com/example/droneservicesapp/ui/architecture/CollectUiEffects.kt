package com.example.droneservicesapp.ui.architecture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/** Lifecycle-aware, single-consumer collection for navigation, pickers, permissions, and messages. */
@Composable
fun <Effect> CollectUiEffects(
    effects: Flow<Effect>,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    onEffect: suspend (Effect) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentHandler by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycle, minActiveState) {
        lifecycle.repeatOnLifecycle(minActiveState) {
            effects.collect(currentHandler)
        }
    }
}
