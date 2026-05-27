package com.example.droneservicesapp.ui.shell.coordinators

import android.content.Context
import androidx.preference.PreferenceManager
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.mavlink.MavlinkConfig
import com.example.droneservicesapp.mavserver.DroneViewModel

class MavlinkSessionCoordinator(
    private val context: Context,
    private val droneViewModel: DroneViewModel,
) {
    fun onResume() {
        droneViewModel.onAppForegrounded(readMavlinkConfig())
    }

    fun onPause() {
        droneViewModel.onAppBackgrounded()
    }

    private fun readMavlinkConfig(): MavlinkConfig {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val ifaceStr = sharedPreferences.getString(
            context.getString(R.string.mavlink_interface_pref),
            "UDP"
        ) ?: "UDP"

        val port = sharedPreferences.getString(
            context.getString(R.string.mavlink_lan_port_pref),
            "14550"
        )?.toIntOrNull() ?: 14550

        val targetHost = sharedPreferences.getString(
            context.getString(R.string.mavlink_target_host_pref),
            "192.168.199.33"
        )?.trim()?.takeIf { it.isNotEmpty() }

        val iface = runCatching { MavlinkConfig.InterfaceType.valueOf(ifaceStr.uppercase()) }
            .getOrDefault(MavlinkConfig.InterfaceType.UDP)

        return MavlinkConfig(interfaceType = iface, port = port, targetHost = targetHost)
    }
}
