package com.example.droneservicesapp.ui.shell.binders

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.droneservicesapp.R
import com.example.droneservicesapp.databinding.ActivityMainBinding
import com.example.droneservicesapp.mavserver.DroneViewModel
import java.math.RoundingMode
import java.text.DecimalFormat

class ShellToolbarBinder(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val droneViewModel: DroneViewModel,
) {
    private val toolbar by lazy { binding.appBarMain.customToolbar }

    fun bind(lifecycleOwner: LifecycleOwner) {
        activity.setSupportActionBar(toolbar)
        toolbar.background = ContextCompat.getDrawable(activity, R.drawable.action_bar_bg_red)

        droneViewModel.conStateLiveData.observe(lifecycleOwner) { connState ->
            toolbar.setBackgroundResource(
                if (connState) R.drawable.action_bar_bg_green else R.drawable.action_bar_bg_red
            )
        }

        droneViewModel.droneBatteryPercentage.observe(lifecycleOwner) { batteryPercentage ->
            if (droneViewModel.conStateLiveData.value != true) return@observe

            val df = DecimalFormat("#.##").apply { roundingMode = RoundingMode.DOWN }
            toolbar.findViewById<TextView>(R.id.drone_battery_percentage_text).text =
                "${df.format(batteryPercentage * 100.0F)}%"

            val iconRes = when {
                batteryPercentage == -1.0F -> R.drawable.ic_baseline_battery_alert_24
                batteryPercentage >= 1.0F -> R.drawable.ic_baseline_battery_full_24
                batteryPercentage >= 0.7F -> R.drawable.ic_baseline_battery_6_bar_24
                batteryPercentage >= 0.4F -> R.drawable.ic_baseline_battery_4_bar_24
                batteryPercentage >= 0.25F -> R.drawable.ic_baseline_battery_3_bar_24
                else -> R.drawable.ic_baseline_battery_2_bar_24
            }
            toolbar.findViewById<ImageView>(R.id.drone_battery_image).setImageResource(iconRes)
        }

        droneViewModel.droneLocationLiveData.observe(lifecycleOwner) { location ->
            if (droneViewModel.conStateLiveData.value != true) return@observe
            toolbar.findViewById<TextView>(R.id.drone_alt_txt).text = "${location.altitude.toInt()}m"
        }

        droneViewModel.liquidLevel.observe(lifecycleOwner) { liquidLevel ->
            toolbar.findViewById<TextView>(R.id.sprayer_flow_text).text = "$liquidLevel%"
        }

        droneViewModel.armedState.observe(lifecycleOwner) { armedState ->
            val resId = if (armedState) R.string.armed else R.string.disarmed
            toolbar.findViewById<TextView>(R.id.drone_arm_text).text = activity.getString(resId)
        }

        droneViewModel.uploadProgressPercent.observe(lifecycleOwner) { percent ->
            val uploadTxt = toolbar.findViewById<TextView>(R.id.mission_upload_progress_text)
            when {
                percent in 1..99 -> {
                    uploadTxt.visibility = View.VISIBLE
                    uploadTxt.text = "Uploading ${percent}%"
                }
                percent >= 100 -> {
                    uploadTxt.visibility = View.GONE
                }
                else -> {
                    uploadTxt.visibility = View.GONE
                }
            }
        }
    }
}
