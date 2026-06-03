package com.example.droneservicesapp.ui.home.binders

import android.view.MotionEvent
import android.view.View
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.SeekBar
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

class MissionParamsInputBinder(
    private val views: MissionParamsViews,
    private val lifecycleOwner: LifecycleOwner,
    private val activityViewModel: MainActivityViewModel,
) {
    private val minSpeed = 1.0
    private val maxSpeed = 5.0
    private val speedStep = 0.5

    fun bind() {
        bindSeekbars()
        bindSpeedButtons()
        bindIconButtons()
    }

    private fun bindSeekbars() {
        bindSeekbar(
            touchTarget = views.angleSliderRow,
            seekbar = views.angleSeekbar,
            valueView = views.angleValue,
            target = activityViewModel.angleProgress,
            updateValue = activityViewModel::updateMissionAngle
        )
        bindSeekbar(
            touchTarget = views.lineDistanceSliderRow,
            seekbar = views.lineDistanceSeekbar,
            valueView = views.lineDistanceValue,
            target = activityViewModel.lineDistanceProgress,
            updateValue = activityViewModel::updateLineSpacing
        )
        bindSeekbar(
            touchTarget = views.altitudeSliderRow,
            seekbar = views.altitudeSeekbar,
            valueView = views.altitudeValue,
            target = activityViewModel.flightAltProgress,
            updateValue = activityViewModel::updateAltitude
        )
        bindSeekbar(
            touchTarget = views.sprayerSliderRow,
            seekbar = views.sprayerSeekbar,
            valueView = views.sprayerValue,
            target = activityViewModel.sprayerProgress,
            updateValue = activityViewModel::updateSprayIntensity
        )
    }

    private fun bindSpeedButtons() {
        views.speedMinusButton.setOnClickListener {
            val cur = activityViewModel.flightSpeed.value ?: minSpeed
            if (cur > minSpeed) {
                activityViewModel.updateMissionSpeed(cur - speedStep)
            }
        }

        views.speedPlusButton.setOnClickListener {
            val cur = activityViewModel.flightSpeed.value ?: minSpeed
            if (cur < maxSpeed) {
                activityViewModel.updateMissionSpeed(cur + speedStep)
            }
        }
    }

    private fun bindIconButtons() {
        bindIncrementButtons(views.angleMinusButton, views.anglePlusButton, views.angleSeekbar)
        bindIncrementButtons(
            views.lineDistanceMinusButton,
            views.lineDistancePlusButton,
            views.lineDistanceSeekbar
        )
        bindIncrementButtons(
            views.altitudeMinusButton,
            views.altitudePlusButton,
            views.altitudeSeekbar
        )
        bindIncrementButtons(
            views.sprayerMinusButton,
            views.sprayerPlusButton,
            views.sprayerSeekbar
        )
    }

    private fun bindIncrementButtons(minusButton: android.view.View, plusButton: android.view.View, seekbar: SeekBar) {
        minusButton.setOnClickListener {
            val newProgress = (seekbar.progress - 1).coerceAtLeast(seekbar.min)
            seekbar.progress = newProgress
        }
        plusButton.setOnClickListener {
            val newProgress = (seekbar.progress + 1).coerceAtMost(seekbar.max)
            seekbar.progress = newProgress
        }
    }

    private fun bindSeekbar(
        touchTarget: View,
        seekbar: SeekBar,
        valueView: EditText,
        target: MutableLiveData<Double>,
        updateValue: (Int) -> Unit,
    ) {
        var suppressChange = false
        val initial = target.value?.toInt() ?: 0
        seekbar.progress = initial
        valueView.setText(initial.toString())
        bindExpandedTouchTarget(touchTarget, seekbar)

        target.observe(lifecycleOwner) { value ->
            val progress = value?.toInt() ?: return@observe
            if (seekbar.progress == progress && valueView.text.toString() == progress.toString()) {
                return@observe
            }
            suppressChange = true
            seekbar.progress = progress
            valueView.setText(progress.toString())
            suppressChange = false
        }

        seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (suppressChange) return
                valueView.setText(progress.toString())
                updateValue(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        valueView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                valueView.setSelection(valueView.length())
            }

            override fun afterTextChanged(s: Editable?) {
                if (suppressChange) return
                val progress = s.toString().toIntOrNull() ?: return
                seekbar.setProgress(progress, true)
            }
        })
    }

    private fun bindExpandedTouchTarget(touchTarget: View, seekbar: SeekBar) {
        touchTarget.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                seekbar.parent?.requestDisallowInterceptTouchEvent(true)
            }

            val forwardedEvent = MotionEvent.obtain(event)
            val seekbarLocation = IntArray(2)
            val touchTargetLocation = IntArray(2)
            seekbar.getLocationOnScreen(seekbarLocation)
            touchTarget.getLocationOnScreen(touchTargetLocation)
            forwardedEvent.offsetLocation(
                (touchTargetLocation[0] - seekbarLocation[0]).toFloat(),
                (touchTargetLocation[1] - seekbarLocation[1]).toFloat()
            )
            val handled = seekbar.dispatchTouchEvent(forwardedEvent)
            forwardedEvent.recycle()

            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                seekbar.parent?.requestDisallowInterceptTouchEvent(false)
            }

            handled
        }
    }
}
