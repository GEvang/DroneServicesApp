package com.example.droneservicesapp.ui.compose.navigation

import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager

/** Temporary bridge used only until each legacy destination is converted to a Compose screen. */
@Composable
fun LegacyFragmentHost(
    destination: AppDestination,
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current.findAppCompatActivity()
    val fragmentManager = activity.supportFragmentManager
    val fragmentTag = remember(destination.route) { "compose-shell:${destination.route}" }
    var containerReady by remember(destination.route) { mutableStateOf(false) }
    val fragment = remember(destination.route) {
        fragmentManager.findFragmentByTag(fragmentTag) ?: destination.createFragment()
    }

    AndroidView(
        factory = { context ->
            FragmentContainerView(context).apply {
                id = destination.legacyContainerId
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { containerReady = true },
        modifier = modifier.fillMaxSize(),
    )

    LaunchedEffect(destination.route, containerReady) {
        if (!containerReady) return@LaunchedEffect
        withFrameNanos { }
        fragmentManager.attachDestination(
            fragment = fragment,
            containerId = destination.legacyContainerId,
            tag = fragmentTag,
        )
    }

    DisposableEffect(destination.route, fragment) {
        onDispose {
            fragmentManager.detachDestination(fragment)
        }
    }
}

private fun FragmentManager.attachDestination(
    fragment: Fragment,
    containerId: Int,
    tag: String,
) {
    if (isDestroyed) return
    val transaction = beginTransaction().setReorderingAllowed(true)
    when {
        fragment.isDetached -> transaction.attach(fragment)
        !fragment.isAdded -> transaction.add(containerId, fragment, tag)
        else -> return
    }
    if (isStateSaved) transaction.commitAllowingStateLoss() else transaction.commitNow()
}

private fun FragmentManager.detachDestination(fragment: Fragment) {
    if (isDestroyed || !fragment.isAdded || fragment.isDetached) return
    val transaction = beginTransaction().setReorderingAllowed(true).detach(fragment)
    if (isStateSaved) transaction.commitAllowingStateLoss() else transaction.commitNow()
}

private tailrec fun Context.findAppCompatActivity(): AppCompatActivity = when (this) {
    is AppCompatActivity -> this
    is ContextWrapper -> baseContext.findAppCompatActivity()
    else -> error("A legacy destination requires an AppCompatActivity host.")
}
