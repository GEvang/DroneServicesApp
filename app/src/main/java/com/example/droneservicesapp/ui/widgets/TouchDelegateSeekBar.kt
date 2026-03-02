package com.example.droneservicesapp.ui.widgets

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.TouchDelegate
import androidx.appcompat.widget.AppCompatSeekBar

/**
 * Custom SeekBar that expands its touch hit-rect on all sides using TouchDelegate.
 * This makes it easier to interact with the seek bar on touch screens.
 */
class TouchDelegateSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.seekBarStyle
) : AppCompatSeekBar(context, attrs, defStyleAttr) {

    private var extraTouchDp: Int = 18

    /**
     * Set the amount of extra touch padding in dp on all sides.
     * Default is 18dp.
     *
     * @param dp The extra padding in dp
     */
    fun setExtraTouchDp(dp: Int) {
        this.extraTouchDp = dp
        setupTouchDelegate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Post to ensure layout has completed before setting up the delegate
        post { setupTouchDelegate() }
    }

    /**
     * Setup the touch delegate to expand the touch area by the specified dp value.
     */
    private fun setupTouchDelegate() {
        val parent = parent as? android.view.View ?: return

        // Convert dp to pixels
        val extraTouchPx = (extraTouchDp * resources.displayMetrics.density).toInt()

        // Get the bounds of this view
        val delegateArea = Rect()
        getHitRect(delegateArea)

        // Expand the rect on all sides
        delegateArea.left -= extraTouchPx
        delegateArea.top -= extraTouchPx
        delegateArea.right += extraTouchPx
        delegateArea.bottom += extraTouchPx

        // Create and set the touch delegate on the parent
        parent.touchDelegate = TouchDelegate(delegateArea, this)
    }
}