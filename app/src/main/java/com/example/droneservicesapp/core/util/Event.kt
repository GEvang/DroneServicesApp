package com.example.droneservicesapp.core.util

import androidx.lifecycle.MutableLiveData

class Event<out T>(private val content: T) {
    private var hasBeenHandled = false

    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) null else {
            hasBeenHandled = true
            content
        }
    }

    fun peekContent(): T = content
}

fun <T> MutableLiveData<Event<T>>.emit(value: T) {
    postValue(Event(value))
}
