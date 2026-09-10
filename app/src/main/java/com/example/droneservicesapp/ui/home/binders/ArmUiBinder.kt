package com.example.droneservicesapp.ui.home.binders

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.R
import com.example.droneservicesapp.mavserver.ArmCommandState
import com.example.droneservicesapp.mavserver.ArmRequestResult
import com.example.droneservicesapp.mavserver.DroneViewModel

/** Presents a guarded slide gesture before sending MAV_CMD_COMPONENT_ARM_DISARM. */
class ArmUiBinder(
    private val rootView: View,
    private val droneViewModel: DroneViewModel,
) {
    private var popup: PopupWindow? = null
    private var dragStartX = 0f
    private var lastPresentedState: ArmCommandState? = null

    fun bind(lifecycleOwner: LifecycleOwner) {
        rootView.findViewById<View>(R.id.top_armed_card).setOnClickListener {
            when {
                droneViewModel.conStateLiveData.value != true -> toast(R.string.arm_unavailable)
                droneViewModel.armedState.value == true -> toast(R.string.arm_already_armed)
                else -> showSlider()
            }
        }
        droneViewModel.armCommandState.observe(lifecycleOwner, ::renderCommandState)
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }

    private fun showSlider() {
        if (popup?.isShowing == true) {
            dismiss()
            return
        }
        val content = LayoutInflater.from(rootView.context)
            .inflate(R.layout.view_slide_to_arm_popup, rootView as? ViewGroup, false)
        val width = (340 * rootView.resources.displayMetrics.density).toInt()
        popup = PopupWindow(content, width, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = 14 * rootView.resources.displayMetrics.density
            setOnDismissListener { popup = null }
        }
        val slider = content.findViewById<SeekBar>(R.id.arm_slider)
        slider.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) dragStartX = event.x
            if (event.actionMasked == MotionEvent.ACTION_UP) slider.performClick()
            false
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val deliberateDistance = seekBar.width * 0.65f
                if (seekBar.width > 0 && seekBar.progress >= seekBar.max) {
                    // A full-width movement is required; tapping the end of the track is not enough.
                    val lastTouchX = seekBar.width * (seekBar.progress / seekBar.max.toFloat())
                    if (lastTouchX - dragStartX >= deliberateDistance) submitArm(content)
                    else seekBar.progress = 0
                } else {
                    seekBar.progress = 0
                }
            }
        })
        renderPopup(content, droneViewModel.armCommandState.value ?: ArmCommandState.Idle)
        val anchor = rootView.findViewById<View>(R.id.top_armed_card)
        popup?.showAsDropDown(anchor, (anchor.width - width).coerceAtMost(0), 4)
    }

    private fun submitArm(content: View) {
        when (droneViewModel.requestArm()) {
            ArmRequestResult.SENT -> renderPopup(content, ArmCommandState.Pending())
            ArmRequestResult.DISCONNECTED -> toast(R.string.arm_unavailable)
            ArmRequestResult.TARGET_UNAVAILABLE -> toast(R.string.arm_target_unavailable)
            ArmRequestResult.ALREADY_ARMED -> toast(R.string.arm_already_armed)
            ArmRequestResult.ALREADY_PENDING -> toast(R.string.arm_request_pending)
        }
    }

    private fun renderCommandState(state: ArmCommandState) {
        popup?.contentView?.let { renderPopup(it, state) }
        if (state == lastPresentedState) return
        lastPresentedState = state
        when (state) {
            ArmCommandState.Succeeded -> {
                toast(R.string.arm_succeeded)
                dismiss()
                droneViewModel.clearArmCommandResult()
            }
            is ArmCommandState.Failed -> {
                Toast.makeText(
                    rootView.context,
                    rootView.context.getString(R.string.arm_failed, state.reason),
                    Toast.LENGTH_LONG,
                ).show()
                popup?.contentView?.findViewById<SeekBar>(R.id.arm_slider)?.progress = 0
                droneViewModel.clearArmCommandResult()
            }
            else -> Unit
        }
    }

    private fun renderPopup(content: View, state: ArmCommandState) {
        val pending = state is ArmCommandState.Pending
        content.findViewById<SeekBar>(R.id.arm_slider).isEnabled = !pending
        content.findViewById<View>(R.id.arm_slider_container).alpha = if (pending) 0.55f else 1f
        content.findViewById<ProgressBar>(R.id.arm_slider_progress).visibility =
            if (pending) View.VISIBLE else View.GONE
        content.findViewById<TextView>(R.id.arm_slider_instruction).setText(
            if (pending) R.string.arm_waiting_confirmation else R.string.arm_slider_instruction
        )
    }

    private fun toast(message: Int) =
        Toast.makeText(rootView.context, message, Toast.LENGTH_SHORT).show()
}
