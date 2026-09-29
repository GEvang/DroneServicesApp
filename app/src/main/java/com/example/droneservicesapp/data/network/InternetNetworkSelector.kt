package com.example.droneservicesapp.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/** Selects internet independently from the Wi-Fi network reserved for the aircraft link. */
object InternetNetworkSelector {
    fun select(context: Context): Network? {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
            ?: return null
        val candidates = manager.allNetworks.mapNotNull { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return@mapNotNull null
            }
            network to capabilities
        }
        return candidates.firstOrNull { (_, capabilities) ->
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }?.first ?: candidates.firstOrNull { (_, capabilities) ->
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }?.first ?: candidates.firstOrNull()?.first
    }
}
