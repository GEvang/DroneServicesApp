package com.example.droneservicesapp.ui.compose.screens.missionmap

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.droneservicesapp.R
import com.example.droneservicesapp.mavserver.ArduCopterFlightMode
import com.example.droneservicesapp.mavserver.ArmCommandState
import com.example.droneservicesapp.mavserver.FlightModeCommandState
import com.example.droneservicesapp.ui.home.MissionMapController
import com.example.droneservicesapp.ui.home.model.HomeTelemetryUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val FLIGHT_MODE_POPUP_TAG = "mission-map-flight-mode-popup"
const val ARM_POPUP_TAG = "mission-map-arm-popup"
const val ARM_SLIDER_TAG = "mission-map-arm-slider"

private val PopupWidth = 340.dp
private val CardHeight = 54.dp

/** Anchors a 340 dp popup right-aligned under its status card, like the former PopupWindow. */
@Composable
private fun StatusCardPopup(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val yOffset = with(density) { (CardHeight + 4.dp).roundToPx() }
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, yOffset),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = Modifier.width(PopupWidth),
            shape = RoundedCornerShape(12.dp),
            color = MapChrome.popup,
            border = BorderStroke(1.dp, MapChrome.popupStroke),
            shadowElevation = 14.dp,
        ) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

@Composable
internal fun FlightModePopup(
    telemetry: HomeTelemetryUiState,
    pendingConfirmation: ArduCopterFlightMode?,
    controller: MissionMapController,
) {
    val commandState = telemetry.flightModeCommandState
    val pendingMode = (commandState as? FlightModeCommandState.Pending)?.requestedMode
    val requestPending = commandState is FlightModeCommandState.Pending
    StatusCardPopup(onDismiss = controller::dismissFlightMode) {
        Column(Modifier.testTag(FLIGHT_MODE_POPUP_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PopupHeader(
                iconRes = R.drawable.ic_flight_mode_24,
                title = stringResource(R.string.flight_mode_selector_title),
                subtitle = stringResource(R.string.flight_mode_current_format, telemetry.flightModeText),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ArduCopterFlightMode.LOITER to R.drawable.ic_flight_mode_24,
                    ArduCopterFlightMode.AUTO to R.drawable.ic_workflow_24,
                ).forEach { (mode, icon) ->
                    FlightModeButton(
                        mode = mode,
                        iconRes = icon,
                        selected = telemetry.flightModeCustomMode == mode.customMode,
                        pending = pendingMode == mode,
                        enabled = !requestPending,
                        tint = null,
                        onClick = { controller.selectFlightMode(mode, telemetry.flightModeCustomMode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(stringResource(R.string.flight_mode_safety_actions), color = MapChrome.unselected, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(ArduCopterFlightMode.RTL, R.drawable.ic_mode_rtl_24, MapChrome.warning),
                    Triple(ArduCopterFlightMode.LAND, R.drawable.ic_mode_land_24, MapChrome.danger),
                ).forEach { (mode, icon, tint) ->
                    FlightModeButton(
                        mode = mode,
                        iconRes = icon,
                        selected = telemetry.flightModeCustomMode == mode.customMode,
                        pending = pendingMode == mode,
                        enabled = !requestPending,
                        tint = tint,
                        onClick = { controller.selectFlightMode(mode, telemetry.flightModeCustomMode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            when {
                pendingConfirmation == ArduCopterFlightMode.AUTO -> DeliberateSlideTrack(
                    label = stringResource(R.string.flight_mode_auto_slider_label),
                    enabled = !requestPending,
                    onCompleted = { controller.submitFlightMode(ArduCopterFlightMode.AUTO) },
                )
                pendingConfirmation != null && pendingConfirmation.holdDurationMs > 0L -> HoldToConfirmButton(
                    text = stringResource(
                        R.string.flight_mode_hold_to_apply,
                        pendingConfirmation.holdDurationMs / 1_000f,
                        stringResource(MissionMapController.flightModeLabelRes(pendingConfirmation)),
                    ),
                    color = if (pendingConfirmation == ArduCopterFlightMode.LAND) MapChrome.danger else MapChrome.warning,
                    holdDurationMs = pendingConfirmation.holdDurationMs,
                    onConfirmed = { controller.submitFlightMode(pendingConfirmation) },
                )
            }
        }
    }
}

@Composable
internal fun SlideToArmPopup(
    armCommandState: ArmCommandState,
    submitted: Boolean,
    controller: MissionMapController,
) {
    val pending = submitted || armCommandState is ArmCommandState.Pending
    StatusCardPopup(onDismiss = controller::dismissArm) {
        Column(Modifier.testTag(ARM_POPUP_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PopupHeader(
                iconRes = R.drawable.ic_status_shield_24,
                title = stringResource(R.string.arm_slider_title),
                subtitle = stringResource(if (pending) R.string.arm_waiting_confirmation else R.string.arm_slider_instruction),
            )
            DeliberateSlideTrack(
                label = stringResource(R.string.arm_slider_track_label),
                enabled = !pending,
                onCompleted = controller::submitArm,
                modifier = Modifier.testTag(ARM_SLIDER_TAG),
                resetKey = armCommandState,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.arm_slider_warning),
                    color = MapChrome.warning,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                if (pending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MapChrome.active)
            }
        }
    }
}

@Composable
private fun PopupHeader(@DrawableRes iconRes: Int, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(iconRes), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(MapChrome.active))
        Column {
            Text(title, color = MapChrome.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MapChrome.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FlightModeButton(
    mode: ArduCopterFlightMode,
    @DrawableRes iconRes: Int,
    selected: Boolean,
    pending: Boolean,
    enabled: Boolean,
    tint: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(MissionMapController.flightModeLabelRes(mode))
    val contentColor = tint ?: if (selected) MapChrome.active else MapChrome.textPrimary
    MapPanelButton(
        text = if (pending) stringResource(R.string.flight_mode_applying, label) else label,
        onClick = onClick,
        modifier = modifier,
        iconRes = iconRes,
        enabled = enabled,
        contentColor = contentColor,
        highlighted = selected,
        height = 44.dp,
    )
}

/**
 * Press-and-hold confirmation. The action runs only after the full [holdDurationMs]; releasing
 * early cancels it, exactly like the former touch-listener implementation.
 */
@Composable
private fun HoldToConfirmButton(
    text: String,
    color: Color,
    holdDurationMs: Long,
    onConfirmed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val currentOnConfirmed by rememberUpdatedState(onConfirmed)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(MapChrome.pillShape)
            .background(MapChrome.pillInactive)
            .border(1.dp, color.copy(alpha = 0.6f), MapChrome.pillShape)
            .semantics {
                role = Role.Button
                contentDescription = text
            }
            .pointerInput(holdDurationMs) {
                awaitEachGesture {
                    awaitFirstDown()
                    val holdJob = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(holdDurationMs.toInt(), easing = LinearEasing))
                        delay(1)
                        currentOnConfirmed()
                    }
                    waitForUpOrCancellation()
                    holdJob.cancel()
                    scope.launch { progress.snapTo(0f) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(color.copy(alpha = 0.25f)),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Image(painterResource(R.drawable.ic_status_shield_24), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(color))
            Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Deliberate slide gesture: the thumb must reach the end of the track and the finger must have
 * travelled at least 65% of the track width. Tapping the end of the track is not enough.
 */
@Composable
internal fun DeliberateSlideTrack(
    label: String,
    enabled: Boolean,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
) {
    val scope = rememberCoroutineScope()
    val currentOnCompleted by rememberUpdatedState(onCompleted)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(MapChrome.sliderShape)
            .background(MapChrome.sliderPill)
            .border(1.dp, MapChrome.sliderPillStroke, MapChrome.sliderShape)
            .alpha(if (enabled) 1f else 0.55f)
            .semantics {
                contentDescription = label
                if (!enabled) disabled()
            },
    ) {
        val density = LocalDensity.current
        val thumbSize = 46.dp
        val trackPx = with(density) { (maxWidth - thumbSize - 8.dp).toPx() }.coerceAtLeast(1f)
        val widthPx = with(density) { maxWidth.toPx() }
        var position by remember { mutableFloatStateOf(0f) }
        var dragDistance by remember { mutableFloatStateOf(0f) }
        var returnJob by remember { mutableStateOf<Job?>(null) }
        fun animateBack() {
            returnJob?.cancel()
            returnJob = scope.launch {
                animate(position, 0f) { value, _ -> position = value }
            }
        }
        LaunchedEffect(resetKey, enabled) {
            returnJob?.cancel()
            position = 0f
        }

        Text(
            label,
            color = MapChrome.unselected,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .fillMaxHeight()
                .width(with(density) { (position + thumbSize.toPx()).toDp() })
                .clip(MapChrome.sliderShape)
                .background(MapChrome.active.copy(alpha = 0.22f)),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(position.roundToInt() + 4.dp.roundToPx(), 0) }
                .size(thumbSize)
                .pointerInput(enabled, trackPx) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            returnJob?.cancel()
                            dragDistance = 0f
                        },
                        onDragEnd = {
                            val reachedEnd = position >= trackPx * 0.98f
                            val deliberate = dragDistance >= widthPx * 0.65f
                            if (reachedEnd && deliberate) currentOnCompleted()
                            animateBack()
                        },
                        onDragCancel = { animateBack() },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            dragDistance += amount
                            position = (position + amount).coerceIn(0f, trackPx)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_slide_to_arm_thumb), null, Modifier.fillMaxSize())
        }
    }
}
