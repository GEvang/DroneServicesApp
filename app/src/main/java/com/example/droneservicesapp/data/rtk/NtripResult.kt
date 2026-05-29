package com.example.droneservicesapp.data.rtk

sealed class NtripResult {
    data class SourceTableSuccess(val mountpoints: List<RtkMountpoint>) : NtripResult()
    object ConnectionSuccess : NtripResult()
    object AuthFailure : NtripResult()
    object MountpointNotFound : NtripResult()
    data class NetworkFailure(val message: String) : NtripResult()
    data class InvalidConfig(val message: String) : NtripResult()
    data class ProtocolFailure(val message: String) : NtripResult()
}
