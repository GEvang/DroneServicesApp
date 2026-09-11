package com.example.droneservicesapp.ui.shell.coordinators

import android.content.Context
import android.content.SharedPreferences
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
) : SharedPreferences.OnSharedPreferenceChangeListener {
    private companion object {
        private const val TAG = "MavlinkSessionCoordinator"
    }

    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val mavlinkPreferenceKeys by lazy {
        setOf(
            R.string.mavlink_interface_pref,
            R.string.mavlink_lan_port_pref,
            R.string.mavlink_target_host_pref,
            R.string.mavlink_target_port_pref,
            R.string.mavlink_gcs_system_id_pref,
            R.string.mavlink_bridge_enabled_pref,
            R.string.mavlink_bridge_host_pref,
            R.string.mavlink_bridge_port_pref,
        ).map(context::getString).toSet()
    }
    private var listeningForChanges = false

    fun onResume() {
        if (!listeningForChanges) {
            sharedPreferences.registerOnSharedPreferenceChangeListener(this)
            listeningForChanges = true
        }
        droneViewModel.onAppForegrounded(readMavlinkConfig())
    }

    fun onPause() {
        if (listeningForChanges) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
            listeningForChanges = false
        }
        droneViewModel.onAppBackgrounded()
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences?, key: String?) {
        if (key == null || key !in mavlinkPreferenceKeys) return
        Log.i(TAG, "MAVLink preference changed key=$key; applying connection configuration")
        droneViewModel.onAppForegrounded(readMavlinkConfig())
    }

    private fun readMavlinkConfig(): MavlinkConfig {
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

        val targetPort = sharedPreferences.getString(
            context.getString(R.string.mavlink_target_port_pref),
            "14550"
        )?.toIntOrNull() ?: 14550

        val gcsSystemId = sharedPreferences.getString(
            context.getString(R.string.mavlink_gcs_system_id_pref),
            "254"
        )?.toIntOrNull()?.takeIf { it in 1..255 } ?: 254

        val qgcBridgeEnabled = sharedPreferences.getBoolean(
            context.getString(R.string.mavlink_bridge_enabled_pref),
            false
        )

        val qgcBridgeHost = sharedPreferences.getString(
            context.getString(R.string.mavlink_bridge_host_pref),
            ""
        )?.trim()?.takeIf { it.isNotEmpty() }

        val qgcBridgePort = sharedPreferences.getString(
            context.getString(R.string.mavlink_bridge_port_pref),
            "14550"
        )?.toIntOrNull() ?: 14550

        val iface = runCatching { MavlinkConfig.InterfaceType.valueOf(ifaceStr.uppercase()) }
            .getOrDefault(MavlinkConfig.InterfaceType.UDP)

        val network = selectMavlinkNetwork(targetHost)
        Log.i(
            TAG,
            "MAVLink config iface=$iface localPort=$port targetHost=${targetHost ?: "<auto>"} targetPort=$targetPort gcsSystemId=$gcsSystemId qgcBridge=$qgcBridgeEnabled qgcHost=${qgcBridgeHost ?: "<unset>"} qgcPort=$qgcBridgePort network=${network?.networkHandle ?: "<default>"}"
        )
        return MavlinkConfig(
            interfaceType = iface,
            port = port,
            targetHost = targetHost,
            targetPort = targetPort,
            gcsSystemId = gcsSystemId,
            qgcBridgeEnabled = qgcBridgeEnabled,
            qgcBridgeHost = qgcBridgeHost,
            qgcBridgePort = qgcBridgePort,
            network = network
        )
    }

    private fun selectMavlinkNetwork(targetHost: String?): Network? {
        if (targetHost != null) {
            Log.i(TAG, "explicit MAVLink target configured; using default route for targetHost=$targetHost")
            return null
        }
        return selectWifiNetwork()
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
