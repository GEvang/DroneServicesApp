package com.example.droneservicesapp.data.rtk

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.net.SocketFactory

class RtkInternetMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val _isInternetAvailable = MutableStateFlow(currentInternetAvailable())
    val isInternetAvailable: StateFlow<Boolean> = _isInternetAvailable.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isInternetAvailable.value = currentInternetAvailable()
        }

        override fun onLost(network: Network) {
            _isInternetAvailable.value = currentInternetAvailable()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _isInternetAvailable.value = currentInternetAvailable()
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager?.registerNetworkCallback(request, callback)
    }

    fun shutdown() {
        runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
    }

    fun currentInternetSocketFactory(): SocketFactory? {
        return preferredInternetNetwork()?.socketFactory
    }

    private fun currentInternetAvailable(): Boolean {
        return preferredInternetNetwork() != null
    }

    private fun preferredInternetNetwork(): Network? {
        val manager = connectivityManager ?: return null
        val networks = manager.allNetworks

        val internetNetworks = networks.mapNotNull { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return@mapNotNull null
            }
            network to capabilities
        }

        return internetNetworks.firstOrNull { (_, capabilities) ->
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }?.first
            ?: internetNetworks.firstOrNull { (_, capabilities) ->
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }?.first
            ?: internetNetworks.firstOrNull()?.first
    }
}
