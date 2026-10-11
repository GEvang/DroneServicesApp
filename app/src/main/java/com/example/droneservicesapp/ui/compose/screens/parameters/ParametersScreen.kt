package com.example.droneservicesapp.ui.compose.screens.parameters

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.droneservicesapp.R
import com.example.droneservicesapp.mavserver.DroneParameterController
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.mavserver.VehicleParameterAvailability
import com.example.droneservicesapp.mavserver.VehicleParameterResult
import com.example.droneservicesapp.mavserver.VehicleParameterUiState
import com.example.droneservicesapp.ui.compose.screens.common.MorePanel
import com.example.droneservicesapp.ui.compose.screens.common.MoreScreen
import com.example.droneservicesapp.ui.compose.screens.common.MoreSettingRow

const val PARAMETERS_SCREEN_TAG = "compose-parameters-screen"

@Composable
fun ParametersScreen(droneViewModel: DroneViewModel) {
    val context = LocalContext.current
    val avoidance by droneViewModel.avoidanceParameter.observeAsState(VehicleParameterUiState())
    val surfaceTracking by droneViewModel.surfaceTrackingParameter.observeAsState(VehicleParameterUiState())
    val connected by droneViewModel.conStateLiveData.observeAsState(false)
    val feedbackEvent by droneViewModel.parameterFeedback.observeAsState()
    var wasConnected by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(connected) {
        if (connected && wasConnected != true) droneViewModel.refreshTerrainFollowingParameters()
        wasConnected = connected
    }
    LaunchedEffect(feedbackEvent) {
        feedbackEvent?.getIfNotHandled()?.let { feedback ->
            val label = context.getString(
                if (feedback.parameterName == DroneParameterController.AVOID_ENABLE) {
                    R.string.parameter_avoidance_label
                } else {
                    R.string.parameter_surface_tracking_label
                }
            )
            Toast.makeText(
                context,
                context.getString(
                    if (feedback.succeeded) R.string.parameter_write_success else R.string.parameter_write_error,
                    label,
                ),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    MoreScreen(
        title = stringResource(R.string.parameters_title),
        subtitle = stringResource(R.string.parameters_subtitle),
        iconRes = R.drawable.ic_menu_tune,
        modifier = Modifier.testTag(PARAMETERS_SCREEN_TAG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ParameterCard(
                title = stringResource(R.string.parameter_avoidance_label),
                helper = stringResource(R.string.parameter_avoidance_helper),
                state = avoidance,
                onCheckedChange = droneViewModel::setAvoidanceEnabled,
            )
            ParameterCard(
                title = stringResource(R.string.parameter_surface_tracking_label),
                helper = stringResource(R.string.parameter_surface_tracking_ground_helper),
                state = surfaceTracking,
                onCheckedChange = droneViewModel::setSurfaceTrackingEnabled,
            )
        }
    }
}

@Composable
private fun ParameterCard(
    title: String,
    helper: String,
    state: VehicleParameterUiState,
    onCheckedChange: (Boolean) -> Unit,
) {
    val enabled = state.availability == VehicleParameterAvailability.SUPPORTED && !state.isWriting
    MorePanel(title = title, subtitle = helper) {
        MoreSettingRow(
            title = title,
            value = statusText(state),
            trailing = {
                Switch(
                    checked = (state.value ?: 0) != 0,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                )
            },
        )
    }
}

@Composable
private fun statusText(state: VehicleParameterUiState): String {
    val resource = when {
        state.isWriting -> R.string.parameter_status_writing
        state.result == VehicleParameterResult.SUCCESS -> R.string.parameter_status_saved
        state.result == VehicleParameterResult.ERROR -> R.string.parameter_status_write_error
        state.availability == VehicleParameterAvailability.LOADING -> R.string.parameter_status_loading
        state.availability == VehicleParameterAvailability.UNSUPPORTED -> R.string.parameter_not_supported
        state.availability == VehicleParameterAvailability.DISCONNECTED -> R.string.parameter_status_disconnected
        else -> null
    }
    return resource?.let { stringResource(it) }.orEmpty()
}
