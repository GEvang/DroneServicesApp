package com.example.droneservicesapp.ui.architecture

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** Result of reducing one explicit UI action. Effects are delivered once and are not UI state. */
data class UiReduction<State, Effect>(
    val state: State,
    val effects: List<Effect> = emptyList(),
)

fun interface UiReducer<State, Action, Effect> {
    fun reduce(state: State, action: Action): UiReduction<State, Effect>
}

/**
 * Android-free state container shared by View-based adapters today and Compose screens later.
 * State is replayed; transient effects are deliberately not replayed after collection.
 */
class UiStateStore<State, Action, Effect>(
    initialState: State,
    private val reducer: UiReducer<State, Action, Effect>,
) {
    private val mutableState = MutableStateFlow(initialState)
    private val effectChannel = Channel<Effect>(capacity = Channel.BUFFERED)

    val state: StateFlow<State> = mutableState.asStateFlow()
    val effects: Flow<Effect> = effectChannel.receiveAsFlow()

    @Synchronized
    fun dispatch(action: Action) {
        val reduction = reducer.reduce(mutableState.value, action)
        mutableState.value = reduction.state
        reduction.effects.forEach { effect ->
            check(effectChannel.trySend(effect).isSuccess) { "Unable to deliver UI effect" }
        }
    }
}
