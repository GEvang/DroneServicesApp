package com.example.droneservicesapp.ui.architecture

/** Platform-neutral descriptions of transient work performed by the current UI host. */
sealed interface CommonUiEffect {
    data class Navigate(val route: String) : CommonUiEffect

    data class LaunchFilePicker(
        val requestKey: String,
        val mimeTypes: Set<String>,
        val allowMultiple: Boolean = false,
    ) : CommonUiEffect

    data class RequestPermission(
        val requestKey: String,
        val permissions: Set<String>,
    ) : CommonUiEffect

    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
    ) : CommonUiEffect

    data class ShowConfirmation(
        val requestKey: String,
        val title: String,
        val message: String,
        val destructive: Boolean = false,
    ) : CommonUiEffect
}
