package com.example.droneservicesapp.ui.home

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Transient operator feedback for the mission screen: toasts, and snackbars rendered by Compose. */
class MissionMessages(context: Context) {
    private val appContext = context.applicationContext
    private val mutableSnackbars = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val snackbars: SharedFlow<String> = mutableSnackbars.asSharedFlow()

    fun toast(text: String, long: Boolean = false) {
        Toast.makeText(appContext, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    fun toast(@StringRes text: Int, long: Boolean = false) = toast(appContext.getString(text), long)

    fun snackbar(text: String) {
        mutableSnackbars.tryEmit(text)
    }
}
