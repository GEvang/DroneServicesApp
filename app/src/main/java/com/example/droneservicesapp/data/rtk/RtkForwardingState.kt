package com.example.droneservicesapp.data.rtk

sealed class RtkForwardingState {
    object Idle : RtkForwardingState()
    object WaitingForDrone : RtkForwardingState()
    object ConnectingToCaster : RtkForwardingState()
    object Streaming : RtkForwardingState()
    data class InvalidConfig(val message: String) : RtkForwardingState()
    data class AuthFailed(val message: String) : RtkForwardingState()
    data class NetworkError(val message: String) : RtkForwardingState()
    data class ProtocolError(val message: String) : RtkForwardingState()
    object Stopped : RtkForwardingState()
}
