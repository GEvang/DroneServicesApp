package com.example.droneservicesapp.ui.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class MissionScreenStateTest {
    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @Test
    fun dialogQueueShowsTheMostRecentDialogFirst() {
        val queue = MissionDialogQueue()
        val first = MissionDialog.Message("First", "one")
        val second = MissionDialog.Message("Second", "two")
        queue.show(first)
        queue.show(second)
        assertSame(second, queue.current)
        queue.dismiss(second)
        assertSame(first, queue.current)
        queue.dismiss(second)
        assertSame(first, queue.current)
        queue.clear()
        assertNull(queue.current)
    }

    @Test
    fun dismissRemovesOnlyTheSameInstance() {
        val queue = MissionDialogQueue()
        val a = MissionDialog.Message("Same", "text")
        val b = MissionDialog.Message("Same", "text")
        queue.show(a)
        queue.show(b)
        queue.dismiss(a)
        assertEquals(listOf<MissionDialog>(b), queue.dialogs)
        assertSame(b, queue.current)
    }

    @Test
    fun screenLifecycleFollowsHostAndEndsDestroyed() {
        val owner = CompositionLifecycleOwner()
        owner.follow(Lifecycle.State.CREATED)
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)
        owner.follow(Lifecycle.State.RESUMED)
        assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)
        owner.follow(Lifecycle.State.STARTED)
        assertEquals(Lifecycle.State.STARTED, owner.lifecycle.currentState)
        owner.destroy()
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        owner.follow(Lifecycle.State.RESUMED)
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }
}
