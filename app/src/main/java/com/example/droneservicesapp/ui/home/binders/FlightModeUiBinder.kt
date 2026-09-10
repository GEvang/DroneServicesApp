package com.example.droneservicesapp.ui.home.binders

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.R
import com.example.droneservicesapp.mavserver.ArduCopterFlightMode
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.mavserver.FlightModeCommandState
import com.example.droneservicesapp.mavserver.FlightModeRequestResult
import com.example.droneservicesapp.ui.home.model.HomeTelemetryViewModel
import com.google.android.material.button.MaterialButton

class FlightModeUiBinder(
    private val rootView: View,
    private val droneViewModel: DroneViewModel,
    private val telemetryViewModel: HomeTelemetryViewModel,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var popup: PopupWindow? = null
    private var confirmationRunnable: Runnable? = null
    private var lastPresentedResult: FlightModeCommandState? = null

    fun bind(lifecycleOwner: LifecycleOwner) {
        rootView.findViewById<View>(R.id.top_flight_mode_card).setOnClickListener {
            val state = telemetryViewModel.homeTelemetryUiState.value
            if (state?.isFlightModeControlEnabled != true) {
                Toast.makeText(rootView.context, R.string.flight_mode_unavailable, Toast.LENGTH_SHORT).show()
            } else {
                showSelector()
            }
        }

        telemetryViewModel.homeTelemetryUiState.observe(lifecycleOwner) {
            popup?.contentView?.let(::renderPopup)
        }
        droneViewModel.flightModeCommandState.observe(lifecycleOwner, ::presentCommandResult)
    }

    fun dismiss() {
        cancelConfirmation()
        popup?.dismiss()
        popup = null
    }

    private fun showSelector() {
        if (popup?.isShowing == true) {
            dismiss()
            return
        }
        val content = LayoutInflater.from(rootView.context)
            .inflate(R.layout.view_flight_mode_popup, rootView as? ViewGroup, false)
        val popupWidth = (340 * rootView.resources.displayMetrics.density).toInt()
        popup = PopupWindow(
            content,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = 14 * rootView.resources.displayMetrics.density
            setOnDismissListener {
                cancelConfirmation()
                popup = null
            }
        }

        modeButtons(content).forEach { (mode, button) ->
            button.setOnClickListener { selectMode(content, mode) }
        }
        renderPopup(content)

        val anchor = rootView.findViewById<View>(R.id.top_flight_mode_card)
        val horizontalOffset = (anchor.width - popupWidth).coerceAtMost(0)
        popup?.showAsDropDown(anchor, horizontalOffset, 4)
    }

    private fun selectMode(content: View, mode: ArduCopterFlightMode) {
        val currentMode = telemetryViewModel.homeTelemetryUiState.value?.flightModeCustomMode
        if (currentMode == mode.customMode) return
        if (mode.requiresUploadedMission && droneViewModel.missionItems.value.isNullOrEmpty()) {
            toast(R.string.flight_mode_auto_requires_mission)
            return
        }
        if (mode.holdDurationMs == 0L) {
            submit(mode)
            return
        }

        val confirmButton = content.findViewById<MaterialButton>(R.id.flight_mode_confirm_button)
        confirmButton.visibility = View.VISIBLE
        confirmButton.text = rootView.context.getString(
            R.string.flight_mode_hold_to_apply,
            mode.holdDurationMs / 1_000f,
            modeLabel(mode)
        )
        confirmButton.setTextColor(
            ContextCompat.getColor(
                rootView.context,
                if (mode == ArduCopterFlightMode.LAND) R.color.ds_color_shell_danger
                else R.color.ds_color_shell_warning
            )
        )
        bindHoldConfirmation(confirmButton, mode)
    }

    private fun bindHoldConfirmation(button: MaterialButton, mode: ArduCopterFlightMode) {
        cancelConfirmation()
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    confirmationRunnable = Runnable {
                        view.isPressed = false
                        submit(mode)
                    }.also { handler.postDelayed(it, mode.holdDurationMs) }
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_OUTSIDE -> {
                    view.isPressed = false
                    cancelConfirmation()
                    true
                }
                else -> true
            }
        }
    }

    private fun submit(mode: ArduCopterFlightMode) {
        cancelConfirmation()
        when (droneViewModel.requestFlightMode(mode)) {
            FlightModeRequestResult.Sent -> dismiss()
            FlightModeRequestResult.Disconnected -> toast(R.string.flight_mode_unavailable)
            FlightModeRequestResult.TargetUnavailable -> toast(R.string.flight_mode_target_unavailable)
            FlightModeRequestResult.AlreadyPending -> toast(R.string.flight_mode_request_pending)
        }
    }

    private fun renderPopup(content: View) {
        val telemetry = telemetryViewModel.homeTelemetryUiState.value ?: return
        val commandState = telemetry.flightModeCommandState
        val pendingMode = (commandState as? FlightModeCommandState.Pending)?.requestedMode
        content.findViewById<TextView>(R.id.flight_mode_current_value).text =
            rootView.context.getString(R.string.flight_mode_current_format, telemetry.flightModeText)
        modeButtons(content).forEach { (mode, button) ->
            val selected = telemetry.flightModeCustomMode == mode.customMode
            val pending = pendingMode == mode
            val activeColor = ContextCompat.getColor(rootView.context, R.color.ds_color_shell_active)
            val normalColor = ContextCompat.getColor(rootView.context, R.color.ds_color_text_primary)
            button.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    rootView.context,
                    if (selected) R.color.ds_color_shell_selected_surface else R.color.ds_color_shell_overlay_strong
                )
            )
            button.strokeWidth = 1
            button.strokeColor = ColorStateList.valueOf(
                ContextCompat.getColor(
                    rootView.context,
                    if (selected) R.color.ds_color_shell_active else R.color.ds_color_shell_stroke
                )
            )
            if (mode != ArduCopterFlightMode.RTL &&
                mode != ArduCopterFlightMode.LAND &&
                mode != ArduCopterFlightMode.BRAKE
            ) {
                button.setTextColor(if (selected) activeColor else normalColor)
                button.iconTint = ColorStateList.valueOf(if (selected) activeColor else normalColor)
            }
            button.text = if (pending) {
                rootView.context.getString(R.string.flight_mode_applying, modeLabel(mode))
            } else {
                modeLabel(mode)
            }
            button.isEnabled = commandState !is FlightModeCommandState.Pending
            button.alpha = if (button.isEnabled) 1f else 0.62f
        }
    }

    private fun presentCommandResult(state: FlightModeCommandState) {
        if (state === lastPresentedResult) return
        lastPresentedResult = state
        when (state) {
            is FlightModeCommandState.Succeeded -> {
                Toast.makeText(
                    rootView.context,
                    rootView.context.getString(R.string.flight_mode_applied, modeLabel(state.mode)),
                    Toast.LENGTH_SHORT
                ).show()
                droneViewModel.clearFlightModeCommandResult()
            }
            is FlightModeCommandState.Failed -> {
                Toast.makeText(
                    rootView.context,
                    rootView.context.getString(
                        R.string.flight_mode_failed,
                        modeLabel(state.requestedMode),
                        state.reason
                    ),
                    Toast.LENGTH_LONG
                ).show()
                droneViewModel.clearFlightModeCommandResult()
            }
            else -> Unit
        }
    }

    private fun modeButtons(content: View): List<Pair<ArduCopterFlightMode, MaterialButton>> = listOf(
        ArduCopterFlightMode.LOITER to content.findViewById(R.id.flight_mode_loiter_button),
        ArduCopterFlightMode.AUTO to content.findViewById(R.id.flight_mode_auto_button),
        ArduCopterFlightMode.BRAKE to content.findViewById(R.id.flight_mode_brake_button),
        ArduCopterFlightMode.GUIDED to content.findViewById(R.id.flight_mode_guided_button),
        ArduCopterFlightMode.RTL to content.findViewById(R.id.flight_mode_rtl_button),
        ArduCopterFlightMode.LAND to content.findViewById(R.id.flight_mode_land_button),
    )

    private fun modeLabel(mode: ArduCopterFlightMode): String = rootView.context.getString(
        when (mode) {
            ArduCopterFlightMode.AUTO -> R.string.flight_mode_auto
            ArduCopterFlightMode.GUIDED -> R.string.flight_mode_guided
            ArduCopterFlightMode.LOITER -> R.string.flight_mode_loiter
            ArduCopterFlightMode.RTL -> R.string.flight_mode_rtl
            ArduCopterFlightMode.LAND -> R.string.flight_mode_land
            ArduCopterFlightMode.BRAKE -> R.string.flight_mode_brake
        }
    )

    private fun cancelConfirmation() {
        confirmationRunnable?.let(handler::removeCallbacks)
        confirmationRunnable = null
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(rootView.context, messageRes, Toast.LENGTH_SHORT).show()
    }
}
