package com.example.droneservicesapp.ui.shell.coordinators

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.preference.PreferenceManager
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.mavlink.MavlinkConfig
import com.example.droneservicesapp.mavserver.DroneViewModel

class MavlinkSessionCoordinator(
    private val context: Context,
    private val droneViewModel: DroneViewModel,
) {
    private companion object {
        private const val TAG = "MavlinkSessionCoordinator"
    }

    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

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
            ""
        )?.trim()?.takeIf { it.isNotEmpty() }

        val iface = runCatching { MavlinkConfig.InterfaceType.valueOf(ifaceStr.uppercase()) }
            .getOrDefault(MavlinkConfig.InterfaceType.UDP)

        val network = selectWifiNetwork()
        Log.i(
            TAG,
            "MAVLink config iface=$iface port=$port targetHost=${targetHost ?: "<auto>"} network=${network?.networkHandle ?: "<default>"}"
        )
        return MavlinkConfig(interfaceType = iface, port = port, targetHost = targetHost, network = network)
    }

    private fun selectWifiNetwork(): Network? {
        val manager = connectivityManager ?: return null
        val networks = manager.allNetworks.mapNotNull { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@mapNotNull null
            network to capabilities
        }

        val wifi = networks.firstOrNull { (_, capabilities) ->
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }

        if (wifi != null) {
            Log.i(
                TAG,
                "selected Wi-Fi network for MAVLink network=${wifi.first.networkHandle} validated=${wifi.second.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)} internet=${wifi.second.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}"
            )
            return wifi.first
        }

        Log.w(TAG, "no Wi-Fi network available for MAVLink; using default network")
        return null
    }
}
