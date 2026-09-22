package com.example.droneservicesapp.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatSeekBar

/**
 * SeekBar with a stable, full-row touch surface. The layout supplies the actual
 * hit area: using a TouchDelegate here made adjacent scroll content repeatedly
 * take ownership of a drag gesture.
 */
class TouchDelegateSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.seekBarStyle
) : AppCompatSeekBar(context, attrs, defStyleAttr) {

    /**
     * SeekBar's platform thumb can have optical insets that make its effective drag line sit
     * above or below the painted track. Normalizing Y keeps the horizontal value under the
     * user's finger anywhere inside the expanded row.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }

        val centeredEvent = MotionEvent.obtain(event)
        centeredEvent.setLocation(event.x, height / 2f)
        return try {
            super.onTouchEvent(centeredEvent)
        } finally {
            centeredEvent.recycle()
        }
    }
}
