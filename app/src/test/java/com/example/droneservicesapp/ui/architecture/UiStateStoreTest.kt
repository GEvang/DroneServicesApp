package com.example.droneservicesapp.ui.architecture

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UiStateStoreTest {
    @Test
    fun actionUpdatesReplayableStateAndDeliversEffectOnce() = runBlocking {
        val store = UiStateStore<Int, Int, String>(0) { state, action ->
            UiReduction(state + action, listOf("changed"))
        }

        store.dispatch(3)

        assertEquals(3, store.state.value)
        assertEquals("changed", store.effects.first())
    }
}
